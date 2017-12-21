/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.lang;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A class representing a version of some Java platform - e.g. the runtime the class is loaded into, or some installed JRE.
 *
 * Based on <a href="http://openjdk.java.net/jeps/322">JEP 322 "Time-Based Release Versioning"</a> (Java 10+), but also supports JEP 223
 * "New Version-String Scheme" (Java 9), as well as earlier version's formats.
 *
 * @see #parse(String) for examples of supported version strings
 * @since 2018.1
 */
public final class JavaVersion {
  /**
   * The major version.
   * Corresponds to the first number of a Java 9+ version string and to the second number of Java 1.0 to 1.8 strings.
   */
  public final int feature;

  /**
   * The minor version.
   * Corresponds to the second number of a Java 9+ version string and to the third number of Java 1.0 to 1.8 strings.
   * Used in version strings prior to Java 1.5, in newer strings is always {@code 0}.
   */
  public final int minor;

  /**
   * The patch version.
   * Corresponds to the third number of a Java 9+ version string and to the number of Java 1.0 to 1.8 strings (one after an underscore).
   */
  public final int update;

  /**
   * The build number.
   * Corresponds to a number prefixed by a plus sign in a Java 9+ version string and by "-b" string in earlier versions.
   */
  public final int build;

  /**
   * {@code true} if the platform is an early access release, {@code false} otherwise (or when not known).
   */
  public final boolean ea;

