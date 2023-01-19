// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtilRt;
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
   * Comparing paths by elements and without taking separators into account.
   * The key difference from {@link String#compareTo} is that "a/b" is less than "a.b":
   * instead of character-vs-character matching, the paths are compared as ["a", "b"] vs. ["a.b"].
   */
  public static final Comparator<String> COMPARATOR = (@Nullable String path1, @Nullable String path2) -> {
    //noinspection StringEquality
    if (path1 == path2) return 0;
    if (path1 == null) return -1;
    if (path2 == null) return 1;

    int length1 = path1.length();
    int length2 = path2.length();
    boolean ignoreCase = !SystemInfoRt.isFileSystemCaseSensitive;

    for (int pos = 0; pos < length1 && pos < length2; pos++) {
      char ch1 = path1.charAt(pos);
      char ch2 = path2.charAt(pos);
      if (ch1 == ch2) continue;
      if (ch1 == '/') {
        if (ch2 == '\\') continue;
        return -1;
      }
      else if (ch1 == '\\') {
        if (ch2 == '/') continue;
        return -1;
      }
      else if (ch2 == '/' || ch2 == '\\') {
        return 1;
      }
      int diff = StringUtil.compare(ch1, ch2, ignoreCase);
      if (diff != 0) {
        return diff;
      }
    }
    return Integer.compare(length1, length2);
  };

  /**
   * Returns {@code true} for UNC (even incomplete), absolute DOS and Unix paths; {@code false} otherwise.
   *
   * @see OSAgnosticPathUtil applicability warning
   */
  public static boolean isAbsolute(@NotNull String path) {
    return path.startsWith("/") || isAbsoluteDosPath(path) || isUncPath(path);
  }

  public static boolean isAbsoluteDosPath(@NotNull String path) {
    return path.length() > 2 && startsWithWindowsDrive(path) && PathUtilRt.isSeparator(path.charAt(2));
  }

  /**
   * @return true when the path starts with a drive letter followed by colon, e.g., "C:"
   */
  public static boolean startsWithWindowsDrive(@NotNull String path) {
    return path.length() >= 2 && path.charAt(1) == ':' && isDriveLetter(path.charAt(0));
  }

  public static boolean isUncPath(@NotNull String path) {
    if (!PathUtilRt.startsWithSeparatorSeparator(path)) return false;
    int slashIndex = nextSeparatorIndex(path, 2);
    return PathUtilRt.isWindowsUNCRoot(path, slashIndex == -1 ? path.length() : slashIndex);
  }

  public static boolean startsWith(@NotNull String path, @NotNull String prefix) {
    int pathLength = path.length();
    int prefixLength = prefix.length();
    if (prefixLength == 0) return true;
    if (prefixLength > pathLength) return false;
    boolean ignoreCase = !SystemInfoRt.isFileSystemCaseSensitive;
    for (int pos = 0; pos < pathLength && pos < prefixLength; pos++) {
      char ch1 = path.charAt(pos);
      char ch2 = prefix.charAt(pos);
      if (ch1 == ch2) continue;
      if (ch1 == '/') {
        if (ch2 == '\\') continue;
        return false;
      }
      else if (ch1 == '\\') {
        if (ch2 == '/') continue;
        return false;
      }
      else if (ch2 == '/' || ch2 == '\\') {
        return false;
      }
      if (StringUtil.compare(ch1, ch2, ignoreCase) != 0) {
        return false;
      }
    }
    if (pathLength == prefixLength) {
      return true;
    }
    char lastPrefixChar = prefix.charAt(prefixLength - 1);
    int slashOrSeparatorIdx = prefixLength;
    if (lastPrefixChar == '/' || lastPrefixChar == '\\') {
      slashOrSeparatorIdx = prefixLength - 1;
    }
    char next = path.charAt(slashOrSeparatorIdx);
    return next == '/' || next == '\\';
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
    int lastSeparator = PathUtilRt.lastSeparatorIndex(path, length-1);
    if (lastSeparator < 0) return null;
    if (lastSeparator == length - 1) lastSeparator = PathUtilRt.lastSeparatorIndex(path, length - 2);
    if (lastSeparator < 0) return null;

    if (PathUtilRt.startsWithSeparatorSeparator(path)) {
      // a UNC path
      int slashIndex = nextSeparatorIndex(path, 2);
      if (lastSeparator > 1 && slashIndex != -1 && slashIndex <= lastSeparator && PathUtilRt.isWindowsUNCRoot(path, slashIndex)) {
        int prevSeparator = PathUtilRt.lastSeparatorIndex(path, lastSeparator - 1);
        if (prevSeparator > 1) {
          return path.substring(0, lastSeparator);
        }
      }
      return null;
    }

    if (lastSeparator == 2 && startsWithWindowsDrive(path)) {
      // a DOS path
      return path.substring(0, 3);
    }

    return path.substring(0, lastSeparator == 0 ? 1 : lastSeparator);
  }

  private static int nextSeparatorIndex(String s, @SuppressWarnings("SameParameterValue") int start) {
    for (int i = start; i < s.length(); i++) {
      char c = s.charAt(i);
      if (PathUtilRt.isSeparator(c)) {
        return i;
      }
    }
    return -1;
  }

  public static boolean isDriveLetter(char c) {
    return 'A' <= c && c <= 'Z' || 'a' <= c && c <= 'z';
  }
}
