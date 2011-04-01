/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.cvsSupport2.javacvsImpl.io;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Bas Leijdekkers
 */
public class LineReader {

  private static final int BUFFER_SIZE = 128 * 1024;
  private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  private static final ThreadLocal<byte[]> myBuffer = new ThreadLocal<byte[]>() {
    @Override
    protected byte[] initialValue() {
      return new byte[BUFFER_SIZE];
    }
  };

  private final InputStream myInputStream;
  private final ByteArrayOutputStream myOutputStream = new ByteArrayOutputStream();
  private int myBytesExpected;
  private int myPosition = 0;
  private int myBytesBuffered = 0;
  private int myEolFlag = 0;

  public LineReader(InputStream in, int bytesExpected) {
    myInputStream = in;
    myBytesExpected = bytesExpected;
  }

  private void fillBuffer() throws IOException {
    if (myBytesExpected == 0) {
      myBytesBuffered = -1;
      return;
    }
    final byte[] buffer = myBuffer.get();
    myBytesBuffered = myInputStream.read(buffer, 0, Math.min(buffer.length, myBytesExpected));
    if (myBytesBuffered > 0) {
      myBytesExpected -= myBytesBuffered;
      myPosition = 0;
    }
  }

  private byte[] getLineArray() {
    myEolFlag = 0;
    myPosition++;
    if (myOutputStream.size() == 0) {
      return EMPTY_BYTE_ARRAY;
    }
    final byte[] bytes = myOutputStream.toByteArray();
    myOutputStream.reset();
    return bytes;
  }

  @Nullable
  public byte[] readLine() throws IOException {
    if (myBytesBuffered == -1) {
      return null;
    } else if (myPosition >= myBytesBuffered) {
      fillBuffer();
      if (myBytesBuffered == -1) {
        return EMPTY_BYTE_ARRAY;
      }
    }

    while (myBytesBuffered != -1) {
      for (; myPosition < myBytesBuffered; myPosition++) {
        final byte c = myBuffer.get()[myPosition];
        switch (c) {
          case '\r':
            if (myEolFlag < 2) {
              myEolFlag++;
            }
            else {
              myPosition -= 2;
              return getLineArray();
            }
            break;
          case '\n':
            return getLineArray();
          default:
            switch (myEolFlag) {
              case 2:
                myPosition--;
              case 1:
                myPosition--;
                return getLineArray();
              default:
                myEolFlag = 0;
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