  private JavaVersion(int feature, int minor, int update, int build, boolean ea) {
    this.feature = feature;
    this.minor = minor;
    this.update = update;
    this.build = build;
    this.ea = ea;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaVersion)) return false;
    JavaVersion other = (JavaVersion)o;
    return feature == other.feature &&
           minor == other.minor &&
           update == other.update &&
           build == other.build &&
           ea == other.ea;
  }

  @Override
  public int hashCode() {
    int hash = feature;
    hash = 31 * hash + minor;
    hash = 31 * hash + update;
    hash = 31 * hash + build;
    hash = 31 * hash + (ea ? 1231 : 1237);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (feature > 8) {
      sb.append(feature);
      if (minor > 0 || update > 0) sb.append('.').append(minor).append('.').append(update);
      if (ea) sb.append("-ea");
      if (build > 0) sb.append('+').append(build);
    }
    else {
      sb.append("1.").append(feature).append('.').append(minor);
      if (update > 0 || ea) sb.append('_').append(update);
      if (ea) sb.append("-ea");
      if (build > 0) sb.append("-b").append(build);
    }
    return sb.toString();
  }

  /**
   * Composes a version object out of given parameters.
   *
   * @throws IllegalArgumentException when any of numbers is negative
   */
  public static @NotNull JavaVersion compose(int feature, int minor, int update, int build, boolean ea) throws IllegalArgumentException {
    if (feature < 0) throw new IllegalArgumentException();
    if (minor < 0) throw new IllegalArgumentException();
    if (update < 0) throw new IllegalArgumentException();
    if (build < 0) throw new IllegalArgumentException();
    return new JavaVersion(feature, minor, update, build, ea);
  }

  private static JavaVersion current;

  /**
   * Returns the version of a Java runtime the class is loaded into.
   * The method attempts to parse {@code "java.runtime.version"} system property first (usually, it is more complete),
   * and falls back to {@code "java.version"} if the former is invalid or differs in {@link #feature} or {@link #minor} numbers.
   */
  public static @NotNull JavaVersion current() {
    if (current == null) {
      JavaVersion fallback = parse(System.getProperty("java.version")), rt = null;
      try {
        rt = parse(System.getProperty("java.runtime.version"));
      }
      catch (Throwable ignored) { }
      current = rt != null && rt.feature == fallback.feature && rt.minor == fallback.minor ? rt : fallback;
    }
    return current;
  }

  /**
   * <p>Parses a Java version string.</p>
   *
   * <p>Supports various sources, including (but not limited to):<br>
   *   - {@code "java.*version"} system properties (a version number without any decoration)<br>
   *   - output of "{@code java -version}" (usually "java version \"$VERSION\"")<br>
   *   - a second line of the above command (something like to "Java(TM) SE Runtime Environment (build $VERSION)")<br>
   *   - output of "{@code java --full-version}" ("java $VERSION")</p>
   *
   * <p>See com.intellij.util.lang.JavaVersionTest for examples.</p>
   *
   * @throws IllegalArgumentException if failed to recognize the number.
   */
  public static @NotNull JavaVersion parse(@NotNull String versionString) throws IllegalArgumentException {
    // trimming
    String str = versionString.trim();
    if (str.contains("Runtime Environment")) {
      int p = str.indexOf("(build ");
      if (p > 0) str = str.substring(p);
    }

    // partitioning
    List<String> separators = new ArrayList<String>();
    List<String> numbers = new ArrayList<String>();
    int length = str.length(), p = 0;
    boolean number = false;
    while (p < length) {
      int start = p;
      while (p < length && Character.isDigit(str.charAt(p)) == number) p++;
      String part = str.substring(start, p);
      (number ? numbers : separators).add(part);
      number = !number;
    }

    // parsing
    if (!numbers.isEmpty() && !separators.isEmpty()) {
      try {
        int feature = Integer.parseInt(numbers.get(0)), minor = 0, update = 0, build = 0;
        boolean ea = false;

        if (feature > 8 && feature < 50 /*sanity check*/) {
          // Java 9+
          p = 1;
          while (p < separators.size() && ".".equals(separators.get(p))) p++;
          if (p > 1 && numbers.size() > 2) {
            minor = Integer.parseInt(numbers.get(1));
            update = Integer.parseInt(numbers.get(2));
          }
          if (p < separators.size()) {
            String s = separators.get(p);
            if (StringUtil.startsWithChar(s, '-')) {
              ea = startsWithWord(s, "-ea") || startsWithWord(s, "-internal");
              if (p < numbers.size() && StringUtil.endsWithChar(s, '+')) {
                build = Integer.parseInt(numbers.get(p));
              }
              p++;
            }
            if (build == 0 && p < separators.size() && p < numbers.size() && "+".equals(separators.get(p))) {
              build = Integer.parseInt(numbers.get(p));
            }
          }
          return new JavaVersion(feature, minor, update, build, ea);
        }
        else if (feature == 1 && numbers.size() > 1 && separators.size() > 1 && ".".equals(separators.get(1))) {
          // Java 1.0 .. 1.8
          feature = Integer.parseInt(numbers.get(1));
          if (feature <= 8) {
            if (numbers.size() > 2 && separators.size() > 2 && ".".equals(separators.get(2))) {
              minor = Integer.parseInt(numbers.get(2));
              if (numbers.size() > 3 && separators.size() > 3 && "_".equals(separators.get(3))) {
                update = Integer.parseInt(numbers.get(3));
                if (separators.size() > 4) {
                  String s = separators.get(4);
                  if (StringUtil.startsWithChar(s, '-')) {
                    ea = startsWithWord(s, "-ea") || startsWithWord(s, "-internal");
                  }
                  p = 4;
                  while (p < separators.size() && !separators.get(p).endsWith("-b")) p++;
                  if (p < numbers.size()) {
                    build = Integer.parseInt(numbers.get(p));
                  }
                }
              }
            }
            return new JavaVersion(feature, minor, update, build, ea);
          }
        }
      }
      catch (NumberFormatException ignored) { }
    }

    throw new IllegalArgumentException(versionString);
  }

  private static boolean startsWithWord(String s, String word) {
    return s.startsWith(word) && (s.length() == word.length() || !Character.isLetterOrDigit(s.charAt(word.length())));
  }

  /**
   * A safe version of {@link #parse(String)} - returns {@code null} if can't parse a version string.
   */
  public static @Nullable JavaVersion tryParse(String versionString) {
    if (versionString != null) {
      try {
        return parse(versionString);
      }
      catch (IllegalArgumentException ignored) { }
    }

    return null;
  }
}