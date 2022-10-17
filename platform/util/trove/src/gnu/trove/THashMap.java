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
import java.util.*;

/**
 * An implementation of the Map interface which uses an open addressed
 * hash table to store its contents.
 * <p>
 * Created: Sun Nov  4 08:52:45 2001
 *
 * @author Eric D. Friedman
 */
@Deprecated
public class THashMap<K, V> extends TObjectHash<K> implements Map<K, V> {

  /**
   * the values of the  map
   */
  protected transient V[] _values;

  /**
   * Creates a new <code>THashMap</code> instance with the default
   * capacity and load factor.
   */
  public THashMap() {
    super();
  }

  /**
   * Creates a new <code>THashMap</code> instance with the default
   * capacity and load factor.
   *
   * @param strategy used to compute hash codes and to compare objects.
   */
  public THashMap(TObjectHashingStrategy<K> strategy) {
    super(strategy);
  }

  /**
   * Creates a new <code>THashMap</code> instance with a prime
   * capacity equal to or greater than <tt>initialCapacity</tt> and
   * with the default load factor.
   *
   * @param initialCapacity an <code>int</code> value
   */
  public THashMap(int initialCapacity) {
    super(initialCapacity);
  }

  /**
   * Creates a new <code>THashMap</code> instance with a prime
   * capacity equal to or greater than <tt>initialCapacity</tt> and
   * with the default load factor.
   *
   * @param initialCapacity an <code>int</code> value
   * @param strategy        used to compute hash codes and to compare objects.
   */
  public THashMap(int initialCapacity, TObjectHashingStrategy<K> strategy) {
    super(initialCapacity, strategy);
  }

  /**
   * Creates a new <code>THashMap</code> instance with a prime
   * capacity equal to or greater than <tt>initialCapacity</tt> and
   * with the specified load factor.
   *
   * @param initialCapacity an <code>int</code> value
   * @param loadFactor      a <code>float</code> value
   * @param strategy        used to compute hash codes and to compare objects.
   */
  public THashMap(int initialCapacity, float loadFactor, TObjectHashingStrategy<K> strategy) {
    super(initialCapacity, loadFactor, strategy);
  }

  /**
   * Creates a new <code>THashMap</code> instance which contains the
   * key/value pairs in <tt>map</tt>.
   *
   * @param map a <code>Map</code> value
   */
  public THashMap(Map<K, V> map) {
    super(map.size());
    putAll(map);
  }

  /**
   * Creates a new <code>THashMap</code> instance which contains the
   * key/value pairs in <tt>map</tt>.
   *
   * @param map      a <code>Map</code> value
   * @param strategy used to compute hash codes and to compare objects.
   */
  public THashMap(Map<K, V> map, TObjectHashingStrategy<K> strategy) {
    this(map.size(), strategy);
    putAll(map);
  }

  /**
   * @return a shallow clone of this collection
   */
  @Override
  public THashMap<K, V> clone() {
    THashMap<K, V> m = (THashMap<K, V>)super.clone();
    m._values = _values.clone();
    return m;
  }

  /**
   * initialize the value array of the map.
   *
   * @param initialCapacity an <code>int</code> value
   * @return an <code>int</code> value
   */
  @Override
  protected int setUp(int initialCapacity) {
    int capacity = super.setUp(initialCapacity);
    _values = (V[])(initialCapacity == JUST_CREATED_CAPACITY ? EMPTY_OBJECT_ARRAY : new Object[capacity]);
    return capacity;
  }

  /**
   * Inserts a key/value pair into the map.
   *
   * @param key   an <code>Object</code> value
   * @param value an <code>Object</code> value
   * @return the previous value associated with <tt>key</tt>,
   * or null if none was found.
   */
  @Override
  public V put(K key, V value) {
    if (null == key) {
      throw new NullPointerException("null keys not supported");
    }
    V previous = null;
    int index = insertionIndex(key);
    boolean alreadyStored = index < 0;
    if (alreadyStored) {
      index = -index - 1;
      previous = _values[index];
    }
    Object oldKey = _set[index];
    _set[index] = key;
    _values[index] = value;
    if (!alreadyStored) {
      postInsertHook(oldKey == null);
    }

    return previous;
  }

