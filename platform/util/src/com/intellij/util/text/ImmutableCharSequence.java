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

import org.jetbrains.annotations.NotNull;

public abstract class ImmutableCharSequence implements CharSequence {

  @Override
  public abstract ImmutableCharSequence subSequence(int start, int end);

  public static ImmutableCharSequence asImmutable(@NotNull final CharSequence cs) {
    if (cs instanceof ImmutableCharSequence) return (ImmutableCharSequence)cs;
    return new StringCharSequence(cs.toString());
  }
  
  protected static class ImmutableSubSequence extends ImmutableCharSequence {
    private final ImmutableCharSequence myChars;
    private final int myStart;
    private final int myEnd;

    public ImmutableSubSequence(@NotNull ImmutableCharSequence chars, int start, int end) {
      if (start < 0 || end > chars.length() || start > end) {
        throw new IndexOutOfBoundsException("chars sequence.length:" + chars.length() +
                                            ", start:" + start +
                                            ", end:" + end);
      }
      myChars = chars;
      myStart = start;
      myEnd = end;
    }

    @Override
    public final int length() {
      return myEnd - myStart;
    }

    @Override
    public final char charAt(int index) {
      return myChars.charAt(index + myStart);
    }

    @Override
    public ImmutableSubSequence subSequence(int start, int end) {
      if (start == myStart && end == myEnd) return this;
      return new ImmutableSubSequence(myChars, myStart + start, myStart + end);
    }

    @NotNull
    public String toString() {
      if (myChars instanceof StringCharSequence) return myChars.toString().substring(myStart, myEnd);
      return StringFactory.createShared(CharArrayUtil.fromSequence(myChars, myStart, myEnd));
    }
  }

  private static class StringCharSequence extends ImmutableCharSequence {
    private final String myString;

    public StringCharSequence(@NotNull String string) {
      myString = string;
    }

    @Override
    public int length() {
      return myString.length();
    }

    @Override
    public char charAt(int index) {
      return myString.charAt(index);
    }

    @Override
    public ImmutableCharSequence subSequence(int start, int end) {
      return new ImmutableSubSequence(this, start, end);
    }

    @NotNull
    @Override
    public String toString() {
      return myString;
    }
  }
}
