// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.ints.AbstractIntIterator;
import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Helper class to generates a <a href="https://en.wikipedia.org/wiki/Trigram">trigrams</a> from a given text.
 * Notes on our implementation:
 * <ol>
 *   <li>
 *   <b>Packed trigram:</b> we represent trigram in a packed form: {@code int trigram = (ch[0]<<16) + (ch[1]<<8) + ch[2]}.
 *   <br/>
 *   For 1-byte characters this is a lossless representation, but for multibyte characters the representation is
 *   actually lossy: >1 different trigram could be packed into the same int32 value. So the representation is
 *   really not an actual trigram 'packed', but a kind of _hash_ of a trigram (ch[0], ch[1], ch[2]).
 *   <br/>
 *   This non-unique representation doesn't hurt search because we use trigram index only to produce a list of
 *   file _candidates_, and re-scan the candidates files to find ones that really contain a query string.
 *   </li>
 *   <li>
 *   <b>Words separators:</b> we use {@link Character#isJavaIdentifierPart(char)} as a way to differentiate between
 *   'word' and 'separator' symbols -- i.e. java-identifier symbols are 'word' symbols, all other symbols are
 *   separators.
 *   </li>
 *   <li>
 *   <b>Case-sensitivity:</b> we're trying to generate case-insensitive trigrams. For that we lower-case
 *   {@link Character#toLowerCase(char)} every symbol before utilising it in a trigram.
 *   <b>BEWARE</b>: This is <b>imperfect case-insensitivity</b> implementation: case conversion rules could be quite
 *   tricky outside basic ASCII.
 *   E.g. for some symbols ch.toUpper().toLower() != ch.toLower(), and for some symbols ch.toLower() could be a one
 *   char, and String(ch).toLower() could be a 2-chars string. Both cases are not covered very well with the currently
 *   used approach: a search via trigram-based index _could_ sometimes miss potential matches, if such matches are of
 *   a different case than a query string, and case-conversion is non-trivial.
 *   Since non-trivial case-conversions are all beyond ASCII, and infrequent in general -- we decided to not deal with
 *   it now.
 *   </li>
 * </ol>
 */
@ApiStatus.Internal
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
   * Every single trigram is represented by a single integer where char bytes are stored with 8-bit offsets.
   */
  public static @NotNull IntSet getTrigrams(@NotNull CharSequence text) {
    State state = new State(new AddonlyIntSet(1 + text.length() / 8));
    char[] array = CharArrayUtil.fromSequenceWithoutCopying(text);
    return array != null ? state.processArray(array) : state.processSequence(text);
  }

  private static class State {
    private int tc1, tc2, idChars;
    private final AddonlyIntSet set;

    private State(AddonlyIntSet set) {
      this.set = set;
    }

    void process(char c) {
      if (c < ASCII_END ? idPartAscii[c] : Character.isJavaIdentifierPart(c)) {
        c = StringUtilRt.toLowerCase(c);
        if (idChars == 0) {
          tc1 = tc2 = c;
          idChars = 1;
        }
        else {
          if (++idChars >= 3) {
            set.add((tc2 << 8) + c);
          }
          tc2 = (tc1 << 8) + c;
          tc1 = c;
        }
      }
      else {
        idChars = 0;
      }
    }

    AddonlyIntSet processSequence(CharSequence text) {
      int length = text.length();
      for (int i = 0; i < length; i++) {
        process(text.charAt(i));
      }
      return set;
    }

    AddonlyIntSet processArray(char[] text) {
      for (char c : text) {
        process(c);
      }
      return set;
    }
  }

  private static final int ASCII_END = 128;
  private static final boolean[] idPartAscii = new boolean[ASCII_END];

  static {
    for (char c = 0; c < idPartAscii.length; c++) {
      idPartAscii[c] = Character.isJavaIdentifierPart(c);
    }
  }

  @VisibleForTesting
  public static final class AddonlyIntSet extends AbstractIntSet {

    private int size;
    private int[] data;
    private int mask;
    private boolean hasZeroKey;

    public AddonlyIntSet() {
      this(21);
    }

    AddonlyIntSet(int expectedSize) {
      int powerOfTwo = Integer.highestOneBit((3 * expectedSize) / 2) << 1;
      mask = powerOfTwo - 1;
      data = new int[powerOfTwo];
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
    public @NotNull IntIterator iterator() {
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
      for (int i = data.length; --i >= 0; ) {
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

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("AddonlyIntSet[");
      int[] items = toIntArray();
      Arrays.sort(items);
      for (int item : items) {
        sb.append(item).append(", ");
      }
      sb.append(']');
      return sb.toString();
    }
  }
}

