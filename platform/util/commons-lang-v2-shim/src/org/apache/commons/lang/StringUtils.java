// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@SuppressWarnings("unused")
@Deprecated(forRemoval = true)
public class StringUtils extends org.apache.commons.lang3.StringUtils {
  public static boolean contains(String seq, String searchSeq) {
    return org.apache.commons.lang3.StringUtils.contains(seq, searchSeq);
  }

  public static boolean contains(String seq, char c) {
    return org.apache.commons.lang3.StringUtils.contains(seq, c);
  }

  public static boolean containsAny(String seq, String s2) {
    return org.apache.commons.lang3.StringUtils.containsAny(seq, s2);
  }

  public static boolean containsAny(String seq, char[] chars) {
    return org.apache.commons.lang3.StringUtils.containsAny(seq, chars);
  }

  public static boolean containsIgnoreCase(String str, String searchStr) {
    return org.apache.commons.lang3.StringUtils.containsIgnoreCase(str, searchStr);
  }

  public static boolean containsNone(String str, String searchStr) {
    return org.apache.commons.lang3.StringUtils.containsNone(str, searchStr);
  }

  public static boolean containsNone(String str, char[] chars) {
    return org.apache.commons.lang3.StringUtils.containsNone(str, chars);
  }

  public static int countMatches(String str, String s2) {
    return org.apache.commons.lang3.StringUtils.countMatches(str, s2);
  }

  public static String defaultIfEmpty(String str, String defaultStr) {
    return isEmpty(str) ? defaultStr : str;
  }

  public static boolean endsWith(String seq, String s2) {
    return org.apache.commons.lang3.StringUtils.endsWith(seq, s2);
  }

  public static boolean endsWithIgnoreCase(String seq, String s2) {
    return org.apache.commons.lang3.StringUtils.endsWithIgnoreCase(seq, s2);
  }

  public static boolean equals(String seq, String s2) {
    return org.apache.commons.lang3.StringUtils.equals(seq, s2);
  }

  public static boolean equalsIgnoreCase(String seq, String s2) {
    return org.apache.commons.lang3.StringUtils.equalsIgnoreCase(seq, s2);
  }

  public static int getLevenshteinDistance(String s, String t) {
    return org.apache.commons.lang3.StringUtils.getLevenshteinDistance(s, t);
  }
}
