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
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class DefaultLogger extends Logger {
  public DefaultLogger(String category) {
  }

  public boolean isDebugEnabled() {
    return false;
  }

  public void debug(String message) {
  }

  public void debug(Throwable t) {
  }

  public void debug(@NonNls String message, Throwable t) {
  }

  @SuppressWarnings({"HardCodedStringLiteral", "UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
  public void error(String message, @Nullable Throwable t, String... details) {
    System.err.println("ERROR: " + message);
    if (t != null) t.printStackTrace();
    if (details != null && details.length > 0) {
      System.out.println("details: ");
      for (String detail : details) {
        System.out.println(detail);
      }
    }

    throw new AssertionError(message);
  }

  public void info(String message) {
  }

  public void info(String message, Throwable t) {
  }

  public void warn(@NonNls String message, Throwable t) {
  }

  public void setLevel(Level level) {
  }
}
