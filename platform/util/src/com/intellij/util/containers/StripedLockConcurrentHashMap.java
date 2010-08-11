/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

/**
 * similar to java.util.ConcurrentHashMap except:
 * conserved as much memory as possible by
 * -- using only one Segment
 * -- eliminating unnecessary fields
 * -- using one of 256 ReentrantLock for Segment statically preallocated in {@link StripedReentrantLocks}
 * added hashing strategy argument
 * made not Serializable
 */
public class StripedLockConcurrentHashMap<K, V> extends _CHMSegment<K, V> implements ConcurrentMap<K, V> {
  /* ---------------- Constants -------------- */

  /**
   * The default initial number of table slots for this table.
   * Used when not otherwise specified in constructor.
   */
  static int DEFAULT_INITIAL_CAPACITY = 16;

  /**
   * The maximum capacity, used if a higher value is implicitly
   * specified by either of the constructors with arguments.  MUST
   * be a power of two <= 1<<30 to ensure that entries are indexible
   * using ints.
   */
  static final int MAXIMUM_CAPACITY = 1 << 30;

  /**
   * The default load factor for this table.  Used when not
   * otherwise specified in constructor.
   */
  public static final float DEFAULT_LOAD_FACTOR = 0.75f;


  /* ---------------- Fields -------------- */

  Set<K> keySet;
  Set<Entry<K, V>> entrySet;
  Collection<V> values;

  /* ---------------- Small Utilities -------------- */

  /* ---------------- Inner Classes -------------- */


  /* ---------------- Public operations -------------- */

