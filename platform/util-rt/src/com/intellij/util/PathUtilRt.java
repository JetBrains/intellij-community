// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PathUtilRt {
  @NotNull
  public static String getFileName(@Nullable String path) {
    if (StringUtilRt.isEmpty(path)) {
      return "";
    }

    int end = lastNonSeparatorIndex(path);
    int start = lastSeparatorIndex(path, end);
    if (isWindowsUNCRoot(path, start)) {
      start = -1;
    }
    return path.substring(start + 1, end+1);
  }

  @Nullable("null means no extension (e.g. 'xxx'), empty string means empty extension (e.g. 'xxx.')")
  public static String getFileExtension(@Nullable String path) {
    if (StringUtilRt.isEmpty(path)) {
      return null;
    }

    int end = lastNonSeparatorIndex(path);
    if (end == -1) return null;
    int start = lastSeparatorIndex(path, end) + 1;
    int index = StringUtilRt.lastIndexOf(path, '.', Math.max(start, 0), end+1);
    return index < 0 ? null : path.substring(index + 1, end+1);
  }

  private static int lastNonSeparatorIndex(@NotNull String path) {
    for (int index = path.length() - 1; index >= 0; --index) {
      char c = path.charAt(index);
      if (!isSeparator(c)) {
        return index;
      }
    }
    return -1;
  }

  @NotNull
  public static String getParentPath(@NotNull String path) {
    if (path.isEmpty()) return "";
    int end = lastSeparatorIndex(path, path.length()-1);
    if (end == path.length() - 1 && end >= 1) {
      end = lastSeparatorIndex(path, end-1);
    }
    if (end == -1 || end == 0) {
      return "";
    }
    if (isWindowsUNCRoot(path, end)) {
      return "";
    }
    // parent of '//host' is root
    char prev = path.charAt(end - 1);
    if (isSeparator(prev)) {
      end--;
    }
    return path.substring(0, end);
  }

  public static boolean isWindowsUNCRoot(@NotNull CharSequence path, int lastPathSeparatorPosition) {
    return Platform.CURRENT == Platform.WINDOWS &&
           startsWithSeparatorSeparator(path) && lastPathSeparatorPosition >= 1
           && !hasFileSeparatorsOrNavigatableDots(path, 2, lastPathSeparatorPosition);
  }

  private static boolean hasFileSeparatorsOrNavigatableDots(@NotNull CharSequence path, int start, int end) {
    for (int i = end - 1; i >= start; i--) {
      char c = path.charAt(i);
      if (isSeparator(c)) return true;
      // contains '.' or '..' surrounded by slashes
      if (c == '.' && (i==2 || i==3 && path.charAt(2)=='.')) return true;
    }
    return false;
  }

  @NotNull
  public static String suggestFileName(@NotNull String text) {
    return suggestFileName(text, false, false);
  }

  @NotNull
  public static String suggestFileName(@NotNull String text, boolean allowDots, boolean allowSpaces) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (!isValidFileNameChar(c, Platform.CURRENT, true) || (!allowDots && c == '.') || (!allowSpaces && Character.isWhitespace(c))) {
        result.append('_');
      }
      else {
        result.append(c);
      }
    }
    return result.toString();
  }

  /**
   * Checks whether a file with the given name can be created on a current platform.
   * @see #isValidFileName(String, Platform, boolean, Charset)
   */
  public static boolean isValidFileName(@NotNull String fileName, boolean strict) {
    return isValidFileName(fileName, Platform.CURRENT, strict, FS_CHARSET);
  }

  public static boolean startsWithSeparatorSeparator(@NotNull CharSequence path) {
    return path.length() > 1 && isSeparator(path.charAt(0)) && path.charAt(1) == path.charAt(0);
  }

  public static boolean isSeparator(char c) {
    return c == '/' || c == '\\';
  }

  public static int lastSeparatorIndex(@NotNull CharSequence s, int endInclusive) {
    for (int i = endInclusive; i >= 0; i--) {
      if (isSeparator(s.charAt(i))) return i;
    }
    return -1;
  }

  public enum Platform {
    UNIX, WINDOWS;
    public static final Platform CURRENT = SystemInfoRt.isWindows ? WINDOWS : UNIX;
  }

  /**
   * Checks whether a file with the given name can be created on a platform specified by given parameters.
   * <p>
   * Platform restrictions:<br>
   * {@code Platform.UNIX} prohibits empty names, traversals (".", ".."), and names containing '/' or '\' characters.<br>
   * {@code Platform.WINDOWS} prohibits empty names, traversals (".", ".."), reserved names ("CON", "NUL", "COM1" etc.),
   * and names containing any of characters {@code <>:"/\|?*} or control characters (range 0..31)
   * (<a href="https://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx">more info</a>).
   *
   * @param os     specifies a platform.
   * @param strict prohibits names containing any of characters {@code <>:"/\|?*;} and control characters (range 0..31).
   * @param cs     prohibits names which cannot be encoded by this charset (optional).
   */
  public static boolean isValidFileName(@NotNull String name, @NotNull Platform os, boolean strict, @Nullable Charset cs) {
    if (name.isEmpty() || name.equals(".") || name.equals("..")) {
      return false;
    }

    for (int i = 0; i < name.length(); i++) {
      if (!isValidFileNameChar(name.charAt(i), os, strict)) {
        return false;
      }
    }

    if (os == Platform.WINDOWS && name.length() >= 3 && name.length() <= 4 &&
        WINDOWS_RESERVED_NAMES.contains(name.toUpperCase(Locale.ENGLISH))) {
      return false;
    }

    return cs == null || cs.canEncode() && cs.newEncoder().canEncode(name);
  }

  private static boolean isValidFileNameChar(char c, Platform os, boolean strict) {
    if (isSeparator(c)) return false;
    if ((strict || os == Platform.WINDOWS) && (c < 32 || WINDOWS_INVALID_CHARS.indexOf(c) >= 0)) return false;
    return !strict || c != ';';
  }

  private static final String WINDOWS_INVALID_CHARS = "<>:\"|?*";
  private static final Set<String> WINDOWS_RESERVED_NAMES = new HashSet<>(Arrays.asList(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"));

  private static final Charset FS_CHARSET = fsCharset();
  private static Charset fsCharset() {
    if (!SystemInfoRt.isWindows && !SystemInfoRt.isMac) {
      String property = System.getProperty("sun.jnu.encoding");
      if (property != null) {
        try {
          return Charset.forName(property);
        }
        catch (Exception e) {
          LoggerRt.getInstance(PathUtilRt.class).warn("unknown JNU charset: " + property, e);
        }
      }
    }

    return null;
  }
}