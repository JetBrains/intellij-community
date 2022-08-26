///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package gnu.trove;

import java.util.Objects;

/**
 * An open addressed hashing implementation for Object types.
 * <p>
 * Created: Sun Nov  4 08:56:06 2001
 *
 * @author Eric D. Friedman
 * @version $Id: TObjectHash.java,v 1.8 2004/09/24 09:11:15 cdr Exp $
 */
public abstract class TObjectHash<T> extends THash implements TObjectHashingStrategy<T> {
  /**
   * the set of Objects
   */
  protected transient Object[] _set;

  /**
   * the strategy used to hash objects in this collection.
   */
  protected final TObjectHashingStrategy<T> _hashingStrategy;

  public static final Object REMOVED = new Object();

  private static class NULL {
    NULL() { }
  }

  public static final NULL NULL = new NULL();

  /**
   * Creates a new <code>TObjectHash</code> instance with the
   * default capacity and load factor.
   */
  public TObjectHash() {
    _hashingStrategy = this;
  }

  /**
   * Creates a new <code>TObjectHash</code> instance with the
   * default capacity and load factor and a custom hashing strategy.
   *
   * @param strategy used to compute hash codes and to compare objects.
   */
  public TObjectHash(TObjectHashingStrategy<T> strategy) {
    _hashingStrategy = strategy;
  }

  /**
   * Creates a new <code>TObjectHash</code> instance whose capacity
   * is the next highest prime above <tt>initialCapacity + 1</tt>
   * unless that value is already prime.
   *
   * @param initialCapacity an <code>int</code> value
   */
  public TObjectHash(int initialCapacity) {
    super(initialCapacity);
    _hashingStrategy = this;
  }

  /**
   * Creates a new <code>TObjectHash</code> instance whose capacity
   * is the next highest prime above <tt>initialCapacity + 1</tt>
   * unless that value is already prime.  Uses the specified custom
   * hashing strategy.
   *
   * @param initialCapacity an <code>int</code> value
   * @param strategy        used to compute hash codes and to compare objects.
   */
  public TObjectHash(int initialCapacity, TObjectHashingStrategy<T> strategy) {
    super(initialCapacity);
    _hashingStrategy = strategy;
  }

  /**
   * Creates a new <code>TObjectHash</code> instance with a prime
   * value at or near the specified capacity and load factor.
   *
   * @param initialCapacity used to find a prime capacity for the table.
   * @param loadFactor      used to calculate the threshold over which
   *                        rehashing takes place.
   */
  public TObjectHash(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
    _hashingStrategy = this;
  }

  /**
   * Creates a new <code>TObjectHash</code> instance with a prime
   * value at or near the specified capacity and load factor.  Uses
   * the specified custom hashing strategy.
   *
   * @param initialCapacity used to find a prime capacity for the table.
   * @param loadFactor      used to calculate the threshold over which
   *                        rehashing takes place.
   * @param strategy        used to compute hash codes and to compare objects.
   */
  public TObjectHash(int initialCapacity, float loadFactor, TObjectHashingStrategy<T> strategy) {
    super(initialCapacity, loadFactor);
    _hashingStrategy = strategy;
  }

  /**
   * @return a shallow clone of this collection
   */
  @Override
  public TObjectHash<T> clone() {
    TObjectHash<T> h = (TObjectHash<T>)super.clone();
    h._set = _set == EMPTY_OBJECT_ARRAY ? EMPTY_OBJECT_ARRAY : _set.clone();
    return h;
  }

  @Override
  protected int capacity() {
    return _set.length;
  }

  @Override
  protected void removeAt(int index) {
    _set[index] = REMOVED;
    super.removeAt(index);
  }

  /**
   * initializes the Object set of this hash table.
   *
   * @param initialCapacity an <code>int</code> value
   * @return an <code>int</code> value
   */
  @Override
  protected int setUp(int initialCapacity) {
    int capacity = super.setUp(initialCapacity);
    _set = initialCapacity == JUST_CREATED_CAPACITY ? EMPTY_OBJECT_ARRAY : new Object[capacity];
    return capacity;
  }

