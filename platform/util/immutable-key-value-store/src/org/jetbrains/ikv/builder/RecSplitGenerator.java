// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ikv.builder;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.ikv.RecSplitSettings;
import org.jetbrains.ikv.UniversalHash;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.function.IntFunction;

/**
 * Generator of a hybrid MPHF. It is guaranteed to use linear space, because
 * large buckets are encoded using an alternative algorithm.
 *
 * @param <T> the type
 */
@SuppressWarnings("DuplicatedCode")
public final class RecSplitGenerator<T> {
  public static final int MAX_FILL = 8;

  final UniversalHash<T> hash;
  private final RecSplitSettings settings;

  public RecSplitGenerator(@SuppressWarnings("BoundedWildcard") UniversalHash<T> hash, RecSplitSettings settings) {
    this.settings = settings;
    this.hash = hash;
  }

  private void generate(T[] keys, long[] hashes, long startIndex, LongArrayList result) {
    int size = keys.length;
    if (size < 2) {
      return;
    }

    if (size <= settings.getLeafSize()) {
      result.add(getIndex(keys, hashes, startIndex));
      return;
    }

    long index = startIndex + 1;
    while (true) {
      if (RecSplitSettings.needNewUniversalHashIndex(index)) {
        computeHashes(keys, index, size, hash, hashes);
      }
      if (trySplitEvenly(hashes, index)) {
        break;
      }
      index++;
    }

    result.add(index);
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

    T[][] data2;
    long[][] hashes2;
    if (firstPart != otherPart) {
      //noinspection unchecked
      data2 = (T[][])new Object[][]{new Object[firstPart], new Object[otherPart]};
      hashes2 = new long[][]{new long[firstPart], new long[otherPart]};
    }
    else {
      //noinspection unchecked
      data2 = (T[][])new Object[split][firstPart];
      hashes2 = new long[split][firstPart];
    }
    splitEvenly(keys, hashes, index, data2, hashes2);
    for (int i = 0; i < data2.length; i++) {
      generate(data2[i], hashes2[i], index, result);
    }
  }

  private static <T> void computeHashes(T[] keys, long index, int size, UniversalHash<T> hash, long[] hashes) {
    long universalHashIndex = RecSplitSettings.getUniversalHashIndex(index);
    for (int i = 0; i < size; i++) {
      hashes[i] = hash.universalHash(keys[i], universalHashIndex);
    }
  }

  private long getIndex(T[] data, long[] hashes, long startIndex) {
    int size = data.length;
    long index = startIndex + 1;
    outer:
    while (true) {
      if (RecSplitSettings.needNewUniversalHashIndex(index)) {
        computeHashes(data, index, size, hash, hashes);
        Arrays.sort(hashes);
        for (int i = 1; i < size; i++) {
          if (hashes[i - 1] == hashes[i]) {
            index++;
            while (!RecSplitSettings.needNewUniversalHashIndex(index)) {
              index++;
            }
            continue outer;
          }
        }
      }
      if (tryUnique(hashes, index)) {
        return index;
      }
      index++;
    }
  }

  private boolean trySplitEvenly(long[] hashes, long index) {
    int size = hashes.length;
    @SuppressWarnings("DuplicatedCode")
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
    if (firstPart != otherPart) {
      int limit = firstPart;
      for (long h : hashes) {
        if (RecSplitSettings.reduce(RecSplitSettings.supplementalHash(h, index), size) < limit) {
          firstPart--;
        }
      }
      return firstPart == 0;
    }

    int[] count = new int[split];
    Arrays.fill(count, firstPart);
    for (long h : hashes) {
      count[RecSplitSettings.reduce(RecSplitSettings.supplementalHash(h, index), split)]--;
    }
    for (int i = 0; i < split; i++) {
      if (count[i] != 0) {
        return false;
      }
    }
    return true;
  }

  private void splitEvenly(T[] data, long[] hashes, long index, T[][] data2, long[][] hashes2) {
    int size = data.length;
    int split = data2.length;
    int firstPartSize = data2[0].length;
    int otherPartSize = data2[1].length;
    if (firstPartSize != otherPartSize) {
      int i0 = 0, i1 = 0;
      for (int i = 0; i < size; i++) {
        T t = data[i];
        long h = hashes[i];
        int x = RecSplitSettings.reduce(RecSplitSettings.supplementalHash(h, index), size);
        if (x < firstPartSize) {
          data2[0][i0] = t;
          hashes2[0][i0] = h;
          i0++;
        }
        else {
          data2[1][i1] = t;
          hashes2[1][i1] = h;
          i1++;
        }
      }
      return;
    }
    int[] pos = new int[split];
    for (int i = 0; i < size; i++) {
      T t = data[i];
      long h = hashes[i];
      int bucket = RecSplitSettings.reduce(RecSplitSettings.supplementalHash(h, index), split);
      int p = pos[bucket]++;
      data2[bucket][p] = t;
      hashes2[bucket][p] = h;
    }
  }

