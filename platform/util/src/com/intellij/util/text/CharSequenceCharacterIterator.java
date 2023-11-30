// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import java.text.CharacterIterator;

public final class CharSequenceCharacterIterator implements CharacterIterator {
  private final CharSequence myChars;
  private int myCurPosition;

  public CharSequenceCharacterIterator(final CharSequence chars) {
    myChars = chars;
    myCurPosition = 0;
  }

  @Override
  public char current() {
    if (myCurPosition < 0) {
      myCurPosition = 0;
      return DONE;
    }

    if (myCurPosition >= myChars.length()) {
      myCurPosition = myChars.length();
      return DONE;
    }

    return myChars.charAt(myCurPosition);
  }

  @Override
  public char first() {
    myCurPosition = 0;
    return current();
  }

  @Override
  public char last() {
    myCurPosition = myChars.length() - 1;
    return current();
  }

  @Override
  public char next() {
    myCurPosition++;
    return current();
  }

  @Override
  public char previous() {
    myCurPosition--;
    return current();
  }

  @Override
  public int getBeginIndex() {
    return 0;
  }

  @Override
  public int getEndIndex() {
    return myChars.length();
  }

  @Override
  public int getIndex() {
    return myCurPosition;
  }

  @Override
  public char setIndex(int position) {
    if (position < 0 || position > myChars.length()) {
      throw new IllegalArgumentException("Wrong index: " + position);
    }
    myCurPosition = position;
    return current();
  }

  @Override
  public Object clone() {
    final CharSequenceCharacterIterator it = new CharSequenceCharacterIterator(myChars);
    it.myCurPosition = myCurPosition;
    return it;
  }
}
