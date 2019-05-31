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
abstract class MappedBufferWrapper extends ByteBufferWrapper {
  private static final int MAX_FORCE_ATTEMPTS = 10;

  private volatile MappedByteBuffer myBuffer;

  protected MappedBufferWrapper(File file, long pos, long length) {
    super(file, pos, length);
  }

  protected abstract MappedByteBuffer map() throws IOException;

  @Override
  public final void unmap() {
    long started = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;

    if (myBuffer != null) {
      if (isDirty()) flush();
      if (!ByteBufferUtil.cleanBuffer(myBuffer)) {
        Logger.getInstance(MappedBufferWrapper.class).error("Unmapping failed for: " + myFile);
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

  @Override
  public void flush() {
    MappedByteBuffer buffer = myBuffer;
    if (buffer != null && isDirty()) {
      for (int i = 0; i < MAX_FORCE_ATTEMPTS; i++) {
        try {
          buffer.force();
          myDirty = false;
          break;
        }
        catch (Throwable e) {
          Logger.getInstance(MappedBufferWrapper.class).info(e);
          TimeoutUtil.sleep(10);
        }
      }
    }
  }
}