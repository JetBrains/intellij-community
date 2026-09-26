// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper which uses either IDE logging subsystem (if available) or java.util.logging.
 */
@NonExtendable
public abstract class LoggerRt {
  private interface Factory {
    LoggerRt getInstance(String category);
  }

  private static Factory ourFactory;

  private static synchronized Factory getFactory() {
    if (ourFactory == null) {
      try {
        ourFactory = new IdeaFactory();
      }
      catch (Throwable t) {
        try {
          ourFactory = new Slf4JFactory();
        }
        catch (Throwable t2) {
          ourFactory = new JavaFactory();
        }
      }
    }
    return ourFactory;
  }

  @NotNull
  public static LoggerRt getInstance(@NotNull String category) {
    return getFactory().getInstance(category);
  }

  @NotNull
  public static LoggerRt getInstance(@NotNull Class<?> clazz) {
    return getInstance('#' + clazz.getName());
  }

  public void info(@Nullable String message) {
    info(message, null);
  }

  public void info(@NotNull Throwable t) {
    info(t.getMessage(), t);
  }

  public void warn(@Nullable String message) {
    warn(message, null);
  }

  public void warn(@NotNull Throwable t) {
    warn(t.getMessage(), t);
  }

  public void error(@Nullable String message) {
    error(message, null);
  }

  public void error(@NotNull Throwable t) {
    error(t.getMessage(), t);
  }

  public abstract void info(@Nullable String message, @Nullable Throwable t);

  public abstract void warn(@Nullable String message, @Nullable Throwable t);

  public abstract void error(@Nullable String message, @Nullable Throwable t);

  private static class JavaFactory implements Factory {
    @Override
    public LoggerRt getInstance(String category) {
      final Logger logger = Logger.getLogger(category);
      return new LoggerRt() {
        @Override
        public void info(@Nullable String message, @Nullable Throwable t) {
          logger.log(Level.INFO, message, t);
        }

        @Override
        public void warn(@Nullable String message, @Nullable Throwable t) {
          logger.log(Level.WARNING, message, t);
        }

        @Override
        public void error(@Nullable String message, @Nullable Throwable t) {
          logger.log(Level.SEVERE, message, t);
        }
      };
    }
  }

  private abstract static class ReflectionBasedFactory implements Factory {
    @Override
    public LoggerRt getInstance(String category) {
      try {
        final Object logger = getLogger(category);
        return new LoggerRt() {
          @Override
          public void info(@Nullable String message, @Nullable Throwable t) {
            try {
              ReflectionBasedFactory.this.info(message, t, logger);
            }
            catch (Exception ignored) {
            }
          }

          @Override
          public void warn(@Nullable String message, @Nullable Throwable t) {
            try {
              ReflectionBasedFactory.this.warn(message, t, logger);
            }
            catch (Exception ignored) {
            }
          }

          @Override
          public void error(@Nullable String message, @Nullable Throwable t) {
            try {
              ReflectionBasedFactory.this.error(message, t, logger);
            }
            catch (Exception ignored) {
            }
          }
        };
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    protected abstract void error(@Nullable String message, @Nullable Throwable t, Object logger) throws Exception;

    protected abstract void warn(@Nullable String message, @Nullable Throwable t, Object logger) throws Exception;

    protected abstract void info(@Nullable String message, @Nullable Throwable t, Object logger) throws Exception;

    protected abstract Object getLogger(String category) throws Exception;
  }

  private static final class IdeaFactory extends ReflectionBasedFactory {
    private final Method myGetInstance;
    private final Method myInfo;
    private final Method myWarn;
    private final Method myError;

    private IdeaFactory() throws Exception {
      final Class<?> loggerClass = Class.forName("com.intellij.openapi.diagnostic.Logger");
      myGetInstance = loggerClass.getMethod("getInstance", String.class);
      myGetInstance.setAccessible(true);
      myInfo = loggerClass.getMethod("info", String.class, Throwable.class);
      myInfo.setAccessible(true);
      myWarn = loggerClass.getMethod("warn", String.class, Throwable.class);
      myInfo.setAccessible(true);
      myError = loggerClass.getMethod("error", String.class, Throwable.class);
      myError.setAccessible(true);
    }

    @Override
    protected void error(@Nullable String message, @Nullable Throwable t, Object logger) throws Exception {
      myError.invoke(logger, message, t);
    }

    @Override
    protected void warn(@Nullable String message, @Nullable Throwable t, Object logger) throws Exception {
      myWarn.invoke(logger, message, t);
    }

    @Override
    protected void info(@Nullable String message, @Nullable Throwable t, Object logger) throws Exception {
      myInfo.invoke(logger, message, t);
    }

    @Override
    protected Object getLogger(String category) throws Exception {
      return myGetInstance.invoke(null, category);
    }
  }

  private static final class Slf4JFactory extends ReflectionBasedFactory {
    private final Method myGetLogger;
    private final Method myInfo;
    private final Method myWarn;
    private final Method myError;

    private Slf4JFactory() throws Exception {
      final Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
      myGetLogger = loggerFactoryClass.getMethod("getLogger", String.class);
      myGetLogger.setAccessible(true);

      final Class<?> loggerClass = Class.forName("org.slf4j.Logger");
      myInfo = loggerClass.getMethod("info", String.class, Throwable.class);
      myInfo.setAccessible(true);
      myWarn = loggerClass.getMethod("warn", String.class, Throwable.class);
      myInfo.setAccessible(true);
      myError = loggerClass.getMethod("error", String.class, Throwable.class);
      myError.setAccessible(true);
    }

    @Override
    protected void error(@Nullable String message, @Nullable Throwable t, Object logger) throws Exception {
      myError.invoke(logger, message, t);
    }

    @Override
    protected void warn(@Nullable String message, @Nullable Throwable t, Object logger) throws Exception {
      myWarn.invoke(logger, message, t);
    }

    @Override
    protected void info(@Nullable String message, @Nullable Throwable t, Object logger) throws Exception {
      myInfo.invoke(logger, message, t);
    }

    @Override
    protected Object getLogger(String category) throws Exception {
      return myGetLogger.invoke(null, category);
    }
  }
}
