// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.text.CharacterIterator;

/**
 * {@link CharacterIterator} implementation for a given fragment of a {@code char} array.
 */
public final class CharArrayIterator implements CharacterIterator {
  private final char @NotNull [] myText;
  private final int myStart;
  private final int myEnd;
  private int myIndex;

  public CharArrayIterator(char @NotNull [] text, int start, int end) {
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
