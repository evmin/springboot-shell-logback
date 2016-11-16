import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.AppenderBase
import org.crsh.cli.*
import org.crsh.cli.completers.EnumCompleter
import org.crsh.cli.descriptor.ParameterDescriptor
import org.crsh.cli.spi.Completer
import org.crsh.cli.spi.Completion
import org.slf4j.LoggerFactory

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.regex.Pattern

@Usage("logback commands")
public class log {

    static boolean hasAppenders(ch.qos.logback.classic.Logger logger) {
        Iterator<Appender<LoggingEvent>> it = logger.iteratorForAppenders();
        return it.hasNext();
    }

    static Collection<Logger> getLoggers() {
        List<Logger> resultList = new ArrayList<Logger>();
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (Logger log : lc.getLoggerList()) {
            resultList.add(log)
        }
        return resultList;
    }

    @Usage("list the available loggers")
    @Man("""\
The log ls command list all the available loggers, for instance:

   % log ls
   ROOT
   org.springframework.web
   ...

Command takes in a filter argument (Java regular expression):

   % log ls javax.*
   javax.management.mbeanserver
   javax.management.modelmbean

It is also possible to limit the output to the explicitly declared loggers,
either in the config file or programmatically:

   % log ls -d
   javax.management.mbeanserver

Command can autocomplete with <TAB>

""")
    @Command
    public void ls(@FilterArg String filter, @DeclaredOnlyOpt Boolean declaredOnly) {

        Boolean _declaredOnly = (null != declaredOnly && Boolean.valueOf(declaredOnly))

        // Regex filter
        def pattern = Pattern.compile(filter ?: ".*");

        loggers.each {
            def matcher = it.getName() =~ pattern;
            if (matcher.matches()) {
                if (!_declaredOnly) {
                    out.println(it.getName())
                } else if (it.getLevel() != null || hasAppenders(it)) {
                    out.println(it.getName())
                }
            }
        }
    }


    @Man("""\
The command starts tailing the given logger.
If no logger provided, ROOT is used.

% log tail
14:43:07.068 | INFO  | o.a.camel.spring.SpringCamelContext | Started

Command formats the output using the payout defined for "FILE" appender of the root logger (if available).

The tail process will end upon interruption (ctrl-c).""")
    @Usage("tail <logger>")
    @Command
    public void tail(
            @Usage("the logger name to tail or empty for the root logger")
            @LoggerArg String name) {

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory()
        String pattern = "%d{HH:mm:ss.SSS} | %-5level | %logger{36} | %msg"

        def logger
        if (null != name && !name.isEmpty())
            logger = (Logger) LoggerFactory.getLogger(name)
        else
            logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)


        PatternLayout layout = new PatternLayout()
        layout.setPattern(pattern)
        layout.setContext(lc)
        layout.start()

        def appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent eventObject) {
                out.println(layout.doLayout(eventObject));
                out.flush()
            }
        }

        appender.setContext(lc)
        appender.start()

        logger.addAppender(appender)

        def lock = new Object();
        try {
            synchronized (lock) {
                // Wait until ctrl-c
                lock.wait();
            }
        } finally {
            logger.detachAppender(appender)
        }
    }

    @Man("""\
The command sets the level of a logger.

% log set LEVEL LOGGER

If LOGGER is not defined, ROOT will be updated.

""")
    @Usage("Sets the level of the given logger")
    @Command
    public void set(@LevelOpt @Required Level level, @LoggerArg String name) {
        def logger
        if (null != name && !name.isEmpty())
            logger = (Logger) LoggerFactory.getLogger(name)
        else
            logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)

        logger.setLevel(level.getLogLevel())
    }
}

/**
 * Logger Name Completer
 */
class LoggerCompleter implements Completer {

    Completion complete(ParameterDescriptor parameter, String prefix) throws Exception {
        def builder = new Completion.Builder(prefix);
        log.loggers.each() {
            if (it.getName().startsWith(prefix)) {
                builder.add(it.getName().substring(prefix.length()), true);
            }
        }
        return builder.build();
    }

}

enum Level {
    OFF(ch.qos.logback.classic.Level.OFF),
    TRACE(ch.qos.logback.classic.Level.TRACE),
    DEBUG(ch.qos.logback.classic.Level.DEBUG),
    INFO(ch.qos.logback.classic.Level.INFO),
    WARN(ch.qos.logback.classic.Level.WARN),
    ERROR(ch.qos.logback.classic.Level.ERROR);

    final ch.qos.logback.classic.Level level

    Level(ch.qos.logback.classic.Level level) {
        this.level = level
    }

    public ch.qos.logback.classic.Level getLogLevel(){
       return this.level;
    }
}


@Retention(RetentionPolicy.RUNTIME)
@Usage("a regexp filter")
@Man("A regular expression used to filter the loggers")
@Argument(name = "filter", completer = LoggerCompleter.class)
@interface FilterArg {}

@Retention(RetentionPolicy.RUNTIME)
@Usage("Declared in the config file only filter")
@Man("To display only the loggers edeclared in the configuration file")
@Option(names = ["d", "declared"])
@interface DeclaredOnlyOpt {}


@Retention(RetentionPolicy.RUNTIME)
@Usage("the logger name")
@Man("The name of the logger")
@Argument(name = "name", completer = LoggerCompleter.class)
@interface LoggerArg {}

@Retention(RetentionPolicy.RUNTIME)
@Usage("the logger level")
@Man("The logger level to assign among {trace, debug, info, warn, error}")
@Argument(name = "level", completer = EnumCompleter)
@interface LevelOpt {}




