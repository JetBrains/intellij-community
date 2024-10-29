// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class CompressedIntList implements IntList {
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
  public static IntList newInstance(@NotNull List<Integer> delegateList) {
    return newInstance(new IntList() {
      @Override
      public int size() {
        return delegateList.size();
      }

      @Override
      public int get(int index) {
        return delegateList.get(index);
      }
    }, DEFAULT_BLOCK_SIZE);
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

  private final int @NotNull [] myStrongValues;

  @NotNull private final IntList myCompressedDeltas;

  private CompressedIntList(int blockSize, int @NotNull [] strongValues, @NotNull final IntList compressedDeltas) {
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
