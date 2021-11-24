// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.minperf;

import org.minperf.universal.UniversalHash;

/**
 * Evaluator for the hybrid mechanism.
 *
 * @param <T> the data type
 */
@SuppressWarnings({"BoundedWildcard", "DuplicatedCode"})
public final class RecSplitEvaluator<T> {
  private final Settings settings;
  private final UniversalHash<T> hash;
  private final BitBuffer buffer;
  private final int bucketCount;
  private final int minStartDiff;
  private final MonotoneList startList;
  private final int minOffsetDiff;
  private final MonotoneList offsetList;
  private final int startBuckets;
  private final int endHeader;
  private final int endOffsetList;
  private final BDZ<T> alternative;

  public RecSplitEvaluator(BitBuffer buffer, UniversalHash<T> hash, Settings settings) {
    this.settings = settings;
    this.hash = hash;
    this.buffer = buffer;
    long size = (int)(buffer.readEliasDelta() - 1);
    this.bucketCount = Settings.getBucketCount(size, settings.getAverageBucketSize());
    boolean alternative = buffer.readBit() != 0;
    this.minOffsetDiff = (int)(buffer.readEliasDelta() - 1);
    this.minStartDiff = (int)(buffer.readEliasDelta() - 1);
    this.endHeader = buffer.position();
    this.offsetList = MultiStageMonotoneList.load(buffer);
    this.endOffsetList = buffer.position();
    this.startList = MultiStageMonotoneList.load(buffer);
    this.startBuckets = buffer.position();
    if (alternative) {
      int b = bucketCount;
      int offset = offsetList.get(b);
      int pos = startBuckets +
                Generator.getMinBitCount(offset) +
                startList.get(b) + b * minStartDiff;
      buffer.seek(pos);
      this.alternative = BDZ.load(hash, buffer);
    }
    else {
      this.alternative = null;
    }
  }

  public int getHeaderSize() {
    return endHeader;
  }

  public int getOffsetListSize() {
    return endOffsetList - endHeader;
  }

  public int getStartListSize() {
    return startBuckets - endOffsetList;
  }

  public int evaluate(T obj) {
    int b;
    long hashCode = hash.universalHash(obj, 0);
    //System.out.println("hashCode " + obj + " =" + hashCode);
    if (bucketCount == 1) {
      b = 0;
    }
    else {
      b = Settings.reduce((int)hashCode, bucketCount);
    }
    //System.out.println("bucket " + b);
    int startPos;
    long offsetPair = offsetList.getPair(b);
    int offset = (int)(offsetPair >>> 32) + b * minOffsetDiff;
    int offsetNext = ((int)offsetPair) + (b + 1) * minOffsetDiff;
    if (offsetNext == offset) {
      if (alternative == null) {
        // entry not found
        return 0;
      }
      offset = offsetList.get(bucketCount) + bucketCount * minOffsetDiff;
      return offset + alternative.evaluate(obj);
    }
    int bucketSize = offsetNext - offset;
    startPos = startBuckets +
               Generator.getMinBitCount(offset) +
               startList.get(b) + b * minStartDiff;
    //System.out.println("startPos " + startPos + " offset " + offset + " bucketSize " + bucketSize);
    return evaluate(startPos, obj, hashCode, offset, bucketSize);
  }

  private int skip(int pos, int size) {
    if (size < 2) {
      return pos;
    }
    pos = buffer.skipGolombRice(pos, settings.getGolombRiceShift(size));
    if (size <= settings.getLeafSize()) {
      return pos;
    }
    @SuppressWarnings("RedundantExplicitVariableType")
    int split = settings.getSplit(size);
    int firstPart, otherPart;
    if (split < 0) {
      firstPart = -split;
      otherPart = size - firstPart;
      split = 2;
    }
    else {
      firstPart = size / split;
      otherPart = firstPart;
    }
    int s = firstPart;
    for (int i = 0; i < split; i++) {
      pos = skip(pos, s);
      s = otherPart;
    }
    return pos;
  }

  private int evaluate(int pos, T obj, long hashCode, int add, int size) {
    long index = 0;
    while (true) {
      if (size < 2) {
        return add;
      }
      int shift = settings.getGolombRiceShift(size);
      long q = buffer.readUntilZero(pos);
      pos += q + 1;
      long value = (q << shift) | buffer.readNumber(pos, shift);
      pos += shift;
      long oldX = Settings.getUniversalHashIndex(index);
      index += value + 1;
      long x = Settings.getUniversalHashIndex(index);
      if (x != oldX) {
        hashCode = hash.universalHash(obj, x);
      }
      if (size <= settings.getLeafSize()) {
        int h = Settings.supplementalHash(hashCode, index);
        h = Settings.reduce(h, size);
        //System.out.printf("shift %d q %lld value %d oldX %d x %d size %d h %d add %d\n", shift, q, value, oldX, x, size, h, add);
        return add + h;
      }
      int split = settings.getSplit(size);
      int firstPart, otherPart;
      if (split < 0) {
        firstPart = -split;
        otherPart = size - firstPart;
        split = 2;
      }
      else {
        firstPart = size / split;
        otherPart = firstPart;
      }
      int h = Settings.supplementalHash(hashCode, index);
      if (firstPart != otherPart) {
        h = Settings.reduce(h, size);
        if (h < firstPart) {
          size = firstPart;
          continue;
        }
        pos = skip(pos, firstPart);
        add += firstPart;
        size = otherPart;
        continue;
      }
      h = Settings.reduce(h, split);
      for (int i = 0; i < h; i++) {
        pos = skip(pos, firstPart);
        add += firstPart;
      }
      size = firstPart;
    }
  }
}
