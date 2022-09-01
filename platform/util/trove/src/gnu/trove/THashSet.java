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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * An implementation of the <tt>Set</tt> interface that uses an
 * open-addressed hash table to store its contents.
 * <p>
 * Created: Sat Nov  3 10:38:17 2001
 *
 * @author Eric D. Friedman
 * @version $Id: THashSet.java,v 1.5 2004/09/24 09:11:15 cdr Exp $
 */
@Deprecated
public class THashSet<E> extends TObjectHash<E> implements Set<E> {

  /**
   * Creates a new <code>THashSet</code> instance with the default
   * capacity and load factor.
   */
  public THashSet() {
    super();
  }

  /**
   * Creates a new <code>THashSet</code> instance with the default
   * capacity and load factor.
   *
   * @param strategy used to compute hash codes and to compare objects.
   */
  public THashSet(TObjectHashingStrategy<E> strategy) {
    super(strategy);
  }

  /**
   * Creates a new <code>THashSet</code> instance with a prime
   * capacity equal to or greater than <tt>initialCapacity</tt> and
   * with the default load factor.
   *
   * @param initialCapacity an <code>int</code> value
   */
  public THashSet(int initialCapacity) {
    super(initialCapacity);
  }

  /**
   * Creates a new <code>THashSet</code> instance with a prime
   * capacity equal to or greater than <tt>initialCapacity</tt> and
   * with the default load factor.
   *
   * @param initialCapacity an <code>int</code> value
   * @param strategy        used to compute hash codes and to compare objects.
   */
  public THashSet(int initialCapacity, TObjectHashingStrategy<E> strategy) {
    super(initialCapacity, strategy);
  }

