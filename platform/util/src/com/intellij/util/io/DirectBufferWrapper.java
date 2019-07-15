// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

abstract class DirectBufferWrapper extends ByteBufferWrapper {
  private volatile ByteBuffer myBuffer;

  DirectBufferWrapper(File file, long offset, long length) {
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
    if (myBuffer != null) {
      ByteBufferUtil.cleanBuffer(myBuffer);
      myBuffer = null;
    }
  }
}