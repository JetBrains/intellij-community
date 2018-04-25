// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestStackTraceParser {

  private final static Pattern outerPattern = Pattern.compile("\tat (.*)\\.([^.]*)\\((.*)\\)");
  private final static Pattern innerPattern = Pattern.compile("(.*):(\\d*)");

  /**
   * Return line number and called method name.
   */
  @Nullable
  public static Pair<Integer, String> findFailLine(@Nullable String stacktrace, @Nullable String url) {

    if (stacktrace == null || url == null) return null;
    int i = url.indexOf("//");
    if (i == -1) return null;
    String path = "\tat " + url.substring(i + 2);

    try (BufferedReader reader = new BufferedReader(new StringReader(stacktrace))) {
      String line, previous = null;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(path)) {
          Matcher matcher = outerPattern.matcher(line);
          if (!matcher.matches()) return null;
          Matcher matcher1 = innerPattern.matcher(matcher.group(3));
          if (!matcher1.matches()) return null;
          int lineNumber = Integer.parseInt(matcher1.group(2));

          if (previous == null) return null;
          Matcher matcher2 = outerPattern.matcher(previous);
          if (!matcher2.matches()) return null;
          return Pair.create(lineNumber, matcher2.group(2));
        }
        previous = line;
      }

      return null;
    }
    catch (IOException | NumberFormatException e) {
      return null;
    }
  }
}
