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

public class FastSparseSetFactory<E> {

  private final VBStyleCollection<int[], E> colValuesInternal = new VBStyleCollection<>();

  private int lastBlock;

  private int lastMask;

  public FastSparseSetFactory(Collection<E> set) {

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

  public FastSparseSet<E> spawnEmptySet() {
    return new FastSparseSet<>(this);
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


  public static class FastSparseSet<E> implements Iterable<E> {
    public static final FastSparseSet[] EMPTY_ARRAY = new FastSparseSet[0];

    private final FastSparseSetFactory<E> factory;

    private final VBStyleCollection<int[], E> colValuesInternal;

    private int[] data;
    private int[] next;

    private FastSparseSet(FastSparseSetFactory<E> factory) {
      this.factory = factory;
      this.colValuesInternal = factory.getInternalValuesCollection();

      int length = factory.getLastBlock() + 1;
      this.data = new int[length];
      this.next = new int[length];
    }

    private FastSparseSet(FastSparseSetFactory<E> factory, int[] data, int[] next) {
      this.factory = factory;
      this.colValuesInternal = factory.getInternalValuesCollection();

      this.data = data;
      this.next = next;
    }

    public FastSparseSet<E> getCopy() {

      int arrlength = data.length;
      int[] cpdata = new int[arrlength];
      int[] cpnext = new int[arrlength];

      System.arraycopy(data, 0, cpdata, 0, arrlength);
      System.arraycopy(next, 0, cpnext, 0, arrlength);

      return new FastSparseSet<>(factory, cpdata, cpnext);
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
      data = newdata;

      int[] newnext = new int[newlength];
      System.arraycopy(next, 0, newnext, 0, next.length);
      next = newnext;

      return newdata;
    }

    public void add(E element) {
      int[] index = colValuesInternal.getWithKey(element);

      if (index == null) {
        index = factory.addElement(element);
      }

      int block = index[0];
      if (block >= data.length) {
        ensureCapacity(block);
      }

      data[block] |= index[1];

      changeNext(next, block, next[block], block);
    }

    public void setAllElements() {

      int lastblock = factory.getLastBlock();
      int lastmask = factory.getLastMask();

      if (lastblock >= data.length) {
        ensureCapacity(lastblock);
      }

      for (int i = lastblock - 1; i >= 0; i--) {
        data[i] = 0xFFFFFFFF;
        next[i] = i + 1;
      }

      data[lastblock] = lastmask | (lastmask - 1);
      next[lastblock] = 0;
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

      int block = index[0];
      if (block < data.length) {
        data[block] &= ~index[1];

        if (data[block] == 0) {
          changeNext(next, block, block, next[block]);
        }
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

    public boolean contains(FastSparseSet<E> set) {
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

    private void setNext() {

      int link = 0;
      for (int i = data.length - 1; i >= 0; i--) {
        next[i] = link;
        if (data[i] != 0) {
          link = i;
        }
      }
    }

    private static void changeNext(int[] arrnext, int key, int oldnext, int newnext) {
      for (int i = key - 1; i >= 0; i--) {
        if (arrnext[i] == oldnext) {
          arrnext[i] = newnext;
        }
        else {
          break;
        }
      }
    }

    public void union(FastSparseSet<E> set) {

      int[] extdata = set.getData();
      int[] extnext = set.getNext();
      int[] intdata = data;
      int intlength = intdata.length;

      int pointer = 0;
      do {
        if (pointer >= intlength) {
          intdata = ensureCapacity(extdata.length - 1);
        }

        boolean nextrec = (intdata[pointer] == 0);
        intdata[pointer] |= extdata[pointer];

        if (nextrec) {
          changeNext(next, pointer, next[pointer], pointer);
        }

        pointer = extnext[pointer];
      }
      while (pointer != 0);
    }

    public void intersection(FastSparseSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      int minlength = Math.min(extdata.length, intdata.length);

      for (int i = minlength - 1; i >= 0; i--) {
        intdata[i] &= extdata[i];
      }

      for (int i = intdata.length - 1; i >= minlength; i--) {
        intdata[i] = 0;
      }

      setNext();
    }

    public void symdiff(FastSparseSet<E> set) {
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

      setNext();
    }

    public void complement(FastSparseSet<E> set) {

      int[] extdata = set.getData();
      int[] intdata = data;
      int extlength = extdata.length;

      int pointer = 0;
      do {
        if (pointer >= extlength) {
          break;
        }

        intdata[pointer] &= ~extdata[pointer];
        if (intdata[pointer] == 0) {
          changeNext(next, pointer, pointer, next[pointer]);
        }

        pointer = next[pointer];
      }
      while (pointer != 0);
    }


    public boolean equals(Object o) {
      if (o == this) return true;
      if (o == null || !(o instanceof FastSparseSet)) return false;

      int[] longdata = ((FastSparseSet)o).getData();
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

    public boolean isEmpty() {
      return data.length == 0 || (next[0] == 0 && data[0] == 0);
    }

    public Iterator<E> iterator() {
      return new FastSparseSetIterator<>(this);
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

    public String toString() {
      return toPlainSet().toString();
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

    private int[] getNext() {
      return next;
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

    public FastSparseSetFactory<E> getFactory() {
      return factory;
    }
  }

  public static class FastSparseSetIterator<E> implements Iterator<E> {

    private final VBStyleCollection<int[], E> colValuesInternal;
    private final int[] data;
    private final int[] next;
    private final int size;

    private int pointer = -1;
    private int next_pointer = -1;

    private FastSparseSetIterator(FastSparseSet<E> set) {
      colValuesInternal = set.getFactory().getInternalValuesCollection();
      data = set.getData();
      next = set.getNext();
      size = colValuesInternal.size();
    }

    private int getNextIndex(int index) {

      index++;
      int bindex = index >>> 5;
      int dindex = index & 0x1F;

      while (bindex < data.length) {
        int block = data[bindex];

        if (block != 0) {
          block >>>= dindex;
          while (dindex < 32) {
            if ((block & 1) != 0) {
              return (bindex << 5) + dindex;
            }
            block >>>= 1;
            dindex++;
          }
        }

        dindex = 0;
        bindex = next[bindex];

        if (bindex == 0) {
          break;
        }
      }

      return -1;
    }

    public boolean hasNext() {
      next_pointer = getNextIndex(pointer);
      return (next_pointer >= 0);
    }

    public E next() {
      if (next_pointer >= 0) {
        pointer = next_pointer;
      }
      else {
        pointer = getNextIndex(pointer);
        if (pointer == -1) {
          pointer = size;
        }
      }

      next_pointer = -1;
      return pointer < size ? colValuesInternal.getKey(pointer) : null;
    }

    public void remove() {
      int[] index = colValuesInternal.get(pointer);
      data[index[0]] &= ~index[1];
    }
  }
}

