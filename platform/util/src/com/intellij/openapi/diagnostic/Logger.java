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

import com.intellij.util.ArrayUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;

public abstract class Logger {
  public interface Factory {
    Logger getLoggerInstance(String category);
  }

  public static Factory ourFactory = new Factory() {
    public Logger getLoggerInstance(String category) {
      return new DefaultLogger(category);
    }
  };

  public static void setFactory(Factory factory) {
    ourFactory = factory;
  }

  public static Logger getInstance(@NonNls String category) {
    return ourFactory.getLoggerInstance(category);
  }

  public abstract boolean isDebugEnabled();

  public abstract void debug(@NonNls String message);
  public abstract void debug(Throwable t);
  public abstract void debug(@NonNls String message, Throwable t);

  public void error(@NonNls String message) {
    error(message, new Throwable(), ArrayUtil.EMPTY_STRING_ARRAY);
  }
  public void error(Object message) {
    error(String.valueOf(message));
  }

  public void error(@NonNls String message, @NonNls String... details) {
    error(message, new Throwable(), details);
  }

  public void error(@NonNls String message, Throwable e) {
    error(message, e, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void error(Throwable t) {
    error(t.getMessage(), t, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void warn(@NonNls String message) {
    warn(message, null);
  }

  public void warn(Throwable t) {
    warn(t.getMessage(), t);
  }


  public abstract void error(@NonNls String message, Throwable t, @NonNls String... details);

  public abstract void info(@NonNls String message);

  public abstract void info(@NonNls String message, Throwable t);

  public abstract void warn(@NonNls String message, Throwable t);

  public void info(Throwable t) {
    info(t.getMessage(), t);
  }

  public boolean assertTrue(boolean value, @NonNls Object message) {
    if (!value) {
      @NonNls StringBuilder resultMessage = new StringBuilder("Assertion failed");
      if (message != null) resultMessage.append(": ").append(message);

      error(resultMessage.toString(), new Throwable());
    }

    return value;
  }

  public boolean assertTrue(boolean value) {
    return value || assertTrue(value, "");
  }

  public abstract void setLevel(Level level);

}
