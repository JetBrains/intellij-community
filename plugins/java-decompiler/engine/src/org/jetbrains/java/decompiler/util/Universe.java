// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.util;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A collection that can create sets that work on the list of elements in the
 * universe. See https://en.wikipedia.org/wiki/Universe_(mathematics).
 *
 * @param <E> The element type.
 */
public class Universe<E> {
  private final List<E> universe;
  /**
   * If true, unknown elements will be added to the universe (shared by all
   * created sets from this universe), rather than being rejected.
   */
  private final boolean allowExpansion;

  public Universe(Collection<E> universe) {
    this(universe, false);
  }

  public Universe(Collection<E> universe, boolean allowExpansion) {
    this.universe = new ArrayList<>(universe);
    this.allowExpansion = allowExpansion;
  }

  public UniversedSet<E> spawnEmptySet() {
    return new UniversedSet<>(this);
  }

  int size() {
    return universe.size();
  }

  int indexOf(Object element) {
    int index = universe.indexOf(element);
    if (index == -1) {
      if (!allowExpansion) {
        throw new IllegalArgumentException(element + " is not in the universe!");
      } else {
        universe.add((E) element);
        return universe.size() - 1;
      }
    }
    return index;
  }

  E getAt(int index) {
    return universe.get(index);
  }

  public static class UniversedSet<E> extends AbstractSet<E> {
    private final Universe<E> universe;
    private final BitSet bits;

    private UniversedSet(Universe<E> universe) {
      this.universe = universe;
      this.bits = new BitSet(universe.size());
    }

    private UniversedSet(UniversedSet<E> other) {
      this(other.universe);
      bits.or(other.bits); // i.e. set this.bits to be other.bits, since this.bits is empty
    }

    public UniversedSet<E> getCopy() {
      return new UniversedSet<>(this);
    }

    public void setAllElements() {
      bits.set(0, universe.size(), true);
    }

    public boolean add(E element) {
      int index = universe.indexOf(element);

      boolean previousValue = bits.get(index);
      bits.set(index);
      return !previousValue;
    }

    @Override
    public boolean remove(Object o) {
      int index = universe.indexOf(o);

      boolean previousValue = bits.get(index);
      bits.clear(index);
      return previousValue;
    }

    @Override
    public boolean contains(Object o) {
      int index = universe.indexOf(o);

      return bits.get(index);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      if (!(c instanceof UniversedSet)) {
        return super.containsAll(c);
      }
      UniversedSet<?> other = (UniversedSet<?>)c;
      if (other.universe != this.universe) {
        // If the universes do not match, these cannot be compared quickly.
        return super.containsAll(c);
      }
      BitSet otherBits = (BitSet)other.bits.clone();
      otherBits.andNot(this.bits);
      return otherBits.isEmpty();
    }

    public void union(UniversedSet<E> other) {
      if (other.universe != this.universe) {
        super.addAll(other);
        return;
      }
      this.bits.or(other.bits);
    }

    public void intersection(UniversedSet<E> other) {
      if (other.universe != this.universe) {
        super.retainAll(other);
        return;
      }
      this.bits.and(other.bits);
    }

    public void complement(UniversedSet<E> other) {
      if (other.universe != this.universe) {
        super.removeAll(other);
        return;
      }
      this.bits.andNot(other.bits);
    }

    // Cannot be implemented performantly due to the return value; warn about it
    @Deprecated
    public boolean addAll(UniversedSet<? extends E> c) {
      return super.addAll(c);
    }
    @Deprecated
    public boolean retainAll(UniversedSet<?> c) {
      return super.retainAll(c);
    }
    @Deprecated
    public boolean removeAll(UniversedSet<?> c) {
      return super.removeAll(c);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof UniversedSet)) {
        return super.equals(o);
      }
      UniversedSet<?> other = (UniversedSet<?>)o;
      if (other.universe != this.universe) {
        // If the universes do not match, these cannot be compared quickly.
        // Maybe an exception instead?
        return super.equals(o);
      }
      return this.bits.equals(other.bits);
    }

    @Override
    public int size() {
      return bits.cardinality();
    }

    @Override
    public boolean isEmpty() {
      return bits.isEmpty();
    }

    @Override
    public Iterator<E> iterator() {
      return new UniversedSetIterator();
    }

    private class UniversedSetIterator implements Iterator<E> {
      int index = -1;

      @Override
      public boolean hasNext() {
        return (bits.nextSetBit(index + 1) != -1);
      }

      @Override
      public E next() {
        index = bits.nextSetBit(index + 1);
        if (index == -1) {
          throw new NoSuchElementException();
        }
        return universe.getAt(index);
      }

      @Override
      public void remove() {
        if (index == -1) {
          throw new IllegalStateException("Cannot call remove before next");
        } else if (!bits.get(index)) {
          throw new IllegalStateException("Cannot call remove multiple times");
        }
        bits.clear(index);
      }
    }
  }
}
