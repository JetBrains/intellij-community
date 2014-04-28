/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.util.text;

import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntProcedure;

public class TrigramBuilder {
  private TrigramBuilder() {
  }

  public static boolean processTrigrams(CharSequence text, TrigramProcessor consumer) {
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

  public static abstract class TrigramProcessor implements TIntProcedure {
    public boolean consumeTrigramsCount(int count) { return true; }
  }
}

class AddonlyIntSet {
  //private static final int MAGIC = 0x9E3779B9;
  private int size;
  private int[] data;
  private int shift;
  private int mask;
  private boolean hasZeroKey;

  public AddonlyIntSet() {
    this(21);
  }

  public AddonlyIntSet(int expectedSize) {
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

  public boolean forEach(TIntProcedure consumer) {
    if (hasZeroKey && !consumer.execute(0)) return false;
    for(int o:data) {
      if (o == 0) continue;
      if(!consumer.execute(o)) return false;
    }
    return true;
  }
}

