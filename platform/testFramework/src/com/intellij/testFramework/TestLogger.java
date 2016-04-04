/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Log4jBasedLogger;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestLogger extends Log4jBasedLogger {
  public TestLogger(Logger logger) {
    super(logger);
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    t = checkException(t);
    LoggedErrorProcessor.getInstance().processWarn(message, t, myLogger);
  }

  @Override
  public void error(String message, @Nullable Throwable t, @NotNull String... details) {
    t = checkException(t);
    LoggedErrorProcessor.getInstance().processError(message, t, details, myLogger);
  }

  public Level getLevel() {
    return myLogger.getLevel();
  }
}