  /**
   * Creates a new <code>THashSet</code> instance with a prime
   * capacity equal to or greater than <tt>initialCapacity</tt> and
   * with the specified load factor.
   *
   * @param initialCapacity an <code>int</code> value
   * @param loadFactor      a <code>float</code> value
   */
  public THashSet(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  /**
   * Creates a new <code>THashSet</code> instance with a prime
   * capacity equal to or greater than <tt>initialCapacity</tt> and
   * with the specified load factor.
   *
   * @param initialCapacity an <code>int</code> value
   * @param loadFactor      a <code>float</code> value
   * @param strategy        used to compute hash codes and to compare objects.
   */
  public THashSet(int initialCapacity, float loadFactor, TObjectHashingStrategy<E> strategy) {
    super(initialCapacity, loadFactor, strategy);
  }

  /**
   * Creates a new <code>THashSet</code> instance containing the
   * elements of <tt>collection</tt>.
   *
   * @param collection a <code>Collection</code> value
   */
  public THashSet(Collection<? extends E> collection) {
    this(collection.size());
    addAll(collection);
  }

  /**
   * Creates a new <code>THashSet</code> instance containing the
   * elements of <tt>collection</tt>.
   *
   * @param collection a <code>Collection</code> value
   * @param strategy   used to compute hash codes and to compare objects.
   */
  public THashSet(Collection<? extends E> collection, TObjectHashingStrategy<E> strategy) {
    this(collection.size(), strategy);
    addAll(collection);
  }

  /**
   * Inserts a value into the set.
   *
   * @param obj an <code>Object</code> value
   * @return true if the set was modified by the add operation
   */
  @Override
  public boolean add(E obj) {
    if (obj == null) {
      throw new NullPointerException("Null elements are not allowed in THashSet");
    }
    int index = insertionIndex(obj);

    if (index < 0) {
      return false;       // already present in set, nothing to add
    }

    Object old = _set[index];
    _set[index] = obj;

    postInsertHook(old == null);
    return true;            // yes, we added something
  }

  public boolean equals(Object other) {
    if (!(other instanceof Set)) {
      return false;
    }
    Set that = (Set)other;
    if (that.size() != size()) {
      return false;
    }
    return containsAll(that);
  }

  public int hashCode() {
    HashProcedure p = new HashProcedure();
    forEach(p);
    return p.getHashCode();
  }

  private final class HashProcedure implements TObjectProcedure<E> {
    private int h;

    HashProcedure() {
    }

    public int getHashCode() {
      return h;
    }

    @Override
    public boolean execute(E key) {
      h += _hashingStrategy.computeHashCode(key);
      return true;
    }
  }

  /**
   * Expands the set to accomodate new values.
   *
   * @param newCapacity an <code>int</code> value
   */
  @Override
  protected void rehash(int newCapacity) {
    int oldCapacity = capacity();
    Object[] oldSet = _set;

    _set = new Object[newCapacity];

    for (int i = oldCapacity; i-- > 0; ) {
      if (oldSet[i] != null && oldSet[i] != REMOVED) {
        E o = (E)oldSet[i];
        int index = insertionIndex(o);
        if (index < 0) { // everyone pays for this because some people can't RTFM
          throwObjectContractViolation(_set[(-index - 1)], o);
        }
        _set[index] = o;
      }
    }
  }

  /**
   * Returns a new array containing the objects in the set.
   *
   * @return an <code>Object[]</code> value
   */
  @Override
  public Object[] toArray() {
    final Object[] result = new Object[size()];
    forEach(new TObjectProcedure() {
      private int pos;

      @Override
      public boolean execute(Object value) {
        result[pos++] = value;
        return true;
      }
    });
    return result;
  }

  /**
   * Returns a typed array of the objects in the set.
   *
   * @param a an <code>Object[]</code> value
   * @return an <code>Object[]</code> value
   */
  @Override
  public <T> T[] toArray(T[] a) {
    int size = size();
    if (a.length < size) {
      a = (T[])Array.newInstance(a.getClass().getComponentType(), size);
    }

    Object[] result = a;

    Iterator it = iterator();
    for (int i = 0; i < size; i++) {
      result[i] = it.next();
    }

    if (a.length > size) {
      a[size] = null;
    }

    return a;
  }

  /**
   * Empties the set.
   */
  @Override
  public void clear() {
    super.clear();
    Object[] set = _set;

    for (int i = set.length; i-- > 0; ) {
      set[i] = null;
    }
  }

  /**
   * Removes <tt>obj</tt> from the set.
   *
   * @param obj an <code>Object</code> value
   * @return true if the set was modified by the remove operation.
   */
  @Override
  public boolean remove(Object obj) {
    int index = index((E)obj);
    if (index >= 0) {
      removeAt(index);
      return true;
    }
    return false;
  }

  /**
   * Creates an iterator over the values of the set.  The iterator
   * supports element deletion.
   *
   * @return an <code>Iterator</code> value
   */
  @Override
  public Iterator<E> iterator() {
    return new TObjectHashIterator<>(this);
  }

  /**
   * Tests the set to determine if all of the elements in
   * <tt>collection</tt> are present.
   *
   * @param collection a <code>Collection</code> value
   * @return true if all elements were present in the set.
   */
  @Override
  public boolean containsAll(Collection<?> collection) {
    for (Object element : collection) {
      if (!contains(element)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Adds all of the elements in <tt>collection</tt> to the set.
   *
   * @param collection a <code>Collection</code> value
   * @return true if the set was modified by the add all operation.
   */
  @Override
  public boolean addAll(Collection<? extends E> collection) {
    int size = collection.size();

    ensureCapacity(size);
    Iterator<? extends E> it = collection.iterator();
    boolean changed = false;
    while (size-- > 0) {
      if (add(it.next())) {
        changed = true;
      }
    }
    return changed;
  }

  /**
   * Removes all of the elements in <tt>collection</tt> from the set.
   *
   * @param collection a <code>Collection</code> value
   * @return true if the set was modified by the remove all operation.
   */
  @Override
  public boolean removeAll(Collection<?> collection) {
    int size = collection.size();

    Iterator it = collection.iterator();
    boolean changed = false;
    while (size-- > 0) {
      if (remove(it.next())) {
        changed = true;
      }
    }
    return changed;
  }

  /**
   * Removes any values in the set which are not contained in
   * <tt>collection</tt>.
   *
   * @param collection a <code>Collection</code> value
   * @return true if the set was modified by the retain all operation
   */
  @Override
  public boolean retainAll(Collection<?> collection) {
    int size = size();

    Iterator it = iterator();
    boolean changed = false;
    while (size-- > 0) {
      if (!collection.contains(it.next())) {
        it.remove();
        changed = true;
      }
    }
    return changed;
  }

  private void writeObject(ObjectOutputStream stream)
    throws IOException {
    stream.defaultWriteObject();

    // number of entries
    stream.writeInt(_size);

    SerializationProcedure writeProcedure = new SerializationProcedure(stream);
    if (!forEach(writeProcedure)) {
      throw writeProcedure.exception;
    }
  }

  private void readObject(ObjectInputStream stream)
    throws IOException, ClassNotFoundException {
    stream.defaultReadObject();

    int size = stream.readInt();
    setUp(size);
    while (size-- > 0) {
      E val = (E)stream.readObject();
      add(val);
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    forEach(new TObjectProcedure<E>() {
      @Override
      public boolean execute(E value) {
        if (sb.length() != 0) {
          sb.append(',').append(' ');
        }
        sb.append(value == this ? "(this set)" : value);
        return true;
      }
    });
    sb.append(']');
    sb.insert(0, '[');
    return sb.toString();
  }
} // THashSet
