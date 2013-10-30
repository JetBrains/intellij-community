/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.text.CharacterIterator;

/**
 * @author max
 */
public class CharSequenceCharacterIterator implements CharacterIterator {
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