  /**
   * Compares this map with another map for equality of their stored
   * entries.
   *
   * @param other an <code>Object</code> value
   * @return a <code>boolean</code> value
   */
  public boolean equals(Object other) {
    if (!(other instanceof Map)) {
      return false;
    }
    Map<K, V> that = (Map<K, V>)other;
    if (that.size() != size()) {
      return false;
    }
    return forEachEntry(new EqProcedure<>(that));
  }

  public int hashCode() {
    HashProcedure p = new HashProcedure();
    forEachEntry(p);
    return p.getHashCode();
  }

  private final class HashProcedure implements TObjectObjectProcedure<K, V> {
    private int h;

    HashProcedure() {
    }

    public int getHashCode() {
      return h;
    }

    @Override
    public boolean execute(K key, V value) {
      h += _hashingStrategy.computeHashCode(key) ^ (value == null ? 0 : value.hashCode());
      return true;
    }
  }

  private static final class EqProcedure<K, V> implements TObjectObjectProcedure<K, V> {
    private final Map<K, V> _otherMap;

    EqProcedure(Map<K, V> otherMap) {
      _otherMap = otherMap;
    }

    @Override
    public boolean execute(K key, V value) {
      V oValue = _otherMap.get(key);
      return Objects.equals(oValue, value);
    }
  }

