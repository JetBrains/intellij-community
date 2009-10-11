/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util;

import java.io.IOException;
import java.io.InputStream;

public class ScrambledInputStream extends InputStream{
  private static final int MASK = ScrambledOutputStream.MASK;
  private final InputStream myOriginalStream;

  public ScrambledInputStream(InputStream originalStream) {
    myOriginalStream = originalStream;
  }

  public int read() throws IOException {
    int b = myOriginalStream.read();
    if (b == -1) return -1;
    return b ^ MASK;
  }

  public int read(byte[] b, int off, int len) throws IOException {
    int read = myOriginalStream.read(b, off, len);
    for(int i = 0; i < read; i++){
      b[off + i] ^= MASK;
    }
    return read;
  }

  public long skip(long n) throws IOException {
    return myOriginalStream.skip(n);
  }

  public int available() throws IOException {
    return myOriginalStream.available();
  }

  public void close() throws IOException {
    myOriginalStream.close();
  }

  public synchronized void mark(int readlimit) {
    myOriginalStream.mark(readlimit);
  }

  public synchronized void reset() throws IOException {
    myOriginalStream.reset();
  }

  public boolean markSupported() {
    return myOriginalStream.markSupported();
  }
}
