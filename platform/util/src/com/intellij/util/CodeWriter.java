/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import org.jetbrains.annotations.NonNls;

import java.io.PrintWriter;
import java.util.StringTokenizer;

/**
 * @author dsl
 */
public class CodeWriter extends PrintWriter {
  private final int myIndent;
  private int myIndentLevel = 0;

  // Printer state
  private boolean myNewLineStarted = true;

  public CodeWriter(PrintWriter writer) {
    super(writer);
    myIndent = 2;
  }

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

  public void println(String s) {
    StringTokenizer st = new StringTokenizer(s, "\r\n", false);

    while (st.hasMoreTokens()) {
      super.println(st.nextToken());
    }
  }

}
