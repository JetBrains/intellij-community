/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.ConcurrencyUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

// added hashing strategy argument
// added cacheOrGet convenience method
// changed DEFAULT_SEGMENTS to 2 from 16
public class ConcurrentHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Serializable, TObjectHashingStrategy<K> {
  private static final long serialVersionUID = 7249069246763182397L;

    /*
     * The basic strategy is to subdivide the table among Segments,
     * each of which itself is a concurrently readable hash table.
     */

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

  /**
   * The default number of concurrency control segments.
   **/
  static final int DEFAULT_SEGMENTS = Math.min(2, Runtime.getRuntime().availableProcessors()); // CHANGED FROM 16

  /**
   * The maximum number of segments to allow; used to bound
   * constructor arguments.
   */
  static final int MAX_SEGMENTS = 1 << 16; // slightly conservative

  /**
   * Number of unsynchronized retries in size and containsValue
   * methods before resorting to locking. This is used to avoid
   * unbounded retries if tables undergo continuous modification
   * which would make it impossible to obtain an accurate result.
   */
  static final int RETRIES_BEFORE_LOCK = 2;

    /* ---------------- Fields -------------- */

  /**
   * Mask value for indexing into segments. The upper bits of a
   * key's hash code are used to choose the segment.
   **/
  final int segmentMask;

  /**
   * Shift value for indexing within segments.
   **/
  final int segmentShift;

  /**
   * The segments, each of which is a specialized hash table
   */
  final Segment[] segments;

  transient Set<K> keySet;
  transient Set<Entry<K,V>> entrySet;
  transient Collection<V> values;
  private final TObjectHashingStrategy<K> myHashingStrategy;

  /* ---------------- Small Utilities -------------- */

  /**
   * Returns the segment that should be used for key with given hash
   * @param hash the hash code for the key
   * @return the segment
   */
  final Segment<K,V> segmentFor(int hash) {
    return segments[(hash >>> segmentShift) & segmentMask];
  }

  /* ---------------- Inner Classes -------------- */

  /**
   * ConcurrentHashMap list entry. Note that this is never exported
   * out as a user-visible Map.Entry.
   *
   * Because the value field is volatile, not final, it is legal wrt
   * the Java Memory Model for an unsynchronized reader to see null
   * instead of initial value when read via a data race.  Although a
   * reordering leading to this is not likely to ever actually
   * occur, the Segment.readValueUnderLock method is used as a
   * backup in case a null (pre-initialized) value is ever seen in
   * an unsynchronized access method.
   */
  static final class HashEntry<K,V> {
    final K key;
    final int hash;
    volatile V value;
    final HashEntry<K,V> next;

    HashEntry(K key, int hash, HashEntry<K,V> next, V value) {
      this.key = key;
      this.hash = hash;
      this.next = next;
      this.value = value;
    }
  }

  /**
   * Segments are specialized versions of hash tables.  This
   * subclasses from ReentrantLock opportunistically, just to
   * simplify some locking and avoid separate construction.
   **/
  static final class Segment<K,V> extends ReentrantLock implements Serializable {
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

    private static final long serialVersionUID = 2249069246763182397L;

    /**
     * The number of elements in this segment's region.
     **/
    transient volatile int count;

    /**
     * Number of updates that alter the size of the table. This is
     * used during bulk-read methods to make sure they see a
     * consistent snapshot: If modCounts change during a traversal
     * of segments computing size or checking containsValue, then
     * we might have an inconsistent view of state so (usually)
     * must retry.
     */
    transient int modCount;

    /**
     * The table is rehashed when its size exceeds this threshold.
     * (The value of this field is always (int)(capacity *
     * loadFactor).)
     */
    transient int threshold;

    /**
     * The per-segment table. Declared as a raw type, casted
     * to HashEntry<K,V> on each use.
     */
    transient volatile HashEntry[] table;

    /**
     * The load factor for the hash table.  Even though this value
     * is same for all segments, it is replicated to avoid needing
     * links to outer object.
     * @serial
     */
    final float loadFactor;
    private final TObjectHashingStrategy<K> myHashingStrategy;

    Segment(int initialCapacity, float lf, TObjectHashingStrategy<K> hashingStrategy) {
      loadFactor = lf;
      myHashingStrategy = hashingStrategy;
      setTable(new HashEntry[initialCapacity]);
    }