  /**
   * Executes <tt>procedure</tt> for each element in the set.
   *
   * @param procedure a <code>TObjectProcedure</code> value
   * @return false if the loop over the set terminated because
   * the procedure returned false for some value.
   */
  public boolean forEach(TObjectProcedure<T> procedure) {
    Object[] set = _set;
    for (int i = set.length; i-- > 0; ) {
      if (set[i] != null
          && set[i] != REMOVED
          && !procedure.execute((T)set[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Searches the set for <tt>obj</tt>
   *
   * @param obj an <code>Object</code> value
   * @return a <code>boolean</code> value
   */
  public boolean contains(Object obj) {
    return index((T)obj) >= 0;
  }

  /**
   * Locates the index of <tt>obj</tt>.
   *
   * @param obj an <code>Object</code> value
   * @return the index of <tt>obj</tt> or -1 if it isn't in the set.
   */
  protected int index(T obj) {
    Object[] set = _set;
    if (set == EMPTY_OBJECT_ARRAY) return -1;
    int length = set.length;
    int hash = _hashingStrategy.computeHashCode(obj) & 0x7fffffff;
    int index = hash % length;
    Object cur = set[index];

    if (cur != null
        && (cur == REMOVED || !_hashingStrategy.equals((T)cur, obj))) {
      // see Knuth, p. 529
      int probe = 1 + hash % (length - 2);

      do {
        index -= probe;
        if (index < 0) {
          index += length;
        }
        cur = set[index];
      }
      while (cur != null
             && (cur == REMOVED || !_hashingStrategy.equals((T)cur, obj)));
    }

    return cur == null ? -1 : index;
  }

  /**
   * Locates the index at which <tt>obj</tt> can be inserted.  if
   * there is already a value equal()ing <tt>obj</tt> in the set,
   * returns that value's index as <tt>-index - 1</tt>.
   *
   * @param obj an <code>Object</code> value
   * @return the index of a FREE slot at which obj can be inserted
   * or, if obj is already stored in the hash, the negative value of
   * that index, minus 1: -index -1.
   */
  protected int insertionIndex(T obj) {
    if (_set == EMPTY_OBJECT_ARRAY) {
      setUp((int)(DEFAULT_INITIAL_CAPACITY / DEFAULT_LOAD_FACTOR + 1));
    }
    Object[] set = _set;
    int length = set.length;
    int hash = _hashingStrategy.computeHashCode(obj) & 0x7fffffff;
    int index = hash % length;
    Object cur = set[index];

    if (cur == null) {
      return index;       // empty, all done
    }
    if (cur != REMOVED && _hashingStrategy.equals((T)cur, obj)) {
      return -index - 1;   // already stored
    }

    // already FULL or REMOVED, must probe
    // compute the double hash
    int probe = 1 + hash % (length - 2);

    // keep track of the first removed cell. it's the natural candidate for re-insertion
    int firstRemoved = cur == REMOVED ? index : -1;

    // starting at the natural offset, probe until we find an
    // offset that isn't full.
    do {
      index -= probe;
      if (index < 0) {
        index += length;
      }
      cur = set[index];
      if (firstRemoved == -1 && cur == REMOVED) {
        firstRemoved = index;
      }
    }
    while (cur != null
           && cur != REMOVED
           && !_hashingStrategy.equals((T)cur, obj));

    // if the index we found was removed: continue probing until we
    // locate a free location or an element which equal()s the
    // one we have.
    if (cur == REMOVED) {
      while (cur != null
             && (cur == REMOVED || !_hashingStrategy.equals((T)cur, obj))) {
        index -= probe;
        if (index < 0) {
          index += length;
        }
        cur = set[index];
      }
    }

    // if it's full, the key is already stored
    if (cur != null) {
      return -index - 1;
    }

    return firstRemoved == -1 ? index : firstRemoved;
  }

  /**
   * This is the default implementation of TObjectHashingStrategy:
   * it delegates hashing to the Object's hashCode method.
   *
   * @param o for which the hashcode is to be computed
   * @return the hashCode
   * @see Object#hashCode()
   */
  @Override
  public final int computeHashCode(T o) {
    return o != null ? o.hashCode() : 0;
  }

  /**
   * This is the default implementation of TObjectHashingStrategy:
   * it delegates equality comparisons to the first parameter's
   * equals() method.
   *
   * @param o1 an <code>Object</code> value
   * @param o2 an <code>Object</code> value
   * @return true if the objects are equal
   * @see Object#equals(Object)
   */
  @Override
  public final boolean equals(T o1, T o2) {
    return Objects.equals(o1, o2);
  }

  /**
   * Convenience methods for subclasses to use in throwing exceptions about
   * badly behaved user objects employed as keys.  We have to throw an
   * IllegalArgumentException with a rather verbose message telling the
   * user that they need to fix their object implementation to conform
   * to the general contract for java.lang.Object.
   *
   * @param o1 the first of the equal elements with unequal hash codes.
   * @param o2 the second of the equal elements with unequal hash codes.
   * @throws IllegalArgumentException the whole point of this method.
   */
  protected final void throwObjectContractViolation(Object o1, Object o2)
    throws IllegalArgumentException {
    throw new IllegalArgumentException("Equal objects must have equal hashcodes. "
                                       + "During rehashing, Trove discovered that "
                                       + "the following two objects claim to be "
                                       + "equal (as in java.lang.Object.equals() or TObjectHashingStrategy.equals()) "
                                       + "but their hashCodes (or those calculated by "
                                       + "your TObjectHashingStrategy) are not equal."
                                       + "This violates the general contract of "
                                       + "java.lang.Object.hashCode().  See bullet point two "
                                       + "in that method's documentation. "
                                       + "object #1 =" + o1 + (o1 == null ? "" : " (" + o1.getClass() + ")")
                                       + ", hashCode=" + _hashingStrategy.computeHashCode((T)o1)
                                       + "; object #2 =" + o2 + (o2 == null ? "" : " (" + o2.getClass() + ")")
                                       + ", hashCode=" + _hashingStrategy.computeHashCode((T)o2)
    );
  }
} // TObjectHash
