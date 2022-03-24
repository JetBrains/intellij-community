// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ikv;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Evaluator for the hybrid mechanism.
 *
 * @param <T> the data type
 */
@SuppressWarnings({"DuplicatedCode", "BoundedWildcard", "RedundantSuppression"})
public final class RecSplitEvaluator<T> {
  private final RecSplitSettings settings;
  private final UniversalHash<T> hash;
  // indexes for all buckets
  private final long[] indexes;
  private final int bucketCount;
  // start position of bucket's indexes in indexes
  private final int[] indexStarts;
  // entry index offset for bucket
  private final int[] entryOffsets;

  public RecSplitEvaluator(ByteBuffer buffer, UniversalHash<T> hash) {
    this(buffer, hash, RecSplitSettings.DEFAULT_SETTINGS);
  }

  public RecSplitEvaluator(ByteBuffer buffer, UniversalHash<T> hash, RecSplitSettings settings) {
    this.settings = settings;
    this.hash = hash;

    // make sure that we are able to update average size for generator without breaking a backward compatibility
    int averageBucketSize = buffer.getShort() & 0xffff;
    IntBuffer intBuffer = buffer.asIntBuffer();
    indexStarts = new int[intBuffer.get()];
    entryOffsets = new int[indexStarts.length + 1];
    int indexTotalCount = intBuffer.get();

    intBuffer.get(indexStarts);
    intBuffer.get(entryOffsets);

    buffer.position(buffer.position() + (intBuffer.position() * Integer.BYTES));
    indexes = new long[indexTotalCount];
    buffer.asLongBuffer().get(indexes);
    buffer.position(buffer.position() + (indexes.length * Long.BYTES));

    assert !buffer.hasRemaining();

    bucketCount = RecSplitSettings.getBucketCount(entryOffsets[entryOffsets.length - 1], averageBucketSize);
  }

  public int evaluate(T obj) {
    return evaluate(obj, hash.universalHash(obj, 0), hash);
  }

  public <K> int evaluate(K obj, long hashCode, UniversalHash<K> hash) {
    int bucketIndex = bucketCount == 1 ? 0 : RecSplitSettings.reduce(hashCode, bucketCount);
    int offset = entryOffsets[bucketIndex];
    int offsetNext = entryOffsets[bucketIndex + 1];
    int bucketSize = offsetNext - offset;
    int startPos = indexStarts[bucketIndex];
    return evaluate(startPos, obj, hashCode, offset, bucketSize, hash);
  }

  // skip indexes of a subtree
  private int skip(int indexPosition, int keyCount) {
    if (keyCount < 2) {
      return indexPosition;
    }
    else if (keyCount <= settings.getLeafSize()) {
      return indexPosition + 1;
    }

    // index for split
    indexPosition++;

    int split = settings.getSplit(keyCount);
    int firstPart;
    int otherPart;
    if (split < 0) {
      firstPart = -split;
      otherPart = keyCount - firstPart;
      split = 2;
    }
    else {
      firstPart = keyCount / split;
      otherPart = firstPart;
    }

    int s = firstPart;
    for (int i = 0; i < split; i++) {
      indexPosition = skip(indexPosition, s);
      s = otherPart;
    }
    return indexPosition;
  }

  private <K> int evaluate(int indexPosition, K obj, long hashCode, int add, int size, UniversalHash<K> hash) {
    long prevIndex = -1;
    while (true) {
      if (size < 2) {
        return add;
      }

      long currentIndex = indexes[indexPosition];
      if (prevIndex == -1) {
        prevIndex = currentIndex;
      }
      else {
        long oldX = RecSplitSettings.getUniversalHashIndex(prevIndex);
        prevIndex = currentIndex;
        long x = RecSplitSettings.getUniversalHashIndex(currentIndex);
        if (x != oldX) {
          hashCode = hash.universalHash(obj, x);
        }
      }

      if (size <= settings.getLeafSize()) {
        int h = RecSplitSettings.reduce(RecSplitSettings.supplementalHash(hashCode, prevIndex), size);
        return add + h;
      }

      int split = settings.getSplit(size);
      int firstPart;
      int otherPart;
      if (split < 0) {
        firstPart = -split;
        otherPart = size - firstPart;
        split = 2;
      }
      else {
        firstPart = size / split;
        otherPart = firstPart;
      }

      long sH = RecSplitSettings.supplementalHash(hashCode, prevIndex);
      if (firstPart != otherPart) {
        if (RecSplitSettings.reduce(sH, size) < firstPart) {
          // key located in the first part
          size = firstPart;
          // seek to our part
          indexPosition++;
        }
        else {
          // key located in the second part

          // skip subtree
          indexPosition = skip(indexPosition, firstPart);
          // seek to our part
          indexPosition++;

          add += firstPart;
          size = otherPart;
        }
      }
      else {
        int h = RecSplitSettings.reduce(sH, split);
        for (int i = 0; i < h; i++) {
          indexPosition = skip(indexPosition, firstPart);
          add += firstPart;
        }
        // seek to our part
        indexPosition++;
        size = firstPart;
      }
    }
  }
}