    /**
     * Set table to new HashEntry array.
     * Call only while holding lock or in constructor.
     **/
    void setTable(HashEntry[] newTable) {
      threshold = (int)(newTable.length * loadFactor);
      table = newTable;
    }

    /**
     * Return properly casted first entry of bin for given hash
     */
    HashEntry<K,V> getFirst(int hash) {
      HashEntry[] tab = table;
      return (HashEntry<K,V>) tab[hash & (tab.length - 1)];
    }

    /**
     * Read value field of an entry under lock. Called if value
     * field ever appears to be null. This is possible only if a
     * compiler happens to reorder a HashEntry initialization with
     * its table assignment, which is legal under memory model
     * but is not known to ever occur.
     */
    V readValueUnderLock(HashEntry<K,V> e) {
      lock();
      try {
        return e.value;
      } finally {
        unlock();
      }
    }

        /* Specialized implementations of map methods */

    V get(K key, int hash) {
      if (count != 0) { // read-volatile
        HashEntry<K,V> e = getFirst(hash);
        while (e != null) {
          if (e.hash == hash && myHashingStrategy.equals(key,e.key)) {
            V v = e.value;
            if (v != null)
              return v;
            return readValueUnderLock(e); // recheck
          }
          e = e.next;
        }
      }
      return null;
    }

    boolean containsKey(K key, int hash) {
      if (count != 0) { // read-volatile
        HashEntry<K,V> e = getFirst(hash);
        while (e != null) {
          if (e.hash == hash && myHashingStrategy.equals(key,e.key))
            return true;
          e = e.next;
        }
      }
      return false;
    }

    boolean containsValue(Object value) {
      if (count != 0) { // read-volatile
        HashEntry[] tab = table;
        int len = tab.length;
        for (int i = 0 ; i < len; i++) {
          for (HashEntry<K,V> e = (HashEntry<K,V>)tab[i];
               e != null ;
               e = e.next) {
            V v = e.value;
            if (v == null) // recheck
              v = readValueUnderLock(e);
            if (value.equals(v))
              return true;
          }
        }
      }
      return false;
    }

    boolean replace(K key, int hash, V oldValue, V newValue) {
      lock();
      try {
        HashEntry<K,V> e = getFirst(hash);
        while (e != null && (e.hash != hash || !myHashingStrategy.equals(key,e.key)))
          e = e.next;

        boolean replaced = false;
        if (e != null && oldValue.equals(e.value)) {
          replaced = true;
          e.value = newValue;
        }
        return replaced;
      } finally {
        unlock();
      }
    }

    V replace(K key, int hash, V newValue) {
      lock();
      try {
        HashEntry<K,V> e = getFirst(hash);
        while (e != null && (e.hash != hash || !myHashingStrategy.equals(key,e.key)))
          e = e.next;

        V oldValue = null;
        if (e != null) {
          oldValue = e.value;
          e.value = newValue;
        }
        return oldValue;
      } finally {
        unlock();
      }
    }


    V put(K key, int hash, V value, boolean onlyIfAbsent) {
      lock();
      try {
        int c = count;
        if (c++ > threshold) // ensure capacity
          rehash();
        HashEntry[] tab = table;
        int index = hash & (tab.length - 1);
        HashEntry<K,V> first = (HashEntry<K,V>) tab[index];
        HashEntry<K,V> e = first;
        while (e != null && (e.hash != hash || !myHashingStrategy.equals(key,e.key)))
          e = e.next;

        V oldValue;
        if (e != null) {
          oldValue = e.value;
          if (!onlyIfAbsent)
            e.value = value;
        }
        else {
          oldValue = null;
          ++modCount;
          tab[index] = new HashEntry<K,V>(key, hash, first, value);
          count = c; // write-volatile
        }
        return oldValue;
      } finally {
        unlock();
      }
    }

