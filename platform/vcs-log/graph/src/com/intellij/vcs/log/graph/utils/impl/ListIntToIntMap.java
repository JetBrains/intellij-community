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

package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.util.BooleanFunction;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.UpdatableIntToIntMap;
import org.jetbrains.annotations.NotNull;

public class ListIntToIntMap extends AbstractIntToIntMap implements UpdatableIntToIntMap {
  public static final int DEFAULT_BLOCK_SIZE = 30;

  @NotNull
  public static UpdatableIntToIntMap newInstance(@NotNull final BooleanFunction<Integer> thisIsVisible, final int longSize) {
    return newInstance(thisIsVisible, longSize, DEFAULT_BLOCK_SIZE);
  }

  @NotNull
  public static UpdatableIntToIntMap newInstance(final Flags visibleNodes) {
    return newInstance(new BooleanFunction<Integer>() {
      @Override
      public boolean fun(Integer integer) {
        return visibleNodes.get(integer);
      }
    }, visibleNodes.size());
  }

  /**
   * @param blockSize memory usage is: longSize / blockSize;
   *                  getLongIndex access need: log(longSize) + blockSize
   *                  getShortIndex access need: blockSize
   */
  @NotNull
  public static UpdatableIntToIntMap newInstance(@NotNull final BooleanFunction<Integer> thisIsVisible, final int longSize, int blockSize) {
    if (longSize < 0) throw new NegativeArraySizeException("size < 0: " + longSize);

    if (longSize == 0) return IDIntToIntMap.EMPTY;

    int sumSize = (longSize - 1) / blockSize + 1;
    ListIntToIntMap listIntToIntMap = new ListIntToIntMap(thisIsVisible, longSize, blockSize, new int[sumSize]);
    listIntToIntMap.update(0, longSize - 1);
    return listIntToIntMap;
  }

  @NotNull final BooleanFunction<Integer> myThisIsVisible;

  private final int myLongSize;

  private final int myBlockSize;
  private final int[] mySubSumOfBlocks;

  private ListIntToIntMap(@NotNull BooleanFunction<Integer> thisIsVisible, int longSize, int blockSize, int[] subSumOfBlocks) {
    myLongSize = longSize;
    myThisIsVisible = thisIsVisible;
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

    int a = 0;
    int b = mySubSumOfBlocks.length - 1;
    while (b > a) {
      int middle = (a + b) / 2;
      if (mySubSumOfBlocks[middle] <= shortIndex) {
        a = middle + 1;
      }
      else {
        b = middle;
      }
    }
    assert a == b;

    int blockIndex = a;
    int prefVisibleCount = 0;
    if (blockIndex > 0) prefVisibleCount = mySubSumOfBlocks[blockIndex - 1];

    for (int longIndex = blockIndex * myBlockSize; longIndex < myLongSize; longIndex++) {
      if (myThisIsVisible.fun(longIndex)) prefVisibleCount++;
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
      if (myThisIsVisible.fun(longIndex)) sum++;
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
