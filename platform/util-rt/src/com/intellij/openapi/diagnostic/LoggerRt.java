// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper which uses either IDE logging subsystem (if available) or java.util.logging.
 */
public abstract class LoggerRt {
  private interface Factory {
    LoggerRt getInstance(String category);
  }

  private static Factory ourFactory;

  private synchronized static Factory getFactory() {
    if (ourFactory == null) {
      try {
        ourFactory = new IdeaFactory();
      }
      catch (Throwable t) {
        ourFactory = new JavaFactory();
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

  private static final class IdeaFactory implements Factory {
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
    public LoggerRt getInstance(String category) {
      try {
        final Object logger = myGetInstance.invoke(null, category);
        return new LoggerRt() {
          @Override
          public void info(@Nullable String message, @Nullable Throwable t) {
            try {
              myInfo.invoke(logger, message, t);
            }
            catch (Exception ignored) { }
          }

          @Override
          public void warn(@Nullable String message, @Nullable Throwable t) {
            try {
              myWarn.invoke(logger, message, t);
            }
            catch (Exception ignored) { }
          }

          @Override
          public void error(@Nullable String message, @Nullable Throwable t) {
            try {
              myError.invoke(logger, message, t);
            }
            catch (Exception ignored) { }
          }
        };
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
