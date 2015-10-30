/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

public abstract class DirectBufferWrapper extends ByteBufferWrapper {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.util.io.DirectBufferWrapper");

  private volatile ByteBuffer myBuffer;

  protected DirectBufferWrapper(final File file, final long offset, final long length) {
    super(file, offset, length);
  }

  @Override
  public ByteBuffer getCachedBuffer() {
    return myBuffer;
  }

  @Override
  public ByteBuffer getBuffer() throws IOException {
    ByteBuffer buffer = myBuffer;
    if (buffer == null) {
      myBuffer = buffer = create();
    }
    return buffer;
  }

  protected abstract ByteBuffer create() throws IOException;

  @Override
  public void unmap() {
    if (isDirty()) flush();
    if (myBuffer != null) disposeDirectBuffer(myBuffer);
    myBuffer = null;
  }

 static boolean disposeDirectBuffer(final ByteBuffer buffer) {
    return AccessController.doPrivileged(new PrivilegedAction<Object>() {
      @Override
      @Nullable
      public Object run() {
        try {
          if (buffer instanceof DirectBuffer) {
            Cleaner cleaner = ((DirectBuffer)buffer).cleaner();
            if (cleaner != null) cleaner.clean(); // Already cleaned otherwise
          }
          return null;
        }
        catch (Throwable e) {
          return buffer;
        }
      }
    }) == null;
  }

}
