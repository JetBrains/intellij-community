/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.text.CharacterIterator;

/**
 * {@link CharacterIterator} implementation for a given fragment of a {@code char} array.
 */
public class CharArrayIterator implements CharacterIterator {
  @NotNull 
  private final char[] myText;
  private final int myStart;
  private final int myEnd;
  private int myIndex;

  public CharArrayIterator(@NotNull char[] text, int start, int end) {
    if (start < 0 || start > end || end > text.length) {
      throw new IllegalArgumentException("Text length: " + text.length + ", start: " + start + ", end: " + end);
    }
    myText = text;
    myStart = start;
    myEnd = end;
    myIndex = myStart;
  }

  @Override
  public char first() {
    myIndex = myStart;
    return current();
  }

  @Override
  public char last() {
    myIndex = myStart == myEnd ? myEnd : myEnd - 1;
    return current();
  }

  @Override
  public char current() {
    return myIndex < myEnd ? myText[myIndex] : DONE;
  }

  @Override
  public char next() {
    myIndex = Math.min(myEnd, myIndex + 1);
    return current();
  }

  @Override
  public char previous() {
    if (myIndex == myStart) return DONE;
    myIndex--;
    return current();
  }

  @Override
  public char setIndex(int position) {
    if (position < myStart || position > myEnd) {
      throw new IllegalArgumentException("Position: " + position + ", start: " + myStart + ", end: " + myEnd);
    }
    myIndex = position;
    return current();
  }

  @Override
  public int getBeginIndex() {
    return myStart;
  }

  @Override
  public int getEndIndex() {
    return myEnd;
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @Override
  public CharArrayIterator clone() {
    try {
      return (CharArrayIterator)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
