// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.NotNull;

public class TypoService {
  private static class RefHolder {
    private static final TypoService INSTANCE = new TypoService();
  }

  private final char[][] keyboard = {
    {'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'},
    {'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'},
    {'z', 'x', 'c', 'v', 'b', 'n', 'm'}
  };

  private TypoService() {
  }

  @NotNull
  public static TypoService getInstance() {
    return RefHolder.INSTANCE;
  }

  public char leftMiss(char aChar) {
    boolean isUpperCase = isUpperAscii(aChar);
    char lc = isUpperCase ? toLowerAscii(aChar) : aChar;

    for (char[] line : keyboard) {
      for (int j = 0; j < line.length; j++) {
        char c = line[j];
        if (c == lc) {
          if (j > 0) {
            return isUpperCase ? toUpperAscii(line[j - 1]) : line[j - 1];
          }
          else {
            return 0;
          }
        }
      }
    }
    return 0;
  }

  public char rightMiss(char aChar) {
    boolean isUpperCase = isUpperAscii(aChar);
    char lc = isUpperCase ? toLowerAscii(aChar) : aChar;

    for (char[] line : keyboard) {
      for (int j = 0; j < line.length; j++) {
        char c = line[j];
        if (c == lc) {
          if (j + 1 < line.length) {
            return isUpperCase ? toUpperAscii(line[j + 1]) : line[j + 1];
          }
          else {
            return 0;
          }
        }
      }
    }
    return 0;
  }

  public interface CharComparator {
    boolean equals(char ch1, char ch2);
  }

  private static char toUpperAscii(char c) {
    if (c >= 'a' && c <= 'z') {
      return (char)(c + ('A' - 'a'));
    }
    return c;
  }

  private static char toLowerAscii(char c) {
    if (c >= 'A' && c <= 'Z') {
      return (char)(c - ('A' - 'a'));
    }
    return c;
  }

  private static boolean isUpperAscii(char c) {
    return 'A' <= c && c <= 'Z';
  }

  private static boolean isLowerAscii(char c) {
    return 'a' <= c && c <= 'z';
  }
}