    void rehash() {
      HashEntry[] oldTable = table;
      int oldCapacity = oldTable.length;
      if (oldCapacity >= MAXIMUM_CAPACITY)
        return;

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
      threshold = (int)(newTable.length * loadFactor);
      int sizeMask = newTable.length - 1;
      for (int i = 0; i < oldCapacity ; i++) {
        // We need to guarantee that any existing reads of old Map can
        //  proceed. So we cannot yet null out each bin.
        HashEntry<K,V> e = (HashEntry<K,V>)oldTable[i];

        if (e != null) {
          HashEntry<K,V> next = e.next;
          int idx = e.hash & sizeMask;

          //  Single node on list
          if (next == null)
            newTable[idx] = e;

          else {
            // Reuse trailing consecutive sequence at same slot
            HashEntry<K,V> lastRun = e;
            int lastIdx = idx;
            for (HashEntry<K,V> last = next;
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
            for (HashEntry<K,V> p = e; p != lastRun; p = p.next) {
              int k = p.hash & sizeMask;
              HashEntry<K,V> n = (HashEntry<K,V>)newTable[k];
              newTable[k] = new HashEntry<K,V>(p.key, p.hash,
                                               n, p.value);
            }
          }
        }
      }
      table = newTable;
    }

    /**
     * Remove; match on key only if value null, else match both.
     */
    V remove(K key, int hash, Object value) {
      lock();
      try {
        int c = count - 1;
        HashEntry[] tab = table;
        int index = hash & (tab.length - 1);
        HashEntry<K,V> first = (HashEntry<K,V>)tab[index];
        HashEntry<K,V> e = first;
        while (e != null && (e.hash != hash || !myHashingStrategy.equals(key,e.key)))
          e = e.next;

        V oldValue = null;
        if (e != null) {
          V v = e.value;
          if (value == null || value.equals(v)) {
            oldValue = v;
            // All entries following removed node can stay
            // in list, but all preceding ones need to be
            // cloned.
            ++modCount;
            HashEntry<K,V> newFirst = e.next;
            for (HashEntry<K,V> p = first; p != e; p = p.next)
              newFirst = new HashEntry<K,V>(p.key, p.hash,
                                            newFirst, p.value);
            tab[index] = newFirst;
            count = c; // write-volatile
          }
        }
        return oldValue;
      } finally {
        unlock();
      }
    }

    void clear() {
      if (count != 0) {
        lock();
        try {
          HashEntry[] tab = table;
          for (int i = 0; i < tab.length ; i++)
            tab[i] = null;
          ++modCount;
          count = 0; // write-volatile
        } finally {
          unlock();
        }
      }
    }
  }



    /* ---------------- Public operations -------------- */

  public ConcurrentHashMap(TObjectHashingStrategy<K> hashingStrategy) {
    this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS,hashingStrategy);
  }

