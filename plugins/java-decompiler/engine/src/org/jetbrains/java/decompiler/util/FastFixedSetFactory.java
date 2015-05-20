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

import java.util.*;

public class FastFixedSetFactory<E> {

  private final VBStyleCollection<int[], E> colValuesInternal = new VBStyleCollection<int[], E>();

  private final int dataLength;

  public FastFixedSetFactory(Collection<E> set) {

    dataLength = set.size() / 32 + 1;

    int index = 0;
    int mask = 1;

    for (E element : set) {

      int block = index / 32;

      if (index % 32 == 0) {
        mask = 1;
      }

      colValuesInternal.putWithKey(new int[]{block, mask}, element);

      index++;
      mask <<= 1;
    }
  }

  public FastFixedSet<E> spawnEmptySet() {
    return new FastFixedSet<E>(this);
  }

  private int getDataLength() {
    return dataLength;
  }

  private VBStyleCollection<int[], E> getInternalValuesCollection() {
    return colValuesInternal;
  }

  public static class FastFixedSet<E> implements Iterable<E> {

    private final FastFixedSetFactory<E> factory;

    private final VBStyleCollection<int[], E> colValuesInternal;

    private int[] data;


    private FastFixedSet(FastFixedSetFactory<E> factory) {
      this.factory = factory;
      this.colValuesInternal = factory.getInternalValuesCollection();
      this.data = new int[factory.getDataLength()];
    }

    public FastFixedSet<E> getCopy() {

      FastFixedSet<E> copy = new FastFixedSet<E>(factory);

      int arrlength = data.length;
      int[] cpdata = new int[arrlength];
      System.arraycopy(data, 0, cpdata, 0, arrlength);
      copy.setData(cpdata);

      return copy;
    }

    public void setAllElements() {

      int[] lastindex = colValuesInternal.get(colValuesInternal.size() - 1);

      for (int i = lastindex[0] - 1; i >= 0; i--) {
        data[i] = 0xFFFFFFFF;
      }

      data[lastindex[0]] = lastindex[1] | (lastindex[1] - 1);
    }

    public void add(E element) {
      int[] index = colValuesInternal.getWithKey(element);
      data[index[0]] |= index[1];
    }

    public void addAll(Collection<E> set) {
      for (E element : set) {
        add(element);
      }
    }

    public void remove(E element) {
      int[] index = colValuesInternal.getWithKey(element);
      data[index[0]] &= ~index[1];
    }

    public void removeAll(Collection<E> set) {
      for (E element : set) {
        remove(element);
      }
    }

    public boolean contains(E element) {
      int[] index = colValuesInternal.getWithKey(element);
      return (data[index[0]] & index[1]) != 0;
    }

    public boolean contains(FastFixedSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      for (int i = intdata.length - 1; i >= 0; i--) {
        if ((extdata[i] & ~intdata[i]) != 0) {
          return false;
        }
      }

      return true;
    }

    public void union(FastFixedSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      for (int i = intdata.length - 1; i >= 0; i--) {
        intdata[i] |= extdata[i];
      }
    }

    public void intersection(FastFixedSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      for (int i = intdata.length - 1; i >= 0; i--) {
        intdata[i] &= extdata[i];
      }
    }

    public void symdiff(FastFixedSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      for (int i = intdata.length - 1; i >= 0; i--) {
        intdata[i] ^= extdata[i];
      }
    }

    public void complement(FastFixedSet<E> set) {
      int[] extdata = set.getData();
      int[] intdata = data;

      for (int i = intdata.length - 1; i >= 0; i--) {
        intdata[i] &= ~extdata[i];
      }
    }


    public boolean equals(Object o) {
      if (o == this) return true;
      if (o == null || !(o instanceof FastFixedSet)) return false;

      int[] extdata = ((FastFixedSet)o).getData();
      int[] intdata = data;

      for (int i = intdata.length - 1; i >= 0; i--) {
        if (intdata[i] != extdata[i]) {
          return false;
        }
      }

      return true;
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
      return new FastFixedSetIterator<E>(this);
    }

    public Set<E> toPlainSet() {
      return toPlainCollection(new HashSet<E>());
    }

    public List<E> toPlainList() {
      return toPlainCollection(new ArrayList<E>());
    }


    private <T extends Collection<E>> T toPlainCollection(T cl) {

      int[] intdata = data;
      for (int bindex = 0; bindex < intdata.length; bindex++) {
        int block = intdata[bindex];
        if (block != 0) {
          int index = bindex << 5; // * 32
          for (int i = 31; i >= 0; i--) {
            if ((block & 1) != 0) {
              cl.add(colValuesInternal.getKey(index));
            }
            index++;
            block >>>= 1;
          }
        }
      }

      return cl;
    }

    public String toBinary() {

      StringBuilder buffer = new StringBuilder();
      int[] intdata = data;

      for (int i = 0; i < intdata.length; i++) {
        buffer.append(" ").append(Integer.toBinaryString(intdata[i]));
      }

      return buffer.toString();
    }

    public String toString() {

      StringBuilder buffer = new StringBuilder("{");

      int[] intdata = data;
      boolean first = true;

      for (int i = colValuesInternal.size() - 1; i >= 0; i--) {
        int[] index = colValuesInternal.get(i);

        if ((intdata[index[0]] & index[1]) != 0) {
          if (first) {
            first = false;
          }
          else {
            buffer.append(",");
          }
          buffer.append(colValuesInternal.getKey(i));
        }
      }

      buffer.append("}");

      return buffer.toString();
    }

    private int[] getData() {
      return data;
    }

    private void setData(int[] data) {
      this.data = data;
    }

    public FastFixedSetFactory<E> getFactory() {
      return factory;
    }
  }

  public static class FastFixedSetIterator<E> implements Iterator<E> {

    private final VBStyleCollection<int[], E> colValuesInternal;
    private final int[] data;
    private final int size;

    private int pointer = -1;
    private int next_pointer = -1;

    private FastFixedSetIterator(FastFixedSet<E> set) {
      colValuesInternal = set.getFactory().getInternalValuesCollection();
      data = set.getData();
      size = colValuesInternal.size();
    }

    private int getNextIndex(int index) {

      index++;
      int ret = index;
      int bindex = index / 32;
      int dindex = index % 32;

      while (bindex < data.length) {
        int block = data[bindex];

        if (block != 0) {
          block >>>= dindex;
          while (dindex < 32) {
            if ((block & 1) != 0) {
              return ret;
            }
            block >>>= 1;
            dindex++;
            ret++;
          }
        }
        else {
          ret += (32 - dindex);
        }

        dindex = 0;
        bindex++;
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

