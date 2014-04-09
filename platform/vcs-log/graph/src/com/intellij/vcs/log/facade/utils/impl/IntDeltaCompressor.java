/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.vcs.log.facade.utils.impl;

import com.intellij.util.BooleanFunction;
import com.intellij.vcs.log.facade.utils.Flags;
import com.intellij.vcs.log.facade.utils.IntList;
import com.intellij.vcs.log.facade.utils.IntToIntMap;
import org.jetbrains.annotations.NotNull;

/*package*/ class IntDeltaCompressor {

  @NotNull
  public static IntDeltaCompressor newInstance(@NotNull IntList deltaList) {
    if (deltaList.size() < 0)
      throw new NegativeArraySizeException("size < 0: " + deltaList.size());

    int bytesAfterCompression = IntDeltaUtils.countBytesAfterCompression(deltaList);
    Flags startedDeltaIndex = new BitSetFlags(bytesAfterCompression);
    byte[] compressedDeltas = new byte[bytesAfterCompression];

    int currentStartIndex = 0;
    for (int  i = 0; i < deltaList.size(); i++) {
      startedDeltaIndex.set(currentStartIndex, true);

      int value = deltaList.get(i);
      int sizeOf = IntDeltaUtils.sizeOf(value);
      IntDeltaUtils.writeDelta(currentStartIndex, value, sizeOf, compressedDeltas);

      currentStartIndex += sizeOf;
    }

    return new IntDeltaCompressor(compressedDeltas, startedDeltaIndex);
  }

  @NotNull private final byte[] myCompressedDeltas;
  @NotNull private final Flags myStartedDeltaIndex;

  @NotNull private final IntToIntMap myStartIndexMap;

  private IntDeltaCompressor(@NotNull byte[] compressedDeltas, @NotNull final Flags startedDeltaIndex) {
    myCompressedDeltas = compressedDeltas;
    myStartedDeltaIndex = startedDeltaIndex;
    myStartIndexMap = ListIntToIntMap.newInstance(new BooleanFunction<Integer>() {
      @Override
      public boolean fun(Integer integer) {
        return startedDeltaIndex.get(integer);
      }
    }, startedDeltaIndex.size());
  }

  // [left, right)
  public int getSumOfInterval(int left, int right) {
    if (left < 0 || left > right || right > size())
      throw new IllegalArgumentException("Size is: " + size() + ", but interval is: (" + left +", " + right + ")");
    if (left == size())
      return 0;

    int startIndex = myStartIndexMap.getLongIndex(left);
    int sum = 0;
    for (int i = 0; i < right - left; i++) {
      int sizeOf = getNextStartIndex(startIndex) - startIndex;
      sum += IntDeltaUtils.readDelta(startIndex, sizeOf, myCompressedDeltas);
      startIndex += sizeOf;
    }
    return sum;
  }

  public int get(int index) {
    if (index < 0 || index >= size())
      throw new IllegalArgumentException("Size is: " + size() + ", but index is: " + index);

    int startIndex = myStartIndexMap.getLongIndex(index);
    int sizeOf = getNextStartIndex(startIndex) - startIndex;
    return IntDeltaUtils.readDelta(startIndex, sizeOf, myCompressedDeltas);
  }

  public int size() {
    return myStartIndexMap.shortSize();
  }

  private int getNextStartIndex(int currentIndex) {
    for (int i = currentIndex + 1; i < myStartedDeltaIndex.size(); i++) {
      if (myStartedDeltaIndex.get(i))
        return i;
    }
    return myStartedDeltaIndex.size();
  }

  private static class IntDeltaUtils {
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
      for (int  i = 1; i < 4; i++) {
        if (value == 0)
          return i;
        value >>= BYTE_OFFSET;
      }
      return 4;
    }

    public static void writeDelta(int startIndex, int value, int sizeOf, byte[] bytes) {
      for (int i = sizeOf - 1; i >= 0; i--) {
        bytes[startIndex + i] = (byte) value;
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
  }

}
