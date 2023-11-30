// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;

public final class BufferExposingByteArrayInputStream extends UnsyncByteArrayInputStream {
  public BufferExposingByteArrayInputStream(byte @NotNull [] buf) {
    super(buf);
  }

  public byte @NotNull [] getInternalBuffer() {
    return myBuffer;
  }
}