  /**
   * Creates a new, empty map with the specified initial
   * capacity, load factor, and concurrency level.
   *
   * @param initialCapacity the initial capacity. The implementation
   * performs internal sizing to accommodate this many elements.
   * @param loadFactor  the load factor threshold, used to control resizing.
   * Resizing may be performed when the average number of elements per
   * bin exceeds this threshold.
   * @param concurrencyLevel the estimated number of concurrently
   * updating threads. The implementation performs internal sizing
   * to try to accommodate this many threads.
   * @throws IllegalArgumentException if the initial capacity is
   * negative or the load factor or concurrencyLevel are
   * nonpositive.
   */
  public ConcurrentHashMap(int initialCapacity,
                           float loadFactor, int concurrencyLevel) {
    this(initialCapacity,loadFactor, concurrencyLevel,null);
  }
  public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel, TObjectHashingStrategy<K> hashingStrategy) {
    if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
      throw new IllegalArgumentException();

    if (concurrencyLevel > MAX_SEGMENTS)
      concurrencyLevel = MAX_SEGMENTS;

    // Find power-of-two sizes best matching arguments
    int sshift = 0;
    int ssize = 1;
    while (ssize < concurrencyLevel) {
      ++sshift;
      ssize <<= 1;
    }
    segmentShift = 12; // the middle of the hash is much more random that its HSB. Especially when we use TObjectHashingStrategy.CANONICAl as a hash provider
    segmentMask = ssize - 1;
    segments = new Segment[ssize];

    if (initialCapacity > MAXIMUM_CAPACITY)
      initialCapacity = MAXIMUM_CAPACITY;
    int c = initialCapacity / ssize;
    if (c * ssize < initialCapacity)
      ++c;
    int cap = 1;
    while (cap < c)
      cap <<= 1;

    hashingStrategy = hashingStrategy == null ? this : hashingStrategy;
    for (int i = 0; i < segments.length; ++i)
      segments[i] = new Segment<K,V>(cap, loadFactor,hashingStrategy);
    myHashingStrategy = hashingStrategy;
  }

  /**
   * Creates a new, empty map with the specified initial
   * capacity, and with default load factor and concurrencyLevel.
   *
   * @param initialCapacity the initial capacity. The implementation
   * performs internal sizing to accommodate this many elements.
   * @throws IllegalArgumentException if the initial capacity of
   * elements is negative.
   */
  public ConcurrentHashMap(int initialCapacity) {
    this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
  }

  /**
   * Creates a new, empty map with a default initial capacity,
   * load factor, and concurrencyLevel.
   */
  public ConcurrentHashMap() {
    this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
  }

  /**
   * Creates a new map with the same mappings as the given map.  The
   * map is created with a capacity of twice the number of mappings in
   * the given map or 11 (whichever is greater), and a default load factor
   * and concurrencyLevel.
   * @param t the map
   */
  public ConcurrentHashMap(Map<? extends K, ? extends V> t) {
    this(Math.max((int) (t.size() / DEFAULT_LOAD_FACTOR) + 1,
                  11),
         DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
    putAll(t);
  }

  // inherit Map javadoc
  @Override
  public boolean isEmpty() {
    final Segment[] segments = this.segments;
        /*
         * We keep track of per-segment modCounts to avoid ABA
         * problems in which an element in one segment was added and
         * in another removed during traversal, in which case the
         * table was never actually empty at any point. Note the
         * similar use of modCounts in the size() and containsValue()
         * methods, which are the only other methods also susceptible
         * to ABA problems.
         */
    int[] mc = new int[segments.length];
    int mcsum = 0;
    for (int i = 0; i < segments.length; ++i) {
      if (segments[i].count != 0)
        return false;
      else
        mcsum += mc[i] = segments[i].modCount;
    }
    // If mcsum happens to be zero, then we know we got a snapshot
    // before any modifications at all were made.  This is
    // probably common enough to bother tracking.
    if (mcsum != 0) {
      for (int i = 0; i < segments.length; ++i) {
        if (segments[i].count != 0 ||
            mc[i] != segments[i].modCount)
          return false;
      }
    }
    return true;
  }

  // inherit Map javadoc
  @Override
  public int size() {
    final Segment[] segments = this.segments;
    long sum = 0;
    long check = 0;
    int[] mc = new int[segments.length];
    // Try a few times to get accurate count. On failure due to
    // continuous async changes in table, resort to locking.
    for (int k = 0; k < RETRIES_BEFORE_LOCK; ++k) {
      check = 0;
      sum = 0;
      int mcsum = 0;
      for (int i = 0; i < segments.length; ++i) {
        sum += segments[i].count;
        mcsum += mc[i] = segments[i].modCount;
      }
      if (mcsum != 0) {
        for (int i = 0; i < segments.length; ++i) {
          check += segments[i].count;
          if (mc[i] != segments[i].modCount) {
            check = -1; // force retry
            break;
          }
        }
      }
      if (check == sum)
        break;
    }
    if (check != sum) { // Resort to locking all segments
      sum = 0;
      for (Segment segment : segments) segment.lock();
      for (Segment segment : segments) sum += segment.count;
      for (Segment segment : segments) segment.unlock();
    }
    return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)sum;
  }


  /**
   * Returns the value to which the specified key is mapped in this table.
   *
   * @param   key   a key in the table.
   * @return  the value to which the key is mapped in this table;
   *          <tt>null</tt> if the key is not mapped to any value in
   *          this table.
   * @throws  NullPointerException  if the key is
   *               <tt>null</tt>.
   */
  @Override
  public V get(Object key) {
    int hash = myHashingStrategy.computeHashCode((K)key); // throws NullPointerException if key null
    return segmentFor(hash).get((K)key, hash);
  }

  /**
   * Tests if the specified object is a key in this table.
   *
   * @param   key   possible key.
   * @return  <tt>true</tt> if and only if the specified object
   *          is a key in this table, as determined by the
   *          <tt>equals</tt> method; <tt>false</tt> otherwise.
   * @throws  NullPointerException  if the key is
   *               <tt>null</tt>.
   */
  @Override
  public boolean containsKey(Object key) {
    int hash = myHashingStrategy.computeHashCode((K)key); // throws NullPointerException if key null
    return segmentFor(hash).containsKey((K)key, hash);
  }

  /**
   * Returns <tt>true</tt> if this map maps one or more keys to the
   * specified value. Note: This method requires a full internal
   * traversal of the hash table, and so is much slower than
   * method <tt>containsKey</tt>.
   *
   * @param value value whose presence in this map is to be tested.
   * @return <tt>true</tt> if this map maps one or more keys to the
   * specified value.
   * @throws  NullPointerException  if the value is <tt>null</tt>.
   */
  @Override
  public boolean containsValue(@NotNull Object value) {

    // See explanation of modCount use above

    final Segment[] segments = this.segments;
    int[] mc = new int[segments.length];

    // Try a few times without locking
    for (int k = 0; k < RETRIES_BEFORE_LOCK; ++k) {
      int sum = 0;
      int mcsum = 0;
      for (int i = 0; i < segments.length; ++i) {
        int c = segments[i].count;
        mcsum += mc[i] = segments[i].modCount;
        if (segments[i].containsValue(value))
          return true;
      }
      boolean cleanSweep = true;
      if (mcsum != 0) {
        for (int i = 0; i < segments.length; ++i) {
          int c = segments[i].count;
          if (mc[i] != segments[i].modCount) {
            cleanSweep = false;
            break;
          }
        }
      }
      if (cleanSweep)
        return false;
    }
    // Resort to locking all segments
    for (int i = 0; i < segments.length; ++i)
      segments[i].lock();
    boolean found = false;
    try {
      for (int i = 0; i < segments.length; ++i) {
        if (segments[i].containsValue(value)) {
          found = true;
          break;
        }
      }
    } finally {
      for (int i = 0; i < segments.length; ++i)
        segments[i].unlock();
    }
    return found;
  }

  /**
   * Legacy method testing if some key maps into the specified value
   * in this table.  This method is identical in functionality to
   * {@link #containsValue}, and  exists solely to ensure
   * full compatibility with class {@link java.util.Hashtable},
   * which supported this method prior to introduction of the
   * Java Collections framework.

   * @param      value   a value to search for.
   * @return     <tt>true</tt> if and only if some key maps to the
   *             <tt>value</tt> argument in this table as
   *             determined by the <tt>equals</tt> method;
   *             <tt>false</tt> otherwise.
   * @throws  NullPointerException  if the value is <tt>null</tt>.
   */
  public boolean contains(Object value) {
    return containsValue(value);
  }

  /**
   * Maps the specified <tt>key</tt> to the specified
   * <tt>value</tt> in this table. Neither the key nor the
   * value can be <tt>null</tt>.
   *
   * <p> The value can be retrieved by calling the <tt>get</tt> method
   * with a key that is equal to the original key.
   *
   * @param      key     the table key.
   * @param      value   the value.
   * @return     the previous value of the specified key in this table,
   *             or <tt>null</tt> if it did not have one.
   * @throws  NullPointerException  if the key or value is
   *               <tt>null</tt>.
   */
  @Override
  public V put(K key, @NotNull V value) {
    int hash = myHashingStrategy.computeHashCode(key);
    return segmentFor(hash).put(key, hash, value, false);
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
   * @param key key with which the specified value is to be associated.
   * @param value value to be associated with the specified key.
   * @return previous value associated with specified key, or <tt>null</tt>
   *         if there was no mapping for key.
   * @throws NullPointerException if the specified key or value is
   *            <tt>null</tt>.
   */
  @Override
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    int hash = myHashingStrategy.computeHashCode(key);
    return segmentFor(hash).put(key, hash, value, true);
  }


  /**
   * Copies all of the mappings from the specified map to this one.
   *
   * These mappings replace any mappings that this map had for any of the
   * keys currently in the specified Map.
   *
   * @param t Mappings to be stored in this map.
   */
  @Override
  public void putAll(Map<? extends K, ? extends V> t) {
    for (Iterator<? extends Entry<? extends K, ? extends V>> it = (Iterator<? extends Entry<? extends K, ? extends V>>) t.entrySet().iterator(); it.hasNext(); ) {
      Entry<? extends K, ? extends V> e = it.next();
      put(e.getKey(), e.getValue());
    }
  }

  /**
   * Removes the key (and its corresponding value) from this
   * table. This method does nothing if the key is not in the table.
   *
   * @param   key   the key that needs to be removed.
   * @return  the value to which the key had been mapped in this table,
   *          or <tt>null</tt> if the key did not have a mapping.
   * @throws  NullPointerException  if the key is
   *               <tt>null</tt>.
   */
  @Override
  public V remove(Object key) {
    int hash = myHashingStrategy.computeHashCode((K)key);
    return segmentFor(hash).remove((K)key, hash, null);
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
   * @param key key with which the specified value is associated.
   * @param value value associated with the specified key.
   * @return true if the value was removed
   * @throws NullPointerException if the specified key is
   *            <tt>null</tt>.
   */
  @Override
  public boolean remove(@NotNull Object key, Object value) {
    int hash = myHashingStrategy.computeHashCode((K)key);
    return remove((K)key, hash, value);
  }

  public boolean remove(@NotNull K key, int hash, Object value) {
    return segmentFor(hash).remove(key, hash, value) != null;
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
   * @param key key with which the specified value is associated.
   * @param oldValue value expected to be associated with the specified key.
   * @param newValue value to be associated with the specified key.
   * @return true if the value was replaced
   * @throws NullPointerException if the specified key or values are
   * <tt>null</tt>.
   */
  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    int hash = myHashingStrategy.computeHashCode(key);
    return segmentFor(hash).replace(key, hash, oldValue, newValue);
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
   * @param key key with which the specified value is associated.
   * @param value value to be associated with the specified key.
   * @return previous value associated with specified key, or <tt>null</tt>
   *         if there was no mapping for key.
   * @throws NullPointerException if the specified key or value is
   *            <tt>null</tt>.
   */
  @Override
  public V replace(@NotNull K key, @NotNull V value) {
    int hash = myHashingStrategy.computeHashCode(key);
    return segmentFor(hash).replace(key, hash, value);
  }


  /**
   * Removes all mappings from this map.
   */
  @Override
  public void clear() {
    for (Segment segment : segments) segment.clear();
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
  @Override
  public Set<K> keySet() {
    Set<K> ks = keySet;
    return (ks != null) ? ks : (keySet = new KeySet());
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
  @Override
  public Collection<V> values() {
    Collection<V> vs = values;
    return (vs != null) ? vs : (values = new Values());
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
  @Override
  public Set<Entry<K,V>> entrySet() {
    Set<Entry<K,V>> es = entrySet;
    return (es != null) ? es : (entrySet = (Set<Entry<K,V>>) (Set) new EntrySet());
  }


  /**
   * Returns an enumeration of the keys in this table.
   *
   * @return  an enumeration of the keys in this table.
   * @see     #keySet
   */
  public Enumeration<K> keys() {
    return new KeyIterator();
  }

  /**
   * Returns an enumeration of the values in this table.
   *
   * @return  an enumeration of the values in this table.
   * @see     #values
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
      nextSegmentIndex = segments.length - 1;
      nextTableIndex = -1;
      advance();
    }

    public boolean hasMoreElements() { return hasNext(); }

    final void advance() {
      if (nextEntry != null && (nextEntry = nextEntry.next) != null)
        return;

      while (nextTableIndex >= 0) {
        if ( (nextEntry = (HashEntry<K,V>)currentTable[nextTableIndex--]) != null)
          return;
      }

      while (nextSegmentIndex >= 0) {
        Segment seg = (Segment)segments[nextSegmentIndex--];
        if (seg.count != 0) {
          currentTable = seg.table;
          for (int j = currentTable.length - 1; j >= 0; --j) {
            if ( (nextEntry = (HashEntry<K,V>)currentTable[j]) != null) {
              nextTableIndex = j - 1;
              return;
            }
          }
        }
      }
    }

    public boolean hasNext() { return nextEntry != null; }

    HashEntry<K,V> nextEntry() {
      if (nextEntry == null)
        throw new NoSuchElementException();
      lastReturned = nextEntry;
      advance();
      return lastReturned;
    }

    public void remove() {
      if (lastReturned == null)
        throw new IllegalStateException();
      ConcurrentHashMap.this.remove(lastReturned.key);
      lastReturned = null;
    }
  }

  final class KeyIterator extends HashIterator implements Iterator<K>, Enumeration<K> {
    @Override
    public K next() { return super.nextEntry().key; }
    @Override
    public K nextElement() { return super.nextEntry().key; }
  }

  final class ValueIterator extends HashIterator implements Iterator<V>, Enumeration<V> {
    @Override
    public V next() { return super.nextEntry().value; }
    @Override
    public V nextElement() { return super.nextEntry().value; }
  }



  /**
   * Entry iterator. Exported Entry objects must write-through
   * changes in setValue, even if the nodes have been cloned. So we
   * cannot return internal HashEntry objects. Instead, the iterator
   * itself acts as a forwarding pseudo-entry.
   */
  final class EntryIterator extends HashIterator implements Entry<K,V>, Iterator<Entry<K,V>> {
    @Override
    public Entry<K,V> next() {
      nextEntry();
      return this;
    }

    @Override
    public K getKey() {
      if (lastReturned == null)
        throw new IllegalStateException("Entry was removed");
      return lastReturned.key;
    }

    @Override
    public V getValue() {
      if (lastReturned == null)
        throw new IllegalStateException("Entry was removed");
      return get(lastReturned.key);
    }

    @Override
    public V setValue(V value) {
      if (lastReturned == null)
        throw new IllegalStateException("Entry was removed");
      return put(lastReturned.key, value);
    }

    public boolean equals(Object o) {
      // If not acting as entry, just use default.
      if (lastReturned == null)
        return super.equals(o);
      if (!(o instanceof Entry))
        return false;
      Entry e = (Entry)o;
      K o1 = getKey();
      K o2 = (K)e.getKey();
      return (o1 == null ? o2 == null : myHashingStrategy.equals(o1,o2)) && eq(getValue(), e.getValue());
    }

    public int hashCode() {
      // If not acting as entry, just use default.
      if (lastReturned == null)
        return super.hashCode();

      Object k = getKey();
      Object v = getValue();
      return ((k == null) ? 0 : k.hashCode()) ^
             ((v == null) ? 0 : v.hashCode());
    }

    public String toString() {
      // If not acting as entry, just use default.
      if (lastReturned == null)
        return super.toString();
      else
        return getKey() + "=" + getValue();
    }

    boolean eq(Object o1, Object o2) {
      return (o1 == null ? o2 == null : o1.equals(o2));
    }

  }

  final class KeySet extends AbstractSet<K> {
    @Override
    public Iterator<K> iterator() {
      return new KeyIterator();
    }
    @Override
    public int size() {
      return ConcurrentHashMap.this.size();
    }
    @Override
    public boolean contains(Object o) {
      return containsKey(o);
    }
    @Override
    public boolean remove(Object o) {
      return ConcurrentHashMap.this.remove(o) != null;
    }
    @Override
    public void clear() {
      ConcurrentHashMap.this.clear();
    }
    @Override
    public Object[] toArray() {
      Collection<K> c = new ArrayList<K>();
      for (Iterator<K> i = iterator(); i.hasNext(); )
        c.add(i.next());
      return c.toArray();
    }
    @Override
    public <T> T[] toArray(T[] a) {
      Collection<K> c = new ArrayList<K>();
      for (Iterator<K> i = iterator(); i.hasNext(); )
        c.add(i.next());
      return c.toArray(a);
    }
  }

  final class Values extends AbstractCollection<V> {
    @Override
    public Iterator<V> iterator() {
      return new ValueIterator();
    }
    @Override
    public int size() {
      return ConcurrentHashMap.this.size();
    }
    @Override
    public boolean contains(Object o) {
      return containsValue(o);
    }
    @Override
    public void clear() {
      ConcurrentHashMap.this.clear();
    }
    @Override
    public Object[] toArray() {
      Collection<V> c = new ArrayList<V>();
      for (Iterator<V> i = iterator(); i.hasNext(); )
        c.add(i.next());
      return c.toArray();
    }
    @Override
    public <T> T[] toArray(T[] a) {
      Collection<V> c = new ArrayList<V>();
      for (Iterator<V> i = iterator(); i.hasNext(); )
        c.add(i.next());
      return c.toArray(a);
    }
  }

  final class EntrySet extends AbstractSet<Entry<K,V>> {
    @Override
    public Iterator<Entry<K,V>> iterator() {
      return new EntryIterator();
    }
    @Override
    public boolean contains(Object o) {
      if (!(o instanceof Entry))
        return false;
      Entry<K,V> e = (Entry<K,V>)o;
      V v = get(e.getKey());
      return v != null && v.equals(e.getValue());
    }
    @Override
    public boolean remove(Object o) {
      if (!(o instanceof Entry))
        return false;
      Entry<K,V> e = (Entry<K,V>)o;
      return ConcurrentHashMap.this.remove(e.getKey(), e.getValue());
    }
    @Override
    public int size() {
      return ConcurrentHashMap.this.size();
    }
    @Override
    public void clear() {
      ConcurrentHashMap.this.clear();
    }
    @Override
    public Object[] toArray() {
      // Since we don't ordinarily have distinct Entry objects, we
      // must pack elements using exportable SimpleEntry
      Collection<Entry<K,V>> c = new ArrayList<Entry<K,V>>(size());
      for (Iterator<Entry<K,V>> i = iterator(); i.hasNext(); )
        c.add(new SimpleEntry(i.next()));
      return c.toArray();
    }
    @Override
    public <T> T[] toArray(T[] a) {
      Collection<Entry<K,V>> c = new ArrayList<Entry<K,V>>(size());
      for (Iterator<Entry<K,V>> i = iterator(); i.hasNext(); )
        c.add(new SimpleEntry(i.next()));
      return c.toArray(a);
    }

  }

  /**
   * This duplicates java.util.AbstractMap.SimpleEntry until this class
   * is made accessible.
   */
  final class SimpleEntry implements Entry<K,V> {
    K key;
    V value;

    public SimpleEntry(K key, V value) {
      this.key   = key;
      this.value = value;
    }

    public SimpleEntry(Entry<K,V> e) {
      key = e.getKey();
      value = e.getValue();
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public V setValue(V value) {
      V oldValue = this.value;
      this.value = value;
      return oldValue;
    }

    public boolean equals(Object o) {
      if (!(o instanceof Entry))
        return false;
      Entry e = (Entry)o;
      K o2 = (K)e.getKey();
      return (key == null ? o2 == null : myHashingStrategy.equals(key,o2)) && eq(value, e.getValue());
    }

    public int hashCode() {
      return ((key   == null)   ? 0 :   key.hashCode()) ^
             ((value == null)   ? 0 : value.hashCode());
    }

    public String toString() {
      return key + "=" + value;
    }

    boolean eq(Object o1, Object o2) {
      return (o1 == null ? o2 == null : o1.equals(o2));
    }
  }

    /* ---------------- Serialization Support -------------- */

  /**
   * Save the state of the <tt>ConcurrentHashMap</tt>
   * instance to a stream (i.e.,
   * serialize it).
   * @param s the stream
   * @serialData
   * the key (Object) and value (Object)
   * for each key-value mapping, followed by a null pair.
   * The key-value mappings are emitted in no particular order.
   */
  private void writeObject(java.io.ObjectOutputStream s) throws IOException {
    s.defaultWriteObject();

    for (int k = 0; k < segments.length; ++k) {
      Segment seg = segments[k];
      seg.lock();
      try {
        HashEntry[] tab = seg.table;
        for (int i = 0; i < tab.length; ++i) {
          for (HashEntry<K,V> e = (HashEntry<K,V>)tab[i]; e != null; e = e.next) {
            s.writeObject(e.key);
            s.writeObject(e.value);
          }
        }
      } finally {
        seg.unlock();
      }
    }
    s.writeObject(null);
    s.writeObject(null);
  }

  /**
   * Reconstitute the <tt>ConcurrentHashMap</tt>
   * instance from a stream (i.e.,
   * deserialize it).
   * @param s the stream
   */
  private void readObject(java.io.ObjectInputStream s)
    throws IOException, ClassNotFoundException  {
    s.defaultReadObject();

    // Initialize each segment to be minimally sized, and let grow.
    for (int i = 0; i < segments.length; ++i) {
      segments[i].setTable(new HashEntry[1]);
    }

    // Read the keys and values, and put the mappings in the table
    for (;;) {
      K key = (K) s.readObject();
      V value = (V) s.readObject();
      if (key == null)
        break;
      put(key, value);
    }
  }

  @Override
  public int computeHashCode(final K object) {
    int h = object.hashCode();
    h += ~(h << 9);
    h ^=  (h >>> 14);
    h +=  (h << 4);
    h ^=  (h >>> 10);
    return h;
  }

  @Override
  public boolean equals(final K o1, final K o2) {
    return o1.equals(o2);
  }

  /**
   * @return value if there is no entry in the map, or corresponding value if entry already exists
   */
  public V cacheOrGet(final K key, final V value) {
    return ConcurrencyUtil.cacheOrGet(this, key, value);
  }
}
