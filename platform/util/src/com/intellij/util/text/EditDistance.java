// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

public final class EditDistance {
  private EditDistance() { }

  public static int levenshtein(@NotNull CharSequence str1, @NotNull CharSequence str2, boolean caseSensitive) {
    // Wagner-Fischer implementation of Levenshtein distance
    // (http://en.wikipedia.org/wiki/Wagner-Fischer_algorithm)
    int[][] d = prepare(str1.length(), str2.length());
    for (int i = 1; i <= str1.length(); i++) {
      for (int j = 1; j <= str2.length(); j++) {
        int cost = equal(str1.charAt(i - 1), str2.charAt(j - 1), caseSensitive) ? 0 : 1;
        d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
      }
    }
    return d[str1.length()][str2.length()];
  }

  public static int optimalAlignment(@NotNull CharSequence str1, @NotNull CharSequence str2, boolean caseSensitive) {
    // extension of the above with additional case of adjacent transpositions
    // (http://en.wikipedia.org/wiki/Damerau-Levenshtein_distance#Optimal_string_alignment_distance)
    int[][] d = prepare(str1.length(), str2.length());
    for (int i = 1; i <= str1.length(); i++) {
      for (int j = 1; j <= str2.length(); j++) {
        int cost = equal(str1.charAt(i - 1), str2.charAt(j - 1), caseSensitive) ? 0 : 1;
        d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
        if (i > 1 && j > 1 &&
            equal(str1.charAt(i - 1), str2.charAt(j - 2), caseSensitive) && equal(str1.charAt(i - 2), str2.charAt(j - 1), caseSensitive)) {
          d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + cost);
        }
      }
    }
    return d[str1.length()][str2.length()];
  }

  private static int[][] prepare(int length1, int length2) {
    int[][] d = new int[length1 + 1][length2 + 1];
    for (int i = 0; i <= length1; i++) d[i][0] = i;
    for (int j = 0; j <= length2; j++) d[0][j] = j;
    return d;
  }

  private static boolean equal(char c1, char c2, boolean caseSensitive) {
    return caseSensitive ? c1 == c2 : Character.toLowerCase(c1) == Character.toLowerCase(c2);
  }

  private static int min(int a, int b, int c) {
    return Math.min(Math.min(a, b), c);
  }
}
