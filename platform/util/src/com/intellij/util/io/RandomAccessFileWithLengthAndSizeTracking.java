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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static com.intellij.util.io.FileChannelUtil.unInterruptible;

/**
 * Replacement of read-write random-access file with shadow file pointer / size, valid when file manipulations happen in with this class only.
 * Note that sharing policy is the same as RandomAccessFile
 */
class RandomAccessFileWithLengthAndSizeTracking {
  private static final Logger LOG = Logger.getInstance(RandomAccessFileWithLengthAndSizeTracking.class.getName());
  private static final boolean doAssertions = SystemProperties.getBooleanProperty("idea.do.random.access.wrapper.assertions", false);

  private final Path myPath;
  private final FileChannel myChannel;
  private volatile long mySize;
  private volatile long myPointer;

  RandomAccessFileWithLengthAndSizeTracking(Path path) throws IOException {
    Path parent = path.getParent();
    if (!Files.exists(parent)) {
      Files.createDirectories(parent);
    }
    myChannel = unInterruptible(FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
    mySize = myChannel.size();
    myPath = path;

    if (LOG.isTraceEnabled()) {
      LOG.trace("Inst:" + this + "," + Thread.currentThread() + "," + getClass().getClassLoader());
    }
  }

  void seek(long pos) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Seek:" + this + "," + Thread.currentThread() + "," + pos + "," + myPointer + "," + mySize);
    }
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    if (myPointer == pos) {
      return;
    }
    myChannel.position(pos);
    myPointer = pos;
  }

  long length() throws IOException {
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    return mySize;
  }

  private void checkSizeAndPointerAssertions() throws IOException {
    assert myPointer == myChannel.position();
    assert mySize == myChannel.size();
  }

  void write(byte[] b, int off, int len) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("write:" + this + "," + Thread.currentThread() + "," + len + "," + myPointer + "," + mySize);
    }
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }

    long pointer = myPointer;
    myChannel.write(ByteBuffer.wrap(b, off, len));

    if (pointer == 0) { // first write can introduce extra bytes, reload the position to avoid position tracking problem, e.g. IDEA-106306
      pointer = myChannel.position();
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

  int read(byte[] b, int off, int len) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("read:" + this + "," + Thread.currentThread() + "," + len + "," + myPointer );
    }
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    int read = myChannel.read(ByteBuffer.wrap(b, off, len));
    if (read != -1) myPointer += read;
    if (doAssertions) {
      checkSizeAndPointerAssertions();
    }
    return read;
  }

  void close() throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Closed:" + this + "," + Thread.currentThread() );
    }
    force();
    myChannel.close();
  }

  @Override
  public String toString() {
    return myPath + "@" + Integer.toHexString(hashCode());
  }

  private void force() throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Forcing:" + this + "," + Thread.currentThread() );
    }

    myChannel.force(true);
  }
}
