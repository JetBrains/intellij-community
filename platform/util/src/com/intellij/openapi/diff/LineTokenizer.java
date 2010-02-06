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
package com.intellij.openapi.diff;

import com.intellij.util.ArrayUtil;

import java.util.ArrayList;

public class LineTokenizer {
  private final char[] myChars;
  private final String myText;
  private int myIndex = 0;
  private String myLineSeparator = null;

  public LineTokenizer(String text) {
    myChars = text.toCharArray();
    myText = text;
  }

  public String[] execute() {
    ArrayList<String> lines = new ArrayList<String>();
    while (notAtEnd()) {
      int begin = myIndex;
      skipToEOL();
      int endIndex = myIndex;
      String line;
      boolean appendNewLine = false;
      if (notAtEnd() && isAtEOL()) {
        if (myChars[endIndex] == '\n') endIndex++;
        else appendNewLine = true;
        skipEOL();
      }
//      line = new String(myChars, begin, endIndex - begin);
      line = myText.substring(begin, endIndex);
      if (appendNewLine) line += "\n";
      lines.add(line);
    }
    return ArrayUtil.toStringArray(lines);
  }

  private void skipEOL() {
    int eolStart = myIndex;
    boolean nFound = false;
    boolean rFound = false;
    while (notAtEnd()) {
      boolean n = myChars[myIndex] == '\n';
      boolean r = myChars[myIndex] == '\r';
      if (!n && !r) break;
      if ((nFound && n) || (rFound && r)) break;
      nFound |= n;
      rFound |= r;
      myIndex++;
    }
    if (myLineSeparator == null) myLineSeparator = new String(myChars, eolStart, myIndex - eolStart);
  }

  public String getLineSeparator() { return myLineSeparator; }

  private void skipToEOL() {
    while (myIndex < myChars.length &&
           !(myChars[myIndex] == '\r' || myChars[myIndex] == '\n')) myIndex++;
  }

  private boolean notAtEnd() {
    return myIndex < myChars.length;
  }

  private boolean isAtEOL() {
    return myChars[myIndex] == '\r' || myChars[myIndex] == '\n';
  }

  public static String concatLines(String[] lines) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      buffer.append(line);
    }
    return buffer.substring(0, buffer.length());
  }

  public static String correctLineSeparators(String text) {
    return concatLines(new LineTokenizer(text).execute());
  }
}
