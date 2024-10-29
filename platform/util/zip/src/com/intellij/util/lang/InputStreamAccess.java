// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

class InputStreamAccess implements Access<InputStream> {
  private static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
  private static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

  private byte[] buffer;
  private int currentBlock;
  private final int totalStreamLength;
  private final int roundedUpTotalBlocks;

  private boolean isLatestBlockRead = false;
  // Position in the stream from which the latest buffer started
  private int zeroPositionOffset = 0;

  InputStreamAccess(int streamLength) {
    currentBlock = -1;
    buffer = new byte[Xxh3Impl.getBlockLength()];
    totalStreamLength = streamLength;
    roundedUpTotalBlocks = (int) Math.ceil((double) streamLength / Xxh3Impl.getBlockLength());
  }

  @Override
  public long i64(InputStream input, int offset) {
    int positionInBuffer = actualizeBufferAndGetPosition(input, offset, Long.BYTES - 1);
    return (long)LONG_HANDLE.get(buffer, positionInBuffer);
  }

  @Override
  public int i32(InputStream input, int offset) {
    int positionInBuffer = actualizeBufferAndGetPosition(input, offset, Integer.BYTES - 1);
    return (int)INT_HANDLE.get(buffer, positionInBuffer);
  }

  @Override
  public int i8(InputStream input, int offset) {
    int positionInBuffer = actualizeBufferAndGetPosition(input, offset, 0);
    return buffer[positionInBuffer];
  }

  private int actualizeBufferAndGetPosition(InputStream input, int offset, int align) {
    int blockLength = Xxh3Impl.getBlockLength();
    int requestedBlock = (offset + align) / blockLength;
    int positionInBuffer = offset % blockLength;

    if (isLatestBlockRead) {
      return offset - zeroPositionOffset;
    }
    else if (requestedBlock == currentBlock) {
      return positionInBuffer;
    }
    else if (requestedBlock > currentBlock) {
      try {
        // If it's the penultimate block, we need to read until the end of the stream.
        // There are some cases of hashing algorithm then for the latest block it requested data that is
        // located in between of two blocks. To support this case, it was decided at the penultimate block
        // to read until the end of the stream.
        // For the last block we can use the same logic except the fact that its buffer will be less or equal
        // size of `Xxh3Impl.getBlockLength()`
        if (requestedBlock + 2 == roundedUpTotalBlocks || requestedBlock + 1 == roundedUpTotalBlocks) {
          int remainingBufferSize = totalStreamLength - requestedBlock * blockLength;
          buffer = new byte[remainingBufferSize];
          readDataFromStreamToBuffer(input);
          zeroPositionOffset = requestedBlock * blockLength;
          isLatestBlockRead = true;
          currentBlock++;
          return positionInBuffer;
        }
        readDataFromStreamToBuffer(input);
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

  private void readDataFromStreamToBuffer(@NotNull InputStream input) throws IOException {
    int bytesRead = input.readNBytes(buffer, 0, buffer.length);
    if (bytesRead < buffer.length) {
      throw new IOException("Unexpected end of stream (" + bytesRead + " bytes read; " + buffer.length +
                            " expected; totalBlocks = " + roundedUpTotalBlocks + "; totalStreamLength = " + totalStreamLength + ")");
    }
  }
}
