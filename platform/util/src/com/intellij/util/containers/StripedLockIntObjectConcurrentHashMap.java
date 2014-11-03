/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** similar to java.util.ConcurrentHashMap except:
 keys are ints
 conserved as much memory as possible by
   -- using only one Segment
   -- eliminating unnecessary fields
   -- using one of 256 ReentrantLock for Segment statically pre-allocated in {@link StripedReentrantLocks}
 made not Serializable
 @deprecated use com.intellij.util.containers.ContainerUtil#createConcurrentIntObjectMap() instead
 */
public class StripedLockIntObjectConcurrentHashMap<V> implements ConcurrentIntObjectMap<V> {
  /* ---------------- Constants -------------- */

  /**
   * The default initial number of table slots for this table.
   * Used when not otherwise specified in constructor.
   */
  private static final int DEFAULT_INITIAL_CAPACITY = 16;

  /**
   * The maximum capacity, used if a higher value is implicitly
   * specified by either of the constructors with arguments.  MUST
   * be a power of two <= 1<<30 to ensure that entries are indexible
   * using ints.
   */
  private static final int MAXIMUM_CAPACITY = 1 << 30;

  /**
   * The default load factor for this table.  Used when not
   * otherwise specified in constructor.
   */
  protected static final float DEFAULT_LOAD_FACTOR = 0.75f;


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
    int cap = getInitCap(initialCapacity, loadFactor);
    setTable(new IntHashEntry[cap]);
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

  @Override
  public boolean isEmpty() {
    return count == 0;
  }

  // inherit Map javadoc

