// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public class InputStreamAccess implements Access<InputStream> {
  private static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
  private static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

  private byte[] buffer;
  private int currentBlock;
  private final int totalStreamLength;
  private final int totalBlocks;

  public InputStreamAccess(int streamLength) {
    currentBlock = -1;
    buffer = new byte[Xxh3Impl.getBlockLength()];
    totalStreamLength = streamLength;
    totalBlocks = streamLength / Xxh3Impl.getBlockLength();
  }

  @Override
  public long i64(InputStream input, int offset) {
    int positionInBuffer = actualizeBufferAndGetPosition(input, offset);
    return (long)LONG_HANDLE.get(buffer, positionInBuffer);
  }

  @Override
  public int i32(InputStream input, int offset) {
    int positionInBuffer = actualizeBufferAndGetPosition(input, offset);
    return (int)INT_HANDLE.get(buffer, positionInBuffer);
  }

  @Override
  public int i8(InputStream input, int offset) {
    int positionInBuffer = actualizeBufferAndGetPosition(input, offset);
    return buffer[positionInBuffer];
  }

  private int actualizeBufferAndGetPosition(InputStream input, int offset) {
    int requestedBlock = offset / Xxh3Impl.getBlockLength();
    int positionInBuffer = offset % Xxh3Impl.getBlockLength();
    if (requestedBlock == currentBlock) {
      return positionInBuffer;
    }
    else if (requestedBlock > currentBlock) {
      // If it's the last block we should read the rest stream
      if (requestedBlock + 1 == totalBlocks) {
        int remainingBufferSize = totalStreamLength - requestedBlock * Xxh3Impl.getBlockLength();
        buffer = new byte[remainingBufferSize];
      }
      try {
        input.read(buffer);
        currentBlock++;
        return positionInBuffer;
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      throw new IllegalStateException("Unexpected state at InputStream hashing");
    }
  }
}
