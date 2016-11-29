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
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class IntTimestampGetter implements TimestampGetter {

  public static final int DEFAULT_BLOCK_SIZE = 30;

  private static final long MAX_DELTA = Integer.MAX_VALUE - 10;
  private static final int BROKEN_DELTA = Integer.MAX_VALUE;

  @NotNull
  public static IntTimestampGetter newInstance(@NotNull TimestampGetter delegateGetter) {
    return newInstance(delegateGetter, DEFAULT_BLOCK_SIZE);
  }

  @NotNull
  public static IntTimestampGetter newInstance(@NotNull TimestampGetter delegateGetter, int blockSize) {
    if (delegateGetter.size() < 0) throw new NegativeArraySizeException("delegateGetter.size() < 0: " + delegateGetter.size());
    if (delegateGetter.size() == 0) throw new IllegalArgumentException("Empty TimestampGetter not supported");

    long[] saveTimestamps = new long[(delegateGetter.size() - 1) / blockSize + 1];
    for (int i = 0; i < saveTimestamps.length; i++) {
      saveTimestamps[i] = delegateGetter.getTimestamp(blockSize * i);
    }

    Map<Integer, Long> brokenDeltas = new HashMap<>();
    int[] deltas = new int[delegateGetter.size()];

    for (int i = 0; i < delegateGetter.size(); i++) {
      int blockIndex = i - (i % blockSize);
      long delta = delegateGetter.getTimestamp(i) - delegateGetter.getTimestamp(blockIndex);
      int intDelta = deltaToInt(delta);
      deltas[i] = intDelta;
      if (intDelta == BROKEN_DELTA) brokenDeltas.put(i, delta);
    }

    return new IntTimestampGetter(deltas, blockSize, saveTimestamps, brokenDeltas);
  }

  private static int deltaToInt(long delta) {
    if (delta >= 0 && delta <= MAX_DELTA) return (int)delta;

    if (delta < 0 && -delta <= MAX_DELTA) return (int)delta;

    return BROKEN_DELTA;
  }

  // myDeltas[i] = getTimestamp(i + 1) - getTimestamp(i)
  private final IntList myDeltas;

  @NotNull private final Map<Integer, Long> myBrokenDeltas;

  private final int myBlockSize;

  // saved 0, blockSize, 2 * blockSize, etc.
  private final long[] mySaveTimestamps;

  public IntTimestampGetter(final int[] deltas, int blockSize, long[] saveTimestamps, @NotNull Map<Integer, Long> brokenDeltas) {
    myDeltas = SmartDeltaCompressor.newInstance(new FullIntList(deltas));
    myBlockSize = blockSize;
    mySaveTimestamps = saveTimestamps;
    myBrokenDeltas = brokenDeltas;
  }

  @Override
  public int size() {
    return myDeltas.size();
  }

  @Override
  public long getTimestamp(int index) {
    checkRange(index);
    int relativeSaveIndex = index / myBlockSize;
    long timestamp = mySaveTimestamps[relativeSaveIndex];
    return timestamp + getDelta(index);
  }

  private long getDelta(int index) {
    int delta = myDeltas.get(index);
    if (delta != BROKEN_DELTA) return delta;

    return myBrokenDeltas.get(index);
  }

  private void checkRange(int index) {
    if (index < 0) throw new IndexOutOfBoundsException("index < 0:" + index);
    if (index >= size()) throw new IndexOutOfBoundsException("index: " + index + " >= size: " + size());
  }
}
