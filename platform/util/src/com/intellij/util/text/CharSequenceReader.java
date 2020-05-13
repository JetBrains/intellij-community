// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.io.Reader;

public final class CharSequenceReader extends Reader {
  private final CharSequence myText;
  private int myCurPos;

  public CharSequenceReader(@NotNull CharSequence text) {
    myText = text;
    myCurPos = 0;
  }

  @Override
  public void close() {}

  @Override
  public int read(char @NotNull [] cbuf, int off, int len) {
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
