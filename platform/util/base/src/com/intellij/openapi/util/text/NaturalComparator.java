// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Implementation of <a href="http://www.codinghorror.com/blog/2007/12/sorting-for-humans-natural-sort-order.html">
 * "Sorting for Humans: Natural Sort Order"</a>
 *
 * @author Bas Leijdekkers
 */
public final class NaturalComparator implements Comparator<String> {
  public static final Comparator<String> INSTANCE = new NaturalComparator();

  @Override
  public int compare(String s1, String s2) {
    //noinspection StringEquality
    if (s1 == s2) return 0;
    if (s1 == null) return -1;
    if (s2 == null) return +1;
    return naturalCompare(s1, s2, s1.length(), s2.length(), true);
  }

  @Contract(pure = true)
  private static int naturalCompare(@NotNull String s1, @NotNull String s2, int length1, int length2, boolean ignoreCase) {
    int i = 0;
    int j = 0;
    for (; i < length1 && j < length2; i++, j++) {
      final char ch1 = s1.charAt(i);
      final char ch2 = s2.charAt(j);
      if ((Strings.isDecimalDigit(ch1) || ch1 == ' ') && (Strings.isDecimalDigit(ch2) || ch2 == ' ')) {
        final int start1 = skipChar(s1, skipChar(s1, i, length1, ' '), length1, '0');
        final int start2 = skipChar(s2, skipChar(s2, j, length2, ' '), length2, '0');

        final int end1 = skipDigits(s1, start1, length1);
        final int end2 = skipDigits(s2, start2, length2);

        // numbers with more digits are always greater than shorter numbers
        final int lengthDiff = (end1 - start1) - (end2 - start2);
        if (lengthDiff != 0) return lengthDiff;

        // compare numbers with equal digit count
        final int numberDiff = compareCharRange(s1, s2, start1, start2, end1);
        if (numberDiff != 0) return numberDiff;

        // compare number length including leading spaces and zeroes
        final int fullLengthDiff = (end1 - i) - (end2 - j);
        if (fullLengthDiff != 0) return fullLengthDiff;

        // numbers are same, compare leading spaces and zeroes
        final int leadingDiff = compareCharRange(s1, s2, i, j, start1);
        if (leadingDiff != 0) return leadingDiff;

        i = end1 - 1;
        j = end2 - 1;
      }
      else {
        final int diff = compareChars(ch1, ch2, ignoreCase);
        if (diff != 0) return diff;
      }
    }
    // After the loop the end of one of the strings might not have been reached, if the other
    // string ends with a number and the strings are equal until the end of that number. When
    // there are more characters in the string, then it is greater.
    if (i < length1) return +1;
    if (j < length2) return -1;
    if (length1 != length2) return length1 - length2;

    // do case sensitive compare if case insensitive strings are equal
    return ignoreCase ? naturalCompare(s1, s2, length1, length2, false) : 0;
  }

  private static int compareCharRange(@NotNull String s1, @NotNull String s2, int offset1, int offset2, int end1) {
    for (int i = offset1, j = offset2; i < end1; i++, j++) {
      final int diff = s1.charAt(i) - s2.charAt(j);
      if (diff != 0) return diff;
    }
    return 0;
  }

  private static int compareChars(char ch1, char ch2, boolean ignoreCase) {
    // transitivity fix, otherwise can fail when comparing strings with characters between ' ' and '0' (e.g. '#')
    if (ch1 == ' ' && ch2 > ' ' && ch2 < '0') return +1;
    if (ch2 == ' ' && ch1 > ' ' && ch1 < '0') return -1;
    return Strings.compare(ch1, ch2, ignoreCase);
  }

  private static int skipDigits(String s, int start, int end) {
    while (start < end && Strings.isDecimalDigit(s.charAt(start))) start++;
    return start;
  }

  private static int skipChar(String s, int start, int end, char c) {
    while (start < end && s.charAt(start) == c) start++;
    return start;
  }
}
