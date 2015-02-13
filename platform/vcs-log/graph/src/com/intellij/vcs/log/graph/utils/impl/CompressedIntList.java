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

import com.intellij.vcs.log.graph.utils.IntList;
import org.jetbrains.annotations.NotNull;

public class CompressedIntList implements IntList {
  public static final int DEFAULT_BLOCK_SIZE = 30;

  @NotNull
  public static IntList newInstance(final int[] delegateArray) {
    return newInstance(delegateArray, DEFAULT_BLOCK_SIZE);
  }

  @NotNull
  public static IntList newInstance(final int[] delegateArray, int blockSize) {
    return newInstance(new IntList() {
      @Override
      public int size() {
        return delegateArray.length;
      }

      @Override
      public int get(int index) {
        return delegateArray[index];
      }
    }, blockSize);
  }

  @NotNull
  public static IntList newInstance(final IntList delegateList, final int blockSize) {
    if (blockSize < 1) throw new IllegalArgumentException("Unsupported blockSize:" + blockSize);

    if (delegateList.size() == 0) return new FullIntList(new int[0]);

    IntList intDeltaCompressor = SmartDeltaCompressor.newInstance(new IntList() {
      @Override
      public int size() {
        return delegateList.size();
      }

      @Override
      public int get(int index) {
        return delegateList.get(index) - delegateList.get(index - (index % blockSize));
      }
    });

    int[] strongValues = new int[(delegateList.size() - 1) / blockSize + 1];
    for (int i = 0; i < strongValues.length; i++) {
      strongValues[i] = delegateList.get(i * blockSize);
    }

    return new CompressedIntList(blockSize, strongValues, intDeltaCompressor);
  }

  private final int myBlockSize;

  @NotNull private final int[] myStrongValues;

  @NotNull private final IntList myCompressedDeltas;

  private CompressedIntList(int blockSize, @NotNull int[] strongValues, @NotNull final IntList compressedDeltas) {
    myBlockSize = blockSize;
    myStrongValues = strongValues;
    myCompressedDeltas = compressedDeltas;
  }

  @Override
  public int size() {
    return myCompressedDeltas.size();
  }

  @Override
  public int get(int index) {
    int strongIndex = index / myBlockSize;
    return myStrongValues[strongIndex] + myCompressedDeltas.get(index);
  }

}
