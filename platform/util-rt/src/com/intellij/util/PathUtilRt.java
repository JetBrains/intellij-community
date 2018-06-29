// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Set;

/**
 * @author nik
 */
public class PathUtilRt {
  @NotNull
  public static String getFileName(@Nullable String path) {
    if (path == null || path.length() == 0) {
      return "";
    }

    char c = path.charAt(path.length() - 1);
    int end = c == '/' || c == '\\' ? path.length() - 1 : path.length();
    int start = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1)) + 1;
    return path.substring(start, end);
  }

  @NotNull
  public static String getParentPath(@NotNull String path) {
    if (path.length() == 0) return "";
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
    if (name.length() == 0 || name.equals(".") || name.equals("..")) {
      return false;
    }

    for (int i = 0; i < name.length(); i++) {
      if (!isValidFileNameChar(name.charAt(i), os, strict)) {
        return false;
      }
    }

    if (os == Platform.WINDOWS && name.length() >= 3 && name.length() <= 4 && WINDOWS_NAMES.contains(name.toUpperCase(Locale.US))) {
      return false;
    }

    if (cs != null && !(cs.canEncode() && cs.newEncoder().canEncode(name))) {
      return false;
    }

    return true;
  }

  private static boolean isValidFileNameChar(char c, Platform os, boolean strict) {
    if (c == '/' || c == '\\') return false;
    if ((strict || os == Platform.WINDOWS) && (c < 32 || WINDOWS_CHARS.indexOf(c) >= 0)) return false;
    if (strict && c == ';') return false;
    return true;
  }

  private static final String WINDOWS_CHARS = "<>:\"|?*";
  private static final Set<String> WINDOWS_NAMES = ContainerUtilRt.newHashSet(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

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