// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang;

import java.util.Collection;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@SuppressWarnings({"unused", "rawtypes"})
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

  public static boolean startsWith(String seq, String s2) {
    return org.apache.commons.lang3.StringUtils.startsWith(seq, s2);
  }

  public static boolean endsWithIgnoreCase(String seq, String s2) {
    return org.apache.commons.lang3.StringUtils.endsWithIgnoreCase(seq, s2);
  }

  public static boolean startsWithIgnoreCase(String seq, String s2) {
    return org.apache.commons.lang3.StringUtils.startsWithIgnoreCase(seq, s2);
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

  public static int indexOf(String seq, String searchSeq) {
    return org.apache.commons.lang3.StringUtils.indexOf(seq, searchSeq);
  }

  public static int lastIndexOf(String seq, String searchSeq) {
    return org.apache.commons.lang3.StringUtils.lastIndexOf(seq, searchSeq);
  }

  public static int indexOf(String seq, char c) {
    return org.apache.commons.lang3.StringUtils.indexOf(seq, c);
  }

  public static int lastIndexOf(String seq, char c) {
    return org.apache.commons.lang3.StringUtils.lastIndexOf(seq, c);
  }

  public static int indexOf(String seq, String searchSeq, int i) {
    return org.apache.commons.lang3.StringUtils.indexOf(seq, searchSeq, i);
  }

  public static int indexOf(String seq, char c, int i) {
    return org.apache.commons.lang3.StringUtils.indexOf(seq, c, i);
  }

  public static int indexOfAny(String seq, String[] strings) {
    return org.apache.commons.lang3.StringUtils.indexOfAny(seq, strings);
  }

  public static int indexOfAny(String seq, char[] chars) {
    return org.apache.commons.lang3.StringUtils.indexOfAny(seq, chars);
  }

  public static int indexOfDifference(String seq, String s2) {
    return org.apache.commons.lang3.StringUtils.indexOfDifference(seq, s2);
  }

  public static boolean isAlpha(String seq) {
    return org.apache.commons.lang3.StringUtils.isAlpha(seq);
  }

  public static boolean isAlphanumeric(String seq) {
    return org.apache.commons.lang3.StringUtils.isAlphanumeric(seq);
  }

  public static boolean isBlank(String seq) {
    return org.apache.commons.lang3.StringUtils.isBlank(seq);
  }

  public static boolean isEmpty(String seq) {
    return org.apache.commons.lang3.StringUtils.isEmpty(seq);
  }

  public static boolean isNotBlank(String seq) {
    return org.apache.commons.lang3.StringUtils.isNotBlank(seq);
  }

  public static boolean isNotEmpty(String seq) {
    return org.apache.commons.lang3.StringUtils.isNotEmpty(seq);
  }

  public static boolean isNumeric(String seq) {
    return org.apache.commons.lang3.StringUtils.isNumeric(seq);
  }

  public static boolean isWhitespace(String seq) {
    return org.apache.commons.lang3.StringUtils.isWhitespace(seq);
  }

  public static String join(Collection collection, String separator) {
    return org.apache.commons.lang3.StringUtils.join(collection, separator);
  }

  public static String join(Collection collection, char separator) {
    return org.apache.commons.lang3.StringUtils.join(collection, separator);
  }

  public static int length(String seq) {
    return org.apache.commons.lang3.StringUtils.length(seq);
  }

  public static int ordinalIndexOf(String str, String searchStr, int ordinal) {
    return org.apache.commons.lang3.StringUtils.ordinalIndexOf(str, searchStr, ordinal);
  }
}
