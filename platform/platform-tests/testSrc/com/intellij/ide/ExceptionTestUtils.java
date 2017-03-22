/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public class ExceptionTestUtils {
  /**
   * Attempts to create an exception object that matches the given description, which
   * is in the form of the output of an exception stack dump ({@link Throwable#printStackTrace()})
   *
   * @param desc the description of an exception
   * @return a corresponding exception if possible
   */
  @NotNull
  public static Throwable createExceptionFromDesc(@NotNull String desc) {
    return createExceptionFromDesc(desc, null);
  }

  /**
   * Attempts to create an exception object that matches the given description, which
   * is in the form of the output of an exception stack dump ({@link Throwable#printStackTrace()})
   *
   * @param desc      the description of an exception
   * @param throwable the throwable instance to use (if instantiating exception class isn't
   *                  possible via default constructor)
   * @return a corresponding exception if possible
   */
  @NotNull
  @SuppressWarnings("ThrowableInstanceNeverThrown")
  public static Throwable createExceptionFromDesc(@NotNull String desc,
                                                  @Nullable Throwable throwable) {
    // First line: description and type
    Iterator<String> iterator = Splitter.on('\n').split(desc).iterator();
    assertTrue(iterator.hasNext());
    final String first = iterator.next();
    assertTrue(iterator.hasNext());
    String message = null;
    String exceptionClass;
    int index = first.indexOf(':');
    if (index != -1) {
      exceptionClass = first.substring(0, index).trim();
      message = first.substring(index + 1).trim();
    }
    else {
      exceptionClass = first.trim();
    }

    if (throwable == null) {
      try {
        @SuppressWarnings("unchecked")
        Class<Throwable> clz = (Class<Throwable>)Class.forName(exceptionClass);
        if (message == null) {
          throwable = clz.newInstance();
        }
        else {
          Constructor<Throwable> constructor = clz.getConstructor(String.class);
          throwable = constructor.newInstance(message);
        }
      }
      catch (Throwable t) {
        if (message == null) {
          throwable = new Throwable() {
            @Override
            public String getMessage() {
              return first;
            }

            @Override
            public String toString() {
              return first;
            }
          };
        }
        else {
          throwable = new Throwable(message);
        }
      }
    }

    List<StackTraceElement> frames = Lists.newArrayList();
    Pattern outerPattern = Pattern.compile("\tat (.*)\\.([^.]*)\\((.*)\\)");
    Pattern innerPattern = Pattern.compile("(.*):(\\d*)");
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (line.isEmpty()) {
        break;
      }
      Matcher outerMatcher = outerPattern.matcher(line);
      if (!outerMatcher.matches()) {
        throw new RuntimeException(
          "Line " + line + " does not match expected stactrace pattern");
      } else {
        String clz = outerMatcher.group(1);
        String method = outerMatcher.group(2);
        String inner = outerMatcher.group(3);
        switch (inner) {
          case "Native Method":
            frames.add(new StackTraceElement(clz, method, null, -2));
            break;
          case "Unknown Source":
            frames.add(new StackTraceElement(clz, method, null, -1));
            break;
          default:
            Matcher innerMatcher = innerPattern.matcher(inner);
            if (!innerMatcher.matches()) {
              throw new RuntimeException("Trace parameter list " + inner
                                         + " does not match expected pattern");
            } else {
              String file = innerMatcher.group(1);
              int lineNum = Integer.parseInt(innerMatcher.group(2));
              frames.add(new StackTraceElement(clz, method, file, lineNum));
            }
            break;
        }
      }
    }

    throwable.setStackTrace(frames.toArray(new StackTraceElement[frames.size()]));

    // Dump stack back to string to make sure we have the same exception
    Assert.assertEquals("The generated stacktrace did not match the expected output", getStackTrace(throwable), desc);

    return throwable;
  }

  @NotNull
  public static String getStackTrace(@NotNull Throwable throwable) {
    StringWriter stringWriter = new StringWriter();
    try (PrintWriter writer = new PrintWriter(stringWriter)) {
      throwable.printStackTrace(writer);
      return stringWriter.toString().replace("\r\n", "\n");
    }
  }
}
