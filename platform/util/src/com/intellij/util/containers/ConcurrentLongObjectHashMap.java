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

import com.intellij.util.concurrency.AtomicFieldUpdater;
import gnu.trove.HashFunctions;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Adapted from java.util.concurrent.ConcurrentHashMap to long keys
 * @author Doug Lea
 * @param <V> the type of mapped values
 */

// added hashing strategy argument
// added cacheOrGet convenience method
// Null values are NOT allowed
class ConcurrentLongObjectHashMap<V>  implements ConcurrentLongObjectMap<V> {
  /**
   * The largest possible table capacity.  This value must be
   * exactly 1<<30 to stay within Java array allocation and indexing
   * bounds for power of two table sizes, and is further required
   * because the top two bits of 32bit hash fields are used for
   * control purposes.
   */
  private static final int MAXIMUM_CAPACITY = 1 << 30;

  /**
   * The default initial table capacity.  Must be a power of 2
   * (i.e., at least 1) and at most MAXIMUM_CAPACITY.
   */
  private static final int DEFAULT_CAPACITY = 16;

  /**
   * The largest possible (non-power of two) array size.
   * Needed by toArray and related methods.
   */
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

  /**
   * The bin count threshold for using a tree rather than list for a
   * bin.  Bins are converted to trees when adding an element to a
   * bin with at least this many nodes. The value must be greater
   * than 2, and should be at least 8 to mesh with assumptions in
   * tree removal about conversion back to plain bins upon
   * shrinkage.
   */
  private static final int TREEIFY_THRESHOLD = 8;

  /**
   * The bin count threshold for untreeifying a (split) bin during a
   * resize operation. Should be less than TREEIFY_THRESHOLD, and at
   * most 6 to mesh with shrinkage detection under removal.
   */
  private static final int UNTREEIFY_THRESHOLD = 6;

  /**
   * The smallest table capacity for which bins may be treeified.
   * (Otherwise the table is resized if too many nodes in a bin.)
   * The value should be at least 4 * TREEIFY_THRESHOLD to avoid
   * conflicts between resizing and treeification thresholds.
   */
  private static final int MIN_TREEIFY_CAPACITY = 64;

  /**
   * Minimum number of rebinnings per transfer step. Ranges are
   * subdivided to allow multiple resizer threads.  This value
   * serves as a lower bound to avoid resizers encountering
   * excessive memory contention.  The value should be at least
   * DEFAULT_CAPACITY.
   */
  private static final int MIN_TRANSFER_STRIDE = 16;

  /*
   * Encodings for Node hash fields. See above for explanation.
   */
  private static final int MOVED = -1; // hash for forwarding nodes
  private static final int TREEBIN = -2; // hash for roots of trees

  /**
   * Number of CPUS, to place bounds on some sizings
   */
  private static final int NCPU = Runtime.getRuntime().availableProcessors();

  /* ---------------- Nodes -------------- */

  /**
   * Key-value entry.  This class is never exported out as a
   * user-mutable Map.Entry (i.e., one supporting setValue; see
   * MapEntry below), but can be used for read-only traversals used
   * in bulk tasks.  Subclasses of Node with a negative hash field
   * are special, and contain null keys and values (but are never
   * exported).  Otherwise, keys and vals are never null.
   */
  private static class Node<V> {
    final int hash;
    final long key;
    volatile V val;
    volatile Node<V> next;

    private Node(int hash, long key, V val, Node<V> next) {
      this.hash = hash;
      this.key = key;
      this.val = val;
      this.next = next;
    }

    public final long getKey() {
      return key;
    }

    public final V getValue() {
      return val;
    }

    @Override
    public final int hashCode() {
      return HashFunctions.hash(key) ^ val.hashCode();
    }

    @Override
    public final String toString() {
      return key + "=" + val;
    }

    @Override
    public final boolean equals(Object o) {
      if (!(o instanceof Node)) return false;
      Node e;
      long k = (e = (Node)o).getKey();
      Object u;
      Object v;
      return (v = e.getValue()) != null &&
             (k == key) &&
             (v == (u = val) || v.equals(u));
    }

    /**
     * Virtualized support for map.get(); overridden in subclasses.
     */
    Node<V> find(int h, long k) {
      Node<V> e = this;
      do {
        if (e.hash == h &&
            (e.key == k)) {
          return e;
        }
      }
      while ((e = e.next) != null);
      return null;
    }
  }

  /* ---------------- Static utilities -------------- */

  /**
   * Returns a power of two table size for the given desired capacity.
   * See Hackers Delight, sec 3.2
   */
  private static int tableSizeFor(int c) {
    int n = c - 1;
    n |= n >>> 1;
    n |= n >>> 2;
    n |= n >>> 4;
    n |= n >>> 8;
    n |= n >>> 16;
    return n < 0 ? 1 : n >= MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : n + 1;
  }

  /* ---------------- Table element access -------------- */

  /*
   * Volatile access methods are used for table elements as well as
   * elements of in-progress next table while resizing.  All uses of
   * the tab arguments must be null checked by callers.  All callers
   * also paranoically precheck that tab's length is not zero (or an
   * equivalent check), thus ensuring that any index argument taking
   * the form of a hash value anded with (length - 1) is a valid
   * index.  Note that, to be correct wrt arbitrary concurrency
   * errors by users, these checks must operate on local variables,
   * which accounts for some odd-looking inline assignments below.
   * Note that calls to setTabAt always occur within locked regions,
   * and so in principle require only release ordering, not need
   * full volatile semantics, but are currently coded as volatile
   * writes to be conservative.
   */

  private static <V> Node<V> tabAt(Node<V>[] tab, int i) {
    return (Node<V>)U.getObjectVolatile(tab, ((long)i << ASHIFT) + ABASE);
  }

  private static <V> boolean casTabAt(Node<V>[] tab, int i,
                                 Node<V> c, Node<V> v) {
    return U.compareAndSwapObject(tab, ((long)i << ASHIFT) + ABASE, c, v);
  }

  private static <V> void setTabAt(Node<V>[] tab, int i, Node<V> v) {
    U.putObjectVolatile(tab, ((long)i << ASHIFT) + ABASE, v);
  }

  /* ---------------- Fields -------------- */

  /**
   * The array of bins. Lazily initialized upon first insertion.
   * Size is always a power of two. Accessed directly by iterators.
   */
  private volatile Node<V>[] table;

  /**
   * The next table to use; non-null only while resizing.
   */
  private volatile Node<V>[] nextTable;

  /**
   * Base counter value, used mainly when there is no contention,
   * but also as a fallback during table initialization
   * races. Updated via CAS.
   */
  @SuppressWarnings("UnusedDeclaration")
  private volatile long baseCount;

  /**
   * Table initialization and resizing control.  When negative, the
   * table is being initialized or resized: -1 for initialization,
   * else -(1 + the number of active resizing threads).  Otherwise,
   * when table is null, holds the initial table size to use upon
   * creation, or 0 for default. After initialization, holds the
   * next element count value upon which to resize the table.
   */
  private volatile int sizeCtl;

  /**
   * The next table index (plus one) to split while resizing.
   */
  private volatile int transferIndex;

  /**
   * The least available table index to split while resizing.
   */
  private volatile int transferOrigin;

  /**
   * Spinlock (locked via CAS) used when resizing and/or creating CounterCells.
   */
  private volatile int cellsBusy;

  /**
   * Table of counter cells. When non-null, size is a power of 2.
   */
  private volatile ConcurrentHashMap.CounterCell[] counterCells;

  // views
  private ValuesView<V> values;
  private EntrySetView<V> entrySet;


  /* ---------------- Public operations -------------- */

  /**
   * Creates a new, empty map with the default initial table size (16).
   */
  ConcurrentLongObjectHashMap() {
  }

