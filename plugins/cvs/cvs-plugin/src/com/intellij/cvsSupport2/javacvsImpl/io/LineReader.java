// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Bas Leijdekkers
 */
public class LineReader {

  private static final int DEFAULT_BUFFER_SIZE = 10 * 1024;

  private final byte[] myBuffer;
  private final InputStream myInputStream;
  private final ByteArrayOutputStream myOutputStream = new ByteArrayOutputStream();
  private int myBytesExpected;
  private int myPosition = 0;
  private int myBytesBuffered = 0;
  private int myLastEol = 0;

  public LineReader(InputStream in, int bytesExpected) {
   this(in, bytesExpected, DEFAULT_BUFFER_SIZE);
  }

  public LineReader(InputStream in, int bytesExpected, final int bufferSize) {
    if (bufferSize < 2) {
      throw new IllegalArgumentException("buffer size must be greater than 1");
    }
    myInputStream = in;
    myBytesExpected = bytesExpected;
    myBuffer = new byte[bufferSize];
  }

  private void fillBuffer() throws IOException {
    if (myBytesExpected == 0) {
      myBytesBuffered = -1;
      return;
    }
    myBytesBuffered = myInputStream.read(myBuffer, 0, Math.min(myBuffer.length, myBytesExpected));
    if (myBytesBuffered > 0) {
      myBytesExpected -= myBytesBuffered;
      myPosition = 0;
    }
  }

  private byte[] getLineArray() {
    myPosition++;
    if (myOutputStream.size() == 0) {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
    final byte[] bytes = myOutputStream.toByteArray();
    myOutputStream.reset();
    return bytes;
  }

  public byte @Nullable [] readLine() throws IOException {
    if (myBytesBuffered == -1) {
      if (myLastEol != 0) {
        myLastEol = 0;
        return getLineArray();
      }
      return null;
    } else if (myPosition >= myBytesBuffered) {
      fillBuffer();
      if (myBytesBuffered == -1) {
        return getLineArray();
      }
    }
    while (myBytesBuffered != -1) {
      for (; myPosition < myBytesBuffered; myPosition++) {
        final byte c = myBuffer[myPosition];
        switch (c) {
          case '\r':
            if (myLastEol == '\r') {
              return getLineArray();
            } else {
              myLastEol = '\r';
            }
            break;
          case '\n':
            if (myLastEol == '\n') {
              return getLineArray();
            } else {
              myLastEol = '\n';
            }
            break;
          default:
            if (myLastEol != 0) {
              myLastEol = 0;
              final byte[] line = getLineArray();
              myOutputStream.write(c);
              return line;
            } else {
              myOutputStream.write(c);
            }
            break;
        }
      }
      fillBuffer();
    }
    return getLineArray();
  }
}