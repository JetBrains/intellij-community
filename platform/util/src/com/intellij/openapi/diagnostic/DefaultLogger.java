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
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultLogger extends Logger {
  @SuppressWarnings("UnusedParameters")
  public DefaultLogger(String category) { }

  @Override
  public boolean isDebugEnabled() {
    return false;
  }

  @Override
  public void debug(String message) { }

  @Override
  public void debug(Throwable t) { }

  @Override
  public void debug(@NonNls String message, Throwable t) { }

  @Override
  public void info(String message) { }

  @Override
  public void info(String message, Throwable t) { }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void warn(@NonNls String message, @Nullable Throwable t) {
    t = checkException(t);
    System.err.println("WARN: " + message);
    if (t != null) t.printStackTrace(System.err);
  }

  @Override
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void error(String message, @Nullable Throwable t, @NotNull String... details) {
    t = checkException(t);
    System.err.println("ERROR: " + message);
    if (t != null) t.printStackTrace(System.err);
    if (details.length > 0) {
      System.out.println("details: ");
      for (String detail : details) {
        System.out.println(detail);
      }
    }

    AssertionError error = new AssertionError(message);
    error.initCause(t);
    throw error;
  }

  @Override
  public void setLevel(Level level) { }
}
