/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.testFramework;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestLogger extends com.intellij.openapi.diagnostic.Logger {
  private final Logger myLogger;

  public TestLogger(Logger logger) {
    myLogger = logger;
  }

  @Override
  public boolean isDebugEnabled() {
    return myLogger.isDebugEnabled();
  }

  @Override
  public void debug(String message) {
    myLogger.debug(message);
  }

  @Override
  public void debug(Throwable t) {
    myLogger.debug(t);
  }

  @Override
  public void debug(@NonNls String message, Throwable t) {
    myLogger.debug(message, t);
  }

  @Override
  public void error(String message, @Nullable Throwable t, @NotNull String... details) {
    LoggedErrorProcessor.getInstance().processError(message, t, details, myLogger);
  }

  @Override
  public void info(String message) {
    myLogger.info(message);
  }

  @Override
  public void info(String message, Throwable t) {
    myLogger.info(message, t);
  }

  @Override
  public void warn(@NonNls String message, Throwable t) {
    myLogger.warn(message, t);
  }

  public Level getLevel() {
    return myLogger.getLevel();
  }

  @Override
  public void setLevel(Level level) {
    myLogger.setLevel(level);
  }
}
