// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.io.Reader;

public class UnsyncCharArrayReader extends Reader {
  private final char[] myText;
  private final int myLength;
  private int myCurPos;

  public UnsyncCharArrayReader(final char[] text, int offset, int length) {
    myText = text;
    myLength = length;
    myCurPos = offset;
  }

  @Override
  public void close() {}

  @Override
  public int read(char @NotNull [] cbuf, int off, int len) {
    if (off < 0 || off > cbuf.length || len < 0 || off + len > cbuf.length || off + len < 0) {
        throw new IndexOutOfBoundsException();
    }
    if (len == 0) {
        return 0;
    }

    int charsToCopy = Math.min(len, myLength - myCurPos);
    if (charsToCopy <= 0) return -1;

    System.arraycopy(myText, myCurPos, cbuf, off, charsToCopy);

    myCurPos += charsToCopy;
    return charsToCopy;
  }

  @Override
  public int read() {
    if (myCurPos >= myLength) return -1;
    return myText[myCurPos++];
  }
}
