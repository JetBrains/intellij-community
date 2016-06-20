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
package com.intellij.execution;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class CommandLineUtil {
  private static final char SPECIAL_QUOTE = '\uEFEF';
  private static final String WIN_SHELL_SPECIALS = "&<>()@^|";

  @NotNull
  public static String specialQuote(@NotNull String parameter) {
    return quote(parameter, SPECIAL_QUOTE);
  }

  @NotNull
  public static List<String> toCommandLine(@NotNull List<String> command) {
    assert !command.isEmpty();
    return toCommandLine(command.get(0), command.subList(1, command.size()));
  }

  @NotNull
  public static List<String> toCommandLine(@NotNull String command, @NotNull List<String> parameters) {
    return toCommandLine(command, parameters, Platform.current());
  }

  // please keep an implementation in sync with [junit-rt] ProcessBuilder.createProcess()
  @NotNull
  public static List<String> toCommandLine(@NotNull String command, @NotNull List<String> parameters, @NotNull Platform platform) {
    List<String> commandLine = ContainerUtil.newArrayListWithCapacity(parameters.size() + 1);

    commandLine.add(FileUtilRt.toSystemDependentName(command, platform.fileSeparator));

    boolean isWindows = platform == Platform.WINDOWS;
    boolean winShell = isWindows && isWinShell(command);

    for (String parameter : parameters) {
      if (isWindows) {
        if (parameter.contains("\"")) {
          parameter = StringUtil.replace(parameter, "\"", "\\\"");
        }
        else if (parameter.isEmpty()) {
          parameter = "\"\"";
        }
      }

      if (winShell && StringUtil.containsAnyChar(parameter, WIN_SHELL_SPECIALS)) {
        parameter = quote(parameter, SPECIAL_QUOTE);
      }

      if (isQuoted(parameter, SPECIAL_QUOTE)) {
        parameter = quote(parameter.substring(1, parameter.length() - 1), '"');
      }

      commandLine.add(parameter);
    }

    return commandLine;
  }

  private static boolean isWinShell(@NotNull String command) {
    return endsWithIgnoreCase(command, ".cmd") || endsWithIgnoreCase(command, ".bat") ||
           "cmd".equalsIgnoreCase(command) || "cmd.exe".equalsIgnoreCase(command);
  }

  private static boolean endsWithIgnoreCase(@NotNull String str, @NotNull String suffix) {
    return str.regionMatches(true, str.length() - suffix.length(), suffix, 0, suffix.length());
  }

  private static String quote(String s, char ch) {
    return !isQuoted(s, ch) ? ch + s + ch : s;
  }

  private static boolean isQuoted(String s, char ch) {
    return s.length() >= 2 && s.charAt(0) == ch && s.charAt(s.length() - 1) == ch;
  }

  public static boolean VERBOSE_COMMAND_LINE_MODE;
  @NotNull
  public static String extractPresentableName(@NotNull String commandLine) {
    String executable = commandLine.trim();

    List<String> words = StringUtil.splitHonorQuotes(executable, ' ');
    String execName;
    List<String> args;
    if (words.isEmpty()) {
      execName = executable;
      args = Collections.emptyList();
    }
    else {
      execName = words.get(0);
      args = words.subList(1, words.size());
    }

    if (VERBOSE_COMMAND_LINE_MODE) {
      return StringUtil.shortenPathWithEllipsis(execName + " " + StringUtil.join(args, " "), 250);
    }

    return new File(StringUtil.unquoteString(execName)).getName();
  }
}