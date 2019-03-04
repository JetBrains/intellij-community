// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public final class ScrambledOutputStream extends OutputStream {
  static final int MASK = 0xAA;
  private final OutputStream myOriginalStream;

  public ScrambledOutputStream(@NotNull OutputStream originalStream) {
    myOriginalStream = originalStream;
  }

  @Override
  public void write(int b) throws IOException {
    myOriginalStream.write(b ^ MASK);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    byte[] newBytes = new byte[len];
    for (int i = 0; i < len; i++) {
      newBytes[i] = (byte)(b[off + i] ^ MASK);
    }
    myOriginalStream.write(newBytes, 0, len);
  }

  @Override
  public void flush() throws IOException {
    myOriginalStream.flush();
  }

  @Override
  public void close() throws IOException {
    myOriginalStream.close();
  }
}
