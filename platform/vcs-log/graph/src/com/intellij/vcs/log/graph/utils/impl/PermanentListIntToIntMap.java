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

import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.IntToIntMap;
import org.jetbrains.annotations.NotNull;

public class PermanentListIntToIntMap extends AbstractIntToIntMap implements IntToIntMap {
  public static final int DEFAULT_BLOCK_SIZE = 30;

  @NotNull
  public static IntToIntMap newInstance(@NotNull Flags visibleIndexes, int shortSize) {
    return newInstance(visibleIndexes, shortSize, DEFAULT_BLOCK_SIZE);
  }

  @NotNull
  public static IntToIntMap newInstance(@NotNull final Flags visibleIndexes, int shortSize, int blockSize) {
    if (shortSize < 0) throw new NegativeArraySizeException("shortSize < 0: " + shortSize);
    if (shortSize == 0) return createEmptyIntToIntMap(visibleIndexes);

    int[] strongShortIndexes = new int[(shortSize - 1) / blockSize + 1];

    int currentShortIndex = -1;
    for (int longIndex = 0; longIndex < visibleIndexes.size(); longIndex++) {

      if (visibleIndexes.get(longIndex)) {
        currentShortIndex++;
        if (currentShortIndex % blockSize == 0) strongShortIndexes[currentShortIndex / blockSize] = longIndex;
      }
    }

    return new PermanentListIntToIntMap(visibleIndexes, shortSize, blockSize, strongShortIndexes);
  }

  @NotNull
  private static IntToIntMap createEmptyIntToIntMap(@NotNull final Flags visibleIndexes) {
    return new IntToIntMap() {
      @Override
      public int shortSize() {
        return 0;
      }

      @Override
      public int longSize() {
        return visibleIndexes.size();
      }

      @Override
      public int getLongIndex(int shortIndex) {
        return 0;
      }

      @Override
      public int getShortIndex(int longIndex) {
        return 0;
      }
    };
  }

  @NotNull private final Flags myVisibleIndexes;

  private final int myLongSize;
  private final int myShortSize;

  private final int myBlockSize;
  @NotNull private final int[] myStrongShortIndexes;

  private PermanentListIntToIntMap(@NotNull Flags visibleIndexes, int shortSize, int blockSize, @NotNull int[] strongShortIndexes) {
    myVisibleIndexes = visibleIndexes;
    myLongSize = visibleIndexes.size();
    myShortSize = shortSize;
    myBlockSize = blockSize;
    myStrongShortIndexes = strongShortIndexes;
  }


  @Override
  public int shortSize() {
    return myShortSize;
  }

  @Override
  public int longSize() {
    return myLongSize;
  }

  @Override
  public int getLongIndex(int shortIndex) {
    checkShortIndex(shortIndex);

    int strongIndex = shortIndex / myBlockSize;

    int sub = shortIndex - strongIndex * myBlockSize;
    for (int longIndex = myStrongShortIndexes[strongIndex]; longIndex < myLongSize; longIndex++) {
      if (myVisibleIndexes.get(longIndex)) sub--;
      if (sub == -1) return longIndex;
    }
    throw new IllegalStateException(
      "Not found long index for short index: " + shortIndex + ". Long & short size is: " + myLongSize + ", " + myShortSize + ".");
  }

}