  public StripedLockConcurrentHashMap(TObjectHashingStrategy<K> hashingStrategy) {
    this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, hashingStrategy);
  }

  /**
   * Creates a new, empty map with the specified initial
   * capacity, load factor, and concurrency level.
   *
   * @param initialCapacity the initial capacity. The implementation
   *                        performs internal sizing to accommodate this many elements.
   * @param loadFactor      the load factor threshold, used to control resizing.
   *                        Resizing may be performed when the average number of elements per
   *                        bin exceeds this threshold.
   * @throws IllegalArgumentException if the initial capacity is
   *                                  negative or the load factor or concurrencyLevel are
   *                                  nonpositive.
   */
  public StripedLockConcurrentHashMap(int initialCapacity, float loadFactor) {
    this(initialCapacity, loadFactor, null);
  }

  public StripedLockConcurrentHashMap(int initialCapacity, float loadFactor, TObjectHashingStrategy<K> hashingStrategy) {
    super(getInitCap(initialCapacity, loadFactor), loadFactor, hashingStrategy);
  }

  private static int getInitCap(int initialCapacity, float loadFactor) {
    if (loadFactor <= 0 || initialCapacity < 0) {
      throw new IllegalArgumentException();
    }

    if (initialCapacity > MAXIMUM_CAPACITY) {
      initialCapacity = MAXIMUM_CAPACITY;
    }
    int cap = 1;
    while (cap < initialCapacity) {
      cap <<= 1;
    }
    return cap;
  }

  /**
   * Creates a new, empty map with the specified initial
   * capacity, and with default load factor and concurrencyLevel.
   *
   * @param initialCapacity the initial capacity. The implementation
   *                        performs internal sizing to accommodate this many elements.
   * @throws IllegalArgumentException if the initial capacity of
   *                                  elements is negative.
   */
  public StripedLockConcurrentHashMap(int initialCapacity) {
    this(initialCapacity, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Creates a new, empty map with a default initial capacity,
   * load factor, and concurrencyLevel.
   */
  public StripedLockConcurrentHashMap() {
    this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Creates a new map with the same mappings as the given map.  The
   * map is created with a capacity of twice the number of mappings in
   * the given map or 11 (whichever is greater), and a default load factor
   * and concurrencyLevel.
   *
   * @param t the map
   */
  public StripedLockConcurrentHashMap(Map<? extends K, ? extends V> t) {
    this(Math.max((int)(t.size() / DEFAULT_LOAD_FACTOR) + 1, 11), DEFAULT_LOAD_FACTOR);
    putAll(t);
  }

  // inherit Map javadoc

  public boolean isEmpty() {
    return count == 0;
  }

  // inherit Map javadoc

  public int size() {
    return count;
  }


  /**
   * Returns the value to which the specified key is mapped in this table.
   *
   * @param key a key in the table.
   * @return the value to which the key is mapped in this table;
   *         <tt>null</tt> if the key is not mapped to any value in
   *         this table.
   * @throws NullPointerException if the key is
   *                              <tt>null</tt>.
   */
  public V get(Object key) {
    int hash = myHashingStrategy.computeHashCode((K)key); // throws NullPointerException if key null
    return get((K)key, hash);
  }

  /**
   * Tests if the specified object is a key in this table.
   *
   * @param key possible key.
   * @return <tt>true</tt> if and only if the specified object
   *         is a key in this table, as determined by the
   *         <tt>equals</tt> method; <tt>false</tt> otherwise.
   * @throws NullPointerException if the key is
   *                              <tt>null</tt>.
   */
  public boolean containsKey(Object key) {
    int hash = myHashingStrategy.computeHashCode((K)key); // throws NullPointerException if key null
    return containsKey((K)key, hash);
  }

  /**
   * Legacy method testing if some key maps into the specified value
   * in this table.  This method is identical in functionality to
   * {@link #containsValue}, and  exists solely to ensure
   * full compatibility with class {@link java.util.Hashtable},
   * which supported this method prior to introduction of the
   * Java Collections framework.
   *
   * @param value a value to search for.
   * @return <tt>true</tt> if and only if some key maps to the
   *         <tt>value</tt> argument in this table as
   *         determined by the <tt>equals</tt> method;
   *         <tt>false</tt> otherwise.
   * @throws NullPointerException if the value is <tt>null</tt>.
   */
  public boolean contains(Object value) {
    return containsValue(value);
  }

  /**
   * Maps the specified <tt>key</tt> to the specified
   * <tt>value</tt> in this table. Neither the key nor the
   * value can be <tt>null</tt>.
   * <p/>
   * <p> The value can be retrieved by calling the <tt>get</tt> method
   * with a key that is equal to the original key.
   *
   * @param key   the table key.
   * @param value the value.
   * @return the previous value of the specified key in this table,
   *         or <tt>null</tt> if it did not have one.
   * @throws NullPointerException if the key or value is
   *                              <tt>null</tt>.
   */
  public V put(K key, V value) {
    if (value == null) {
      throw new NullPointerException();
    }
    int hash = myHashingStrategy.computeHashCode(key);
    return put(key, hash, value, false);
  }

  /**
   * If the specified key is not already associated
   * with a value, associate it with the given value.
   * This is equivalent to
   * <pre>
   *   if (!map.containsKey(key))
   *      return map.put(key, value);
   *   else
   *      return map.get(key);
   * </pre>
   * Except that the action is performed atomically.
   *
   * @param key   key with which the specified value is to be associated.
   * @param value value to be associated with the specified key.
   * @return previous value associated with specified key, or <tt>null</tt>
   *         if there was no mapping for key.
   * @throws NullPointerException if the specified key or value is
   *                              <tt>null</tt>.
   */
  public V putIfAbsent(K key, V value) {
    if (value == null) {
      throw new NullPointerException();
    }
    int hash = myHashingStrategy.computeHashCode(key);
    return put(key, hash, value, true);
  }


  /**
   * Copies all of the mappings from the specified map to this one.
   * <p/>
   * These mappings replace any mappings that this map had for any of the
   * keys currently in the specified Map.
   *
   * @param t Mappings to be stored in this map.
   */
  public void putAll(Map<? extends K, ? extends V> t) {
    for (Entry<? extends K, ? extends V> e : t.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  /**
   * Removes the key (and its corresponding value) from this
   * table. This method does nothing if the key is not in the table.
   *
   * @param key the key that needs to be removed.
   * @return the value to which the key had been mapped in this table,
   *         or <tt>null</tt> if the key did not have a mapping.
   * @throws NullPointerException if the key is
   *                              <tt>null</tt>.
   */
  public V remove(Object key) {
    int hash = myHashingStrategy.computeHashCode((K)key);
    return remove((K)key, hash, null);
  }

  /**
   * Remove entry for key only if currently mapped to given value.
   * Acts as
   * <pre>
   *  if (map.get(key).equals(value)) {
   *     map.remove(key);
   *     return true;
   * } else return false;
   * </pre>
   * except that the action is performed atomically.
   *
   * @param key   key with which the specified value is associated.
   * @param value value associated with the specified key.
   * @return true if the value was removed
   * @throws NullPointerException if the specified key is
   *                              <tt>null</tt>.
   */
  public boolean remove(Object key, Object value) {
    int hash = myHashingStrategy.computeHashCode((K)key);
    return remove((K)key, hash, value) != null;
  }


  /**
   * Replace entry for key only if currently mapped to given value.
   * Acts as
   * <pre>
   *  if (map.get(key).equals(oldValue)) {
   *     map.put(key, newValue);
   *     return true;
   * } else return false;
   * </pre>
   * except that the action is performed atomically.
   *
   * @param key      key with which the specified value is associated.
   * @param oldValue value expected to be associated with the specified key.
   * @param newValue value to be associated with the specified key.
   * @return true if the value was replaced
   * @throws NullPointerException if the specified key or values are
   *                              <tt>null</tt>.
   */
  public boolean replace(K key, V oldValue, V newValue) {
    if (oldValue == null || newValue == null) {
      throw new NullPointerException();
    }
    int hash = myHashingStrategy.computeHashCode(key);
    return replace(key, hash, oldValue, newValue);
  }

  /**
   * Replace entry for key only if currently mapped to some value.
   * Acts as
   * <pre>
   *  if ((map.containsKey(key)) {
   *     return map.put(key, value);
   * } else return null;
   * </pre>
   * except that the action is performed atomically.
   *
   * @param key   key with which the specified value is associated.
   * @param value value to be associated with the specified key.
   * @return previous value associated with specified key, or <tt>null</tt>
   *         if there was no mapping for key.
   * @throws NullPointerException if the specified key or value is
   *                              <tt>null</tt>.
   */
  public V replace(K key, V value) {
    if (value == null) {
      throw new NullPointerException();
    }
    int hash = myHashingStrategy.computeHashCode(key);
    return replace(key, hash, value);
  }


  /**
   * Returns a set view of the keys contained in this map.  The set is
   * backed by the map, so changes to the map are reflected in the set, and
   * vice-versa.  The set supports element removal, which removes the
   * corresponding mapping from this map, via the <tt>Iterator.remove</tt>,
   * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt>, and
   * <tt>clear</tt> operations.  It does not support the <tt>add</tt> or
   * <tt>addAll</tt> operations.
   * The view's returned <tt>iterator</tt> is a "weakly consistent" iterator that
   * will never throw {@link java.util.ConcurrentModificationException},
   * and guarantees to traverse elements as they existed upon
   * construction of the iterator, and may (but is not guaranteed to)
   * reflect any modifications subsequent to construction.
   *
   * @return a set view of the keys contained in this map.
   */
  public Set<K> keySet() {
    Set<K> ks = keySet;
    return ks != null ? ks : (keySet = new KeySet());
  }


  /**
   * Returns a collection view of the values contained in this map.  The
   * collection is backed by the map, so changes to the map are reflected in
   * the collection, and vice-versa.  The collection supports element
   * removal, which removes the corresponding mapping from this map, via the
   * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
   * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
   * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
   * The view's returned <tt>iterator</tt> is a "weakly consistent" iterator that
   * will never throw {@link java.util.ConcurrentModificationException},
   * and guarantees to traverse elements as they existed upon
   * construction of the iterator, and may (but is not guaranteed to)
   * reflect any modifications subsequent to construction.
   *
   * @return a collection view of the values contained in this map.
   */
  public Collection<V> values() {
    Collection<V> vs = values;
    return vs != null ? vs : (values = new Values());
  }


  /**
   * Returns a collection view of the mappings contained in this map.  Each
   * element in the returned collection is a <tt>Map.Entry</tt>.  The
   * collection is backed by the map, so changes to the map are reflected in
   * the collection, and vice-versa.  The collection supports element
   * removal, which removes the corresponding mapping from the map, via the
   * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
   * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
   * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
   * The view's returned <tt>iterator</tt> is a "weakly consistent" iterator that
   * will never throw {@link java.util.ConcurrentModificationException},
   * and guarantees to traverse elements as they existed upon
   * construction of the iterator, and may (but is not guaranteed to)
   * reflect any modifications subsequent to construction.
   *
   * @return a collection view of the mappings contained in this map.
   */
  public Set<Entry<K, V>> entrySet() {
    Set<Entry<K, V>> es = entrySet;
    return es != null ? es : (entrySet = new EntrySet());
  }


  /**
   * Returns an enumeration of the keys in this table.
   *
   * @return an enumeration of the keys in this table.
   * @see #keySet
   */
  public Enumeration<K> keys() {
    return new KeyIterator();
  }

  /**
   * Returns an enumeration of the values in this table.
   *
   * @return an enumeration of the values in this table.
   * @see #values
   */
  public Enumeration<V> elements() {
    return new ValueIterator();
  }

  /* ---------------- Iterator Support -------------- */

  abstract class HashIterator {
    int nextSegmentIndex;
    int nextTableIndex;
    HashEntry[] currentTable;
    HashEntry<K, V> nextEntry;
    HashEntry<K, V> lastReturned;

    HashIterator() {
      nextSegmentIndex = 0;
      nextTableIndex = -1;
      advance();
    }

    public boolean hasMoreElements() {
      return hasNext();
    }

    final void advance() {
      if (nextEntry != null && (nextEntry = nextEntry.next) != null) {
        return;
      }

      while (nextTableIndex >= 0) {
        if ((nextEntry = (HashEntry<K, V>)currentTable[nextTableIndex--]) != null) {
          return;
        }
      }

      while (nextSegmentIndex >= 0) {
        _CHMSegment seg = StripedLockConcurrentHashMap.this;
        nextSegmentIndex--;
        if (seg.count != 0) {
          currentTable = seg.table;
          for (int j = currentTable.length - 1; j >= 0; --j) {
            if ((nextEntry = (HashEntry<K, V>)currentTable[j]) != null) {
              nextTableIndex = j - 1;
              return;
            }
          }
        }
      }
    }

    public boolean hasNext() {
      return nextEntry != null;
    }

    HashEntry<K, V> nextEntry() {
      if (nextEntry == null) {
        throw new NoSuchElementException();
      }
      lastReturned = nextEntry;
      advance();
      return lastReturned;
    }

    public void remove() {
      if (lastReturned == null) {
        throw new IllegalStateException();
      }
      StripedLockConcurrentHashMap.this.remove(lastReturned.key);
      lastReturned = null;
    }
  }

  final class KeyIterator extends HashIterator implements Iterator<K>, Enumeration<K> {
    public K next() {
      return nextEntry().key;
    }

    public K nextElement() {
      return nextEntry().key;
    }
  }

  final class ValueIterator extends HashIterator implements Iterator<V>, Enumeration<V> {
    public V next() {
      return nextEntry().value;
    }

    public V nextElement() {
      return nextEntry().value;
    }
  }


  /**
   * Entry iterator. Exported Entry objects must write-through
   * changes in setValue, even if the nodes have been cloned. So we
   * cannot return internal HashEntry objects. Instead, the iterator
   * itself acts as a forwarding pseudo-entry.
   */
  final class EntryIterator extends HashIterator implements Entry<K, V>, Iterator<Entry<K, V>> {
    public Entry<K, V> next() {
      nextEntry();
      return this;
    }

    public K getKey() {
      if (lastReturned == null) {
        throw new IllegalStateException("Entry was removed");
      }
      return lastReturned.key;
    }

    public V getValue() {
      if (lastReturned == null) {
        throw new IllegalStateException("Entry was removed");
      }
      return get(lastReturned.key);
    }

    public V setValue(V value) {
      if (lastReturned == null) {
        throw new IllegalStateException("Entry was removed");
      }
      return put(lastReturned.key, value);
    }

    public boolean equals(Object o) {
      // If not acting as entry, just use default.
      if (lastReturned == null) {
        return super.equals(o);
      }
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry e = (Entry)o;
      K o1 = getKey();
      K o2 = (K)e.getKey();
      return (o1 == null ? o2 == null : myHashingStrategy.equals(o1, o2)) && eq(getValue(), e.getValue());
    }

    public int hashCode() {
      // If not acting as entry, just use default.
      if (lastReturned == null) {
        return super.hashCode();
      }

      Object k = getKey();
      Object v = getValue();
      return (k == null ? 0 : k.hashCode()) ^
             (v == null ? 0 : v.hashCode());
    }

    public String toString() {
      // If not acting as entry, just use default.
      if (lastReturned == null) {
        return super.toString();
      }
      else {
        return getKey() + "=" + getValue();
      }
    }

    boolean eq(Object o1, Object o2) {
      return o1 == null ? o2 == null : o1.equals(o2);
    }

  }

  final class KeySet extends AbstractSet<K> {
    public Iterator<K> iterator() {
      return new KeyIterator();
    }

    public int size() {
      return StripedLockConcurrentHashMap.this.size();
    }

    public boolean contains(Object o) {
      return containsKey(o);
    }

    public boolean remove(Object o) {
      return StripedLockConcurrentHashMap.this.remove(o) != null;
    }

    public void clear() {
      StripedLockConcurrentHashMap.this.clear();
    }

    public Object[] toArray() {
      Collection<K> c = new ArrayList<K>();
      for (K k : this) {
        c.add(k);
      }
      return c.toArray();
    }

    public <T> T[] toArray(T[] a) {
      Collection<K> c = new ArrayList<K>();
      for (K k : this) {
        c.add(k);
      }
      return c.toArray(a);
    }
  }

  final class Values extends AbstractCollection<V> {
    public Iterator<V> iterator() {
      return new ValueIterator();
    }

    public int size() {
      return StripedLockConcurrentHashMap.this.size();
    }

    public boolean contains(Object o) {
      return containsValue(o);
    }

    public void clear() {
      StripedLockConcurrentHashMap.this.clear();
    }

    public Object[] toArray() {
      Collection<V> c = new ArrayList<V>();
      for (V k : this) {
        c.add(k);
      }
      return c.toArray();
    }

    public <T> T[] toArray(T[] a) {
      Collection<V> c = new ArrayList<V>();
      for (V k : this) {
        c.add(k);
      }
      return c.toArray(a);
    }
  }

  final class EntrySet extends AbstractSet<Entry<K, V>> {
    public Iterator<Entry<K, V>> iterator() {
      return new EntryIterator();
    }

    public boolean contains(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry<K, V> e = (Entry<K, V>)o;
      V v = get(e.getKey());
      return v != null && v.equals(e.getValue());
    }

    public boolean remove(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry<K, V> e = (Entry<K, V>)o;
      return StripedLockConcurrentHashMap.this.remove(e.getKey(), e.getValue());
    }

    public int size() {
      return StripedLockConcurrentHashMap.this.size();
    }

    public void clear() {
      StripedLockConcurrentHashMap.this.clear();
    }

    public Object[] toArray() {
      // Since we don't ordinarily have distinct Entry objects, we
      // must pack elements using exportable SimpleEntry
      Collection<Entry<K, V>> c = new ArrayList<Entry<K, V>>(size());
      for (Entry<K, V> i : this) {
        c.add(new SimpleEntry(i));
      }
      return c.toArray();
    }

    public <T> T[] toArray(T[] a) {
      Collection<Entry<K, V>> c = new ArrayList<Entry<K, V>>(size());
      for (Entry<K, V> i : this) {
        c.add(new SimpleEntry(i));
      }
      return c.toArray(a);
    }
  }

  /**
   * This duplicates java.util.AbstractMap.SimpleEntry until this class
   * is made accessible.
   */
  final class SimpleEntry implements Entry<K, V> {
    K key;
    V value;

    public SimpleEntry(Entry<K, V> e) {
      key = e.getKey();
      value = e.getValue();
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public V setValue(V value) {
      V oldValue = this.value;
      this.value = value;
      return oldValue;
    }

    public boolean equals(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry e = (Entry)o;
      K o2 = (K)e.getKey();
      return (key == null ? o2 == null : myHashingStrategy.equals(key, o2)) && eq(value, e.getValue());
    }

    public int hashCode() {
      return (key == null ? 0 : key.hashCode()) ^
             (value == null ? 0 : value.hashCode());
    }

    public String toString() {
      return key + "=" + value;
    }

    boolean eq(Object o1, Object o2) {
      return o1 == null ? o2 == null : o1.equals(o2);
    }
  }

  public static class CanonicalHashingStrategy<K> implements TObjectHashingStrategy<K> {
    private static final CanonicalHashingStrategy INSTANCE = new CanonicalHashingStrategy();

    static <K> CanonicalHashingStrategy<K> getInstance() {
      return INSTANCE;
    }

    public int computeHashCode(final K object) {
      int h = object.hashCode();
      h += ~(h << 9);
      h ^= h >>> 14;
      h += h << 4;
      h ^= h >>> 10;
      return h;
    }

    public boolean equals(final K o1, final K o2) {
      return o1.equals(o2);
    }
  }
}

/**
 * Segments are specialized versions of hash tables.  This
 * subclasses from ReentrantLock opportunistically, just to
 * simplify some locking and avoid separate construction.
 */
class _CHMSegment<K, V> {
  private final Lock lock = StripedReentrantLocks.getInstance().allocateLock();

  private void lock() {
    lock.lock();
  }

  private void unlock() {
    lock.unlock();
  }
  /*
  * Segments maintain a table of entry lists that are ALWAYS
  * kept in a consistent state, so can be read without locking.
  * Next fields of nodes are immutable (final).  All list
  * additions are performed at the front of each bin. This
  * makes it easy to check changes, and also fast to traverse.
  * When nodes would otherwise be changed, new nodes are
  * created to replace them. This works well for hash tables
  * since the bin lists tend to be short. (The average length
  * is less than two for the default load factor threshold.)
  *
  * Read operations can thus proceed without locking, but rely
  * on selected uses of volatiles to ensure that completed
  * write operations performed by other threads are
  * noticed. For most purposes, the "count" field, tracking the
  * number of elements, serves as that volatile variable
  * ensuring visibility.  This is convenient because this field
  * needs to be read in many read operations anyway:
  *
  *   - All (unsynchronized) read operations must first read the
  *     "count" field, and should not look at table entries if
  *     it is 0.
  *
  *   - All (synchronized) write operations should write to
  *     the "count" field after structurally changing any bin.
  *     The operations must not take any action that could even
  *     momentarily cause a concurrent read operation to see
  *     inconsistent data. This is made easier by the nature of
  *     the read operations in Map. For example, no operation
  *     can reveal that the table has grown but the threshold
  *     has not yet been updated, so there are no atomicity
  *     requirements for this with respect to reads.
  *
  * As a guide, all critical volatile reads and writes to the
  * count field are marked in code comments.
  */

  /**
   * The number of elements in this segment's region.
   */
  volatile int count;

  /**
   * The table is rehashed when its size exceeds this threshold.
   */
  private int threshold() {
    return (int)(table.length * loadFactor);
  }

  /**
   * The per-segment table. Declared as a raw type, casted
   * to HashEntry<K,V> on each use.
   */
  volatile HashEntry[] table;

  /**
   * The load factor for the hash table.  Even though this value
   * is same for all segments, it is replicated to avoid needing
   * links to outer object.
   *
   * @serial
   */
  final float loadFactor;
  protected final TObjectHashingStrategy<K> myHashingStrategy;

  _CHMSegment(int initialCapacity, float lf, TObjectHashingStrategy<K> hashingStrategy) {
    loadFactor = lf;
    myHashingStrategy = hashingStrategy == null ? StripedLockConcurrentHashMap.CanonicalHashingStrategy.<K>getInstance() : hashingStrategy;
    setTable(new HashEntry[initialCapacity]);
  }

  /**
   * Set table to new HashEntry array.
   * Call only while holding lock or in constructor.
   */
  void setTable(HashEntry[] newTable) {
    table = newTable;
  }

  /**
   * Return properly casted first entry of bin for given hash
   */
  HashEntry<K, V> getFirst(int hash) {
    HashEntry[] tab = table;
    return tab[hash & tab.length - 1];
  }

  /**
   * Read value field of an entry under lock. Called if value
   * field ever appears to be null. This is possible only if a
   * compiler happens to reorder a HashEntry initialization with
   * its table assignment, which is legal under memory model
   * but is not known to ever occur.
   */
  V readValueUnderLock(HashEntry<K, V> e) {
    lock();
    try {
      return e.value;
    }
    finally {
      unlock();
    }
  }

  /* Specialized implementations of map methods */

  V get(K key, int hash) {
    if (count != 0) { // read-volatile
      HashEntry<K, V> e = getFirst(hash);
      while (e != null) {
        if (e.hash == hash && myHashingStrategy.equals(key, e.key)) {
          V v = e.value;
          if (v != null) {
            return v;
          }
          return readValueUnderLock(e); // recheck
        }
        e = e.next;
      }
    }
    return null;
  }

  boolean containsKey(K key, int hash) {
    if (count != 0) { // read-volatile
      HashEntry<K, V> e = getFirst(hash);
      while (e != null) {
        if (e.hash == hash && myHashingStrategy.equals(key, e.key)) {
          return true;
        }
        e = e.next;
      }
    }
    return false;
  }

  public boolean containsValue(Object value) {
    if (count != 0) { // read-volatile
      HashEntry[] tab = table;
      int len = tab.length;
      for (int i = 0; i < len; i++) {
        for (HashEntry<K, V> e = tab[i];
             e != null;
             e = e.next) {
          V v = e.value;
          if (v == null) // recheck
          {
            v = readValueUnderLock(e);
          }
          if (value.equals(v)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  boolean replace(K key, int hash, V oldValue, V newValue) {
    lock();
    try {
      HashEntry<K, V> e = getFirst(hash);
      while (e != null && (e.hash != hash || !myHashingStrategy.equals(key, e.key))) {
        e = e.next;
      }

      boolean replaced = false;
      if (e != null && oldValue.equals(e.value)) {
        replaced = true;
        e.value = newValue;
      }
      return replaced;
    }
    finally {
      unlock();
    }
  }

  V replace(K key, int hash, V newValue) {
    lock();
    try {
      HashEntry<K, V> e = getFirst(hash);
      while (e != null && (e.hash != hash || !myHashingStrategy.equals(key, e.key))) {
        e = e.next;
      }

      V oldValue = null;
      if (e != null) {
        oldValue = e.value;
        e.value = newValue;
      }
      return oldValue;
    }
    finally {
      unlock();
    }
  }


  V put(K key, int hash, V value, boolean onlyIfAbsent) {
    lock();
    try {
      int c = count;
      if (c++ > threshold()) // ensure capacity
      {
        rehash();
      }
      HashEntry[] tab = table;
      int index = hash & tab.length - 1;
      HashEntry<K, V> first = tab[index];
      HashEntry<K, V> e = first;
      while (e != null && (e.hash != hash || !myHashingStrategy.equals(key, e.key))) {
        e = e.next;
      }

      V oldValue;
      if (e != null) {
        oldValue = e.value;
        if (!onlyIfAbsent) {
          e.value = value;
        }
      }
      else {
        oldValue = null;
        tab[index] = new HashEntry<K, V>(key, hash, first, value);
        count = c; // write-volatile
      }
      return oldValue;
    }
    finally {
      unlock();
    }
  }

  void rehash() {
    HashEntry[] oldTable = table;
    int oldCapacity = oldTable.length;
    if (oldCapacity >= StripedLockConcurrentHashMap.MAXIMUM_CAPACITY) {
      return;
    }

    /*
    * Reclassify nodes in each list to new Map.  Because we are
    * using power-of-two expansion, the elements from each bin
    * must either stay at same index, or move with a power of two
    * offset. We eliminate unnecessary node creation by catching
    * cases where old nodes can be reused because their next
    * fields won't change. Statistically, at the default
    * threshold, only about one-sixth of them need cloning when
    * a table doubles. The nodes they replace will be garbage
    * collectable as soon as they are no longer referenced by any
    * reader thread that may be in the midst of traversing table
    * right now.
    */

    HashEntry[] newTable = new HashEntry[oldCapacity << 1];
    int sizeMask = newTable.length - 1;
    for (int i = 0; i < oldCapacity; i++) {
      // We need to guarantee that any existing reads of old Map can
      //  proceed. So we cannot yet null out each bin.
      HashEntry<K, V> e = oldTable[i];

      if (e != null) {
        HashEntry<K, V> next = e.next;
        int idx = e.hash & sizeMask;

        //  Single node on list
        if (next == null) {
          newTable[idx] = e;
        }

        else {
          // Reuse trailing consecutive sequence at same slot
          HashEntry<K, V> lastRun = e;
          int lastIdx = idx;
          for (HashEntry<K, V> last = next;
               last != null;
               last = last.next) {
            int k = last.hash & sizeMask;
            if (k != lastIdx) {
              lastIdx = k;
              lastRun = last;
            }
          }
          newTable[lastIdx] = lastRun;

          // Clone all remaining nodes
          for (HashEntry<K, V> p = e; p != lastRun; p = p.next) {
            int k = p.hash & sizeMask;
            HashEntry<K, V> n = newTable[k];
            newTable[k] = new HashEntry<K, V>(p.key, p.hash,
                                              n, p.value);
          }
        }
      }
    }
    setTable(newTable);
  }

  /**
   * Remove; match on key only if value null, else match both.
   */
  V remove(K key, int hash, Object value) {
    lock();
    try {
      int c = count - 1;
      HashEntry[] tab = table;
      int index = hash & tab.length - 1;
      HashEntry<K, V> first = tab[index];
      HashEntry<K, V> e = first;
      while (e != null && (e.hash != hash || !myHashingStrategy.equals(key, e.key))) {
        e = e.next;
      }

      V oldValue = null;
      if (e != null) {
        V v = e.value;
        if (value == null || value.equals(v)) {
          oldValue = v;
          // All entries following removed node can stay
          // in list, but all preceding ones need to be
          // cloned.
          HashEntry<K, V> newFirst = e.next;
          for (HashEntry<K, V> p = first; p != e; p = p.next) {
            newFirst = new HashEntry<K, V>(p.key, p.hash,
                                           newFirst, p.value);
          }
          tab[index] = newFirst;
          count = c; // write-volatile
        }
      }
      return oldValue;
    }
    finally {
      unlock();
    }
  }

  public void clear() {
    if (count != 0) {
      lock();
      try {
        HashEntry[] tab = table;
        for (int i = 0; i < tab.length; i++) {
          tab[i] = null;
        }
        count = 0; // write-volatile
      }
      finally {
        unlock();
      }
    }
  }

  /**
     * ConcurrentHashMap list entry. Note that this is never exported
     * out as a user-visible Map.Entry.
     * <p/>
     * Because the value field is volatile, not final, it is legal wrt
     * the Java Memory Model for an unsynchronized reader to see null
     * instead of initial value when read via a data race.  Although a
     * reordering leading to this is not likely to ever actually
     * occur, the Segment.readValueUnderLock method is used as a
     * backup in case a null (pre-initialized) value is ever seen in
     * an unsynchronized access method.
     */
    static final class HashEntry<K, V> {
      final K key;
      final int hash;
      volatile V value;
      final HashEntry<K, V> next;

      HashEntry(K key, int hash, HashEntry<K, V> next, V value) {
        this.key = key;
        this.hash = hash;
        this.next = next;
        this.value = value;
      }
    }
}


