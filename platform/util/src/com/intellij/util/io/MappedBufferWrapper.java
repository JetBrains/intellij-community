// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.TimeoutUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

/**
 * @author max
 */
public abstract class MappedBufferWrapper extends ByteBufferWrapper {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.util.io.MappedBufferWrapper");

  private static final int MAX_FORCE_ATTEMPTS = 10;

  private volatile MappedByteBuffer myBuffer;

  protected MappedBufferWrapper(final File file, final long pos, final long length) {
    super(file, pos, length);
  }

  protected abstract MappedByteBuffer map() throws IOException;

  @Override
  public final void unmap() {
    long started = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;

    if (myBuffer != null) {
      if (isDirty()) tryForce(myBuffer);
      if (!ByteBufferUtil.cleanBuffer(myBuffer)) {
        LOG.error("Unmapping failed for: " + myFile);
      }
      myBuffer = null;
    }

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Unmapped " + myFile + "," + myPosition + "," + myLength + " for " + (finished - started));
      }
    }
  }

  @Override
  public ByteBuffer getCachedBuffer() {
    return myBuffer;
  }

  @Override
  public ByteBuffer getBuffer() throws IOException {
    MappedByteBuffer buffer = myBuffer;
    if (buffer == null) {
      myBuffer = buffer = map();
    }
    return buffer;
  }

  private static boolean tryForce(MappedByteBuffer buffer) {
    for (int i = 0; i < MAX_FORCE_ATTEMPTS; i++) {
      try {
        buffer.force();
        return true;
      }
      catch (Throwable e) {
        LOG.info(e);
        TimeoutUtil.sleep(10);
      }
    }
    return false;
  }

  @Override
  public void flush() {
    final MappedByteBuffer buffer = myBuffer;
    if (buffer != null && isDirty()) {
      if(tryForce(buffer)) myDirty = false;
    }
  }
}