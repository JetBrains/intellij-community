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

/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;

import java.io.IOException;
import java.io.InputStream;

public class ReplicatorInputStream extends InputStream {
  private final BufferExposingByteArrayOutputStream myTarget;
  private final InputStream mySource;
  private int markedSize;

  public ReplicatorInputStream(final InputStream source, final BufferExposingByteArrayOutputStream target) {
    mySource = source;
    myTarget = target;
  }

  @Override
  public int read() throws IOException {
    final int b = mySource.read();
    if (b == -1) return -1;
    myTarget.write(b);
    return b;
  }

  @Override
  public synchronized void mark(final int readlimit) {
    mySource.mark(readlimit);
    markedSize = myTarget.size();
  }

  @Override
  public boolean markSupported() {
    return mySource.markSupported();
  }

  @Override
  public synchronized void reset() throws IOException {
    mySource.reset();
    myTarget.backOff(myTarget.size() - markedSize);
    markedSize = 0;
  }

  @Override
  public int read(final byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public int read(final byte[] b, final int off, final int len) throws IOException {
    final int count = mySource.read(b, off, len);
    if (count < 0) return count;
    myTarget.write(b, off, count);
    return count;
  }

  @Override
  public long skip(final long n) throws IOException {
    final int skipped = read(new byte[(int)n]);
    return skipped;
  }

  @Override
  public int available() throws IOException {
    return mySource.available();
  }

  @Override
  public void close() throws IOException {
    mySource.close();
    myTarget.close();
  }

  public int getBytesRead() {
    return myTarget.size();
  }
}