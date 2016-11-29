/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class FastSetFactory<E> {

  private final VBStyleCollection<int[], E> colValuesInternal = new VBStyleCollection<>();

  private int lastBlock;

  private int lastMask;

  public FastSetFactory(Collection<E> set) {

    int block = -1;
    int mask = -1;
    int index = 0;

    for (E element : set) {

      block = index / 32;

      if (index % 32 == 0) {
        mask = 1;
      }
      else {
        mask <<= 1;
      }

      colValuesInternal.putWithKey(new int[]{block, mask}, element);

      index++;
    }

    lastBlock = block;
    lastMask = mask;
  }

  private int[] addElement(E element) {

    if (lastMask == -1 || lastMask == 0x80000000) {
      lastMask = 1;
      lastBlock++;
    }
    else {
      lastMask <<= 1;
    }

    int[] pointer = new int[]{lastBlock, lastMask};
    colValuesInternal.putWithKey(pointer, element);

    return pointer;
  }

  public FastSet<E> spawnEmptySet() {
    return new FastSet<>(this);
  }

  public int getLastBlock() {
    return lastBlock;
  }

  public int getLastMask() {
    return lastMask;
  }

  private VBStyleCollection<int[], E> getInternalValuesCollection() {
    return colValuesInternal;
  }


  public static class FastSet<E> implements Iterable<E> {

    private final FastSetFactory<E> factory;

    private final VBStyleCollection<int[], E> colValuesInternal;

    private int[] data;

    private FastSet(FastSetFactory<E> factory) {
      this.factory = factory;
      this.colValuesInternal = factory.getInternalValuesCollection();
      this.data = new int[factory.getLastBlock() + 1];
    }

    public FastSet<E> getCopy() {

      FastSet<E> copy = new FastSet<>(factory);

      int arrlength = data.length;
      int[] cpdata = new int[arrlength];

      System.arraycopy(data, 0, cpdata, 0, arrlength);
      copy.setData(cpdata);

      return copy;
    }

    private int[] ensureCapacity(int index) {

      int newlength = data.length;
      if (newlength == 0) {
        newlength = 1;
      }

      while (newlength <= index) {
        newlength *= 2;
      }

      int[] newdata = new int[newlength];
      System.arraycopy(data, 0, newdata, 0, data.length);

      return data = newdata;
    }

    public void add(E element) {
      int[] index = colValuesInternal.getWithKey(element);

      if (index == null) {
        index = factory.addElement(element);
      }

      if (index[0] >= data.length) {
        ensureCapacity(index[0]);
      }

      data[index[0]] |= index[1];
    }

    public void setAllElements() {

      int lastblock = factory.getLastBlock();
      int lastmask = factory.getLastMask();

      if (lastblock >= data.length) {
        ensureCapacity(lastblock);
      }

      for (int i = lastblock - 1; i >= 0; i--) {
        data[i] = 0xFFFFFFFF;
      }

      data[lastblock] = lastmask | (lastmask - 1);
    }

    public void addAll(Set<E> set) {
      for (E element : set) {
        add(element);
      }
    }

    public void remove(E element) {
      int[] index = colValuesInternal.getWithKey(element);

      if (index == null) {
        index = factory.addElement(element);
      }

      if (index[0] < data.length) {
        data[index[0]] &= ~index[1];
      }
    }

    public void removeAll(Set<E> set) {
      for (E element : set) {
        remove(element);
      }
    }

    public boolean contains(E element) {
      int[] index = colValuesInternal.getWithKey(element);

      if (index == null) {
        index = factory.addElement(element);
      }

      return index[0] < data.length && ((data[index[0]] & index[1]) != 0);
    }

    public boolean contains(FastSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      int minlength = Math.min(extdata.length, intdata.length);

      for (int i = minlength - 1; i >= 0; i--) {
        if ((extdata[i] & ~intdata[i]) != 0) {
          return false;
        }
      }

      for (int i = extdata.length - 1; i >= minlength; i--) {
        if (extdata[i] != 0) {
          return false;
        }
      }

      return true;
    }

    public void union(FastSet<E> set) {

      int[] extdata = set.getData();
      int[] intdata = data;

      int minlength = Math.min(extdata.length, intdata.length);

      for (int i = minlength - 1; i >= 0; i--) {
        intdata[i] |= extdata[i];
      }

      boolean expanded = false;
      for (int i = extdata.length - 1; i >= minlength; i--) {
        if (extdata[i] != 0) {
          if (!expanded) {
            intdata = ensureCapacity(extdata.length - 1);
          }
          intdata[i] = extdata[i];
        }
      }
    }

    public void intersection(FastSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      int minlength = Math.min(extdata.length, intdata.length);

      for (int i = minlength - 1; i >= 0; i--) {
        intdata[i] &= extdata[i];
      }

      for (int i = intdata.length - 1; i >= minlength; i--) {
        intdata[i] = 0;
      }
    }

    public void symdiff(FastSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      int minlength = Math.min(extdata.length, intdata.length);

      for (int i = minlength - 1; i >= 0; i--) {
        intdata[i] ^= extdata[i];
      }

      boolean expanded = false;
      for (int i = extdata.length - 1; i >= minlength; i--) {
        if (extdata[i] != 0) {
          if (!expanded) {
            intdata = ensureCapacity(extdata.length - 1);
          }
          intdata[i] = extdata[i];
        }
      }
    }

    public void complement(FastSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      int minlength = Math.min(extdata.length, intdata.length);

      for (int i = minlength - 1; i >= 0; i--) {
        intdata[i] &= ~extdata[i];
      }
    }


    public boolean equals(Object o) {
      if (o == this) return true;
      if (o == null || !(o instanceof FastSet)) return false;

      int[] longdata = ((FastSet)o).getData();
      int[] shortdata = data;

      if (data.length > longdata.length) {
        shortdata = longdata;
        longdata = data;
      }

      for (int i = shortdata.length - 1; i >= 0; i--) {
        if (shortdata[i] != longdata[i]) {
          return false;
        }
      }

      for (int i = longdata.length - 1; i >= shortdata.length; i--) {
        if (longdata[i] != 0) {
          return false;
        }
      }

      return true;
    }

    public int getCardinality() {

      boolean found = false;
      int[] intdata = data;

      for (int i = intdata.length - 1; i >= 0; i--) {
        int block = intdata[i];
        if (block != 0) {
          if (found) {
            return 2;
          }
          else {
            if ((block & (block - 1)) == 0) {
              found = true;
            }
            else {
              return 2;
            }
          }
        }
      }

      return found ? 1 : 0;
    }

    public int size() {

      int size = 0;
      int[] intdata = data;

      for (int i = intdata.length - 1; i >= 0; i--) {
        size += Integer.bitCount(intdata[i]);
      }

      return size;
    }

    public boolean isEmpty() {
      int[] intdata = data;

      for (int i = intdata.length - 1; i >= 0; i--) {
        if (intdata[i] != 0) {
          return false;
        }
      }

      return true;
    }

    public Iterator<E> iterator() {
      return new FastSetIterator<>(this);
    }

    public Set<E> toPlainSet() {
      HashSet<E> set = new HashSet<>();

      int[] intdata = data;

      int size = data.length * 32;
      if (size > colValuesInternal.size()) {
        size = colValuesInternal.size();
      }

      for (int i = size - 1; i >= 0; i--) {
        int[] index = colValuesInternal.get(i);

        if ((intdata[index[0]] & index[1]) != 0) {
          set.add(colValuesInternal.getKey(i));
        }
      }

      return set;
    }

    public String toBinary() {

      StringBuilder buffer = new StringBuilder();
      int[] intdata = data;

      for (int i = 0; i < intdata.length; i++) {
        buffer.append(" ").append(Integer.toBinaryString(intdata[i]));
      }

      return buffer.toString();
    }

    private int[] getData() {
      return data;
    }

    private void setData(int[] data) {
      this.data = data;
    }

    public int[] getLoad() {
      int[] intdata = data;
      int notempty = 0;

      for (int i = 0; i < intdata.length; i++) {
        if (intdata[i] != 0) {
          notempty++;
        }
      }

      return new int[]{intdata.length, notempty};
    }

    public FastSetFactory<E> getFactory() {
      return factory;
    }
  }

  public static class FastSetIterator<E> implements Iterator<E> {

    private final VBStyleCollection<int[], E> colValuesInternal;
    private final int[] data;
    private int size;

    private int pointer = -1;
    private int next_pointer = -1;

    private FastSetIterator(FastSet<E> set) {
      colValuesInternal = set.getFactory().getInternalValuesCollection();
      data = set.getData();

      size = colValuesInternal.size();
      int datasize = data.length * 32;

      if (datasize < size) {
        size = datasize;
      }
    }

    public boolean hasNext() {

      next_pointer = pointer;

      while (++next_pointer < size) {
        int[] index = colValuesInternal.get(next_pointer);
        if ((data[index[0]] & index[1]) != 0) {
          return true;
        }
      }

      next_pointer = -1;
      return false;
    }

    public E next() {
      if (next_pointer >= 0) {
        pointer = next_pointer;
      }
      else {
        while (++pointer < size) {
          int[] index = colValuesInternal.get(pointer);
          if ((data[index[0]] & index[1]) != 0) {
            break;
          }
        }
      }

      next_pointer = -1;
      return pointer < size ? colValuesInternal.getKey(pointer) : null;
    }

    public void remove() {
      int[] index = colValuesInternal.get(pointer);
      data[index[0]] &= ~index[1];

      pointer--;
    }
  }
}

