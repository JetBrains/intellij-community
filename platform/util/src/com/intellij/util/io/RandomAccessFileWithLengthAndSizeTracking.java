/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Replacement of RandomAccessFile("rw") with shadow file pointer / size, valid when file manipulations happen in with this class only.
 * Note that sharing policy is the same as RandomAccessFile
 */
class RandomAccessFileWithLengthAndSizeTracking extends RandomAccessFile {
  private static final Logger LOG = Logger.getInstance(RandomAccessFileWithLengthAndSizeTracking.class.getName());
  private static final boolean doAssertions = SystemProperties.getBooleanProperty("idea.do.random.access.wrapper.assertions", false);

  private final String myPath;
  private volatile long mySize;
  private volatile long myPointer;

  public RandomAccessFileWithLengthAndSizeTracking(String name) throws IOException {
    super(name, "rw");
    mySize = super.length();
    myPath = name;

    if (LOG.isTraceEnabled()) {
      LOG.trace("Inst:" + this + "," + Thread.currentThread() + "," + getClass().getClassLoader());
    }
  }

  @Override
  public void seek(long pos) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Seek:" + this + "," + Thread.currentThread() + "," + pos + "," + myPointer + "," + mySize);
    }
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    if (myPointer == pos) {
      return;
    }
    super.seek(pos);
    myPointer = pos;
  }

  @Override
  public long length() throws IOException {
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    return mySize;
  }

  @Override
  public void write(int b) throws IOException {
    write(new byte[]{ (byte)(b & 0xFF)});
  }

  private void checkSizeAndPointerAssertions() throws IOException {
    if (myPointer != super.getFilePointer()) {
      assert false;
    }
    if (mySize != super.length()) {
      assert false;
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("write:" + this + "," + Thread.currentThread() + "," + len + "," + myPointer + "," + mySize);
    }
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }

    long pointer = myPointer;
    super.write(b, off, len);

    if (pointer == 0) { // first write can introduce extra bytes, reload the position to avoid position tracking problem, e.g. IDEA-106306
      pointer = super.getFilePointer();
    } else {
      pointer += len;
    }
    myPointer = pointer;
    mySize = Math.max(pointer, mySize);
    if (LOG.isTraceEnabled()) {
      LOG.trace("after write:" + this + "," + Thread.currentThread() + "," + myPointer + "," + mySize );
    }
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
  }

  @Override
  public void setLength(long newLength) throws IOException {
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    super.setLength(newLength);
    mySize = newLength;
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("read:" + this + "," + Thread.currentThread() + "," + len + "," + myPointer );
    }
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    int read = super.read(b, off, len);
    if (read != -1) myPointer += read;
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    return read;
  }

  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public int read() throws IOException {
    int read = super.read();
    ++myPointer;
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    return read;
  }

  @Override
  public long getFilePointer() throws IOException {
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }

    return myPointer;
  }

  @Override
  public int skipBytes(int n) throws IOException {
    int i = super.skipBytes(n);
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    return i;
  }

  @Override
  public void close() throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Closed:" + this + "," + Thread.currentThread() );
    }
    super.close();
  }

  @Override
  public String toString() {
    return myPath + "@" + Integer.toHexString(hashCode());
  }
}
