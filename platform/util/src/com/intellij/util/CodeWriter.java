// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NonNls;

import java.io.PrintWriter;
import java.util.StringTokenizer;

public final class CodeWriter extends PrintWriter {
  private final int myIndent;
  private int myIndentLevel = 0;

  // Printer state
  private boolean myNewLineStarted = true;

  public CodeWriter(PrintWriter writer) {
    super(writer);
    myIndent = 2;
  }

  @Override
  public void print(@NonNls String s) {
    possiblyIndent(s);
    super.print(s);
    for (int i = 0; i < s.length(); i++) {
      if (isOpenBrace(s, i)) myIndentLevel++;
      if (isCloseBrace(s, i)) myIndentLevel--;
    }
  }


  private static boolean isCloseBrace(String s, int index) {
    char c = s.charAt(index);

    return c == ')' || c == ']' || c == '}';
  }

  private static boolean isOpenBrace(String s, int index) {
    char c = s.charAt(index);

    return c == '(' || c == '[' || c == '{';
  }

  @Override
  public void println() {
    ((PrintWriter)out).println();
    myNewLineStarted = true;
  }

  private void possiblyIndent(String s) {
    if (myNewLineStarted) {
      int i = 0;
      for (; i < s.length() && s.charAt(i) == ' '; i++) {
      }
      int firstNonBlank = (i < s.length() && s.charAt(i) != ' ') ? i : -1;
      if (firstNonBlank >= 0) {
        if (isCloseBrace(s, firstNonBlank)) myIndentLevel--;
        int blanksToPrint = myIndent * myIndentLevel - firstNonBlank;
        for (int j = 0; j < blanksToPrint; j++) {
          write(" ");
        }
        if (isCloseBrace(s, firstNonBlank)) myIndentLevel++;
      }
      myNewLineStarted = false;
    }
  }

  @Override
  public void println(String s) {
    StringTokenizer st = new StringTokenizer(s, "\r\n", false);

    while (st.hasMoreTokens()) {
      super.println(st.nextToken());
    }
  }

}
