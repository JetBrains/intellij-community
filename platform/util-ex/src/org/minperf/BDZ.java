// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.minperf;

import org.minperf.universal.UniversalHash;

import java.util.*;

/**
 * A simple implementation of the BDZ algorithm as documented in
 * "Simple and Space-Efficient Minimal Perfect Hash Functions" (F. C. Botelho,
 * R. Pagh, N. Ziviani).
 * <p>
 * This implementation around 3.66 bits/key, which is much more than really
 * needed, mainly because no compression is used.
 *
 * @param <T> the type
 */
final class BDZ<T> {
  // needs 3.66 bits/key
  private static final int HASHES = 3;
  private static final int FACTOR_TIMES_100 = 123;
  private static final int BITS_PER_ENTRY = 2;

  private final UniversalHash<T> hash;
  private final BitBuffer data;
  private final int hashIndex;
  private final int arrayLength;
  private final int size;
  private final int startPos;
  private final VerySimpleRank rank;

  private BDZ(UniversalHash<T> hash, BitBuffer data) {
    this.hash = hash;
    this.data = data;
    this.size = (int)data.readEliasDelta() - 1;
    this.arrayLength = getArrayLength(size);
    this.hashIndex = (int)data.readEliasDelta() - 1;
    this.rank = VerySimpleRank.load(data);
    this.startPos = data.position();
    data.seek(startPos + size * BITS_PER_ENTRY);
  }

  public int evaluate(T x) {
    int sum = 0;
    for (int hi = 0; hi < HASHES; hi++) {
      int h = getHash(x, hash, hashIndex, hi, arrayLength);
      if (rank.get(h)) {
        int pos = (int)rank.rank(h);
        sum += data.readNumber(startPos + (long)pos * BITS_PER_ENTRY, BITS_PER_ENTRY);
      }
    }
    int h = getHash(x, hash, hashIndex, sum % HASHES, arrayLength);
    return (int)rank.rank(h);
  }

  public static <T> BDZ<T> load(UniversalHash<T> hash, BitBuffer data) {
    return new BDZ<>(hash, data);
  }

  @SuppressWarnings("unchecked")
  public static <T> BitBuffer generate(UniversalHash<? super T> hash, Collection<T> set) {
    int size = set.size();
    int arrayLength = getArrayLength(size);
    BitBuffer data = new BitBuffer(100 + (long)arrayLength * (BITS_PER_ENTRY + 2));
    data.writeEliasDelta(size + 1);

    ArrayList<T> list = new ArrayList<>(set);
    ArrayList<T> order = new ArrayList<>();
    HashSet<T> done = new HashSet<>();
    T[] at;
    int hashIndex = 0;
    while (true) {
      order.clear();
      done.clear();
      at = (T[])new Object[arrayLength];
      ArrayList<HashSet<T>> list2 = new ArrayList<>();
      for (int i = 0; i < arrayLength; i++) {
        list2.add(new HashSet<>());
      }
      for (int i = 0; i < size; i++) {
        T x = list.get(i);
        for (int hi = 0; hi < HASHES; hi++) {
          int h = getHash(x, hash, hashIndex, hi, arrayLength);
          HashSet<T> l = list2.get(h);
          l.add(x);
        }
      }
      LinkedList<Integer> alone = new LinkedList<>();
      for (int i = 0; i < arrayLength; i++) {
        if (list2.get(i).size() == 1) {
          alone.add(i);
        }
      }
      while (!alone.isEmpty()) {
        int i = alone.removeFirst();
        HashSet<T> l = list2.get(i);
        if (l.isEmpty()) {
          continue;
        }
        T x = l.iterator().next();
        if (done.contains(x)) {
          continue;
        }
        order.add(x);
        done.add(x);
        boolean found = false;
        for (int hi = 0; hi < HASHES; hi++) {
          int h = getHash(x, hash, hashIndex, hi, arrayLength);
          l = list2.get(h);
          l.remove(x);
          if (l.isEmpty()) {
            if (!found) {
              at[h] = x;
              found = true;
            }
          }
          else if (l.size() == 1) {
            alone.add(h);
          }
        }
      }
      if (order.size() == size) {
        break;
      }
      hashIndex++;
    }
    data.writeEliasDelta(hashIndex + 1);

    BitSet visited = new BitSet();
    BitSet used = new BitSet();
    int[] g = new int[arrayLength];
    for (int i = order.size() - 1; i >= 0; i--) {
      T x = order.get(i);
      int sum = 0;
      int change = 0;
      int target = 0;
      for (int hi = 0; hi < HASHES; hi++) {
        int h = getHash(x, hash, hashIndex, hi, arrayLength);
        if (visited.get(h)) {
          sum += g[h];
        }
        else {
          visited.set(h);
          if (at[h] == x) {
            used.set(h);
            change = h;
            target = hi;
          }
        }
      }
      int result = (HASHES + target - (sum % HASHES)) % HASHES;
      g[change] = result;
    }
    VerySimpleRank.generate(used, data);
    for (int i = 0; i < arrayLength; i++) {
      if (used.get(i)) {
        data.writeNumber(g[i], BITS_PER_ENTRY);
      }
      else if (g[i] != 0) {
        throw new AssertionError();
      }
    }
    return data;
  }

  public int getSize() {
    return size;
  }

  private static int getArrayLength(int size) {
    return HASHES + FACTOR_TIMES_100 * size / 100;
  }

  private static <T> int getHash(T x, UniversalHash<T> hash, int hashIndex, int index, int arrayLength) {
    long r = hash.universalHash(x, hashIndex + index);
    r = Settings.reduce((int)r, arrayLength / HASHES);
    r += (long)index * arrayLength / HASHES;
    return (int)r;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " size " + size + " hashIndex " + hashIndex;
  }
}
