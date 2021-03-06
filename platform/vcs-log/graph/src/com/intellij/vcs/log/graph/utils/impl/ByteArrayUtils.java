// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.IntList;
import org.jetbrains.annotations.NotNull;

final class ByteArrayUtils {
  private static final int BYTE_OFFSET = 8;
  private static final int BYTE_MASK = 0xff;

  public static int countBytesAfterCompression(@NotNull IntList deltaList) {
    int count = 0;
    for (int i = 0; i < deltaList.size(); i++) {
      count += sizeOf(deltaList.get(i));
    }
    return count;
  }

  // return count of byte after compression
  public static int sizeOf(int value) {
    if (value < 0) value = ~value;

    value >>= BYTE_OFFSET - 1;
    for (int i = 1; i < 4; i++) {
      if (value == 0) return i;
      value >>= BYTE_OFFSET;
    }
    return 4;
  }

  public static void writeDelta(int startIndex, int value, int sizeOf, byte[] bytes) {
    for (int i = sizeOf - 1; i >= 0; i--) {
      bytes[startIndex + i] = (byte)value;
      value >>= BYTE_OFFSET;
    }
  }

  public static int readDelta(int startIndex, int sizeOf, byte[] bytes) {
    int result = 0;

    boolean isNegative = bytes[startIndex] < 0;
    if (isNegative) {
      for (int i = 0; i < 4 - sizeOf; i++) {
        result <<= BYTE_OFFSET;
        result |= BYTE_MASK;
      }
    }

    for (int i = startIndex; i < startIndex + sizeOf; i++) {
      result <<= BYTE_OFFSET;
      result |= bytes[i] & BYTE_MASK;
    }
    return result;
  }

  public static int groundPointCount(int arraySize, int blockSize) {
    if (arraySize == 0) return 1;

    return (arraySize - 1) / blockSize + 1;
  }
}
