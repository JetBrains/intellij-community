// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.IntList;
import com.intellij.vcs.log.graph.utils.IntToIntMap;
import org.jetbrains.annotations.NotNull;

/*package*/ final class IntDeltaCompressor implements IntList {

  @NotNull
  public static IntDeltaCompressor newInstance(@NotNull IntList deltaList) {
    if (deltaList.size() < 0) throw new NegativeArraySizeException("size < 0: " + deltaList.size());

    int bytesAfterCompression = ByteArrayUtils.countBytesAfterCompression(deltaList);
    Flags startedDeltaIndex = new BitSetFlags(bytesAfterCompression);
    byte[] compressedDeltas = new byte[bytesAfterCompression];

    int currentStartIndex = 0;
    for (int i = 0; i < deltaList.size(); i++) {
      startedDeltaIndex.set(currentStartIndex, true);

      int value = deltaList.get(i);
      int sizeOf = ByteArrayUtils.sizeOf(value);
      ByteArrayUtils.writeDelta(currentStartIndex, value, sizeOf, compressedDeltas);

      currentStartIndex += sizeOf;
    }

    return new IntDeltaCompressor(compressedDeltas, startedDeltaIndex, deltaList.size());
  }

  private final byte @NotNull [] myCompressedDeltas;
  @NotNull private final Flags myStartedDeltaIndex;

  @NotNull private final IntToIntMap myStartIndexMap;

  private IntDeltaCompressor(byte @NotNull [] compressedDeltas, @NotNull final Flags startedDeltaIndex, int countDeltas) {
    myCompressedDeltas = compressedDeltas;
    myStartedDeltaIndex = startedDeltaIndex;
    myStartIndexMap = PermanentListIntToIntMap.newInstance(startedDeltaIndex, countDeltas);
  }

  // [left, right)
  public int getSumOfInterval(int left, int right) {
    if (left < 0 || left > right || right > size()) {
      throw new IllegalArgumentException("Size is: " + size() + ", but interval is: (" + left + ", " + right + ")");
    }
    if (left == size()) return 0;

    int startIndex = myStartIndexMap.getLongIndex(left);
    int sum = 0;
    for (int i = 0; i < right - left; i++) {
      int sizeOf = getNextStartIndex(startIndex) - startIndex;
      sum += ByteArrayUtils.readDelta(startIndex, sizeOf, myCompressedDeltas);
      startIndex += sizeOf;
    }
    return sum;
  }

  @Override
  public int get(int index) {
    if (index < 0 || index >= size()) throw new IllegalArgumentException("Size is: " + size() + ", but index is: " + index);

    int startIndex = myStartIndexMap.getLongIndex(index);
    int sizeOf = getNextStartIndex(startIndex) - startIndex;
    return ByteArrayUtils.readDelta(startIndex, sizeOf, myCompressedDeltas);
  }

  @Override
  public int size() {
    return myStartIndexMap.shortSize();
  }

  private int getNextStartIndex(int currentIndex) {
    for (int i = currentIndex + 1; i < myStartedDeltaIndex.size(); i++) {
      if (myStartedDeltaIndex.get(i)) return i;
    }
    return myStartedDeltaIndex.size();
  }
}
