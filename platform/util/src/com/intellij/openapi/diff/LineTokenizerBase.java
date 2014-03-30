/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class LineTokenizerBase<T> {
  private int myIndex = 0;
  private int myLineSeparatorStart = -1;
  private int myLineSeparatorEnd = -1;

  protected abstract void addLine(List<T> lines, int start, int end, boolean appendNewLine);

  protected abstract char charAt(int index);

  protected abstract int length();

  @NotNull
  protected abstract String substring(int start, int end);

  public void doExecute(List<T> lines) {
    while (notAtEnd()) {
      int begin = myIndex;
      skipToEOL();
      int endIndex = myIndex;
      boolean appendNewLine = false;

      if (notAtEnd() && isAtEOL()) {
        if (charAt(endIndex) == '\n') {
          endIndex++;
        }
        else {
          appendNewLine = true;
        }
        skipEOL();
      }

      addLine(lines, begin, endIndex, appendNewLine);
    }
  }

  private void skipEOL() {
    int eolStart = myIndex;
    boolean nFound = false;
    boolean rFound = false;
    while (notAtEnd()) {
      boolean n = charAt(myIndex) == '\n';
      boolean r = charAt(myIndex) == '\r';
      if (!n && !r) {
        break;
      }
      if ((nFound && n) || (rFound && r)) {
        break;
      }
      nFound |= n;
      rFound |= r;
      myIndex++;
    }
    if (myLineSeparatorStart == -1) {
      myLineSeparatorStart = eolStart;
      myLineSeparatorEnd = myIndex;
    }
  }

  @Nullable
  public String getLineSeparator() {
    if (myLineSeparatorStart == -1) return null;
    return substring(myLineSeparatorStart, myLineSeparatorEnd);
  }

  private void skipToEOL() {
    while (notAtEnd() && !isAtEOL()) {
      myIndex++;
    }
  }

  private boolean notAtEnd() {
    return myIndex < length();
  }

  private boolean isAtEOL() {
    return charAt(myIndex) == '\r' || charAt(myIndex) == '\n';
  }
}