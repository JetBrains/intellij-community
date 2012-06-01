/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.io.Reader;

/**
 * @author max
 */
public class UnsyncCharArrayReader extends Reader {
  private final char[] myText;
  private final int myLength;
  private int myCurPos;

  public UnsyncCharArrayReader(final char[] text, int offset, int length) {
    myText = text;
    myLength = length;
    myCurPos = offset;
  }

  public void close() {}

  public int read(char[] cbuf, int off, int len) {
    if (off < 0 || off > cbuf.length || len < 0 || off + len > cbuf.length || off + len < 0) {
        throw new IndexOutOfBoundsException();
    } else if (len == 0) {
        return 0;
    }

    int charsToCopy = Math.min(len, myLength - myCurPos);
    if (charsToCopy <= 0) return -1;

    System.arraycopy(myText, myCurPos, cbuf, off, charsToCopy);

    myCurPos += charsToCopy;
    return charsToCopy;
  }

  public int read() {
    if (myCurPos >= myLength) return -1;
    return myText[myCurPos++];
  }
}