  /**
   * Creates a new, empty map with an initial table size
   * accommodating the specified number of elements without the need
   * to dynamically resize.
   *
   * @param initialCapacity The implementation performs internal
   *                        sizing to accommodate this many elements.
   * @throws IllegalArgumentException if the initial capacity of
   *                                  elements is negative
   */
  ConcurrentLongObjectHashMap(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException();
    }
    int cap = initialCapacity >= MAXIMUM_CAPACITY >>> 1 ?
              MAXIMUM_CAPACITY :
              tableSizeFor(initialCapacity + (initialCapacity >>> 1) + 1);
    sizeCtl = cap;
  }

  /**
   * Creates a new, empty map with an initial table size based on
   * the given number of elements ({@code initialCapacity}) and
   * initial table density ({@code loadFactor}).
   *
   * @param initialCapacity the initial capacity. The implementation
   *                        performs internal sizing to accommodate this many elements,
   *                        given the specified load factor.
   * @param loadFactor      the load factor (table density) for
   *                        establishing the initial table size
   * @throws IllegalArgumentException if the initial capacity of
   *                                  elements is negative or the load factor is nonpositive
   * @since 1.6
   */
  ConcurrentLongObjectHashMap(int initialCapacity, float loadFactor) {
    this(initialCapacity, loadFactor, 1);
  }


  ConcurrentLongObjectHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0) {
      throw new IllegalArgumentException();
    }
    if (initialCapacity < concurrencyLevel)   // Use at least as many bins
    {
      initialCapacity = concurrencyLevel;   // as estimated threads
    }
    long size = (long)(1.0 + (long)initialCapacity / loadFactor);
    int cap = size >= (long)MAXIMUM_CAPACITY ?
              MAXIMUM_CAPACITY : tableSizeFor((int)size);
    sizeCtl = cap;
  }

  // Original (since JDK1.2) Map methods

  /**
   * {@inheritDoc}
   */
  @Override
  public int size() {
    long n = sumCount();
    return n < 0L ? 0 :
           n > (long)Integer.MAX_VALUE ? Integer.MAX_VALUE :
           (int)n;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEmpty() {
    return sumCount() <= 0L; // ignore transient negative values
  }

  /**
   * Returns the value to which the specified key is mapped,
   * or {@code null} if this map contains no mapping for the key.
   * <p/>
   * <p>More formally, if this map contains a mapping from a key
   * {@code k} to a value {@code v} such that {@code key.equals(k)},
   * then this method returns {@code v}; otherwise it returns
   * {@code null}.  (There can be at most one such mapping.)
   *
   * @throws NullPointerException if the specified key is null
   */
  @Override
  public V get(long key) {
    Node<V>[] tab;
    Node<V> e;
    int n;
    int h = hash(key);
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (e = tabAt(tab, n - 1 & h)) != null) {
      int eh;
      if ((eh = e.hash) == h) {
        if (e.key == key) {
          return e.val;
        }
      }
      else if (eh < 0) {
        Node<V> p;
        return (p = e.find(h, key)) != null ? p.val : null;
      }
      while ((e = e.next) != null) {
        if (e.hash == h &&
            (e.key == key)) {
          return e.val;
        }
      }
    }
    return null;
  }

  /**
   * Tests if the specified object is a key in this table.
   *
   * @param key possible key
   * @return {@code true} if and only if the specified object
   * is a key in this table, as determined by the
   * {@code equals} method; {@code false} otherwise
   * @throws NullPointerException if the specified key is null
   */
  @Override
  public boolean containsKey(long key) {
    return get(key) != null;
  }

  /**
   * Returns {@code true} if this map maps one or more keys to the
   * specified value. Note: This method may require a full traversal
   * of the map, and is much slower than method {@code containsKey}.
   *
   * @param value value whose presence in this map is to be tested
   * @return {@code true} if this map maps one or more keys to the
   * specified value
   * @throws NullPointerException if the specified value is null
   */
  @Override
  public boolean containsValue(@NotNull V value) {
    Node<V>[] t;
    if ((t = table) != null) {
      Traverser<V> it = new Traverser<V>(t, t.length, 0, t.length);
      for (Node<V> p; (p = it.advance()) != null; ) {
        V v;
        if ((v = p.val) == value || v != null && value.equals(v)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Maps the specified key to the specified value in this table.
   * Neither the key nor the value can be null.
   * <p/>
   * <p>The value can be retrieved by calling the {@code get} method
   * with a key that is equal to the original key.
   *
   * @param key   key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @return the previous value associated with {@code key}, or
   * {@code null} if there was no mapping for {@code key}
   * @throws NullPointerException if the specified key or value is null
   */
  @Override
  public V put(long key, @NotNull V value) {
    return putVal(key, value, false);
  }

  /**
   * Implementation for put and putIfAbsent
   */
  private V putVal(long key, @NotNull V value, boolean onlyIfAbsent) {
    int hash = hash(key);
    int binCount = 0;
    for (Node<V>[] tab = table; ; ) {
      Node<V> f;
      int n;
      int i;
      int fh;
      if (tab == null || (n = tab.length) == 0) {
        tab = initTable();
      }
      else if ((f = tabAt(tab, i = n - 1 & hash)) == null) {
        if (casTabAt(tab, i, null,
                     new Node<V>(hash, key, value, null))) {
          break;                   // no lock when adding to empty bin
        }
      }
      else if ((fh = f.hash) == MOVED) {
        tab = helpTransfer(tab, f);
      }
      else {
        V oldVal = null;
        synchronized (f) {
          if (tabAt(tab, i) == f) {
            if (fh >= 0) {
              binCount = 1;
              for (Node<V> e = f; ; ++binCount) {
                if (e.hash == hash &&
                    (e.key == key)) {
                  oldVal = e.val;
                  if (!onlyIfAbsent) {
                    e.val = value;
                  }
                  break;
                }
                Node<V> pred = e;
                if ((e = e.next) == null) {
                  pred.next = new Node<V>(hash, key,
                                             value, null);
                  break;
                }
              }
            }
            else if (f instanceof TreeBin) {
              binCount = 2;
              Node<V> p;
              if ((p = ((TreeBin<V>)f).putTreeVal(hash, key,
                                                     value)) != null) {
                oldVal = p.val;
                if (!onlyIfAbsent) {
                  p.val = value;
                }
              }
            }
          }
        }
        if (binCount != 0) {
          if (binCount >= TREEIFY_THRESHOLD) {
            treeifyBin(tab, i);
          }
          if (oldVal != null) {
            return oldVal;
          }
          break;
        }
      }
    }
    addCount(1L, binCount);
    return null;
  }

  /**
   * Removes the key (and its corresponding value) from this map.
   * This method does nothing if the key is not in the map.
   *
   * @param key the key that needs to be removed
   * @return the previous value associated with {@code key}, or
   * {@code null} if there was no mapping for {@code key}
   * @throws NullPointerException if the specified key is null
   */
  @Override
  public V remove(long key) {
    return replaceNode(key, null, null);
  }

  /**
   * Implementation for the four public remove/replace methods:
   * Replaces node value with v, conditional upon match of cv if
   * non-null.  If resulting value is null, delete.
   */
  private V replaceNode(long key, V value, Object cv) {
    int hash = hash(key);
    for (Node<V>[] tab = table; ; ) {
      Node<V> f;
      int n;
      int i;
      int fh;
      if (tab == null || (n = tab.length) == 0 ||
          (f = tabAt(tab, i = n - 1 & hash)) == null) {
        break;
      }
      else if ((fh = f.hash) == MOVED) {
        tab = helpTransfer(tab, f);
      }
      else {
        V oldVal = null;
        boolean validated = false;
        synchronized (f) {
          if (tabAt(tab, i) == f) {
            if (fh >= 0) {
              validated = true;
              for (Node<V> e = f, pred = null; ; ) {
                if (e.hash == hash &&
                    (e.key == key)) {
                  V ev = e.val;
                  if (cv == null || cv == ev ||
                      ev != null && cv.equals(ev)) {
                    oldVal = ev;
                    if (value != null) {
                      e.val = value;
                    }
                    else if (pred != null) {
                      pred.next = e.next;
                    }
                    else {
                      setTabAt(tab, i, e.next);
                    }
                  }
                  break;
                }
                pred = e;
                if ((e = e.next) == null) {
                  break;
                }
              }
            }
            else if (f instanceof TreeBin) {
              validated = true;
              TreeBin<V> t = (TreeBin<V>)f;
              TreeNode<V> r;
              TreeNode<V> p;
              if ((r = t.root) != null &&
                  (p = r.findTreeNode(hash, key)) != null) {
                V pv = p.val;
                if (cv == null || cv == pv ||
                    pv != null && cv.equals(pv)) {
                  oldVal = pv;
                  if (value != null) {
                    p.val = value;
                  }
                  else if (t.removeTreeNode(p)) {
                    setTabAt(tab, i, untreeify(t.first));
                  }
                }
              }
            }
          }
        }
        if (validated) {
          if (oldVal != null) {
            if (value == null) {
              addCount(-1L, -1);
            }
            return oldVal;
          }
          break;
        }
      }
    }
    return null;
  }

  /**
   * Removes all of the mappings from this map.
   */
  @Override
  public void clear() {
    long delta = 0L; // negative number of deletions
    int i = 0;
    Node<V>[] tab = table;
    while (tab != null && i < tab.length) {
      int fh;
      Node<V> f = tabAt(tab, i);
      if (f == null) {
        ++i;
      }
      else if ((fh = f.hash) == MOVED) {
        tab = helpTransfer(tab, f);
        i = 0; // restart
      }
      else {
        synchronized (f) {
          if (tabAt(tab, i) == f) {
            Node<V> p = fh >= 0 ? f :
                           f instanceof TreeBin ?
                           ((TreeBin<V>)f).first : null;
            while (p != null) {
              --delta;
              p = p.next;
            }
            setTabAt(tab, i++, null);
          }
        }
      }
    }
    if (delta != 0L) {
      addCount(delta, -1);
    }
  }


  /**
   * Returns a {@link java.util.Collection} view of the values contained in this map.
   * The collection is backed by the map, so changes to the map are
   * reflected in the collection, and vice-versa.  The collection
   * supports element removal, which removes the corresponding
   * mapping from this map, via the {@code Iterator.remove},
   * {@code Collection.remove}, {@code removeAll},
   * {@code retainAll}, and {@code clear} operations.  It does not
   * support the {@code add} or {@code addAll} operations.
   * <p/>
   * <p>The view's {@code iterator} is a "weakly consistent" iterator
   * that will never throw {@link java.util.ConcurrentModificationException},
   * and guarantees to traverse elements as they existed upon
   * construction of the iterator, and may (but is not guaranteed to)
   * reflect any modifications subsequent to construction.
   *
   * @return the collection view
   */
  @Override
  @NotNull
  public Collection<V> values() {
    ValuesView<V> vs;
    return (vs = values) != null ? vs : (values = new ValuesView<V>(this));
  }

  /**
   * Returns a {@link java.util.Set} view of the mappings contained in this map.
   * The set is backed by the map, so changes to the map are
   * reflected in the set, and vice-versa.  The set supports element
   * removal, which removes the corresponding mapping from the map,
   * via the {@code Iterator.remove}, {@code Set.remove},
   * {@code removeAll}, {@code retainAll}, and {@code clear}
   * operations.
   * <p/>
   * <p>The view's {@code iterator} is a "weakly consistent" iterator
   * that will never throw {@link java.util.ConcurrentModificationException},
   * and guarantees to traverse elements as they existed upon
   * construction of the iterator, and may (but is not guaranteed to)
   * reflect any modifications subsequent to construction.
   *
   * @return the set view
   */
  @NotNull
  //@Override
  public Set<LongEntry<V>> entrySet() {
    EntrySetView<V> es;
    return (es = entrySet) != null ? es : (entrySet = new EntrySetView<V>(this));
  }

  @NotNull
  @Override
  public Iterable<LongEntry<V>> entries() {
    return entrySet();
  }

  /**
   * Returns the hash code value for this {@link java.util.Map}, i.e.,
   * the sum of, for each key-value pair in the map,
   * {@code key.hashCode() ^ value.hashCode()}.
   *
   * @return the hash code value for this map
   */
  @Override
  public int hashCode() {
    int h = 0;
    Node<V>[] t;
    if ((t = table) != null) {
      Traverser<V> it = new Traverser<V>(t, t.length, 0, t.length);
      for (Node<V> p; (p = it.advance()) != null; ) {
        h += hash(p.key) ^ p.val.hashCode();
      }
    }
    return h;
  }

  /**
   * Returns a string representation of this map.  The string
   * representation consists of a list of key-value mappings (in no
   * particular order) enclosed in braces ("{@code {}}").  Adjacent
   * mappings are separated by the characters {@code ", "} (comma
   * and space).  Each key-value mapping is rendered as the key
   * followed by an equals sign ("{@code =}") followed by the
   * associated value.
   *
   * @return a string representation of this map
   */
  @Override
  public String toString() {
    Node<V>[] t;
    int f = (t = table) == null ? 0 : t.length;
    Traverser<V> it = new Traverser<V>(t, f, 0, f);
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    Node<V> p;
    if ((p = it.advance()) != null) {
      for (; ; ) {
        long k = p.key;
        V v = p.val;
        sb.append(k);
        sb.append('=');
        sb.append(v == this ? "(this Map)" : v);
        if ((p = it.advance()) == null) {
          break;
        }
        sb.append(',').append(' ');
      }
    }
    return sb.append('}').toString();
  }

  /**
   * Compares the specified object with this map for equality.
   * Returns {@code true} if the given object is a map with the same
   * mappings as this map.  This operation may return misleading
   * results if either map is concurrently modified during execution
   * of this method.
   *
   * @param o object to be compared for equality with this map
   * @return {@code true} if the specified object is equal to this map
   */
  @Override
  public boolean equals(Object o) {
    if (o != this) {
      if (!(o instanceof ConcurrentLongObjectHashMap)) {
        return false;
      }
      ConcurrentLongObjectHashMap<?> m = (ConcurrentLongObjectHashMap<?>)o;
      Node<V>[] t;
      int f = (t = table) == null ? 0 : t.length;
      Traverser<V> it = new Traverser<V>(t, f, 0, f);
      for (Node<V> p; (p = it.advance()) != null; ) {
        V val = p.val;
        Object v = m.get(p.key);
        if (v == null || v != val && !v.equals(val)) {
          return false;
        }
      }
      for (LongEntry<?> e : m.entrySet()) {
        long mk= e.getKey();
        Object mv;
        Object v;
        if (
            (mv = e.getValue()) == null ||
            (v = get(mk)) == null ||
            mv != v && !mv.equals(v)) {
          return false;
        }
      }
    }
    return true;
  }

  // ConcurrentMap methods

  /**
   * {@inheritDoc}
   *
   * @return the previous value associated with the specified key,
   * or {@code null} if there was no mapping for the key
   * @throws NullPointerException if the specified key or value is null
   */
  @Override
  public V putIfAbsent(long key, @NotNull V value) {
    return putVal(key, value, true);
  }

  /**
   * {@inheritDoc}
   *
   * @throws NullPointerException if the specified key is null
   */
  @Override
  public boolean remove(long key, @NotNull V value) {
    return replaceNode(key, null, value) != null;
  }

  /**
   * {@inheritDoc}
   *
   * @throws NullPointerException if any of the arguments are null
   */
  @Override
  public boolean replace(long key, @NotNull V oldValue, @NotNull V newValue) {
    return replaceNode(key, newValue, oldValue) != null;
  }

  /**
   * {@inheritDoc}
   *
   * @return the previous value associated with the specified key,
   * or {@code null} if there was no mapping for the key
   * @throws NullPointerException if the specified key or value is null
   */
  @Override
  public V replace(long key, @NotNull V value) {
    return replaceNode(key, value, null);
  }

  // Overrides of JDK8+ Map extension method defaults


  // Hashtable legacy methods

  /**
   * Returns an enumeration of the keys in this table.
   *
   * @return an enumeration of the keys in this table
   */
  @Override
  @NotNull
  public long[] keys() {
    LongEntry[] entries = entrySet().toArray(new LongEntry[0]);
    long[] result = new long[entries.length];
    for (int i = 0; i < entries.length; i++) {
      LongEntry entry = entries[i];
      result[i] = entry.getKey();
    }
    return result;
  }

  // ConcurrentHashMapV8-only methods

  /**
   * Returns the number of mappings. This method should be used
   * instead of {@link #size} because a ConcurrentHashMapV8 may
   * contain more mappings than can be represented as an int. The
   * value returned is an estimate; the actual count may differ if
   * there are concurrent insertions or removals.
   *
   * @return the number of mappings
   * @since 1.8
   */
  public long mappingCount() {
    long n = sumCount();
    return n < 0L ? 0L : n; // ignore transient negative values
  }


  /* ---------------- Special Nodes -------------- */

  /**
   * A node inserted at head of bins during transfer operations.
   */
  private static final class ForwardingNode<V> extends Node<V> {
    private final Node<V>[] nextTable;

    private ForwardingNode(Node<V>[] tab) {
      super(MOVED, 0, null, null);
      nextTable = tab;
    }

    @Override
    Node<V> find(int h, long k) {
      // loop to avoid arbitrarily deep recursion on forwarding nodes
      outer:
      for (Node<V>[] tab = nextTable; ; ) {
        Node<V> e;
        int n;
        if (tab == null || (n = tab.length) == 0 ||
            (e = tabAt(tab, n - 1 & h)) == null) {
          return null;
        }
        for (; ; ) {
          int eh;
          if ((eh = e.hash) == h &&
              (e.key == k)) {
            return e;
          }
          if (eh < 0) {
            if (e instanceof ForwardingNode) {
              tab = ((ForwardingNode<V>)e).nextTable;
              continue outer;
            }
            else {
              return e.find(h, k);
            }
          }
          if ((e = e.next) == null) {
            return null;
          }
        }
      }
    }
  }

  /* ---------------- Table Initialization and Resizing -------------- */

  /**
   * Initializes table, using the size recorded in sizeCtl.
   */
  private Node<V>[] initTable() {
    Node<V>[] tab;
    while ((tab = table) == null || tab.length == 0) {
      int sc;
      if ((sc = sizeCtl) < 0) {
        Thread.yield(); // lost initialization race; just spin
      }
      else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
        try {
          if ((tab = table) == null || tab.length == 0) {
            int n = sc > 0 ? sc : DEFAULT_CAPACITY;
            @SuppressWarnings({"rawtypes", "unchecked"})
            Node<V>[] nt = (Node<V>[])new Node[n];
            table = tab = nt;
            sc = n - (n >>> 2);
          }
        }
        finally {
          sizeCtl = sc;
        }
        break;
      }
    }
    return tab;
  }

  /**
   * Adds to count, and if table is too small and not already
   * resizing, initiates transfer. If already resizing, helps
   * perform transfer if work is available.  Rechecks occupancy
   * after a transfer to see if another resize is already needed
   * because resizings are lagging additions.
   *
   * @param x     the count to add
   * @param check if <0, don't check resize, if <= 1 only check if uncontended
   */
  private void addCount(long x, int check) {
    ConcurrentHashMap.CounterCell[] as;
    long b;
    long s;
    if ((as = counterCells) != null ||
        !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
      ConcurrentHashMap.CounterHashCode hc;
      ConcurrentHashMap.CounterCell a;
      long v;
      int m;
      boolean uncontended = true;
      if ((hc = threadCounterHashCode.get()) == null ||
          as == null || (m = as.length - 1) < 0 ||
          (a = as[m & hc.code]) == null ||
          !(uncontended =
              U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
        fullAddCount(x, hc, uncontended);
        return;
      }
      if (check <= 1) {
        return;
      }
      s = sumCount();
    }
    if (check >= 0) {
      Node<V>[] tab;
      int sc;
      while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
             tab.length < MAXIMUM_CAPACITY) {
        if (sc < 0) {
          Node<V>[] nt;
          if (sc == -1 || transferIndex <= transferOrigin ||
              (nt = nextTable) == null) {
            break;
          }
          if (U.compareAndSwapInt(this, SIZECTL, sc, sc - 1)) {
            transfer(tab, nt);
          }
        }
        else if (U.compareAndSwapInt(this, SIZECTL, sc, -2)) {
          transfer(tab, null);
        }
        s = sumCount();
      }
    }
  }

  /**
   * Helps transfer if a resize is in progress.
   */
  private Node<V>[] helpTransfer(Node<V>[] tab, Node<V> f) {
    Node<V>[] nextTab;
    if (f instanceof ForwardingNode &&
        (nextTab = ((ForwardingNode<V>)f).nextTable) != null) {
      int sc;
      if (nextTab == nextTable && tab == table &&
          transferIndex > transferOrigin && (sc = sizeCtl) < -1 &&
          U.compareAndSwapInt(this, SIZECTL, sc, sc - 1)) {
        transfer(tab, nextTab);
      }
      return nextTab;
    }
    return table;
  }

  /**
   * Moves and/or copies the nodes in each bin to new table. See
   * above for explanation.
   */
  private void transfer(Node<V>[] tab, Node<V>[] nextTab) {
    int n = tab.length;
    int stride;
    if ((stride = NCPU > 1 ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE) {
      stride = MIN_TRANSFER_STRIDE; // subdivide range
    }
    if (nextTab == null) {            // initiating
      try {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Node<V>[] nt = (Node<V>[])new Node[n << 1];
        nextTab = nt;
      }
      catch (Throwable ex) {      // try to cope with OOME
        sizeCtl = Integer.MAX_VALUE;
        return;
      }
      nextTable = nextTab;
      transferOrigin = n;
      transferIndex = n;
      ForwardingNode<V> rev = new ForwardingNode<V>(tab);
      for (int k = n; k > 0; ) {    // progressively reveal ready slots
        int nextk = k > stride ? k - stride : 0;
        for (int m = nextk; m < k; ++m) {
          nextTab[m] = rev;
        }
        for (int m = n + nextk; m < n + k; ++m) {
          nextTab[m] = rev;
        }
        U.putOrderedInt(this, TRANSFERORIGIN, k = nextk);
      }
    }
    int nextn = nextTab.length;
    ForwardingNode<V> fwd = new ForwardingNode<V>(nextTab);
    boolean advance = true;
    boolean finishing = false; // to ensure sweep before committing nextTab
    for (int i = 0, bound = 0; ; ) {
      while (advance) {
        int nextIndex;
        int nextBound;
        if (--i >= bound || finishing) {
          advance = false;
        }
        else if ((nextIndex = transferIndex) <= transferOrigin) {
          i = -1;
          advance = false;
        }
        else if (U.compareAndSwapInt
          (this, TRANSFERINDEX, nextIndex,
           nextBound = nextIndex > stride ?
                       nextIndex - stride : 0)) {
          bound = nextBound;
          i = nextIndex - 1;
          advance = false;
        }
      }
      int fh;
      Node<V> f;
      if (i < 0 || i >= n || i + n >= nextn) {
        if (finishing) {
          nextTable = null;
          table = nextTab;
          sizeCtl = (n << 1) - (n >>> 1);
          return;
        }
        for (; ; ) {
          int sc;
          if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, ++sc)) {
            if (sc != -1) {
              return;
            }
            finishing = advance = true;
            i = n; // recheck before commit
            break;
          }
        }
      }
      else if ((f = tabAt(tab, i)) == null) {
        if (casTabAt(tab, i, null, fwd)) {
          setTabAt(nextTab, i, null);
          setTabAt(nextTab, i + n, null);
          advance = true;
        }
      }
      else if ((fh = f.hash) == MOVED) {
        advance = true; // already processed
      }
      else {
        synchronized (f) {
          if (tabAt(tab, i) == f) {
            Node<V> ln;
            Node<V> hn;
            if (fh >= 0) {
              int runBit = fh & n;
              Node<V> lastRun = f;
              for (Node<V> p = f.next; p != null; p = p.next) {
                int b = p.hash & n;
                if (b != runBit) {
                  runBit = b;
                  lastRun = p;
                }
              }
              if (runBit == 0) {
                ln = lastRun;
                hn = null;
              }
              else {
                hn = lastRun;
                ln = null;
              }
              for (Node<V> p = f; p != lastRun; p = p.next) {
                int ph = p.hash;
                long pk = p.key;
                V pv = p.val;
                if ((ph & n) == 0) {
                  ln = new Node<V>(ph, pk, pv, ln);
                }
                else {
                  hn = new Node<V>(ph, pk, pv, hn);
                }
              }
              setTabAt(nextTab, i, ln);
              setTabAt(nextTab, i + n, hn);
              setTabAt(tab, i, fwd);
              advance = true;
            }
            else if (f instanceof TreeBin) {
              TreeBin<V> t = (TreeBin<V>)f;
              TreeNode<V> lo = null;
              TreeNode<V> loTail = null;
              TreeNode<V> hi = null;
              TreeNode<V> hiTail = null;
              int lc = 0;
              int hc = 0;
              for (Node<V> e = t.first; e != null; e = e.next) {
                int h = e.hash;
                TreeNode<V> p = new TreeNode<V>
                  (h, e.key, e.val, null, null);
                if ((h & n) == 0) {
                  if ((p.prev = loTail) == null) {
                    lo = p;
                  }
                  else {
                    loTail.next = p;
                  }
                  loTail = p;
                  ++lc;
                }
                else {
                  if ((p.prev = hiTail) == null) {
                    hi = p;
                  }
                  else {
                    hiTail.next = p;
                  }
                  hiTail = p;
                  ++hc;
                }
              }
              ln = lc <= UNTREEIFY_THRESHOLD ? untreeify(lo) :
                   hc != 0 ? new TreeBin<V>(lo) : t;
              hn = hc <= UNTREEIFY_THRESHOLD ? untreeify(hi) :
                   lc != 0 ? new TreeBin<V>(hi) : t;
              setTabAt(nextTab, i, ln);
              setTabAt(nextTab, i + n, hn);
              setTabAt(tab, i, fwd);
              advance = true;
            }
          }
        }
      }
    }
  }

  /* ---------------- Conversion from/to TreeBins -------------- */

  /**
   * Replaces all linked nodes in bin at given index unless table is
   * too small, in which case resizes instead.
   */
  private void treeifyBin(Node<V>[] tab, int index) {
    if (tab != null) {
      Node<V> b;
      if (tab.length < MIN_TREEIFY_CAPACITY) {
        int sc;
        if (tab == table && (sc = sizeCtl) >= 0 &&
            U.compareAndSwapInt(this, SIZECTL, sc, -2)) {
          transfer(tab, null);
        }
      }
      else if ((b = tabAt(tab, index)) != null && b.hash >= 0) {
        synchronized (b) {
          if (tabAt(tab, index) == b) {
            TreeNode<V> hd = null;
            TreeNode<V> tl = null;
            for (Node<V> e = b; e != null; e = e.next) {
              TreeNode<V> p =
                new TreeNode<V>(e.hash, e.key, e.val,
                                   null, null);
              if ((p.prev = tl) == null) {
                hd = p;
              }
              else {
                tl.next = p;
              }
              tl = p;
            }
            setTabAt(tab, index, new TreeBin<V>(hd));
          }
        }
      }
    }
  }

  /**
   * Returns a list on non-TreeNodes replacing those in given list.
   */
  private static <V> Node<V> untreeify(Node<V> b) {
    Node<V> hd = null;
    Node<V> tl = null;
    for (Node<V> q = b; q != null; q = q.next) {
      Node<V> p = new Node<V>(q.hash, q.key, q.val, null);
      if (tl == null) {
        hd = p;
      }
      else {
        tl.next = p;
      }
      tl = p;
    }
    return hd;
  }

  /* ---------------- TreeNodes -------------- */

  /**
   * Nodes for use in TreeBins
   */
  private static final class TreeNode<V> extends Node<V> {
    private TreeNode<V> parent;  // red-black tree links
    private TreeNode<V> left;
    private TreeNode<V> right;
    private TreeNode<V> prev;    // needed to unlink next upon deletion
    private boolean red;

    private TreeNode(int hash, long key, V val, Node<V> next,
             TreeNode<V> parent) {
      super(hash, key, val, next);
      this.parent = parent;
    }

    @Override
    Node<V> find(int h, long k) {
      return findTreeNode(h, k);
    }

    /**
     * Returns the TreeNode (or null if not found) for the given key
     * starting at given root.
     */
    private TreeNode<V> findTreeNode(int h, long k) {
      TreeNode<V> p = this;
      do {
        int ph;
        TreeNode<V> q;
        TreeNode<V> pl = p.left;
        TreeNode<V> pr = p.right;
        if ((ph = p.hash) > h) {
          p = pl;
        }
        else if (ph < h) {
          p = pr;
        }
        else if (p.key == k) {
          return p;
        }
        else if (pl == null) {
          p = pr;
        }
        else if (pr == null) {
          p = pl;
        }
        else if ((q = pr.findTreeNode(h, k)) != null) {
          return q;
        }
        else {
          p = pl;
        }
      }
      while (p != null);
      return null;
    }
  }

  /* ---------------- TreeBins -------------- */

  /**
   * TreeNodes used at the heads of bins. TreeBins do not hold user
   * keys or values, but instead point to list of TreeNodes and
   * their root. They also maintain a parasitic read-write lock
   * forcing writers (who hold bin lock) to wait for readers (who do
   * not) to complete before tree restructuring operations.
   */
  private static final class TreeBin<V> extends Node<V> {
    private TreeNode<V> root;
    private volatile TreeNode<V> first;
    private volatile Thread waiter;
    private volatile int lockState;
    // values for lockState
    private static final int WRITER = 1; // set while holding write lock
    private static final int WAITER = 2; // set when waiting for write lock
    private static final int READER = 4; // increment value for setting read lock

    /**
     * Creates bin with initial set of nodes headed by b.
     */
    private TreeBin(TreeNode<V> b) {
      super(TREEBIN, 0, null, null);
      first = b;
      TreeNode<V> r = null;
      for (TreeNode<V> x = b, next; x != null; x = next) {
        next = (TreeNode<V>)x.next;
        x.left = x.right = null;
        if (r == null) {
          x.parent = null;
          x.red = false;
          r = x;
        }
        else {
          int h = x.hash;
          for (TreeNode<V> p = r; ; ) {
            int dir;
            int ph;
            if ((ph = p.hash) > h) {
              dir = -1;
            }
            else if (ph < h) {
              dir = 1;
            }
            else {
              dir = 0;
            }
            TreeNode<V> xp = p;
            if ((p = dir <= 0 ? p.left : p.right) == null) {
              x.parent = xp;
              if (dir <= 0) {
                xp.left = x;
              }
              else {
                xp.right = x;
              }
              r = balanceInsertion(r, x);
              break;
            }
          }
        }
      }
      root = r;
      assert checkInvariants(root);
    }

    /**
     * Acquires write lock for tree restructuring.
     */
    private void lockRoot() {
      if (!U.compareAndSwapInt(this, LOCKSTATE, 0, WRITER)) {
        contendedLock(); // offload to separate method
      }
    }

    /**
     * Releases write lock for tree restructuring.
     */
    private void unlockRoot() {
      lockState = 0;
    }

    /**
     * Possibly blocks awaiting root lock.
     */
    private void contendedLock() {
      boolean waiting = false;
      for (;;) {
        int s;
        if (((s = lockState) & WRITER) == 0) {
          if (U.compareAndSwapInt(this, LOCKSTATE, s, WRITER)) {
            if (waiting) {
              waiter = null;
            }
            return;
          }
        }
        else if ((s & WAITER) == 0) {
          if (U.compareAndSwapInt(this, LOCKSTATE, s, s | WAITER)) {
            waiting = true;
            waiter = Thread.currentThread();
          }
        }
        else if (waiting) {
          LockSupport.park(this);
        }
      }
    }

    /**
     * Returns matching node or null if none. Tries to search
     * using tree comparisons from root, but continues linear
     * search when lock not available.
     */
    @Override
    final Node<V> find(int h, long k) {
      for (Node<V> e = first; e != null; e = e.next) {
        int s;
        if (((s = lockState) & (WAITER | WRITER)) != 0) {
          if (e.hash == h &&
              (e.key == k)) {
            return e;
          }
        }
        else if (U.compareAndSwapInt(this, LOCKSTATE, s,
                                     s + READER)) {
          TreeNode<V> p;
          try {
            TreeNode<V> r;
            p = (r = root) == null ? null :
                r.findTreeNode(h, k);
          }
          finally {
            int ls;
            do {
            }
            while (!U.compareAndSwapInt
              (this, LOCKSTATE,
               ls = lockState, ls - READER));
            Thread w;
            if (ls == (READER | WAITER) && (w = waiter) != null) {
              LockSupport.unpark(w);
            }
          }
          return p;
        }
      }
      return null;
    }

    /**
     * Finds or adds a node.
     *
     * @return null if added
     */
    private TreeNode<V> putTreeVal(int h, long k, V v) {
      boolean searched = false;
      for (TreeNode<V> p = root; ; ) {
        int dir;
        int ph;
        if (p == null) {
          first = root = new TreeNode<V>(h, k, v, null, null);
          break;
        }
        else if ((ph = p.hash) > h) {
          dir = -1;
        }
        else if (ph < h) {
          dir = 1;
        }
        else if (p.key == k) {
          return p;
        }
        else {
          if (!searched) {
            searched = true;
            TreeNode<V> q;
            TreeNode<V> ch;
            if ((ch = p.left) != null &&
                (q = ch.findTreeNode(h, k)) != null ||
                (ch = p.right) != null &&
                (q = ch.findTreeNode(h, k)) != null) {
              return q;
            }
          }
          dir = 0;
        }

        TreeNode<V> xp = p;
        if ((p = dir <= 0 ? p.left : p.right) == null) {
          TreeNode<V> x;
          TreeNode<V> f = first;
          first = x = new TreeNode<V>(h, k, v, f, xp);
          if (f != null) {
            f.prev = x;
          }
          if (dir <= 0) {
            xp.left = x;
          }
          else {
            xp.right = x;
          }
          if (!xp.red) {
            x.red = true;
          }
          else {
            lockRoot();
            try {
              root = balanceInsertion(root, x);
            }
            finally {
              unlockRoot();
            }
          }
          break;
        }
      }
      assert checkInvariants(root);
      return null;
    }

    /**
     * Removes the given node, that must be present before this
     * call.  This is messier than typical red-black deletion code
     * because we cannot swap the contents of an interior node
     * with a leaf successor that is pinned by "next" pointers
     * that are accessible independently of lock. So instead we
     * swap the tree linkages.
     *
     * @return true if now too small, so should be untreeified
     */
    private boolean removeTreeNode(TreeNode<V> p) {
      TreeNode<V> next = (TreeNode<V>)p.next;
      TreeNode<V> pred = p.prev;  // unlink traversal pointers
      if (pred == null) {
        first = next;
      }
      else {
        pred.next = next;
      }
      if (next != null) {
        next.prev = pred;
      }
      if (first == null) {
        root = null;
        return true;
      }
      TreeNode<V> r;
      TreeNode<V> rl;
      if ((r = root) == null || r.right == null || // too small
          (rl = r.left) == null || rl.left == null) {
        return true;
      }
      lockRoot();
      try {
        TreeNode<V> replacement;
        TreeNode<V> pl = p.left;
        TreeNode<V> pr = p.right;
        if (pl != null && pr != null) {
          TreeNode<V> s = pr;
          TreeNode<V> sl;
          while ((sl = s.left) != null) // find successor
          {
            s = sl;
          }
          boolean c = s.red;
          s.red = p.red;
          p.red = c; // swap colors
          TreeNode<V> sr = s.right;
          TreeNode<V> pp = p.parent;
          if (s == pr) { // p was s's direct parent
            p.parent = s;
            s.right = p;
          }
          else {
            TreeNode<V> sp = s.parent;
            if ((p.parent = sp) != null) {
              if (s == sp.left) {
                sp.left = p;
              }
              else {
                sp.right = p;
              }
            }
            if ((s.right = pr) != null) {
              pr.parent = s;
            }
          }
          p.left = null;
          if ((p.right = sr) != null) {
            sr.parent = p;
          }
          if ((s.left = pl) != null) {
            pl.parent = s;
          }
          if ((s.parent = pp) == null) {
            r = s;
          }
          else if (p == pp.left) {
            pp.left = s;
          }
          else {
            pp.right = s;
          }
          if (sr != null) {
            replacement = sr;
          }
          else {
            replacement = p;
          }
        }
        else if (pl != null) {
          replacement = pl;
        }
        else if (pr != null) {
          replacement = pr;
        }
        else {
          replacement = p;
        }
        if (replacement != p) {
          TreeNode<V> pp = replacement.parent = p.parent;
          if (pp == null) {
            r = replacement;
          }
          else if (p == pp.left) {
            pp.left = replacement;
          }
          else {
            pp.right = replacement;
          }
          p.left = p.right = p.parent = null;
        }

        root = p.red ? r : balanceDeletion(r, replacement);

        if (p == replacement) {  // detach pointers
          TreeNode<V> pp;
          if ((pp = p.parent) != null) {
            if (p == pp.left) {
              pp.left = null;
            }
            else if (p == pp.right) {
              pp.right = null;
            }
            p.parent = null;
          }
        }
      }
      finally {
        unlockRoot();
      }
      assert checkInvariants(root);
      return false;
    }

      /* ------------------------------------------------------------ */
    // Red-black tree methods, all adapted from CLR

    private static <V> TreeNode<V> rotateLeft(TreeNode<V> root,
                                            TreeNode<V> p) {
      TreeNode<V> r;
      if (p != null && (r = p.right) != null) {
        TreeNode<V> rl;
        if ((rl = p.right = r.left) != null) {
          rl.parent = p;
        }
        TreeNode<V> pp;
        if ((pp = r.parent = p.parent) == null) {
          (root = r).red = false;
        }
        else if (pp.left == p) {
          pp.left = r;
        }
        else {
          pp.right = r;
        }
        r.left = p;
        p.parent = r;
      }
      return root;
    }

    private static <V> TreeNode<V> rotateRight(TreeNode<V> root,
                                             TreeNode<V> p) {
      TreeNode<V> l;
      if (p != null && (l = p.left) != null) {
        TreeNode<V> lr;
        if ((lr = p.left = l.right) != null) {
          lr.parent = p;
        }
        TreeNode<V> pp;
        if ((pp = l.parent = p.parent) == null) {
          (root = l).red = false;
        }
        else if (pp.right == p) {
          pp.right = l;
        }
        else {
          pp.left = l;
        }
        l.right = p;
        p.parent = l;
      }
      return root;
    }

    private static <V> TreeNode<V> balanceInsertion(TreeNode<V> root,
                                                  TreeNode<V> x) {
      x.red = true;
      for (; ; ) {
        TreeNode<V> xpp;
        TreeNode<V> xp;
        if ((xp = x.parent) == null) {
          x.red = false;
          return x;
        }
        else if (!xp.red || (xpp = xp.parent) == null) {
          return root;
        }
        TreeNode<V> xppl;
        if (xp == (xppl = xpp.left)) {
          TreeNode<V> xppr;
          if ((xppr = xpp.right) != null && xppr.red) {
            xppr.red = false;
            xp.red = false;
            xpp.red = true;
            x = xpp;
          }
          else {
            if (x == xp.right) {
              root = rotateLeft(root, x = xp);
              xpp = (xp = x.parent) == null ? null : xp.parent;
            }
            if (xp != null) {
              xp.red = false;
              if (xpp != null) {
                xpp.red = true;
                root = rotateRight(root, xpp);
              }
            }
          }
        }
        else {
          if (xppl != null && xppl.red) {
            xppl.red = false;
            xp.red = false;
            xpp.red = true;
            x = xpp;
          }
          else {
            if (x == xp.left) {
              root = rotateRight(root, x = xp);
              xpp = (xp = x.parent) == null ? null : xp.parent;
            }
            if (xp != null) {
              xp.red = false;
              if (xpp != null) {
                xpp.red = true;
                root = rotateLeft(root, xpp);
              }
            }
          }
        }
      }
    }

    private static <V> TreeNode<V> balanceDeletion(TreeNode<V> root,
                                                 TreeNode<V> x) {
      for (; ; ) {
        TreeNode<V> xpl;
        TreeNode<V> xp;
        if (x == null || x == root) {
          return root;
        }
        else if ((xp = x.parent) == null) {
          x.red = false;
          return x;
        }
        else if (x.red) {
          x.red = false;
          return root;
        }
        else if ((xpl = xp.left) == x) {
          TreeNode<V> xpr;
          if ((xpr = xp.right) != null && xpr.red) {
            xpr.red = false;
            xp.red = true;
            root = rotateLeft(root, xp);
            xpr = (xp = x.parent) == null ? null : xp.right;
          }
          if (xpr == null) {
            x = xp;
          }
          else {
            TreeNode<V> sl = xpr.left;
            TreeNode<V> sr = xpr.right;
            if ((sr == null || !sr.red) &&
                (sl == null || !sl.red)) {
              xpr.red = true;
              x = xp;
            }
            else {
              if (sr == null || !sr.red) {
                if (sl != null) {
                  sl.red = false;
                }
                xpr.red = true;
                root = rotateRight(root, xpr);
                xpr = (xp = x.parent) == null ?
                      null : xp.right;
              }
              if (xpr != null) {
                xpr.red = xp == null ? false : xp.red;
                if ((sr = xpr.right) != null) {
                  sr.red = false;
                }
              }
              if (xp != null) {
                xp.red = false;
                root = rotateLeft(root, xp);
              }
              x = root;
            }
          }
        }
        else { // symmetric
          if (xpl != null && xpl.red) {
            xpl.red = false;
            xp.red = true;
            root = rotateRight(root, xp);
            xpl = (xp = x.parent) == null ? null : xp.left;
          }
          if (xpl == null) {
            x = xp;
          }
          else {
            TreeNode<V> sl = xpl.left;
            TreeNode<V> sr = xpl.right;
            if ((sl == null || !sl.red) &&
                (sr == null || !sr.red)) {
              xpl.red = true;
              x = xp;
            }
            else {
              if (sl == null || !sl.red) {
                if (sr != null) {
                  sr.red = false;
                }
                xpl.red = true;
                root = rotateLeft(root, xpl);
                xpl = (xp = x.parent) == null ?
                      null : xp.left;
              }
              if (xpl != null) {
                xpl.red = xp == null ? false : xp.red;
                if ((sl = xpl.left) != null) {
                  sl.red = false;
                }
              }
              if (xp != null) {
                xp.red = false;
                root = rotateRight(root, xp);
              }
              x = root;
            }
          }
        }
      }
    }

    /**
     * Recursive invariant check
     */
    private static <V> boolean checkInvariants(TreeNode<V> t) {
      TreeNode<V> tp = t.parent;
      TreeNode<V> tl = t.left;
      TreeNode<V> tr = t.right;
      TreeNode<V> tb = t.prev;
      TreeNode<V> tn = (TreeNode<V>)t.next;
      if (tb != null && tb.next != t) {
        return false;
      }
      if (tn != null && tn.prev != t) {
        return false;
      }
      if (tp != null && t != tp.left && t != tp.right) {
        return false;
      }
      if (tl != null && (tl.parent != t || tl.hash > t.hash)) {
        return false;
      }
      if (tr != null && (tr.parent != t || tr.hash < t.hash)) {
        return false;
      }
      if (t.red && tl != null && tl.red && tr != null && tr.red) {
        return false;
      }
      if (tl != null && !checkInvariants(tl)) {
        return false;
      }
      if (tr != null && !checkInvariants(tr)) {
        return false;
      }
      return true;
    }

    private static final Unsafe U;
    private static final long LOCKSTATE;

    static {
      try {
        U = getUnsafe();
        Class<?> k = TreeBin.class;
        LOCKSTATE = U.objectFieldOffset
          (k.getDeclaredField("lockState"));
      }
      catch (Exception e) {
        throw new Error(e);
      }
    }
  }

  /* ----------------Table Traversal -------------- */

  /**
   * Encapsulates traversal for methods such as containsValue; also
   * serves as a base class for other iterators and spliterators.
   * <p/>
   * Method advance visits once each still-valid node that was
   * reachable upon iterator construction. It might miss some that
   * were added to a bin after the bin was visited, which is OK wrt
   * consistency guarantees. Maintaining this property in the face
   * of possible ongoing resizes requires a fair amount of
   * bookkeeping state that is difficult to optimize away amidst
   * volatile accesses.  Even so, traversal maintains reasonable
   * throughput.
   * <p/>
   * Normally, iteration proceeds bin-by-bin traversing lists.
   * However, if the table has been resized, then all future steps
   * must traverse both the bin at the current index as well as at
   * (index + baseSize); and so on for further resizings. To
   * paranoically cope with potential sharing by users of iterators
   * across threads, iteration terminates if a bounds checks fails
   * for a table read.
   */
  private static class Traverser<V> {
    private Node<V>[] tab;        // current table; updated if resized
    Node<V> next;         // the next entry to use
    private int index;              // index of bin to use next
    private int baseIndex;          // current index of initial table
    private final int baseLimit;          // index bound for initial table
    private final int baseSize;     // initial table size

    private Traverser(Node<V>[] tab, int size, int index, int limit) {
      this.tab = tab;
      baseSize = size;
      baseIndex = this.index = index;
      baseLimit = limit;
      next = null;
    }

    /**
     * Advances if possible, returning next valid node, or null if none.
     */
    final Node<V> advance() {
      Node<V> e;
      if ((e = next) != null) {
        e = e.next;
      }
      for (; ; ) {
        if (e != null) {
          return next = e;
        }
        Node<V>[] t;
        int i;
        int n;
        if (baseIndex >= baseLimit || (t = tab) == null ||
            (n = t.length) <= (i = index) || i < 0) {
          return next = null;
        }
        if ((e = tabAt(t, index)) != null && e.hash < 0) {
          if (e instanceof ForwardingNode) {
            tab = ((ForwardingNode<V>)e).nextTable;
            e = null;
            continue;
          }
          else if (e instanceof TreeBin) {
            e = ((TreeBin<V>)e).first;
          }
          else {
            e = null;
          }
        }
        if ((index += baseSize) >= n) {
          index = ++baseIndex;    // visit upper slots if present
        }
      }
    }
  }

  /**
   * Base of key, value, and entry Iterators. Adds fields to
   * Traverser to support iterator.remove.
   */
  private static class BaseIterator<V> extends Traverser<V> {
    final ConcurrentLongObjectHashMap<V> map;
    Node<V> lastReturned;

    private BaseIterator(Node<V>[] tab, int size, int index, int limit,
                 ConcurrentLongObjectHashMap<V> map) {
      super(tab, size, index, limit);
      this.map = map;
      advance();
    }

    public final boolean hasNext() {
      return next != null;
    }

    public final boolean hasMoreElements() {
      return next != null;
    }

    public final void remove() {
      Node<V> p;
      if ((p = lastReturned) == null) {
        throw new IllegalStateException();
      }
      lastReturned = null;
      map.replaceNode(p.key, null, null);
    }
  }

  private static final class ValueIterator<V> extends BaseIterator<V>
    implements Iterator<V>, Enumeration<V> {
    private ValueIterator(Node<V>[] tab, int index, int size, int limit,
                  ConcurrentLongObjectHashMap<V> map) {
      super(tab, index, size, limit, map);
    }

    @Override
    public final V next() {
      Node<V> p;
      if ((p = next) == null) {
        throw new NoSuchElementException();
      }
      V v = p.val;
      lastReturned = p;
      advance();
      return v;
    }

    @Override
    public final V nextElement() {
      return next();
    }
  }

  private static final class EntryIterator<V> extends BaseIterator<V>
    implements Iterator<LongEntry<V>> {
    private EntryIterator(Node<V>[] tab, int index, int size, int limit,
                  ConcurrentLongObjectHashMap<V> map) {
      super(tab, index, size, limit, map);
    }

    @Override
    public final LongEntry<V> next() {
      Node<V> p;
      if ((p = next) == null) {
        throw new NoSuchElementException();
      }
      long k = p.key;
      V v = p.val;
      lastReturned = p;
      advance();
      return new MapEntry<V>(k, v, map);
    }
  }

  /**
   * Exported Entry for EntryIterator
   */
  private static final class MapEntry<V> implements LongEntry<V> {
    private final long key;
    @NotNull private final V val;       // non-null
    private final ConcurrentLongObjectHashMap<V> map;

    private MapEntry(long key, @NotNull V val, ConcurrentLongObjectHashMap<V> map) {
      this.key = key;
      this.val = val;
      this.map = map;
    }

    @Override
    public long getKey() {
      return key;
    }

    @NotNull
    @Override
    public V getValue() {
      return val;
    }

    @Override
    public int hashCode() {
      return hash(key) ^ val.hashCode();
    }

    @Override
    public String toString() {
      return key + "=" + val;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof LongEntry)) return false;
      LongEntry<?> e = (LongEntry<?>)o;
      long k = (e).getKey();
      Object v = e.getValue();
      V r = map.get(k);
      return
             r != null &&
             (v == val || v.equals(val));
    }

    ///**
    // * Sets our entry's value and writes through to the map. The
    // * value to return is somewhat arbitrary here. Since we do not
    // * necessarily track asynchronous changes, the most recent
    // * "previous" value could be different from what we return (or
    // * could even have been removed, in which case the put will
    // * re-establish). We do not and cannot guarantee more.
    // */
    //public V setValue(@NotNull V value) {
    //  V v = val;
    //  val = value;
    //  map.put(key, value);
    //  return v;
    //}
  }

  /* ----------------Views -------------- */

  /**
   * Base class for views.
   */
  private abstract static class CollectionView<V, E>
    implements Collection<E> {
    final ConcurrentLongObjectHashMap<V> map;

    private CollectionView(@NotNull ConcurrentLongObjectHashMap<V> map) {
      this.map = map;
    }

    /**
     * Returns the map backing this view.
     *
     * @return the map backing this view
     */
    public ConcurrentLongObjectHashMap<V> getMap() {
      return map;
    }

    /**
     * Removes all of the elements from this view, by removing all
     * the mappings from the map backing this view.
     */
    @Override
    public final void clear() {
      map.clear();
    }

    @Override
    public final int size() {
      return map.size();
    }

    @Override
    public final boolean isEmpty() {
      return map.isEmpty();
    }

    // implementations below rely on concrete classes supplying these
    // abstract methods

    /**
     * Returns a "weakly consistent" iterator that will never
     * throw {@link java.util.ConcurrentModificationException}, and
     * guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not
     * guaranteed to) reflect any modifications subsequent to
     * construction.
     */
    @NotNull
    @Override
    public abstract Iterator<E> iterator();

    @Override
    public abstract boolean contains(Object o);

    @Override
    public abstract boolean remove(Object o);

    private static final String oomeMsg = "Required array size too large";

    @NotNull
    @Override
    public final Object[] toArray() {
      long sz = map.mappingCount();
      if (sz > MAX_ARRAY_SIZE) {
        throw new OutOfMemoryError(oomeMsg);
      }
      int n = (int)sz;
      Object[] r = new Object[n];
      int i = 0;
      for (E e : this) {
        if (i == n) {
          if (n >= MAX_ARRAY_SIZE) {
            throw new OutOfMemoryError(oomeMsg);
          }
          if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1) {
            n = MAX_ARRAY_SIZE;
          }
          else {
            n += (n >>> 1) + 1;
          }
          r = Arrays.copyOf(r, n);
        }
        r[i++] = e;
      }
      return i == n ? r : Arrays.copyOf(r, i);
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public final <T> T[] toArray(@NotNull T[] a) {
      long sz = map.mappingCount();
      if (sz > MAX_ARRAY_SIZE) {
        throw new OutOfMemoryError(oomeMsg);
      }
      int m = (int)sz;
      T[] r = a.length >= m ? a :
              (T[])Array
                .newInstance(a.getClass().getComponentType(), m);
      int n = r.length;
      int i = 0;
      for (E e : this) {
        if (i == n) {
          if (n >= MAX_ARRAY_SIZE) {
            throw new OutOfMemoryError(oomeMsg);
          }
          if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1) {
            n = MAX_ARRAY_SIZE;
          }
          else {
            n += (n >>> 1) + 1;
          }
          r = Arrays.copyOf(r, n);
        }
        r[i++] = (T)e;
      }
      if (a == r && i < n) {
        r[i] = null; // null-terminate
        return r;
      }
      return i == n ? r : Arrays.copyOf(r, i);
    }

    /**
     * Returns a string representation of this collection.
     * The string representation consists of the string representations
     * of the collection's elements in the order they are returned by
     * its iterator, enclosed in square brackets ({@code "[]"}).
     * Adjacent elements are separated by the characters {@code ", "}
     * (comma and space).  Elements are converted to strings as by
     * {@link String#valueOf(Object)}.
     *
     * @return a string representation of this collection
     */
    @Override
    public final String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append('[');
      Iterator<E> it = iterator();
      if (it.hasNext()) {
        for (; ; ) {
          Object e = it.next();
          sb.append(e == this ? "(this Collection)" : e);
          if (!it.hasNext()) {
            break;
          }
          sb.append(',').append(' ');
        }
      }
      return sb.append(']').toString();
    }

    @Override
    public final boolean containsAll(@NotNull Collection<?> c) {
      if (c != this) {
        for (Object e : c) {
          if (e == null || !contains(e)) {
            return false;
          }
        }
      }
      return true;
    }

    @Override
    public final boolean removeAll(@NotNull Collection<?> c) {
      boolean modified = false;
      for (Iterator<E> it = iterator(); it.hasNext(); ) {
        if (c.contains(it.next())) {
          it.remove();
          modified = true;
        }
      }
      return modified;
    }

    @Override
    public final boolean retainAll(@NotNull Collection<?> c) {
      boolean modified = false;
      for (Iterator<E> it = iterator(); it.hasNext(); ) {
        if (!c.contains(it.next())) {
          it.remove();
          modified = true;
        }
      }
      return modified;
    }
  }

  /**
   * A view of a ConcurrentHashMapV8 as a {@link java.util.Collection} of
   * values, in which additions are disabled. This class cannot be
   * directly instantiated. See {@link #values()}.
   */
  private static final class ValuesView<V> extends CollectionView<V, V> implements Collection<V> {
    private ValuesView(ConcurrentLongObjectHashMap<V> map) {
      super(map);
    }

    @Override
    public final boolean contains(Object o) {
      return map.containsValue((V)o);
    }

    @Override
    public final boolean remove(Object o) {
      if (o != null) {
        for (Iterator<V> it = iterator(); it.hasNext(); ) {
          if (o.equals(it.next())) {
            it.remove();
            return true;
          }
        }
      }
      return false;
    }

    @NotNull
    @Override
    public final Iterator<V> iterator() {
      ConcurrentLongObjectHashMap<V> m = map;
      Node<V>[] t;
      int f = (t = m.table) == null ? 0 : t.length;
      return new ValueIterator<V>(t, f, 0, f, m);
    }

    @Override
    public final boolean add(V e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final boolean addAll(@NotNull Collection<? extends V> c) {
      throw new UnsupportedOperationException();
    }


  }

  /**
   * A view of a ConcurrentHashMapV8 as a {@link java.util.Set} of (key, value)
   * entries.  This class cannot be directly instantiated. See
   * {@link #entrySet()}.
   */
  private static final class EntrySetView<V> extends CollectionView<V, LongEntry<V>>
    implements Set<LongEntry<V>> {

    private EntrySetView(ConcurrentLongObjectHashMap<V> map) {
      super(map);
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof LongEntry)) return false;
      LongEntry<?> e = (LongEntry<?>)o;
      long k = (e).getKey();
      Object v = e.getValue();
      V r = map.get(k);
      return
             r != null &&
             (v == r || v.equals(r));
    }

    @Override
    public boolean remove(Object o) {
      if (!(o instanceof LongEntry)) return false;
      LongEntry<?> e = (LongEntry<?>)o;
      long k = (e).getKey();
      V v = (V)e.getValue();
      V r = map.get(k);
      return
             r != null &&
             map.remove(k, v);
    }

    /**
     * @return an iterator over the entries of the backing map
     */
    @NotNull
    @Override
    public Iterator<LongEntry<V>> iterator() {
      ConcurrentLongObjectHashMap<V> m = map;
      Node<V>[] t;
      int f = (t = m.table) == null ? 0 : t.length;
      return new EntryIterator<V>(t, f, 0, f, m);
    }

    @Override
    public boolean add(LongEntry<V> e) {
      return map.putVal(e.getKey(), e.getValue(), false) == null;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends LongEntry<V>> c) {
      boolean added = false;
      for (LongEntry<V> e : c) {
        if (add(e)) {
          added = true;
        }
      }
      return added;
    }

    @Override
    public final int hashCode() {
      int h = 0;
      Node<V>[] t;
      if ((t = map.table) != null) {
        Traverser<V> it = new Traverser<V>(t, t.length, 0, t.length);
        for (Node<V> p; (p = it.advance()) != null; ) {
          h += p.hashCode();
        }
      }
      return h;
    }

    @Override
    public final boolean equals(Object o) {
      Set<?> c;
      return o instanceof Set &&
             ((c = (Set<?>)o) == this ||
              containsAll(c) && c.containsAll(this));
    }


  }

  /* ---------------- Counters -------------- */

  // Adapted from LongAdder and Striped64.
  // See their internal docs for explanation.

  /**
   * Generates initial value for per-thread CounterHashCodes.
   */
  private static final AtomicInteger counterHashCodeGenerator = new AtomicInteger();

  /**
   * Increment for counterHashCodeGenerator. See class ThreadLocal
   * for explanation.
   */
  private static final int SEED_INCREMENT = 0x61c88647;

  /**
   * Per-thread counter hash codes. Shared across all instances.
   */
  private static final ThreadLocal<ConcurrentHashMap.CounterHashCode> threadCounterHashCode =
    new ThreadLocal<ConcurrentHashMap.CounterHashCode>();


  private long sumCount() {
    ConcurrentHashMap.CounterCell[] as = counterCells;
    long sum = baseCount;
    if (as != null) {
      for (ConcurrentHashMap.CounterCell a : as) {
        if (a != null) {
          sum += a.value;
        }
      }
    }
    return sum;
  }

  // See LongAdder version for explanation
  private void fullAddCount(long x, ConcurrentHashMap.CounterHashCode hc,
                                  boolean wasUncontended) {
    int h;
    if (hc == null) {
      hc = new ConcurrentHashMap.CounterHashCode();
      int s = counterHashCodeGenerator.addAndGet(SEED_INCREMENT);
      h = hc.code = s == 0 ? 1 : s; // Avoid zero
      threadCounterHashCode.set(hc);
    }
    else {
      h = hc.code;
    }
    for (boolean collide = false;                // True if last slot nonempty
      ; ) {
      ConcurrentHashMap.CounterCell[] as;
      int n;
      long v;
      if ((as = counterCells) != null && (n = as.length) > 0) {
        ConcurrentHashMap.CounterCell a;
        if ((a = as[n - 1 & h]) == null) {
          if (cellsBusy == 0) {            // Try to attach new Cell
            ConcurrentHashMap.CounterCell r = new ConcurrentHashMap.CounterCell(x); // Optimistic create
            if (cellsBusy == 0 &&
                U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
              boolean created = false;
              try {               // Recheck under lock
                ConcurrentHashMap.CounterCell[] rs;
                int m;
                int j;
                if ((rs = counterCells) != null &&
                    (m = rs.length) > 0 &&
                    rs[j = m - 1 & h] == null) {
                  rs[j] = r;
                  created = true;
                }
              }
              finally {
                cellsBusy = 0;
              }
              if (created) {
                break;
              }
              continue;           // Slot is now non-empty
            }
          }
          collide = false;
        }
        else if (!wasUncontended)       // CAS already known to fail
        {
          wasUncontended = true;      // Continue after rehash
        }
        else if (U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x)) {
          break;
        }
        else if (counterCells != as || n >= NCPU) {
          collide = false;            // At max size or stale
        }
        else if (!collide) {
          collide = true;
        }
        else if (cellsBusy == 0 &&
                 U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
          try {
            if (counterCells == as) {// Expand table unless stale
              ConcurrentHashMap.CounterCell[] rs = new ConcurrentHashMap.CounterCell[n << 1];
              for (int i = 0; i < n; ++i) {
                rs[i] = as[i];
              }
              counterCells = rs;
            }
          }
          finally {
            cellsBusy = 0;
          }
          collide = false;
          continue;                   // Retry with expanded table
        }
        h ^= h << 13;                   // Rehash
        h ^= h >>> 17;
        h ^= h << 5;
      }
      else if (cellsBusy == 0 && counterCells == as &&
               U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
        boolean init = false;
        try {                           // Initialize table
          if (counterCells == as) {
            ConcurrentHashMap.CounterCell[] rs = new ConcurrentHashMap.CounterCell[2];
            rs[h & 1] = new ConcurrentHashMap.CounterCell(x);
            counterCells = rs;
            init = true;
          }
        }
        finally {
          cellsBusy = 0;
        }
        if (init) {
          break;
        }
      }
      else if (U.compareAndSwapLong(this, BASECOUNT, v = baseCount, v + x)) {
        break;                          // Fall back on using base
      }
    }
    hc.code = h;                            // Record index for next time
  }

  // Unsafe mechanics
  private static final Unsafe U;
  private static final long SIZECTL;
  private static final long TRANSFERINDEX;
  private static final long TRANSFERORIGIN;
  private static final long BASECOUNT;
  private static final long CELLSBUSY;
  private static final long CELLVALUE;
  private static final long ABASE;
  private static final int ASHIFT;

  static {
    try {
      U = getUnsafe();
      Class<?> k = ConcurrentLongObjectHashMap.class;
      SIZECTL = U.objectFieldOffset
        (k.getDeclaredField("sizeCtl"));
      TRANSFERINDEX = U.objectFieldOffset
        (k.getDeclaredField("transferIndex"));
      TRANSFERORIGIN = U.objectFieldOffset
        (k.getDeclaredField("transferOrigin"));
      BASECOUNT = U.objectFieldOffset
        (k.getDeclaredField("baseCount"));
      CELLSBUSY = U.objectFieldOffset
        (k.getDeclaredField("cellsBusy"));
      Class<?> ck = ConcurrentHashMap.CounterCell.class;
      CELLVALUE = U.objectFieldOffset
        (ck.getDeclaredField("value"));
      Class<?> ak = Node[].class;
      ABASE = U.arrayBaseOffset(ak);
      int scale = U.arrayIndexScale(ak);
      if ((scale & scale - 1) != 0) {
        throw new Error("data type scale not a power of two");
      }
      ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
    }
    catch (Exception e) {
      throw new Error(e);
    }
  }

  /**
   * Returns a sun.misc.Unsafe.  Suitable for use in a 3rd party package.
   * Replace with a simple call to Unsafe.getUnsafe when integrating
   * into a jdk.
   *
   * @return a sun.misc.Unsafe
   */
  private static Unsafe getUnsafe() {
    return AtomicFieldUpdater.getUnsafe();
  }

  ////////////////////// IJ specific

  /**
   * @return value if there is no entry in the map, or corresponding value if entry already exists
   */
  @Override
  @NotNull
  public V cacheOrGet(final long key, @NotNull final V defaultValue) {
    V v = get(key);
    if (v != null) return v;
    V prev = putIfAbsent(key, defaultValue);
    return prev == null ? defaultValue : prev;
  }

  private static int hash(long key) {
    return HashFunctions.hash(key);
  }
}
