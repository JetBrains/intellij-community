// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntPredicate;

public final class TrigramBuilder {
  private TrigramBuilder() {
  }

  /**
   * Produces <a href="https://en.wikipedia.org/wiki/Trigram">trigrams</a> from a given text.
   *
   * Every single trigram is represented by single integer where char bytes are stored with 8 bit offset.
   */
  public static boolean processTrigrams(@NotNull CharSequence text, @NotNull TrigramProcessor consumer) {
    final AddonlyIntSet set = new AddonlyIntSet();
    int index = 0;
    final char[] fileTextArray = CharArrayUtil.fromSequenceWithoutCopying(text);

    ScanWordsLoop:
    while (true) {
      while (true) {
        if (index == text.length()) break ScanWordsLoop;
        final char c = fileTextArray != null ? fileTextArray[index]:text.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
            Character.isJavaIdentifierPart(c)) {
          break;
        }
        index++;
      }
      int identifierStart = index;
      while (true) {
        index++;
        if (index == text.length()) break;
        final char c = fileTextArray != null ? fileTextArray[index]:text.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
        if (!Character.isJavaIdentifierPart(c)) break;
      }

      int tc1 = 0;
      int tc2 = 0;
      int tc3;
      for (int i = identifierStart, iters = 0; i < index; ++i, ++iters) {
        char c = StringUtil.toLowerCase(fileTextArray != null ? fileTextArray[i]:text.charAt(i));
        tc3 = (tc2 << 8) + c;
        tc2 = (tc1 << 8) + c;
        tc1 = c;

        if (iters >= 2) {
          set.add(tc3);
        }
      }
    }

    return consumer.consumeTrigramsCount(set.size()) && set.forEach(consumer);
  }

  public static abstract class TrigramProcessor implements IntPredicate {
    public boolean consumeTrigramsCount(int count) {
      return true;
    }
  }

  private static class AddonlyIntSet {
    //private static final int MAGIC = 0x9E3779B9;
    private int size;
    private int[] data;
    private int shift;
    private int mask;
    private boolean hasZeroKey;

    AddonlyIntSet() {
      this(21);
    }

    AddonlyIntSet(int expectedSize) {
      int powerOfTwo = Integer.highestOneBit((3 * expectedSize) / 2) << 1;
      shift = Integer.numberOfLeadingZeros(powerOfTwo) + 1;
      mask = powerOfTwo - 1;
      data = new int[powerOfTwo];
    }

    public int size() {
      return size;
    }

    private int hash(int h, int[] a) {
      h ^= (h >>> 20) ^ (h >>> 12);
      return (h ^ (h >>> 7) ^ (h >>> 4)) & mask;
      //int idx = (id * MAGIC) >>> shift;
      //if (idx >= a.length) {
      //  idx %= a.length;
      //}
      //return idx;
    }

    public void add(int key) {
      if (key == 0) {
        if (!hasZeroKey) ++size;
        hasZeroKey = true;
        return;
      }
      if (size >= (2 * data.length) / 3) rehash();
      if (doPut(data, key)) size++;
    }

    private boolean doPut(int[] a, int o) {
      int index = hash(o, a);
      int obj;
      while ((obj = a[index]) != 0) {
        if (obj == o) break;
        if (index == 0) index = a.length;
        index--;
      }
      a[index] = o;
      return obj == 0;
    }

    private void rehash() {
      --shift;
      int[] b = new int[data.length << 1];
      mask = b.length - 1;
      for (int i = data.length; --i >= 0;) {
        int ns = data[i];
        if (ns != 0) doPut(b, ns);
      }
      data = b;
    }

    public boolean contains(int key) {
      if (key == 0) return hasZeroKey;
      int index = hash(key, data);
      int v;
      while ((v = data[index]) != 0) {
        if (v == key) return true;
        if (index == 0) index = data.length;
        index--;
      }
      return false;
    }

    public boolean forEach(@NotNull IntPredicate consumer) {
      if (hasZeroKey && !consumer.test(0)) {
        return false;
      }

      for (int o : data) {
        if (o == 0) {
          continue;
        }
        if (!consumer.test(o)) {
          return false;
        }
      }
      return true;
    }
  }

}

