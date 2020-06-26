// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.util.BooleanFunction;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.UpdatableIntToIntMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class ListIntToIntMap extends AbstractIntToIntMap implements UpdatableIntToIntMap {
  public static final int DEFAULT_BLOCK_SIZE = 30;

  @NotNull
  public static UpdatableIntToIntMap newInstance(@NotNull BooleanFunction<Integer> thisIsVisible, int longSize) {
    return newInstance(new BooleanFunctionFlags(thisIsVisible, longSize));
  }

  @NotNull
  public static UpdatableIntToIntMap newInstance(@NotNull Flags visibleNodes) {
    return newInstance(visibleNodes, DEFAULT_BLOCK_SIZE);
  }

  /**
   * @param blockSize memory usage is: longSize / blockSize;
   *                  getLongIndex access need: log(longSize) + blockSize
   *                  getShortIndex access need: blockSize
   */
  @NotNull
  public static UpdatableIntToIntMap newInstance(@NotNull Flags visibility, int blockSize) {
    int longSize = visibility.size();
    if (longSize < 0) throw new NegativeArraySizeException("size < 0: " + longSize);

    if (longSize == 0) return IDIntToIntMap.EMPTY;

    int sumSize = (longSize - 1) / blockSize + 1;
    ListIntToIntMap listIntToIntMap = new ListIntToIntMap(visibility, longSize, blockSize, new int[sumSize]);
    listIntToIntMap.update(0, longSize - 1);
    return listIntToIntMap;
  }

  @NotNull final Flags myVisibility;

  private final int myLongSize;

  private final int myBlockSize;
  private final int[] mySubSumOfBlocks;

  private ListIntToIntMap(@NotNull Flags visibility, int longSize, int blockSize, int[] subSumOfBlocks) {
    myLongSize = longSize;
    myVisibility = visibility;
    myBlockSize = blockSize;
    mySubSumOfBlocks = subSumOfBlocks;
  }

  @Override
  public int shortSize() {
    return mySubSumOfBlocks[mySubSumOfBlocks.length - 1];
  }

  @Override
  public int longSize() {
    return myLongSize;
  }

  private int getRelevantSumIndex(int longIndex) {
    return longIndex / myBlockSize;
  }

  @Override
  public int getLongIndex(int shortIndex) {
    checkShortIndex(shortIndex);
    int i = Arrays.binarySearch(mySubSumOfBlocks, shortIndex);
    if (i < 0) {
      i = -i - 1;
    }

    int blockIndex = i;
    int prefVisibleCount = 0;
    if (blockIndex > 0) prefVisibleCount = mySubSumOfBlocks[blockIndex - 1];

    for (int longIndex = blockIndex * myBlockSize; longIndex < myLongSize; longIndex++) {
      if (myVisibility.get(longIndex)) prefVisibleCount++;
      if (prefVisibleCount > shortIndex) return longIndex;
    }

    throw new IllegalAccessError("This should never happen!");
  }

  @Override
  public int getShortIndex(int longIndex) {
    checkLongIndex(longIndex);

    int blockIndex = getRelevantSumIndex(longIndex);
    int countVisible = calculateSumForBlock(blockIndex, longIndex);
    if (countVisible > 0) {
      return countVisible - 1;
    }
    else {
      return 0;
    }
  }

  // for calculate sum used blocks with index less that blockIndex
  private int calculateSumForBlock(int blockIndex, int lastLongIndex) {
    int sum = 0;
    if (blockIndex > 0) sum = mySubSumOfBlocks[blockIndex - 1];

    for (int longIndex = blockIndex * myBlockSize; longIndex <= lastLongIndex; longIndex++) {
      if (myVisibility.get(longIndex)) sum++;
    }
    return sum;
  }

  private void updateSumWithCorrectPrevious(int blockIndex) {
    int endIndex = Math.min(myLongSize, (blockIndex + 1) * myBlockSize);

    mySubSumOfBlocks[blockIndex] = calculateSumForBlock(blockIndex, endIndex - 1);
  }

  @Override
  public void update(int startLongIndex, int endLongIndex) {
    checkUpdateParameters(startLongIndex, endLongIndex);
    int startSumIndex = getRelevantSumIndex(startLongIndex);
    int endSumIndex = getRelevantSumIndex(endLongIndex);
    int prevEndSum = mySubSumOfBlocks[endSumIndex];

    for (int blockIndex = startSumIndex; blockIndex <= endSumIndex; blockIndex++) {
      updateSumWithCorrectPrevious(blockIndex);
    }

    int sumDelta = mySubSumOfBlocks[endSumIndex] - prevEndSum;
    for (int blockIndex = endSumIndex + 1; blockIndex < mySubSumOfBlocks.length; blockIndex++) {
      mySubSumOfBlocks[blockIndex] += sumDelta;
    }
  }
}
