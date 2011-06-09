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

import com.intellij.openapi.diagnostic.Logger;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

public abstract class MappedBufferWrapper {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.util.io.MappedBufferWrapper");

  protected final File myFile;
  protected final long myPosition;
  protected final long myLength;

  private volatile MappedByteBuffer myBuffer;

  public MappedBufferWrapper(final File file, final long pos, final long length) {
    myFile = file;
    myPosition = pos;
    myLength = length;
  }

  protected abstract MappedByteBuffer map() throws IOException;

  private static final int MAX_FORCE_ATTEMPTS = 10;

  public final void unmap() {
    if (!unmapMappedByteBuffer142b19(this)) {
      LOG.error("Unmapping failed for: " + myFile);
    }
    myBuffer = null;
  }

  public MappedByteBuffer getIfCached() {
    return myBuffer;
  }

  public MappedByteBuffer buf() throws IOException {
    MappedByteBuffer buffer = myBuffer;
    if (buffer == null) {
      myBuffer = buffer = map();
    }
    return buffer;
  }


  private static boolean unmapMappedByteBuffer142b19(MappedBufferWrapper holder) {
    return clean(holder.getIfCached());
  }

  private static boolean clean(final MappedByteBuffer buffer) {
    if (buffer == null) return true;

    if (!tryForce(buffer)) {
      return false;
    }

    return AccessController.doPrivileged(new PrivilegedAction<Object>() {
      public Object run() {
        try {
          Cleaner cleaner = ((DirectBuffer)buffer).cleaner();
          if (cleaner != null) cleaner.clean(); // Already cleaned otherwise
          return null;
        }
        catch (Exception e) {
          return buffer;
        }
      }
    }) == null;
  }

  private static boolean tryForce(MappedByteBuffer buffer) {
    for (int i = 0; i < MAX_FORCE_ATTEMPTS; i++) {
      try {
        buffer.force();
        return true;
      }
      catch (Throwable e) {
        try {
          Thread.sleep(10);
        }
        catch (InterruptedException e1) {
          // Can't be
        }
      }
    }
    return false;
  }

  public boolean isMapped() {
    return getIfCached() != null;
  }

  public void flush() {
    final MappedByteBuffer buffer = getIfCached();
    if (buffer != null) {
      tryForce(buffer);
    }
  }

  public void dispose() {
    unmap();
  }
}
