/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public class TestLogger extends com.intellij.openapi.diagnostic.Logger {
  private final Logger myLogger;

  public TestLogger(Logger logger) {
    myLogger = logger;
  }

  public boolean isDebugEnabled() {
    return false;
  }

  public void debug(String message) {
    myLogger.debug(message);
  }

  public void debug(Throwable t) {
    myLogger.debug(t);
  }

  public void debug(@NonNls String message, Throwable t) {
    myLogger.debug(message, t);
  }

  public void error(String message, Throwable t, String... details) {
    LoggedErrorProcessor.getInstance().processError(message, t, details, myLogger);
  }

  public void info(String message) {
    myLogger.info(message);
  }

  public void info(String message, Throwable t) {
    myLogger.info(message, t);
  }

  public void warn(@NonNls String message, Throwable t) {
    if (t == null) {
      myLogger.warn(message);
    }
    else {
      myLogger.warn(message, t);
    }
  }

  public void setLevel(Level level) {
    myLogger.setLevel(level);
  }
}
