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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;

@NonNls public abstract class LoggedErrorProcessor {
  private static final LoggedErrorProcessor DEFAULT = new LoggedErrorProcessor() {
    @Override
    public void processError(String message, Throwable t, String[] details, Logger logger) {
      logger.info(message, t);
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
  };

  private static LoggedErrorProcessor ourInstance = DEFAULT;

  public static LoggedErrorProcessor getInstance() {
    return ourInstance;
  }

  public static void setNewInstance(LoggedErrorProcessor newInstance) {
    ourInstance = newInstance;
  }

  public static void restoreDefaultProcessor() {
    ourInstance = DEFAULT;
  }

  public abstract void processError(String message, Throwable t, String[] details, Logger logger);
}
