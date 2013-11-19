/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PathUtilRt {
  @NotNull
  public static String getFileName(@NotNull String path) {
    if (path.isEmpty()) {
      return "";
    }
    final char c = path.charAt(path.length() - 1);
    int end = c == '/' || c == '\\' ? path.length() - 1 : path.length();
    int start = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1)) + 1;
    return path.substring(start, end);
  }

  @NotNull
  public static String getParentPath(@NotNull String path) {
    if (path.length() == 0) {
      return "";
    }
    int end = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    if (end == path.length() - 1) {
      end = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1));
    }
    return end == -1 ? "" : path.substring(0, end);
  }

  @NotNull
  public static String suggestFileName(@NotNull String text) {
    return suggestFileName(text, false, false);
  }

  @NotNull
  public static String suggestFileName(@NotNull String text, final boolean allowDots, final boolean allowSpaces) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (!isValidFileNameChar(c) || (!allowDots && c == '.') || (!allowSpaces && Character.isWhitespace(c))) {
        result.append('_');
      }
      else {
        result.append(c);
      }
    }
    return result.toString();
  }

  public static boolean isValidFileName(@NotNull String fileName) {
    if (fileName.length() == 0 || fileName.equals(".") || fileName.equals("..")) {
      return false;
    }
    for (int i = 0; i < fileName.length(); i++) {
      if (!isValidFileNameChar(fileName.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidFileNameChar(char c) {
    return c != '/' && c != '\\' && c != '\t' && c != '\n' && c != '\r' && c != ':' && c != ';' && c != '*' && c != '?' &&
           c != '"' && c != '\'' && c != '<' && c != '>';
  }
}
