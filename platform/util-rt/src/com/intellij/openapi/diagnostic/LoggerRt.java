/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper which uses either IDE logging subsystem (if available) or java.util.logging.
 *
 * @since 12.0
 */
public abstract class LoggerRt {
  private interface Factory {
    LoggerRt getInstance(@NotNull final String category);
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
  public static LoggerRt getInstance(@NotNull final String category) {
    return getFactory().getInstance(category);
  }

  @NotNull
  public static LoggerRt getInstance(@NotNull final Class<?> clazz) {
    return getInstance('#' + clazz.getName());
  }

  public void info(@Nullable final String message) {
    info(message, null);
  }

  public void info(@NotNull final Throwable t) {
    info(t.getMessage(), t);
  }

  public void warn(@Nullable final String message) {
    warn(message, null);
  }

  public void warn(@NotNull final Throwable t) {
    warn(t.getMessage(), t);
  }

  public void error(@Nullable final String message) {
    error(message, null);
  }

  public void error(@NotNull final Throwable t) {
    error(t.getMessage(), t);
  }

  public abstract void info(@Nullable final String message, @Nullable final Throwable t);
  public abstract void warn(@Nullable final String message, @Nullable final Throwable t);
  public abstract void error(@Nullable final String message, @Nullable final Throwable t);

  private static class JavaFactory implements Factory {
    public LoggerRt getInstance(@NotNull final String category) {
      final Logger logger = Logger.getLogger(category);
      return new LoggerRt() {
        @Override
        public void info(@Nullable final String message, @Nullable final Throwable t) {
          logger.log(Level.INFO, message, t);
        }

        @Override
        public void warn(@Nullable final String message, @Nullable final Throwable t) {
          logger.log(Level.WARNING, message, t);
        }

        @Override
        public void error(@Nullable final String message, @Nullable final Throwable t) {
          logger.log(Level.SEVERE, message, t);
        }
      };
    }
  }

  private static class IdeaFactory implements Factory {
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

    public LoggerRt getInstance(@NotNull final String category) {
      try {
        final Object logger = myGetInstance.invoke(null, category);
        return new LoggerRt() {
          @Override
          public void info(@Nullable final String message, @Nullable final Throwable t) {
            try {
              myInfo.invoke(logger, message, t);
            }
            catch (Exception ignored) { }
          }

          @Override
          public void warn(@Nullable final String message, @Nullable final Throwable t) {
            try {
              myWarn.invoke(logger, message, t);
            }
            catch (Exception ignored) { }
          }

          @Override
          public void error(@Nullable final String message, @Nullable final Throwable t) {
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