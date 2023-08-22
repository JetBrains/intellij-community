/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.intellij.openapi.vcs.changes.ignore.util;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Util class for regex operation on the files paths.
 */
public final class RegexUtil {

  /**
   * Extracts alphanumeric parts from the regex pattern and checks if any of them is contained in the tested path.
   * Looking for the parts speed ups the matching and prevents from running whole regex on the string.
   *
   * @param pattern to explode
   * @param path    to check
   * @return path matches the pattern
   */
  public static boolean match(@Nullable Pattern pattern, @Nullable String path) {
    if (pattern == null || path == null) {
      return false;
    }

    String[] parts = getParts(pattern);
    boolean result = false;

    if (parts.length == 0 || matchAllParts(parts, path)) {
      try {
        result = pattern.matcher(path).find();
      }
      catch (StringIndexOutOfBoundsException ignored) {
      }
    }

    return result;
  }

  /**
   * Checks if given path contains all of the path parts.
   *
   * @param parts that should be contained in path
   * @param path  to check
   * @return path contains all parts
   */
  public static boolean matchAllParts(String @Nullable [] parts, @Nullable String path) {
    if (parts == null || path == null) {
      return false;
    }

    int index = -1;
    for (String part : parts) {
      index = path.indexOf(part, index);
      if (index == -1) {
        return false;
      }
    }

    return true;
  }

  /**
   * Checks if given path contains any of the path parts.
   *
   * @param parts that should be contained in path
   * @param path  to check
   * @return path contains any of the parts
   */
  public static boolean matchAnyPart(String @Nullable [] parts, @Nullable String path) {
    if (parts == null || path == null) {
      return false;
    }

    for (String part : parts) {
      if (path.contains(part)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Extracts alphanumeric parts from  {@link Pattern}.
   *
   * @param pattern to handle
   * @return extracted parts
   */
  public static String @NotNull [] getParts(@Nullable Pattern pattern) {
    if (pattern == null) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    List<String> parts = new ArrayList<>();
    String sPattern = pattern.toString();

    StringBuilder part = new StringBuilder();
    boolean inSquare = false;
    for (int i = 0; i < sPattern.length(); i++) {
      char ch = sPattern.charAt(i);
      if (!inSquare && Character.isLetterOrDigit(ch)) {
        part.append(sPattern.charAt(i));
      }
      else if (part.length() > 0) {
        parts.add(part.toString());
        part = new StringBuilder();
      }

      inSquare = ch != ']' && ((ch == '[') || inSquare);
    }

    return ArrayUtilRt.toStringArray(parts);
  }
}