  @Override
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
  @Override
  public V put(int key, @NotNull V value) {
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
  public V putIfAbsent(int key, @NotNull V value) {
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
  @Override
  public V remove(int key) {
    return doRemove(key, null);
  }

  @Override
  public boolean remove(int key, @NotNull V value) {
    return doRemove(key, value) != null;
  }

  @NotNull
  @Override
  public V cacheOrGet(int key, @NotNull V value) {
    V prev = putIfAbsent(key, value);
    return prev == null ? value : prev;
  }

  /**
   * Returns an enumeration of the values in this table.
   *
   * @return an enumeration of the values in this table.
   */
  @Override
  @NotNull
  public Enumeration<V> elements() {
    return new ValueIterator();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    Set<V> result = new THashSet<V>();
    ContainerUtil.addAll(result, elements());
    return result;
  }
/* ---------------- Iterator Support -------------- */

  private class HashIterator {
    private int nextTableIndex = table.length - 1;
    private IntHashEntry<V> nextEntry;
    private IntHashEntry<V> lastReturned;

    private HashIterator() {
      advance();
    }

    public boolean hasMoreElements() {
      return hasNext();
    }

    private void advance() {
      if (nextEntry != null && (nextEntry = nextEntry.next) != null) {
        return;
      }

      while (nextTableIndex >= 0) {
        if ((nextEntry = (IntHashEntry<V>)table[nextTableIndex--]) != null) {
          return;
        }
      }
    }

    public boolean hasNext() {
      return nextEntry != null;
    }

    protected IntHashEntry<V> nextEntry() {
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

  private final class ValueIterator extends HashIterator implements Iterator<V>, Enumeration<V> {
    @Override
    public V next() {
      return nextEntry().value;
    }

    @Override
    public V nextElement() {
      return nextEntry().value;
    }
  }

  @Override
  @NotNull
  public Iterable<IntEntry<V>> entries() {
    return new Iterable<IntEntry<V>>() {
      @Override
      public Iterator<IntEntry<V>> iterator() {
        final HashIterator hashIterator = new HashIterator();
        return new Iterator<IntEntry<V>>() {
          @Override
          public boolean hasNext() {
            return hashIterator.hasNext();
          }

          @Override
          public IntEntry<V> next() {
            IntHashEntry<V> ie = hashIterator.nextEntry;
            hashIterator.nextEntry();
            return new SimpleEntry<V>(ie.key, ie.value);
          }

          @Override
          public void remove() {
            hashIterator.remove();
          }
        };
      }
    };
  }

  /**
   * This duplicates java.util.AbstractMap.SimpleEntry until this class
   * is made accessible.
   */
  private static final class SimpleEntry<V> implements IntEntry<V> {
    private final int key;
    private final V value;

    private SimpleEntry(int key, @NotNull V value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public int getKey() {
      return key;
    }

    @Override
    @NotNull
    public V getValue() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof SimpleEntry)) {
        return false;
      }
      SimpleEntry e = (SimpleEntry)o;
      int o2 = e.getKey();
      return key == o2 && eq(value, e.getValue());
    }

    @Override
    public int hashCode() {
      return key ^ value.hashCode();
    }

    @Override
    public String toString() {
      return key + "=" + value;
    }

    private static boolean eq(Object o1, Object o2) {
      return o1 == null ? o2 == null : o1.equals(o2);
    }
  }


  private static final StripedReentrantLocks STRIPED_REENTRANT_LOCKS = StripedReentrantLocks.getInstance();
  private final byte lockIndex = (byte)STRIPED_REENTRANT_LOCKS.allocateLockIndex();

  private void lock() {
    STRIPED_REENTRANT_LOCKS.lock(lockIndex & 0xff);
  }

  private void unlock() {
    STRIPED_REENTRANT_LOCKS.unlock(lockIndex & 0xff);
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
   protected volatile int count;

  /**
   * Number of updates that alter the size of the table. This is
   * used during bulk-read methods to make sure they see a
   * consistent snapshot: If modCounts change during a traversal
   * of segments computing size or checking containsValue, then
   * we might have an inconsistent view of state so (usually)
   * must retry.
   */
  protected int modCount;

  /**
   * The table is rehashed when its size exceeds this threshold.
   */
  private int threshold() {
    return (int)(table.length * DEFAULT_LOAD_FACTOR);
  }

  /**
   * The per-segment table. Declared as a raw type, casted
   * to IntHashEntry<K,V> on each use.
   */
  protected volatile IntHashEntry[] table;

  /**
   * Set table to new IntHashEntry array.
   * Call only while holding lock or in constructor.
   */
  private void setTable(IntHashEntry[] newTable) {
    table = newTable;
  }

  /**
   * Return properly casted first entry of bin for given hash
   */
  private IntHashEntry<V> getFirst(int hash) {
    IntHashEntry[] tab = table;
    return tab[hash & tab.length - 1];
  }

  /* Specialized implementations of map methods */

  @Override
  public V get(int key) {
    if (count != 0) { // read-volatile
      IntHashEntry<V> e = getFirst(key);
      while (e != null) {
        if (key == e.key) {
          return e.value;
        }
        e = e.next;
      }
    }
    return null;
  }

  @Override
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

  @Override
  public boolean containsValue(@NotNull V value) {
    if (count != 0) { // read-volatile
      ValueIterator valueIterator = new ValueIterator();
      while (valueIterator.hasNext()) {
        V next = valueIterator.next();
        if (Comparing.equal(next, value)) return true;
      }
    }
    return false;
  }

  @Override
  public boolean replace(int key, @NotNull V oldValue, @NotNull V newValue) {
    lock();
    try {
      IntHashEntry<V> e = getFirst(key);
      while (e != null && key != e.key) {
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

  protected V put(int key, @NotNull V value, boolean onlyIfAbsent) {
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

  private void rehash() {
    IntHashEntry[] oldTable = table;
    int oldCapacity = oldTable.length;
    if (oldCapacity >= MAXIMUM_CAPACITY) {
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
    for (IntHashEntry e : oldTable) {
      // We need to guarantee that any existing reads of old Map can
      //  proceed. So we cannot yet null out each bin.
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
            newTable[k] = new IntHashEntry<V>(p.key, n, p.value);
          }
        }
      }
    }
    setTable(newTable);
  }

  /**
   * Remove; match on key only if value null, else match both.
   */
  protected V doRemove(int key, V value) {
    lock();
    try {
      int c = count - 1;
      IntHashEntry[] tab = table;
      int index = key & tab.length - 1;
      IntHashEntry<V> first = tab[index];
      IntHashEntry<V> e = first;
      while (e != null && key != e.key) {
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
            newFirst = new IntHashEntry<V>(p.key, newFirst, p.value);
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

  @Override
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

  public void putAll(@NotNull StripedLockIntObjectConcurrentHashMap<? extends V> t) {
    for (IntEntry<? extends V> e : t.entries()) {
      V value = e.getValue();
      put(e.getKey(), value);
    }
  }

  @Override
  @NotNull
  public int[] keys() {
    TIntArrayList keys = new TIntArrayList(size());
    for (IntHashEntry entry : table) {
      if (entry != null) {
        keys.add(entry.key);
      }
    }
    return keys.toNativeArray();
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
  private static final class IntHashEntry<V> {
    final int key;
    @NotNull volatile V value;
    final IntHashEntry<V> next;

    IntHashEntry(int key, IntHashEntry<V> next, @NotNull V value) {
      this.key = key;
      this.next = next;
      this.value = value;
    }
  }
}
