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

import java.util.*;
import java.util.concurrent.locks.Lock;

/** similar to java.util.ConcurrentHashMap except:
 keys are ints 
 conserved as much memory as possible by
   -- using only one Segment
   -- eliminating unnecessary fields
   -- using one of 256 ReentrantLock for Segment staticly preallocated in StripedReentrantLocks
 added hashing strategy argument
 made not Serializable
 */
public class StripedLockIntObjectConcurrentHashMap<V> extends IntSegment<V> {
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

  /* ---------------- Small Utilities -------------- */

  /* ---------------- Inner Classes -------------- */


  /* ---------------- Public operations -------------- */

  /**
   * Creates a new, empty map with the specified initial
   * capacity, load factor, and concurrency level.
   *
   * @param initialCapacity  the initial capacity. The implementation
   *                         performs internal sizing to accommodate this many elements.
   * @param loadFactor       the load factor threshold, used to control resizing.
   *                         Resizing may be performed when the average number of elements per
   *                         bin exceeds this threshold.
   * @throws IllegalArgumentException if the initial capacity is
   *                                  negative or the load factor or concurrencyLevel are
   *                                  nonpositive.
   */
  public StripedLockIntObjectConcurrentHashMap(int initialCapacity, float loadFactor) {
    super(getInitCap(initialCapacity, loadFactor), loadFactor);
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
  public StripedLockIntObjectConcurrentHashMap(int initialCapacity) {
    this(initialCapacity, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Creates a new, empty map with a default initial capacity,
   * load factor, and concurrencyLevel.
   */
  public StripedLockIntObjectConcurrentHashMap() {
    this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
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
  public V put(int key, V value) {
    if (value == null) {
      throw new NullPointerException();
    }
    return put(key, value, false);
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
  public V putIfAbsent(int key, V value) {
    if (value == null) {
      throw new NullPointerException();
    }
    return put(key, value, true);
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
  public V remove(int key) {
    return remove(key, null);
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
    IntHashEntry[] currentTable;
    IntHashEntry<V> nextEntry;
    IntHashEntry<V> lastReturned;

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
        if ((nextEntry = (IntHashEntry<V>)currentTable[nextTableIndex--]) != null) {
          return;
        }
      }

      while (nextSegmentIndex >= 0) {
        IntSegment seg = StripedLockIntObjectConcurrentHashMap.this;
        nextSegmentIndex--;
        if (seg.count != 0) {
          currentTable = seg.table;
          for (int j = currentTable.length - 1; j >= 0; --j) {
            if ((nextEntry = (IntHashEntry<V>)currentTable[j]) != null) {
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

    IntHashEntry<V> nextEntry() {
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
      StripedLockIntObjectConcurrentHashMap.this.remove(lastReturned.key);
      lastReturned = null;
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

  interface IntEntry<V> {
    int getKey();
    V getValue();
    V setValue(V value);
  }


  final class Values extends AbstractCollection<V> {
    public Iterator<V> iterator() {
      return new ValueIterator();
    }

    public int size() {
      return StripedLockIntObjectConcurrentHashMap.this.size();
    }

    public boolean contains(Object o) {
      return containsValue(o);
    }

    public void clear() {
      StripedLockIntObjectConcurrentHashMap.this.clear();
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

  /**
   * This duplicates java.util.AbstractMap.SimpleEntry until this class
   * is made accessible.
   */
  static final class SimpleEntry<V> implements IntEntry<V> {
    int key;
    V value;

    public SimpleEntry(IntEntry<V> e) {
      key = e.getKey();
      value = e.getValue();
    }

    public int getKey() {
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
      if (!(o instanceof SimpleEntry)) {
        return false;
      }
      SimpleEntry e = (SimpleEntry)o;
      int o2 = e.getKey();
      return key == o2 && eq(value, e.getValue());
    }

    public int hashCode() {
      return key ^
             (value == null ? 0 : value.hashCode());
    }

    public String toString() {
      return key + "=" + value;
    }

    boolean eq(Object o1, Object o2) {
      return o1 == null ? o2 == null : o1.equals(o2);
    }
  }
}

class IntSegment<V> {
  private final Lock lock = StripedReentrantLocks.getInstance().allocateLock();

  public void lock() {
    lock.lock();
  }

  public void unlock() {
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
   * Number of updates that alter the size of the table. This is
   * used during bulk-read methods to make sure they see a
   * consistent snapshot: If modCounts change during a traversal
   * of segments computing size or checking containsValue, then
   * we might have an inconsistent view of state so (usually)
   * must retry.
   */
   int modCount;

  /**
   * The table is rehashed when its size exceeds this threshold.
   */
  int threshold() {
    return (int)(table.length * loadFactor);
  }

  /**
   * The per-segment table. Declared as a raw type, casted
   * to IntHashEntry<K,V> on each use.
   */
   volatile IntHashEntry[] table;

  /**
   * The load factor for the hash table.  Even though this value
   * is same for all segments, it is replicated to avoid needing
   * links to outer object.
   *
   * @serial
   */
  final float loadFactor;

  IntSegment(int initialCapacity, float lf) {
    loadFactor = lf;
    setTable(new IntHashEntry[initialCapacity]);
  }

  /**
   * Set table to new IntHashEntry array.
   * Call only while holding lock or in constructor.
   */
  void setTable(IntHashEntry[] newTable) {
    table = newTable;
  }

  /**
   * Return properly casted first entry of bin for given hash
   */
  IntHashEntry<V> getFirst(int hash) {
    IntHashEntry[] tab = table;
    return tab[hash & tab.length - 1];
  }

  /**
   * Read value field of an entry under lock. Called if value
   * field ever appears to be null. This is possible only if a
   * compiler happens to reorder a IntHashEntry initialization with
   * its table assignment, which is legal under memory model
   * but is not known to ever occur.
   */
  V readValueUnderLock(IntHashEntry<V> e) {
    lock();
    try {
      return e.value;
    }
    finally {
      unlock();
    }
  }

  /* Specialized implementations of map methods */

  public V get(int key) {
    if (count != 0) { // read-volatile
      IntHashEntry<V> e = getFirst(key);
      while (e != null) {
        if (key == e.key) {
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

  public boolean containsKey(int key) {
    if (count != 0) { // read-volatile
      IntHashEntry<V> e = getFirst(key);
      while (e != null) {
        if (key == e.key) {
          return true;
        }
        e = e.next;
      }
    }
    return false;
  }

  public boolean containsValue(Object value) {
    if (count != 0) { // read-volatile
      IntHashEntry[] tab = table;
      int len = tab.length;
      for (int i = 0; i < len; i++) {
        for (IntHashEntry<V> e = tab[i];
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

  public boolean replace(int key, V oldValue, V newValue) {
    if (oldValue == null || newValue == null) {
      throw new NullPointerException();
    }
    lock();
    try {
      IntHashEntry<V> e = getFirst(key);
      while (e != null && !(key == e.key)) {
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

  public V replace(int key, V newValue) {
    if (newValue == null) {
      throw new NullPointerException();
    }
    lock();
    try {
      IntHashEntry<V> e = getFirst(key);
      while (e != null && !(key == e.key)) {
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


  V put(int key, V value, boolean onlyIfAbsent) {
    lock();
    try {
      int c = count;
      if (c++ > threshold()) // ensure capacity
      {
        rehash();
      }
      IntHashEntry[] tab = table;
      int index = key & tab.length - 1;
      IntHashEntry<V> first = tab[index];
      IntHashEntry<V> e = first;
      while (e != null && key != e.key) {
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
        ++modCount;
        tab[index] = new IntHashEntry<V>(key, first, value);
        count = c; // write-volatile
      }
      return oldValue;
    }
    finally {
      unlock();
    }
  }

  void rehash() {
    IntHashEntry[] oldTable = table;
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

    IntHashEntry[] newTable = new IntHashEntry[oldCapacity << 1];
    int sizeMask = newTable.length - 1;
    for (int i = 0; i < oldCapacity; i++) {
      // We need to guarantee that any existing reads of old Map can
      //  proceed. So we cannot yet null out each bin.
      IntHashEntry<V> e = oldTable[i];

      if (e != null) {
        IntHashEntry<V> next = e.next;
        int idx = e.key & sizeMask;

        //  Single node on list
        if (next == null) {
          newTable[idx] = e;
        }

        else {
          // Reuse trailing consecutive sequence at same slot
          IntHashEntry<V> lastRun = e;
          int lastIdx = idx;
          for (IntHashEntry<V> last = next;
               last != null;
               last = last.next) {
            int k = last.key & sizeMask;
            if (k != lastIdx) {
              lastIdx = k;
              lastRun = last;
            }
          }
          newTable[lastIdx] = lastRun;

          // Clone all remaining nodes
          for (IntHashEntry<V> p = e; p != lastRun; p = p.next) {
            int k = p.key & sizeMask;
            IntHashEntry<V> n = newTable[k];
            newTable[k] = new IntHashEntry<V>(p.key,
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
  public V remove(int key, Object value) {
    lock();
    try {
      int c = count - 1;
      IntHashEntry[] tab = table;
      int index = key & tab.length - 1;
      IntHashEntry<V> first = tab[index];
      IntHashEntry<V> e = first;
      while (e != null && !(key == e.key)) {
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
          ++modCount;
          IntHashEntry<V> newFirst = e.next;
          for (IntHashEntry<V> p = first; p != e; p = p.next) {
            newFirst = new IntHashEntry<V>(p.key,
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
        IntHashEntry[] tab = table;
        for (int i = 0; i < tab.length; i++) {
          tab[i] = null;
        }
        ++modCount;
        count = 0; // write-volatile
      }
      finally {
        unlock();
      }
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
final class IntHashEntry<V> {
  final int key;
  volatile V value;
  final IntHashEntry<V> next;

  IntHashEntry(int key, IntHashEntry<V> next, V value) {
    this.key = key;
    this.next = next;
    this.value = value;
  }
}
