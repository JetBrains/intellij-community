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

import java.io.Reader;

/**
 * @author max
 */
public class CharSequenceReader extends Reader {
  private final CharSequence myText;
  private int myCurPos;

  public CharSequenceReader(@NotNull CharSequence text) {
    myText = text;
    myCurPos = 0;
  }

  @Override
  public void close() {}

  @Override
  public int read(@NotNull char[] cbuf, int off, int len) {
    if (off < 0 || off > cbuf.length || len < 0 || off + len > cbuf.length || off + len < 0) {
        throw new IndexOutOfBoundsException("cbuf.length="+cbuf.length+"; off="+off+"; len="+len);
    }
    if (len == 0) {
        return 0;
    }

    if (myText instanceof CharArrayCharSequence) { // Optimization
      final int readChars = ((CharArrayCharSequence)myText).readCharsTo(myCurPos, cbuf, off, len);
      if (readChars < 0) return -1;
      myCurPos += readChars;
      return readChars;
    }

    int charsToCopy = Math.min(len, myText.length() - myCurPos);
    if (charsToCopy <= 0) return -1;

    for (int n = 0; n < charsToCopy; n++) {
      cbuf[n + off] = myText.charAt(n + myCurPos);
    }

    myCurPos += charsToCopy;
    return charsToCopy;
  }

  @Override
  public int read() {
    if (myCurPos >= myText.length()) return -1;
    return myText.charAt(myCurPos++);
  }
}
