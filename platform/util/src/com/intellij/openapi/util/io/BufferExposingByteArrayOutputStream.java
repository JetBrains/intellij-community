// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.util.io.UnsyncByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;

public final class BufferExposingByteArrayOutputStream extends UnsyncByteArrayOutputStream {
  public BufferExposingByteArrayOutputStream() {}

  public BufferExposingByteArrayOutputStream(int size) {
    super(size);
  }

  public BufferExposingByteArrayOutputStream(byte[] buffer) {
    super(buffer);
  }

  public byte @NotNull [] getInternalBuffer() {
    return myBuffer;
  }

  // moves back the written bytes pointer by {@link #size}, to "unwrite" last {@link #size} bytes
  public int backOff(int size) {
    assert size >= 0 : size;
    myCount -= size;
    assert myCount >= 0 : myCount;
    return myCount;
  }
}