  static boolean tryUnique(long[] hashes, long index) {
    int bits = 0;
    int size = hashes.length;
    int found = (1 << size) - 1;
    for (long x : hashes) {
      int h = RecSplitSettings.reduce(RecSplitSettings.supplementalHash(x, index), size);
      bits |= 1 << h;
    }
    return bits == found;
  }

  /**
   * Generate the hash function description for a collection.
   * The entries in the collection must be unique.
   *
   * @param collection the collection
   * @return the hash function description
   */
  public ByteBuffer generate(Collection<T> collection, IntFunction<? extends ByteBuffer> byteBufferAllocator) {
    int keyCount = collection.size();
    int averageBucketSize = settings.getAverageBucketSize();
    int bucketCount = RecSplitSettings.getBucketCount(keyCount, averageBucketSize);
    List<Bucket> buckets = new ArrayList<>(bucketCount);
    int initialCapacity = averageBucketSize * 11 / 10;
    for (int i = 0; i < bucketCount; i++) {
      buckets.add(new Bucket(initialCapacity));
    }
    for (T key : collection) {
      int bucketIndex = bucketCount == 1 ? 0 : RecSplitSettings.reduce(hash.universalHash(key, 0), bucketCount);
      buckets.get(bucketIndex).add(key);
    }
    processBuckets(keyCount, bucketCount, buckets);

    int[] startList = new int[buckets.size()];
    int[] offsetList = new int[buckets.size() + 1];
    int start = 0;
    int offset = 0;
    for (int i = 0; i < buckets.size(); i++) {
      Bucket b = buckets.get(i);

      int size = b.indexes.size();
      startList[i] = start;
      offsetList[i] = offset;

      start += size;
      offset += b.entryCount;
    }
    offsetList[offsetList.length - 1] = offset;

    ByteBuffer buffer = byteBufferAllocator.apply(Short.BYTES + 2 * Integer.BYTES + (startList.length * Integer.BYTES) + (offsetList.length * Integer.BYTES) +
                                                  start * Long.BYTES);
    buffer.putShort((short)(averageBucketSize & 0xffff));

    IntBuffer intBuffer = buffer.asIntBuffer();
    intBuffer.put(startList.length);
    intBuffer.put(start);

    intBuffer.put(startList);
    intBuffer.put(offsetList);
    buffer.position(buffer.position() + (intBuffer.position() * Integer.BYTES));

    LongBuffer longBuffer = buffer.asLongBuffer();
    for (Bucket bucket : buckets) {
      longBuffer.put(bucket.indexes.elements(), 0, bucket.indexes.size());
    }
    buffer.position(buffer.position() + (longBuffer.position() * Long.BYTES));
    buffer.flip();
    return buffer;
  }

  private void processBuckets(long size, int bucketCount, List<Bucket> buckets) {
    int averageBucketSize = (int)(size / bucketCount);
    int maxBucketSize = averageBucketSize * MAX_FILL;
    ForkJoinTask<?>[] list = new ForkJoinTask<?>[buckets.size()];
    for (int i = 0; i < buckets.size(); i++) {
      Bucket b = buckets.get(i);
      list[i] = ForkJoinTask.adapt(() -> b.generateBucket(hash, maxBucketSize));
    }
    ForkJoinTask.invokeAll(list);
  }

  @SuppressWarnings("BoundedWildcard")
  final class Bucket {
    private List<T> list;
    LongArrayList indexes;
    int entryCount;

    Bucket(int initialCapacity) {
      list = new ArrayList<>(initialCapacity);
    }

    @Override
    public String toString() {
      return String.valueOf(entryCount);
    }

    void add(T obj) {
      list.add(obj);
    }

    void generateBucket(UniversalHash<T> hash, int maxBucketSize) {
      int size = list.size();
      entryCount = size;
      if (size <= 1) {
        // zero or one entry
        indexes = new LongArrayList();
        return;
      }

      if (size > maxBucketSize) {
        throw new IllegalStateException("Hash has a poor quality, use another one");
      }

      @SuppressWarnings({"unchecked", "SSBasedInspection"})
      T[] data = (T[])list.toArray(new Object[0]);
      list = null;
      long[] hashes = new long[size];
      computeHashes(data, 0, size, hash, hashes);
      indexes = new LongArrayList(size);
      generate(data, hashes, 0, indexes);
    }
  }
}
