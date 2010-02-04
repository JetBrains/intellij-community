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
package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;

public class Word {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.processing.Word");
  private final String myText;
  private final TextRange myRange;

  public Word(String text, TextRange range) {
    myText = text;
    myRange = range;
    LOG.assertTrue(myRange.getStartOffset() >= 0);
    LOG.assertTrue(myRange.getEndOffset() >= myRange.getStartOffset(), myRange);
  }

  public int hashCode() {
    return getText().hashCode();
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof Word)) return false;
    Word other = (Word)obj;
    return getText().equals(other.getText());
  }

  public String getText() {
    return myRange.substring(myText);
  }

  public String getPrefix(int fromPosition) {
    LOG.assertTrue(fromPosition >= 0, "" + fromPosition);
    int wordStart = myRange.getStartOffset();
    LOG.assertTrue(fromPosition <= wordStart, "" + fromPosition + " " + wordStart);
    return myText.substring(fromPosition, wordStart);
  }

  public int getEnd() {
    return myRange.getEndOffset();
  }

  public int getStart() {
    return myRange.getStartOffset();
  }

  public String toString() {
    return getText();
  }

  public boolean isWhitespace() {
    return false;
  }

  public boolean atEndOfLine() {
    int start = myRange.getStartOffset();
    if (start == 0) return true;
    if (myText.charAt(start - 1) == '\n') return true;
    int end = myRange.getEndOffset();
    if (end == myText.length()) return true;
    if (myText.charAt(end) == '\n') return true;
    return false;
  }
}
