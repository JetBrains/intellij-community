// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.text.CharacterIterator;

/**
 * {@link CharacterIterator} implementation for a given fragment of a {@link CharSequence}.
 */
public final class CharSequenceIterator implements CharacterIterator {
  private final @NotNull CharSequence myText;
  private final int myStart;
  private final int myEnd;
  private int myIndex;

  public CharSequenceIterator(@NotNull CharSequence text, int start, int end) {
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
    return myIndex < myEnd ? myText.charAt(myIndex) : DONE;
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
  public CharSequenceIterator clone() {
    try {
      return (CharSequenceIterator)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
