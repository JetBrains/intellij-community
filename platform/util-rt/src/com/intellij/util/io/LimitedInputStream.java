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

/*
 * @author max
 */
package com.intellij.util.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedInputStream extends FilterInputStream {
  private final int myReadLimit;
  private int myBytesRead;

  public LimitedInputStream(final InputStream in, final int readLimit) {
    super(in);
    myReadLimit = readLimit;
    myBytesRead = 0;
  }

  public boolean markSupported() {
    return false;
  }

  public int read() throws IOException {
    if (myBytesRead == myReadLimit) return -1;
    final int r = super.read();
    if (r >= 0) myBytesRead++;
    return r;
  }

  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  public int read(byte[] b, int off, int len) throws IOException {
    if (myBytesRead >= myReadLimit) return -1;
    len = Math.min(len, myReadLimit - myBytesRead);
    if (len <= 0) return -1;

    final int actuallyRead = super.read(b, off, len);
    if (actuallyRead >= 0) myBytesRead += actuallyRead;

    return actuallyRead;
  }

  public long skip(long n) throws IOException {
    n = Math.min(n, myReadLimit - myBytesRead);
    if (n <= 0) return 0;

    final long skipped = super.skip(n);
    myBytesRead += skipped;
    return skipped;
  }

  public int available() throws IOException {
    return Math.min(super.available(), myReadLimit - myBytesRead);
  }

  protected int remainingLimit() {
    return myReadLimit - myBytesRead;
  }

  public synchronized void mark(final int readLimit) {
    throw new UnsupportedOperationException();
  }

  public synchronized void reset() throws IOException {
    throw new UnsupportedOperationException();
  }
}