  /**
   * Executes <tt>procedure</tt> for each value in the map.
   *
   * @param procedure a <code>TObjectProcedure</code> value
   * @return false if the loop over the values terminated because
   * the procedure returned false for some value.
   */
  public boolean forEachValue(TObjectProcedure<V> procedure) {
    V[] values = _values;
    Object[] set = _set;
    for (int i = values.length; i-- > 0; ) {
      if (set[i] != null
          && set[i] != REMOVED
          && !procedure.execute(values[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Executes <tt>procedure</tt> for each key/value entry in the
   * map.
   *
   * @param procedure a <code>TObjectObjectProcedure</code> value
   * @return false if the loop over the entries terminated because
   * the procedure returned false for some entry.
   */
  public boolean forEachEntry(TObjectObjectProcedure<K, V> procedure) {
    Object[] keys = _set;
    V[] values = _values;
    for (int i = keys.length; i-- > 0; ) {
      if (keys[i] != null
          && keys[i] != REMOVED
          && !procedure.execute((K)keys[i], values[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Transform the values in this map using <tt>function</tt>.
   *
   * @param function a <code>TObjectFunction</code> value
   */
  public void transformValues(TObjectFunction<V, V> function) {
    V[] values = _values;
    Object[] set = _set;
    for (int i = values.length; i-- > 0; ) {
      if (set[i] != null && set[i] != REMOVED) {
        values[i] = function.execute(values[i]);
      }
    }
  }

  /**
   * rehashes the map to the new capacity.
   *
   * @param newCapacity an <code>int</code> value
   */
  @Override
  protected void rehash(int newCapacity) {
    int oldCapacity = _set.length;
    Object[] oldKeys = _set;
    V[] oldVals = _values;

    _set = new Object[newCapacity];
    _values = (V[])new Object[newCapacity];

    for (int i = oldCapacity; i-- > 0; ) {
      if (oldKeys[i] != null && oldKeys[i] != REMOVED) {
        Object o = oldKeys[i];
        int index = insertionIndex((K)o);
        if (index < 0) {
          throwObjectContractViolation(_set[-index - 1], o);
        }
        _set[index] = o;
        _values[index] = oldVals[i];
      }
    }
  }

  /**
   * retrieves the value for <tt>key</tt>
   *
   * @param key an <code>Object</code> value
   * @return the value of <tt>key</tt> or null if no such mapping exists.
   */
  @Override
  public V get(Object key) {
    int index = index((K)key);
    return index < 0 ? null : _values[index];
  }

  /**
   * Empties the map.
   */
  @Override
  public void clear() {
    // optimization
    if (size() != 0) {
      super.clear();
      Object[] keys = _set;
      V[] values = _values;

      for (int i = keys.length; i-- > 0; ) {
        keys[i] = null;
        values[i] = null;
      }
    }
  }

  /**
   * Deletes a key/value pair from the map.
   *
   * @param key an <code>Object</code> value
   * @return an <code>Object</code> value
   */
  @Override
  public V remove(Object key) {
    V prev = null;
    int index = index((K)key);
    if (index >= 0) {
      prev = _values[index];
      removeAt(index);    // clear key,state; adjust size
    }
    return prev;
  }

  /**
   * removes the mapping at <tt>index</tt> from the map.
   *
   * @param index an <code>int</code> value
   */
  @Override
  protected void removeAt(int index) {
    _values[index] = null;
    super.removeAt(index);  // clear key, state; adjust size
  }

  /**
   * Returns a view on the values of the map.
   *
   * @return a <code>Collection</code> value
   */
  @Override
  public Collection<V> values() {
    return new ValueView();
  }

  /**
   * returns a Set view on the keys of the map.
   *
   * @return a <code>Set</code> value
   */
  @Override
  public Set<K> keySet() {
    return new KeyView();
  }

  /**
   * Returns a Set view on the entries of the map.
   *
   * @return a <code>Set</code> value
   */
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return new EntryView();
  }

  /**
   * checks for the presence of <tt>val</tt> in the values of the map.
   *
   * @param val an <code>Object</code> value
   * @return a <code>boolean</code> value
   */
  @Override
  public boolean containsValue(Object val) {
    Object[] set = _set;
    V[] vals = _values;

    // special case null values so that we don't have to
    // perform null checks before every call to equals()
    if (null == val) {
      for (int i = vals.length; i-- > 0; ) {
        if (set[i] != null && set[i] != REMOVED &&
            val == vals[i]) {
          return true;
        }
      }
    }
    else {
      for (int i = vals.length; i-- > 0; ) {
        if (set[i] != null && set[i] != REMOVED &&
            (val == vals[i] || val.equals(vals[i]))) {
          return true;
        }
      }
    } // end of else
    return false;
  }

  /**
   * checks for the present of <tt>key</tt> in the keys of the map.
   *
   * @param key an <code>Object</code> value
   * @return a <code>boolean</code> value
   */
  @Override
  public boolean containsKey(Object key) {
    return contains(key);
  }

  /**
   * copies the key/value mappings in <tt>map</tt> into this map.
   *
   * @param map a <code>Map</code> value
   */
  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    ensureCapacity(map.size());
    // could optimize this for cases when map instanceof THashMap
    for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  /**
   * a view onto the values of the map.
   */
  protected class ValueView extends MapBackedView<V> {
    @Override
    public Iterator<V> iterator() {
      return new THashIterator<V>(THashMap.this) {
        @Override
        protected V objectAtIndex(int index) {
          return _values[index];
        }
      };
    }

    @Override
    public boolean containsElement(V value) {
      return containsValue(value);
    }

    @Override
    public boolean removeElement(V value) {
      boolean changed = false;
      Object[] values = _values;
      Object[] set = _set;

      for (int i = values.length; i-- > 0; ) {
        if (set[i] != null && set[i] != REMOVED &&
            value == values[i] ||
            (null != values[i] && values[i].equals(value))) {
          removeAt(i);
          changed = true;
        }
      }
      return changed;
    }
  }

  /**
   * a view onto the entries of the map.
   */
  protected class EntryView extends MapBackedView<Map.Entry<K, V>> {
    EntryView() {
    }

    private final class EntryIterator extends THashIterator<Map.Entry<K, V>> {
      EntryIterator(THashMap<K, V> map) {
        super(map);
      }

      @Override
      public Entry objectAtIndex(final int index) {
        return new Entry((K)_set[index], _values[index], index);
      }
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
      return new EntryIterator(THashMap.this);
    }

    @Override
    public boolean removeElement(Map.Entry<K, V> entry) {
      // have to effectively reimplement Map.remove here
      // because we need to return true/false depending on
      // whether the removal took place.  Since the Entry's
      // value can be null, this means that we can't rely
      // on the value of the object returned by Map.remove()
      // to determine whether a deletion actually happened.
      //
      // Note also that the deletion is only legal if
      // both the key and the value match.

      K key = keyForEntry(entry);
      int index = index(key);
      if (index >= 0) {
        Object val = valueForEntry(entry);
        if (Objects.equals(val, _values[index])) {
          removeAt(index);    // clear key,state; adjust size
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean containsElement(Map.Entry<K, V> entry) {
      Object val = get(keyForEntry(entry));
      Object entryValue = entry.getValue();
      return Objects.equals(val, entryValue);
    }

    protected V valueForEntry(Map.Entry<K, V> entry) {
      return entry.getValue();
    }

    protected K keyForEntry(Map.Entry<K, V> entry) {
      return entry.getKey();
    }
  }

  private abstract class MapBackedView<E> implements Set<E> {
    MapBackedView() {
    }

    @Override
    public abstract Iterator<E> iterator();

    public abstract boolean removeElement(E key);

    public abstract boolean containsElement(E key);

    @Override
    public boolean contains(Object key) {
      return containsElement((E)key);
    }

    @Override
    public boolean remove(Object o) {
      return removeElement((E)o);
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
      for (Object element : collection) {
        if (!contains(element)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
      boolean changed = false;
      for (Object element : collection) {
        if (remove(element)) {
          changed = true;
        }
      }
      return changed;
    }

    @Override
    public void clear() {
      THashMap.this.clear();
    }

    @Override
    public boolean add(E obj) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
      return THashMap.this.size();
    }

    @Override
    public Object[] toArray() {
      Object[] result = new Object[size()];
      Iterator e = iterator();
      for (int i = 0; e.hasNext(); i++) {
        result[i] = e.next();
      }
      return result;
    }

    @Override
    public <T> T[] toArray(T[] a) {
      int size = size();
      if (a.length < size) {
        a = (T[])Array.newInstance(a.getClass().getComponentType(), size);
      }

      Iterator<E> it = iterator();
      Object[] result = a;
      for (int i = 0; i < size; i++) {
        result[i] = it.next();
      }

      if (a.length > size) {
        a[size] = null;
      }

      return a;
    }

    @Override
    public boolean isEmpty() {
      return THashMap.this.isEmpty();
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
      boolean changed = false;
      Iterator i = iterator();
      while (i.hasNext()) {
        if (!collection.contains(i.next())) {
          i.remove();
          changed = true;
        }
      }
      return changed;
    }
  }

  /**
   * a view onto the keys of the map.
   */
  protected class KeyView extends MapBackedView<K> {
    KeyView() {
    }

    @Override
    public Iterator<K> iterator() {
      return new TObjectHashIterator<>(THashMap.this);
    }

    @Override
    public boolean removeElement(K key) {
      return null != THashMap.this.remove(key);
    }

    @Override
    public boolean containsElement(K key) {
      return THashMap.this.contains(key);
    }
  }

  final class Entry implements Map.Entry<K, V> {
    private final K key;
    private V val;
    private final int index;

    Entry(final K key, V value, final int index) {
      this.key = key;
      val = value;
      this.index = index;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return val;
    }

    @Override
    public V setValue(V o) {
      if (_values[index] != val) {
        throw new ConcurrentModificationException();
      }
      _values[index] = o;
      V prev = val;       // need to return previous value
      val = o;            // update this entry's value, in case
      // setValue is called again
      return prev;
    }
  }

  private void writeObject(ObjectOutputStream stream)
    throws IOException {
    stream.defaultWriteObject();

    // number of entries
    stream.writeInt(_size);

    SerializationProcedure writeProcedure = new SerializationProcedure(stream);
    if (!forEachEntry(writeProcedure)) {
      throw writeProcedure.exception;
    }
  }

  private void readObject(ObjectInputStream stream)
    throws IOException, ClassNotFoundException {
    stream.defaultReadObject();

    int size = stream.readInt();
    setUp(size);
    while (size-- > 0) {
      K key = (K)stream.readObject();
      V val = (V)stream.readObject();
      put(key, val);
    }
  }

  public String toString() {
    final StringBuilder sb = new StringBuilder();
    forEachEntry(new TObjectObjectProcedure<K, V>() {
      @Override
      public boolean execute(K key, V value) {
        if (sb.length() != 0) {
          sb.append(',').append(' ');
        }
        sb.append(key == this ? "(this Map)" : key);
        sb.append('=');
        sb.append(value == this ? "(this Map)" : value);
        return true;
      }
    });
    sb.append('}');
    sb.insert(0, '{');
    return sb.toString();
  }
} // THashMap
