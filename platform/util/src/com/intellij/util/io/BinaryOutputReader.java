// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public abstract class BinaryOutputReader extends BaseDataReader {
  private final @NotNull InputStream myStream;
  private final byte @NotNull [] myBuffer = new byte[8192];

  public BinaryOutputReader(@NotNull InputStream stream, @NotNull SleepingPolicy sleepingPolicy) {
    super(sleepingPolicy);
    myStream = stream;
  }

  @Override
  protected boolean readAvailableNonBlocking() throws IOException {
    byte[] buffer = myBuffer;
    boolean read = false;

    int n;
    while (myStream.available() > 0 && (n = myStream.read(buffer)) >= 0) {
      if (n > 0) {
        read = true;
        onBinaryAvailable(buffer, n);
      }
    }

    return read;
  }

  @Override
  protected final boolean readAvailableBlocking() throws IOException {
    byte[] buffer = myBuffer;
    boolean read = false;

    int n;
    while ((n = myStream.read(buffer)) >= 0) {
      if (n > 0) {
        read = true;
        onBinaryAvailable(buffer, n);
      }
    }

    return read;
  }

  protected abstract void onBinaryAvailable(byte @NotNull [] data, int size);

  @Override
  protected void close() throws IOException {
    myStream.close();
  }
}