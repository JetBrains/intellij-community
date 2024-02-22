// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.ints.AbstractIntIterator;
import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

public final class TrigramBuilder {
  private TrigramBuilder() {
  }

  public abstract static class TrigramProcessor implements IntPredicate {
    public boolean consumeTrigramsCount(@SuppressWarnings("unused") int count) {
      return true;
    }
  }

  @SuppressWarnings("unused")
  public static boolean processTrigrams(@NotNull CharSequence text, @NotNull TrigramProcessor consumer) {
    IntSet trigrams = getTrigrams(text);
    if (!consumer.consumeTrigramsCount(trigrams.size())) {
      return false;
    }
    IntIterator iterator = trigrams.iterator();
    while (iterator.hasNext()) {
      int trigram = iterator.nextInt();
      if (!consumer.test(trigram)) {
        return false;
      }
    }
    return true;
  }

  public static @NotNull Map<Integer, Void> getTrigramsAsMap(@NotNull CharSequence text) {
    return new AbstractMap<Integer, Void>() {
      final IntSet trigrams = getTrigrams(text);

      @Override
      public int size() {
        return trigrams.size();
      }

      @Override
      public boolean containsKey(Object k) {
        //noinspection deprecation
        return trigrams.contains(k);
      }

      @Override
      public boolean containsValue(Object v) {
        return v == null && !isEmpty();
      }

      @Override
      public void forEach(BiConsumer<? super Integer, ? super Void> consumer) {
        trigrams.forEach(integer -> {
          consumer.accept(integer, null);
        });
      }

      @Override
      public IntSet keySet() {
        return trigrams;
      }

      @Override
      public Collection<Void> values() {
        return Collections.nCopies(trigrams.size(), null);
      }

      @NotNull
      @Override
      public Set<Entry<Integer, Void>> entrySet() {
        return new AbstractSet<Entry<Integer, Void>>() {
          @Override
          public Iterator<Entry<Integer, Void>> iterator() {
            IntIterator iterator = trigrams.iterator();
            return new Iterator<Entry<Integer, Void>>() {
              @Override
              public boolean hasNext() {
                return iterator.hasNext();
              }

              @Override
              public Entry<Integer, Void> next() {
                int key = iterator.nextInt();
                return new AbstractMap.SimpleImmutableEntry<>(key, null);
              }
            };
          }

          @Override
          public int size() {
            return trigrams.size();
          }
        };
      }

      @Override
      public Void get(Object key) {
        return null;
      }
    };
  }

  /**
   * Produces <a href="https://en.wikipedia.org/wiki/Trigram">trigrams</a> from a given text.
   * <p>
   * Every single trigram is represented by single integer where char bytes are stored with 8 bit offset.
   */
  public static @NotNull IntSet getTrigrams(@NotNull CharSequence text) {
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

    return set;
  }

  private static final class AddonlyIntSet extends AbstractIntSet {
    private int size;
    private int[] data;
    private int mask;
    private boolean hasZeroKey;

    AddonlyIntSet() {
      this(21);
    }

    @Override
    public int[] toArray(int[] arr) {
      int size = this.size;
      if (arr == null) {
        arr = new int[size];
      }
      else if (arr.length < size) {
        arr = Arrays.copyOf(arr, size);
      }
      int idx = 0;
      if (hasZeroKey) {
        arr[idx++] = 0;
      }
      for (int val : data) {
        if (val != 0) {
          arr[idx++] = val;
        }
      }
      assert idx == size;
      return arr;
    }

    @Override
    public void forEach(IntConsumer action) {
      if (hasZeroKey) {
        action.accept(0);
      }
      for (int val : data) {
        if (val != 0) {
          action.accept(val);
        }
      }
    }

    @Override
    public IntIterator iterator() {
      return new AbstractIntIterator() {
        private int pos = -1;

        @Override
        public int nextInt() {
          if (pos == -1 && hasZeroKey) {
            pos = 0;
            return 0;
          }

          for (int i = Math.max(0, pos); i < data.length; i++) {
            if (data[i] != 0) {
              pos = i + 1;
              return data[i];
            }
          }

          throw new NoSuchElementException();
        }

        @Override
        public boolean hasNext() {
          if (pos == -1 && hasZeroKey) {
            return true;
          }

          for (int i = Math.max(0, pos); i < data.length; i++) {
            if (data[i] != 0) {
              pos = i;
              return true;
            }
          }

          return false;
        }
      };
    }

    AddonlyIntSet(int expectedSize) {
      int powerOfTwo = Integer.highestOneBit((3 * expectedSize) / 2) << 1;
      mask = powerOfTwo - 1;
      data = new int[powerOfTwo];
    }

    @Override
    public int size() {
      return size;
    }

    private int hash(int h) {
      h ^= (h >>> 20) ^ (h >>> 12);
      return (h ^ (h >>> 7) ^ (h >>> 4)) & mask;
    }

    @Override
    public boolean add(int key) {
      if (key == 0) {
        if (!hasZeroKey) {
          hasZeroKey = true;
          ++size;
          return true;
        }
        return false;
      }
      if (size >= (2 * data.length) / 3) rehash();
      boolean updated = doPut(data, key);
      if (updated) {
        size++;
      }
      return updated;
    }

    private boolean doPut(int[] a, int o) {
      int index = hash(o);
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
      int[] b = new int[data.length << 1];
      mask = b.length - 1;
      for (int i = data.length; --i >= 0;) {
        int ns = data[i];
        if (ns != 0) doPut(b, ns);
      }
      data = b;
    }

    @Override
    public boolean contains(int key) {
      if (key == 0) return hasZeroKey;
      int index = hash(key);
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

