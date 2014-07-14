/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class ByteBufferWrapper {
  protected final File myFile;
  protected final long myPosition;
  protected final long myLength;
  protected volatile boolean myDirty;

  protected ByteBufferWrapper(final File file, final long offset, final long length) {
    myFile = file;
    myPosition = offset;
    myLength = length;
  }

  @Nullable
  public abstract ByteBuffer getCachedBuffer();

  public final void markDirty() {
    if (!myDirty) myDirty = true;
  }

  public final boolean isDirty() {
    return myDirty;
  }

  public abstract ByteBuffer getBuffer() throws IOException;

  public abstract void unmap();

  public abstract void flush();

  public void dispose() {
    unmap();
  }

  public static ByteBufferWrapper readWrite(final File file, final int offset, final int length) {
    return new ReadWriteDirectBufferWrapper(file, offset, length);
  }

  public static ByteBufferWrapper readWriteDirect(final File file, final long offset, final int length) {
    return new ReadWriteDirectBufferWrapper(file, offset, length);
  }

  public static ByteBufferWrapper readOnly(final File file, final int offset) {
    return new ReadOnlyMappedBufferWrapper(file, offset);
  }

  @Override
  public String toString() {
    return "Buffer for " + myFile + " size: " + myLength;
  }
}
