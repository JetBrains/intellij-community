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
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

public abstract class MappedBufferWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.MappedBufferWrapper");

  @NonNls private static final String CLEANER_METHOD_NAME = "cleaner";
  @NonNls private static final String CLEAN_METHOD_NAME = "clean";

  protected final File myFile;
  protected final long myPosition;
  protected final long myLength;

  private volatile ByteBuffer myBuffer;

  public MappedBufferWrapper(final File file, final long pos, final long length) {
    myFile = file;
    myPosition = pos;
    myLength = length;
  }

  protected abstract MappedByteBuffer map();

  private static final int MAX_FORCE_ATTEMPTS = 10;

  public final void unmap() {
    if (!unmapMappedByteBuffer142b19(this)) {
      LOG.error("Unmapping failed for: " + myFile);
    }
    myBuffer = null;
  }

  public ByteBuffer getIfCached() {
    return myBuffer;
  }

  public ByteBuffer buf() {
    if (myBuffer == null) {
      myBuffer = map();
    }
    return myBuffer;
  }


  private static boolean unmapMappedByteBuffer142b19(MappedBufferWrapper holder) {
    if (clean(holder.getIfCached())) {
      return true;
    }

    return false;
  }

  public static boolean clean(final ByteBuffer buffer) {
    if (buffer == null) return true;

    if (!tryForce(buffer)) {
      return false;
    }

    return AccessController.doPrivileged(new PrivilegedAction<Object>() {
      public Object run() {
        try {
          Method getCleanerMethod = getCleanerMethod(buffer);
          Object cleaner = getCleanerMethod.invoke(buffer, ArrayUtil.EMPTY_OBJECT_ARRAY);
          if (cleaner == null) return null; // Already cleaned

          Method cleanMethod = getCleanMethod();
          cleanMethod.invoke(cleaner, ArrayUtil.EMPTY_OBJECT_ARRAY);
        }
        catch (Exception e) {
          return buffer;
        }
        return null;
      }
    }) == null;
  }

  public static boolean tryForce(ByteBuffer buffer) {
    for (int i = 0; i < MAX_FORCE_ATTEMPTS; i++) {
      try {
        ((MappedByteBuffer)buffer).force();
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

  private static Method CLEAN_METHOD;
  private static Method getCleanMethod() throws ClassNotFoundException, NoSuchMethodException {
    Method m = CLEAN_METHOD;
    if (m == null) {
      Class cleanerClass = Class.forName("sun.misc.Cleaner");
      m = cleanerClass.getMethod(CLEAN_METHOD_NAME, ArrayUtil.EMPTY_CLASS_ARRAY);

      CLEAN_METHOD = m;
    }

    return m;
  }

  private static Method GET_CLEANER_METHOD;
  private static Method getCleanerMethod(final Object buffer) throws NoSuchMethodException {
    Method m = GET_CLEANER_METHOD;
    if (m == null) {
      m = buffer.getClass().getMethod(CLEANER_METHOD_NAME, ArrayUtil.EMPTY_CLASS_ARRAY);
      m.setAccessible(true);
      GET_CLEANER_METHOD = m;
    }
    return m;
  }

  public boolean isMapped() {
    return getIfCached() != null;
  }

  public void flush() {
    final ByteBuffer buffer = getIfCached();
    if (buffer instanceof MappedByteBuffer) {
      tryForce(buffer);
    }
  }

  public void dispose() {
    unmap();
  }
}
