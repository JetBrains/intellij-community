// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.vcs.log.graph.utils.impl.ByteArrayUtils.*;

@ApiStatus.Internal
public final class SmartDeltaCompressor implements IntList {

  @NotNull
  public static SmartDeltaCompressor newInstance(@NotNull IntList deltaList) {
    if (deltaList.size() < 0) throw new NegativeArraySizeException("size < 0: " + deltaList.size());

    int bytesAfterCompression = countBytesAfterCompression(deltaList);
    byte[] deltas = new byte[bytesAfterCompression];

    int size = deltaList.size();
    int groundPointCount = groundPointCount(size, 64);

    int[] groundIndexes = new int[groundPointCount];
    long[] majorBits = new long[groundPointCount];
    long[] minorBits = new long[groundPointCount];

    int offset = 0;
    for (int main = 0; main < groundPointCount; main++) {
      groundIndexes[main] = offset;

      for (int rem = 0; rem < 64; rem++) {
        int index = main * 64 + rem;
        if (index >= size) break;
        int sizeOf = sizeOf(deltaList.get(index));
        writeDelta(offset, deltaList.get(index), sizeOf, deltas);

        long mask = 1L << rem;
        /*
          4 -> 11
          3 -> 10
          2 -> 01
          1 -> 00
         */
        if (sizeOf == 3 || sizeOf == 4) majorBits[main] |= mask;
        if (sizeOf == 2 || sizeOf == 4) minorBits[main] |= mask;

        offset += sizeOf;
      }
    }

    return new SmartDeltaCompressor(size, deltas, majorBits, minorBits, groundIndexes);
  }

  private final int mySize;
  private final byte[] myDeltas;

  private final long[] myMajorBits;
  private final long[] myMinorBits;

  private final int[] myStrongIndexes;

  private SmartDeltaCompressor(int size, byte[] deltas, long[] majorBits, long[] minorBits, int[] strongIndexes) {
    mySize = size;
    myDeltas = deltas;
    myMajorBits = majorBits;
    myMinorBits = minorBits;
    myStrongIndexes = strongIndexes;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public int get(int index) {
    int main = index >>> 6; // div 64
    int rem = index & 0x3f; // mod 64

    int shift = 63 - rem;
    long major = myMajorBits[main] << shift;
    long minor = myMinorBits[main] << shift;

    int sizeOf = (int)(2 * (major >>> 63) + (minor >>> 63) + 1);

    int endIndex = myStrongIndexes[main] + 2 * Long.bitCount(major) + Long.bitCount(minor) + rem + 1;

    return readDelta(endIndex - sizeOf, sizeOf, myDeltas);
  }
}
