/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stripped-down version of {@code com.intellij.openapi.util.text.StringUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 *
 * @since 12.0
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class StringUtilRt {
  public static boolean charsEqualIgnoreCase(char a, char b) {
    return a == b || toUpperCase(a) == toUpperCase(b) || toLowerCase(a) == toLowerCase(b);
  }

  @NotNull
  public static String toUpperCase(@NotNull String s) {
    StringBuilder answer = null;

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      char upcased = toUpperCase(c);
      if (answer == null && upcased != c) {
        answer = new StringBuilder(s.length());
        answer.append(s.substring(0, i));
      }

      if (answer != null) {
        answer.append(upcased);
      }
    }

    return answer == null ? s : answer.toString();
  }

  public static char toUpperCase(char a) {
    if (a < 'a') {
      return a;
    }
    if (a <= 'z') {
      return (char)(a + ('A' - 'a'));
    }
    return Character.toUpperCase(a);
  }

  public static char toLowerCase(char a) {
    if (a < 'A' || a >= 'a' && a <= 'z') {
      return a;
    }

    if (a <= 'Z') {
      return (char)(a + ('a' - 'A'));
    }

    return Character.toLowerCase(a);
  }

  /**
   * Converts line separators to <code>"\n"</code>
   */
  @NotNull
  public static String convertLineSeparators(@NotNull String text) {
    return convertLineSeparators(text, false);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, boolean keepCarriageReturn) {
    return convertLineSeparators(text, "\n", null, keepCarriageReturn);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator) {
    return convertLineSeparators(text, newSeparator, null);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text, @NotNull String newSeparator, @Nullable int[] offsetsToKeep) {
    return convertLineSeparators(text, newSeparator, offsetsToKeep, false);
  }

  @NotNull
  public static String convertLineSeparators(@NotNull String text,
                                             @NotNull String newSeparator,
                                             @Nullable int[] offsetsToKeep,
                                             boolean keepCarriageReturn) {
    return unifyLineSeparators(text, newSeparator, offsetsToKeep, keepCarriageReturn).toString();
  }

  @NotNull
  public static CharSequence unifyLineSeparators(@NotNull CharSequence text) {
    return unifyLineSeparators(text, "\n", null, false);
  }

  @NotNull
  public static CharSequence unifyLineSeparators(@NotNull CharSequence text,
                                                 @NotNull String newSeparator,
                                                 @Nullable int[] offsetsToKeep,
                                                 boolean keepCarriageReturn) {
    StringBuilder buffer = null;
    int intactLength = 0;
    final boolean newSeparatorIsSlashN = "\n".equals(newSeparator);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        if (!newSeparatorIsSlashN) {
          if (buffer == null) {
            buffer = new StringBuilder(text.length());
            buffer.append(text, 0, intactLength);
          }
          buffer.append(newSeparator);
          shiftOffsets(offsetsToKeep, buffer.length(), 1, newSeparator.length());
        }
        else if (buffer == null) {
          intactLength++;
        }
        else {
          buffer.append(c);
        }
      }
      else if (c == '\r') {
        boolean followedByLineFeed = i < text.length() - 1 && text.charAt(i + 1) == '\n';
        if (!followedByLineFeed && keepCarriageReturn) {
          if (buffer == null) {
            intactLength++;
          }
          else {
            buffer.append(c);
          }
          continue;
        }
        if (buffer == null) {
          buffer = new StringBuilder(text.length());
          buffer.append(text, 0, intactLength);
        }
        buffer.append(newSeparator);
        if (followedByLineFeed) {
          //noinspection AssignmentToForLoopParameter
          i++;
          shiftOffsets(offsetsToKeep, buffer.length(), 2, newSeparator.length());
        }
        else {
          shiftOffsets(offsetsToKeep, buffer.length(), 1, newSeparator.length());
        }
      }
      else {
        if (buffer == null) {
          intactLength++;
        }
        else {
          buffer.append(c);
        }
      }
    }
    return buffer == null ? text : buffer;
  }

  private static void shiftOffsets(int[] offsets, int changeOffset, int oldLength, int newLength) {
    if (offsets == null) return;
    int shift = newLength - oldLength;
    if (shift == 0) return;
    for (int i = 0; i < offsets.length; i++) {
      int offset = offsets[i];
      if (offset >= changeOffset + oldLength) {
        offsets[i] += shift;
      }
    }
  }

  public static int parseInt(final String string, final int defaultValue) {
    try {
      return Integer.parseInt(string);
    }
    catch (Exception e) {
      return defaultValue;
    }
  }

  public static double parseDouble(final String string, final double defaultValue) {
    try {
      return Double.parseDouble(string);
    }
    catch (Exception e) {
      return defaultValue;
    }
  }

  public static boolean parseBoolean(final String string, final boolean defaultValue) {
    try {
      return Boolean.parseBoolean(string);
    }
    catch (Exception e) {
      return defaultValue;
    }
  }

  @NotNull
  public static String getShortName(@NotNull Class aClass) {
    return getShortName(aClass.getName());
  }

  @NotNull
  public static String getShortName(@NotNull String fqName) {
    return getShortName(fqName, '.');
  }

  @NotNull
  public static String getShortName(@NotNull String fqName, char separator) {
    int lastPointIdx = fqName.lastIndexOf(separator);
    if (lastPointIdx >= 0) {
      return fqName.substring(lastPointIdx + 1);
    }
    return fqName;
  }

  public static boolean endsWithChar(@Nullable CharSequence s, char suffix) {
    return s != null && s.length() != 0 && s.charAt(s.length() - 1) == suffix;
  }

  public static boolean startsWithIgnoreCase(@NonNls @NotNull String str, @NonNls @NotNull String prefix) {
    final int stringLength = str.length();
    final int prefixLength = prefix.length();
    return stringLength >= prefixLength && str.regionMatches(true, 0, prefix, 0, prefixLength);
  }

}
