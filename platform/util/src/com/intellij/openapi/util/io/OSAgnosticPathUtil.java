// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * <p>Utility methods for operations with file path strings. Unlike {@link java.io IO}, {@link java.nio.file NIO2} and {@link FileUtil},
 * these methods are platform-agnostic - i.e. able to work with Windows paths on Unix systems and vice versa.
 * Both forward- and backward-slashes are legal separators.</p>
 *
 * <p><strong>Warning:</strong> the methods are by definition less strict, and in some cases may produce incorrect results.
 * Unless you're certain you need the relaxed handling, prefer {@link java.nio.file NIO2} instead.</p>
 */
public final class OSAgnosticPathUtil {
  private OSAgnosticPathUtil() { }

  /**
   * Compares paths by elements and without taking separators into account. The key difference from {@link String#compareTo} is
   * that "a/b" is less than "a.b": instead of character-vs-character matching, the paths are compared as ["a", "b"] vs. ["a.b"].
   */
  public static final Comparator<String> COMPARATOR = (@Nullable String path1, @Nullable String path2) -> {
    if (path1 == path2) return 0;
    if (path1 == null) return -1;
    if (path2 == null) return 1;

    int start = 0;
    while (true) {
      int next1 = separatorIndex(path1, start), next2 = separatorIndex(path2, start);

      if (next1 != start || next2 != start) {
        int end1 = next1 >= 0 ? next1 : path1.length(), end2 = next2 >= 0 ? next2 : path2.length(), minEnd = Math.min(end1, end2);
        for (int i = start; i < minEnd; i++) {
          int diff = StringUtil.compare(path1.charAt(i), path2.charAt(i), !SystemInfo.isFileSystemCaseSensitive);
          if (diff != 0) return diff;
        }
        if (next1 != next2 || next1 < 0) {
          int diff = end1 - end2;
          return diff != 0 ? diff : path1.length() - path2.length();
        }
      }

      start = next1 + 1;
    }
  };

  /**
   * Returns {@code true} absolute UNC (even incomplete), DOS and Unix paths; {@code false} otherwise.
   *
   * @see OSAgnosticPathUtil applicability warning
   */
  public static boolean isAbsolute(@NotNull String path) {
    return path.length() > 2 && path.charAt(1) == ':' && isSlash(path.charAt(2)) && isDriveLetter(path.charAt(0)) ||
           path.length() > 1 && isSlash(path.charAt(0)) && path.charAt(1) == path.charAt(0) ||
           path.startsWith("/");
  }

  /**
   * <p>Returns a parent path according to the rules applicable to the given path,
   * or {@code null} when the path is a file system root or unrecognized.</p>
   * <p>A path should not contain duplicated separators (except at the beginning of a UNC path), otherwise the result could be incorrect.</p>
   * <p>Directory traversals are not processed ({@code getParent("/a/b/..")} returns {@code "/a/b"} instead of {@code "/a"}, etc).</p>
   *
   * @see OSAgnosticPathUtil applicability warning
   */
  public static @Nullable String getParent(@NotNull String path) {
    int length = path.length();
    int lastSeparator = lastSeparatorIndex(path, length);
    if (lastSeparator < 0) return null;
    if (lastSeparator == length - 1) lastSeparator = lastSeparatorIndex(path, length - 2);
    if (lastSeparator < 0) return null;

    if (length > 1 && isSlash(path.charAt(0)) && path.charAt(1) == path.charAt(0)) {
      // a UNC path
      if (lastSeparator > 1) {
        int prevSeparator = lastSeparatorIndex(path, lastSeparator - 1);
        if (prevSeparator > 1) {
          return path.substring(0, lastSeparator);
        }
      }
      return null;
    }

    if (lastSeparator == 2 && path.charAt(1) == ':' && isDriveLetter(path.charAt(0))) {
      // a DOS path
      return path.substring(0, 3);
    }

    return path.substring(0, lastSeparator == 0 ? 1 : lastSeparator);
  }

  private static boolean isSlash(char c) {
    return c == '/' || c == '\\';
  }

  public static boolean isDriveLetter(char c) {
    return 'A' <= c && c <= 'Z' || 'a' <= c && c <= 'z';
  }

  private static int separatorIndex(String s, int from) {
    for (int i = from, l = s.length(); i < l; i++) {
      if (isSlash(s.charAt(i))) return i;
    }
    return -1;
  }

  private static int lastSeparatorIndex(String s, int from) {
    for (int i = Math.min(from, s.length() - 1); i >= 0; i--) {
      if (isSlash(s.charAt(i))) return i;
    }
    return -1;
  }
}