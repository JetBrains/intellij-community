// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.concurrency;

import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.containers.SimpleEntry;
import com.intellij.util.containers.ThreadLocalRandom;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.locks.LockSupport;

/**
 * Adapted from Doug Lea <a href="https://gee.cs.oswego.edu/dl/concurrency-interest/index.html">ConcurrentHashMap</a> to int keys
 * with following additions/changes:
 * - added hashing strategy argument
 * - added cacheOrGet convenience method
 * - Null values are NOT allowed
 * @author Doug Lea
 * @param <V> the type of mapped values
 * Use {@link ConcurrentCollectionFactory#createConcurrentIntObjectMap()} to create this map
 */
final class ConcurrentIntObjectHashMap<V> implements ConcurrentIntObjectMap<V> {
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
  static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;


  /**
   * The bin count threshold for using a tree rather than list for a
   * bin.  Bins are converted to trees when adding an element to a
   * bin with at least this many nodes. The value must be greater
   * than 2, and should be at least 8 to mesh with assumptions in
   * tree removal about conversion back to plain bins upon
   * shrinkage.
   */
  static final int TREEIFY_THRESHOLD = 8;

  /**
   * The bin count threshold for untreeifying a (split) bin during a
   * resize operation. Should be less than TREEIFY_THRESHOLD, and at
   * most 6 to mesh with shrinkage detection under removal.
   */
  static final int UNTREEIFY_THRESHOLD = 6;

  /**
   * The smallest table capacity for which bins may be treeified.
   * (Otherwise the table is resized if too many nodes in a bin.)
   * The value should be at least 4 * TREEIFY_THRESHOLD to avoid
   * conflicts between resizing and treeification thresholds.
   */
  static final int MIN_TREEIFY_CAPACITY = 64;

  /**
   * Minimum number of rebinnings per transfer step. Ranges are
   * subdivided to allow multiple resizer threads.  This value
   * serves as a lower bound to avoid resizers encountering
   * excessive memory contention.  The value should be at least
   * DEFAULT_CAPACITY.
   */
  private static final int MIN_TRANSFER_STRIDE = 16;

  /**
   * The number of bits used for generation stamp in sizeCtl.
   * Must be at least 6 for 32bit arrays.
   */
  private static final int RESIZE_STAMP_BITS = 16;

  /**
   * The maximum number of threads that can help resize.
   * Must fit in 32 - RESIZE_STAMP_BITS bits.
   */
  private static final int MAX_RESIZERS = (1 << (32 - RESIZE_STAMP_BITS)) - 1;

  /**
   * The bit shift for recording size stamp in sizeCtl.
   */
  private static final int RESIZE_STAMP_SHIFT = 32 - RESIZE_STAMP_BITS;

  /*
   * Encodings for Node hash fields. See above for explanation.
   */
  static final int MOVED = -1; // hash for forwarding nodes
  static final int TREEBIN = -2; // hash for roots of trees
  static final int RESERVED = -3; // hash for transient reservations
  static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash

  /**
   * Number of CPUS, to place bounds on some sizings
   */
  static final int NCPU = Runtime.getRuntime().availableProcessors();

    /* ---------------- Nodes -------------- */

  /**
   * Key-value entry.  This class is never exported out as a
   * user-mutable Map.Entry (i.e., one supporting setValue; see
   * MapEntry below), but can be used for read-only traversals used
   * in bulk tasks.  Subclasses of Node with a negative hash field
   * are special, and contain null keys and values (but are never
   * exported).  Otherwise, keys and vals are never null.
   */
  static class Node<V> implements Entry<V> {
    final int hash;
    final int key;
    volatile V val;
    volatile Node<V> next;

    Node(int hash, int key, V val, Node<V> next) {
      this.hash = hash;
      this.key = key;
      this.val = val;
      this.next = next;
    }

    @Override
    public final int getKey() {
      return key;
    }

    @NotNull
    @Override
    public final V getValue() {
      return val;
    }

    @Override
    public final int hashCode() {
      return (key ^ val.hashCode());
    }

    @Override
    public final String toString() {
      return key + "=" + val;
    }

    @Override
    public final boolean equals(Object o) {
      if (!(o instanceof Entry<?> e)) return false;
      if (e.getKey() != key) return false;
      Object v = e.getValue();
      Object u = val;
      return v == u || v.equals(u);
    }

    /**
     * Virtualized support for map.get(); overridden in subclasses.
     */
    Node<V> find(int h, int k) {
      Node<V> e = this;
      do {
        if ((e.key == k)) {
          return e;
        }
      }
      while ((e = e.next) != null);
      return null;
    }
  }

    /* ---------------- Static utilities -------------- */

  /**
   * Spreads (XORs) higher bits of hash to lower and also forces top
   * bit to 0. Because the table uses power-of-two masking, sets of
   * hashes that vary only in bits above the current mask will
   * always collide. (Among known examples are sets of Float keys
   * holding consecutive whole numbers in small tables.)  So we
   * apply a transform that spreads the impact of higher bits
   * downward. There is a tradeoff between speed, utility, and
   * quality of bit-spreading. Because many common sets of hashes
   * are already reasonably distributed (so don't benefit from
   * spreading), and because we use trees to handle large sets of
   * collisions in bins, we just XOR some shifted bits in the
   * cheapest possible way to reduce systematic lossage, as well as
   * to incorporate impact of the highest bits that would otherwise
   * never be used in index calculations because of table bounds.
   */
  static int spread(int h) {
    return (h ^ (h >>> 16)) & HASH_BITS;
  }

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
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
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
     * and so in principle require only release ordering, not
     * full volatile semantics, but are currently coded as volatile
     * writes to be conservative.
     */

  @SuppressWarnings("unchecked")
  static <V> ConcurrentIntObjectHashMap.Node<V> tabAt(ConcurrentIntObjectHashMap.Node<V>[] tab, int i) {
    return (ConcurrentIntObjectHashMap.Node<V>)TAB_ARRAY.getVolatile(tab, i);
  }

  static <V> boolean casTabAt(ConcurrentIntObjectHashMap.Node<V>[] tab, int i,
                                    ConcurrentIntObjectHashMap.Node<V> c, ConcurrentIntObjectHashMap.Node<V> v) {
    return TAB_ARRAY.compareAndSet(tab, i, c, v);
  }

  static <V> void setTabAt(ConcurrentIntObjectHashMap.Node<V>[] tab, int i, ConcurrentIntObjectHashMap.Node<V> v) {
    TAB_ARRAY.setVolatile(tab, i, v);
  }

    /* ---------------- Fields -------------- */

  /**
   * The array of bins. Lazily initialized upon first insertion.
   * Size is always a power of two. Accessed directly by iterators.
   */
  transient volatile Node<V>[] table;

  /**
   * The next table to use; non-null only while resizing.
   */
  private transient volatile Node<V>[] nextTable;

  /**
   * Base counter value, used mainly when there is no contention,
   * but also as a fallback during table initialization
   * races. Updated via CAS.
   */
  @SuppressWarnings("UnusedDeclaration")
  private transient volatile long baseCount;

  /**
   * Table initialization and resizing control.  When negative, the
   * table is being initialized or resized: -1 for initialization,
   * else -(1 + the number of active resizing threads).  Otherwise,
   * when table is null, holds the initial table size to use upon
   * creation, or 0 for default. After initialization, holds the
   * next element count value upon which to resize the table.
   */
  private transient volatile int sizeCtl;

  /**
   * The next table index (plus one) to split while resizing.
   */
  private transient volatile int transferIndex;

  /**
   * Spinlock (locked via CAS) used when resizing and/or creating CounterCells.
   */
  private transient volatile int cellsBusy;


  /**
   * Table of counter cells. When non-null, size is a power of 2.
   */
  private transient volatile ConcurrentHashMap.CounterCell[] counterCells;

  // views
  private transient ValuesView<V> values;
  private transient EntrySetView<V> entrySet;


    /* ---------------- Public operations -------------- */

  /**
   * Creates a new, empty map with the default initial table size (16).
   */
  ConcurrentIntObjectHashMap() {
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
  ConcurrentIntObjectHashMap(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException();
    }
    int cap = ((initialCapacity >= (MAXIMUM_CAPACITY >>> 1)) ?
               MAXIMUM_CAPACITY :
               tableSizeFor(initialCapacity + (initialCapacity >>> 1) + 1));
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
   */
  ConcurrentIntObjectHashMap(int initialCapacity, float loadFactor) {
    this(initialCapacity, loadFactor, 1);
  }

  /**
   * Creates a new, empty map with an initial table size based on
   * the given number of elements ({@code initialCapacity}), table
   * density ({@code loadFactor}), and number of concurrently
   * updating threads ({@code concurrencyLevel}).
   *
   * @param initialCapacity  the initial capacity. The implementation
   *                         performs internal sizing to accommodate this many elements,
   *                         given the specified load factor.
   * @param loadFactor       the load factor (table density) for
   *                         establishing the initial table size
   * @param concurrencyLevel the estimated number of concurrently
   *                         updating threads. The implementation may use this value as
   *                         a sizing hint.
   * @throws IllegalArgumentException if the initial capacity is
   *                                  negative or the load factor or concurrencyLevel are
   *                                  nonpositive
   */
  ConcurrentIntObjectHashMap(int initialCapacity,
                                     float loadFactor, int concurrencyLevel) {
    if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0) {
      throw new IllegalArgumentException();
    }
    if (initialCapacity < concurrencyLevel)   // Use at least as many bins
    {
      initialCapacity = concurrencyLevel;   // as estimated threads
    }
    long size = (long)(1.0 + (long)initialCapacity / loadFactor);
    int cap = (size >= (long)MAXIMUM_CAPACITY) ?
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
    return ((n < 0L) ? 0 :
            (n > (long)Integer.MAX_VALUE) ? Integer.MAX_VALUE :
            (int)n);
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
   */
  @Override
  public V get(int key) {
    Node<V>[] tab;
    Node<V> e;
    Node<V> p;
    int n, eh;
    int h = spread(key);
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (e = tabAt(tab, (n - 1) & h)) != null) {
      if ((eh = e.hash) == h) {
        if (e.key == key) {
          return e.val;
        }
      }
      else if (eh < 0) {
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
   */
  @Override
  public boolean containsKey(int key) {
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
   */
  @Override
  public boolean containsValue(@NotNull Object value) {
    Node<V>[] t;
    if ((t = table) != null) {
      Traverser<V> it = new Traverser<>(t, t.length, 0, t.length);
      for (Node<V> p; (p = it.advance()) != null; ) {
        V v;
        if ((v = p.val) == value || (v != null && value.equals(v))) {
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
   */
  @Override
  public V put(int key, @NotNull V value) {
    return putVal(key, value, false);
  }

  /**
   * Implementation for put and putIfAbsent
   */
  V putVal(int key, @NotNull V value, boolean onlyIfAbsent) {
    int hash = spread(key);
    int binCount = 0;
    for (Node<V>[] tab = table; ; ) {
      Node<V> f;
      int n, i, fh;
      if (tab == null || (n = tab.length) == 0) {
        tab = initTable();
      }
      else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
        if (casTabAt(tab, i, null,
                     new Node<>(hash, key, value, null))) {
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
                if ((e.key == key)) {
                  oldVal = e.val;
                  if (!onlyIfAbsent) {
                    e.val = value;
                  }
                  break;
                }
                Node<V> pred = e;
                if ((e = e.next) == null) {
                  pred.next = new Node<>(hash, key,
                                         value, null);
                  break;
                }
              }
            }
            else if (f instanceof TreeBin) {
              Node<V> p;
              binCount = 2;
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
   */
  @Override
  public V remove(int key) {
    return replaceNode(key, null, null);
  }

  /**
   * Implementation for the four public remove/replace methods:
   * Replaces node value with v, conditional upon match of cv if
   * non-null.  If resulting value is null, delete.
   */
  V replaceNode(int key, V value, Object cv) {
    int hash = spread(key);
    for (Node<V>[] tab = table; ; ) {
      Node<V> f;
      int n, i, fh;
      if (tab == null || (n = tab.length) == 0 ||
          (f = tabAt(tab, i = (n - 1) & hash)) == null) {
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
                if ((e.key == key)) {
                  V ev = e.val;
                  if (cv == null || cv == ev ||
                      (ev != null && cv.equals(ev))) {
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
            else if (f instanceof TreeBin<V> t) {
              validated = true;
              TreeNode<V> r, p;
              if ((r = t.root) != null &&
                  (p = r.findTreeNode(hash, key)) != null) {
                V pv = p.val;
                if (cv == null || cv == pv ||
                    (pv != null && cv.equals(pv))) {
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
            Node<V> p = (fh >= 0 ? f :
                         (f instanceof TreeBin) ?
                         ((TreeBin<V>)f).first : null);
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
   * Returns a {@link Collection} view of the values contained in this map.
   * The collection is backed by the map, so changes to the map are
   * reflected in the collection, and vice-versa.  The collection
   * supports element removal, which removes the corresponding
   * mapping from this map, via the {@code Iterator.remove},
   * {@code Collection.remove}, {@code removeAll},
   * {@code retainAll}, and {@code clear} operations.  It does not
   * support the {@code add} or {@code addAll} operations.
   * <p/>
   * <p>The view's iterators and spliterators are
   * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
   * <p/>
   *
   * @return the collection view
   */
  @NotNull
  @Override
  public Collection<V> values() {
    ValuesView<V> vs;
    return (vs = values) != null ? vs : (values = new ValuesView<>(this));
  }

  /**
   * Returns a {@link Set} view of the mappings contained in this map.
   * The set is backed by the map, so changes to the map are
   * reflected in the set, and vice-versa.  The set supports element
   * removal, which removes the corresponding mapping from the map,
   * via the {@code Iterator.remove}, {@code Set.remove},
   * {@code removeAll}, {@code retainAll}, and {@code clear}
   * operations.
   * <p/>
   * <p>The view's iterators and spliterators are
   * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
   * <p/>
   *
   * @return the set view
   */
  @Override
  @NotNull
  public Set<Entry<V>> entrySet() {
    EntrySetView<V> es;
    return (es = entrySet) != null ? es : (entrySet = new EntrySetView<>(this));
  }

  /**
   * Returns the hash code value for this {@link Map}, i.e.,
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
      Traverser<V> it = new Traverser<>(t, t.length, 0, t.length);
      for (Node<V> p; (p = it.advance()) != null; ) {
        h += p.key ^ p.val.hashCode();
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
    Traverser<V> it = new Traverser<>(t, f, 0, f);
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    Node<V> p;
    if ((p = it.advance()) != null) {
      for (; ; ) {
        int k = p.key;
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
      if (!(o instanceof ConcurrentIntObjectMap)) {
        return false;
      }
      IntObjectMap<?> m = (IntObjectMap)o;
      Node<V>[] t;
      int f = (t = table) == null ? 0 : t.length;
      Traverser<V> it = new Traverser<>(t, f, 0, f);
      for (Node<V> p; (p = it.advance()) != null; ) {
        V val = p.val;
        Object v = m.get(p.key);
        if (v == null || (v != val && !v.equals(val))) {
          return false;
        }
      }
      for (Entry<?> e : m.entrySet()) {
        int mk = e.getKey();
        Object mv = e.getValue();
        Object v = get(mk);
        if (v == null || mv != v && !mv.equals(v)) {
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
   */
  @Override
  public V putIfAbsent(int key, @NotNull V value) {
    return putVal(key, value, true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean remove(int key, @NotNull Object value) {
    return replaceNode(key, null, value) != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean replace(int key, @NotNull V oldValue, @NotNull V newValue) {
    return replaceNode(key, newValue, oldValue) != null;
  }

  /**
   * {@inheritDoc}
   *
   * @return the previous value associated with the specified key,
   * or {@code null} if there was no mapping for the key
   */
  public V replace(int key, @NotNull V value) {
    return replaceNode(key, value, null);
  }

  // Overrides of JDK8+ Map extension method defaults

  /**
   * Returns the value to which the specified key is mapped, or the
   * given default value if this map contains no mapping for the
   * key.
   *
   * @param key          the key whose associated value is to be returned
   * @param defaultValue the value to return if this map contains
   *                     no mapping for the given key
   * @return the mapping for the key, if present; else the default value
   */
  public V getOrDefault(int key, V defaultValue) {
    V v;
    return (v = get(key)) == null ? defaultValue : v;
  }


  // Hashtable legacy methods

  /**
   * Legacy method testing if some key maps into the specified value
   * in this table.  This method is identical in functionality to
   * {@link #containsValue(Object)}, and exists solely to ensure
   * full compatibility with class {@link java.util.Hashtable},
   * which supported this method prior to introduction of the
   * Java Collections framework.
   *
   * @param value a value to search for
   * @return {@code true} if and only if some key maps to the
   * {@code value} argument in this table as
   * determined by the {@code equals} method;
   * {@code false} otherwise
   */
  public boolean contains(Object value) {
    return containsValue(value);
  }

  /**
   * Returns an enumeration of the keys in this table.
   *
   * @return an enumeration of the keys in this table
   */
  @Override
  public int @NotNull [] keys() {
    Object[] entries = new EntrySetView<>(this).toArray();
    int[] result = new int[entries.length];
    for (int i = 0; i < entries.length; i++) {
      Entry<V> entry = (Entry<V>)entries[i];
      result[i] = entry.getKey();
    }
    return result;
  }

  /**
   * Returns an enumeration of the values in this table.
   *
   * @return an enumeration of the values in this table
   * @see #values()
   */
  @NotNull
  @Override
  public Enumeration<V> elements() {
    Node<V>[] t;
    int f = (t = table) == null ? 0 : t.length;
    return new ValueIterator<>(t, f, 0, f, this);
  }

  // ConcurrentHashMap-only methods

  /**
   * Returns the number of mappings. This method should be used
   * instead of {@link #size} because a ConcurrentHashMap may
   * contain more mappings than can be represented as an int. The
   * value returned is an estimate; the actual count may differ if
   * there are concurrent insertions or removals.
   *
   * @return the number of mappings
   */
  public long mappingCount() {
    long n = sumCount();
    return Math.max(n, 0L); // ignore transient negative values
  }



    /* ---------------- Special Nodes -------------- */

  /**
   * A node inserted at head of bins during transfer operations.
   */
  static final class ForwardingNode<V> extends Node<V> {
    final Node<V>[] nextTable;

    ForwardingNode(Node<V>[] tab) {
      super(MOVED, 0, null, null);
      nextTable = tab;
    }

    @Override
    Node<V> find(int h, int k) {
      // loop to avoid arbitrarily deep recursion on forwarding nodes
      outer:
      for (Node<V>[] tab = nextTable; ; ) {
        Node<V> e;
        int n;
        if (tab == null || (n = tab.length) == 0 ||
            (e = tabAt(tab, (n - 1) & h)) == null) {
          return null;
        }
        for (; ; ) {
          if ((e.key == k)) {
            return e;
          }
          if (e.hash < 0) {
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
   * Returns the stamp bits for resizing a table of size n.
   * Must be negative when shifted left by RESIZE_STAMP_SHIFT.
   */
  static int resizeStamp(int n) {
    return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
  }

  /**
   * Initializes table, using the size recorded in sizeCtl.
   */
  private Node<V>[] initTable() {
    Node<V>[] tab;
    int sc;
    while ((tab = table) == null || tab.length == 0) {
      if ((sc = sizeCtl) < 0) {
        Thread.yield(); // lost initialization race; just spin
      }
      else if (SIZECTL.compareAndSet(this, sc, -1)) {
        try {
          if ((tab = table) == null || tab.length == 0) {
            int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
            @SuppressWarnings("unchecked")
            Node<V>[] nt = (Node<V>[])new Node<?>[n];
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
    long b, s;
    if ((as = counterCells) != null ||
        !BASECOUNT.compareAndSet(this, b = baseCount, s = b + x)) {
      ConcurrentHashMap.CounterCell a;
      long v;
      int m;
      boolean uncontended = true;
      if (as == null || (m = as.length - 1) < 0 ||
          (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
          !(uncontended =
              ConcurrentHashMap.CELLVALUE.compareAndSet(a, v = a.value, v + x))) {
        fullAddCount(x, uncontended);
        return;
      }
      if (check <= 1) {
        return;
      }
      s = sumCount();
    }
    if (check >= 0) {
      Node<V>[] tab, nt;
      int n, sc;
      while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
             (n = tab.length) < MAXIMUM_CAPACITY) {
        int rs = resizeStamp(n);
        if (sc < 0) {
          if (sc >>> RESIZE_STAMP_SHIFT != rs || (nt = nextTable) == null || transferIndex <= 0) {
            break;
          }
          if (SIZECTL.compareAndSet(this, sc, sc + 1)) {
            transfer(tab, nt);
          }
        }
        else if (SIZECTL.compareAndSet(this, sc,
                                       (rs << RESIZE_STAMP_SHIFT) + 2)) {
          transfer(tab, null);
        }
        s = sumCount();
      }
    }
  }

  /**
   * Helps transfer if a resize is in progress.
   */
  Node<V>[] helpTransfer(Node<V>[] tab, Node<V> f) {
    Node<V>[] nextTab;
    int sc;
    if (tab != null && (f instanceof ForwardingNode) &&
        (nextTab = ((ForwardingNode<V>)f).nextTable) != null) {
      int rs = resizeStamp(tab.length);
      while (nextTab == nextTable && table == tab &&
             (sc = sizeCtl) < 0) {
        if (sc >>> RESIZE_STAMP_SHIFT != rs || transferIndex <= 0) {
          break;
        }
        if (SIZECTL.compareAndSet(this, sc,  sc + 1)) {
          transfer(tab, nextTab);
          break;
        }
      }
      return nextTab;
    }
    return table;
  }

  /**
   * Tries to presize table to accommodate the given number of elements.
   *
   * @param size number of elements (doesn't need to be perfectly accurate)
   */
  private void tryPresize(int size) {
    int c = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY :
            tableSizeFor(size + (size >>> 1) + 1);
    int sc;
    while ((sc = sizeCtl) >= 0) {
      Node<V>[] tab = table;
      int n;
      if (tab == null || (n = tab.length) == 0) {
        n = Math.max(sc, c);
        if (SIZECTL.compareAndSet(this, sc, -1)) {
          try {
            if (table == tab) {
              @SuppressWarnings("unchecked")
              Node<V>[] nt = (Node<V>[])new Node<?>[n];
              table = nt;
              sc = n - (n >>> 2);
            }
          }
          finally {
            sizeCtl = sc;
          }
        }
      }
      else if (c <= sc || n >= MAXIMUM_CAPACITY) {
        break;
      }
      else if (tab == table) {
        int rs = resizeStamp(n);
        if (SIZECTL.compareAndSet(this, sc,
                                       (rs << RESIZE_STAMP_SHIFT) + 2)) {
          transfer(tab, null);
        }
      }
    }
  }

  /**
   * Moves and/or copies the nodes in each bin to new table. See
   * above for explanation.
   */
  private void transfer(Node<V>[] tab, Node<V>[] nextTab) {
    int n = tab.length, stride;
    if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE) {
      stride = MIN_TRANSFER_STRIDE; // subdivide range
    }
    if (nextTab == null) {            // initiating
      try {
        @SuppressWarnings("unchecked")
        Node<V>[] nt = (Node<V>[])new Node<?>[n << 1];
        nextTab = nt;
      }
      catch (Throwable ex) {      // try to cope with OOME
        sizeCtl = Integer.MAX_VALUE;
        return;
      }
      nextTable = nextTab;
      transferIndex = n;
    }
    int nextn = nextTab.length;
    ForwardingNode<V> fwd = new ForwardingNode<>(nextTab);
    boolean advance = true;
    boolean finishing = false; // to ensure sweep before committing nextTab
    for (int i = 0, bound = 0; ; ) {
      Node<V> f;
      int fh;
      while (advance) {
        int nextIndex, nextBound;
        if (--i >= bound || finishing) {
          advance = false;
        }
        else if ((nextIndex = transferIndex) <= 0) {
          i = -1;
          advance = false;
        }
        else if (TRANSFERINDEX.compareAndSet(this, nextIndex,
                                             nextBound = (nextIndex > stride ?
                                                          nextIndex - stride : 0))) {
          bound = nextBound;
          i = nextIndex - 1;
          advance = false;
        }
      }
      if (i < 0 || i >= n || i + n >= nextn) {
        int sc;
        if (finishing) {
          nextTable = null;
          table = nextTab;
          sizeCtl = (n << 1) - (n >>> 1);
          return;
        }
        if (SIZECTL.compareAndSet(this, sc = sizeCtl, sc - 1)) {
          if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT) {
            return;
          }
          finishing = advance = true;
          i = n; // recheck before commit
        }
      }
      else if ((f = tabAt(tab, i)) == null) {
        advance = casTabAt(tab, i, null, fwd);
      }
      else if ((fh = f.hash) == MOVED) {
        advance = true; // already processed
      }
      else {
        synchronized (f) {
          if (tabAt(tab, i) == f) {
            Node<V> ln, hn;
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
                int pk = p.key;
                V pv = p.val;
                if ((ph & n) == 0) {
                  ln = new Node<>(ph, pk, pv, ln);
                }
                else {
                  hn = new Node<>(ph, pk, pv, hn);
                }
              }
              setTabAt(nextTab, i, ln);
              setTabAt(nextTab, i + n, hn);
              setTabAt(tab, i, fwd);
              advance = true;
            }
            else if (f instanceof TreeBin<V> t) {
              TreeNode<V> lo = null, loTail = null;
              TreeNode<V> hi = null, hiTail = null;
              int lc = 0, hc = 0;
              for (Node<V> e = t.first; e != null; e = e.next) {
                int h = e.hash;
                TreeNode<V> p = new TreeNode<>
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
              ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) :
                   (hc != 0) ? new TreeBin<>(lo) : t;
              hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
                   (lc != 0) ? new TreeBin<>(hi) : t;
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

    /* ---------------- Counter support -------------- */

  long sumCount() {
    ConcurrentHashMap.CounterCell[] as = counterCells;
    ConcurrentHashMap.CounterCell a;
    long sum = baseCount;
    if (as != null) {
      for (int i = 0; i < as.length; ++i) {
        if ((a = as[i]) != null) {
          sum += a.value;
        }
      }
    }
    return sum;
  }

  // See LongAdder version for explanation
  private void fullAddCount(long x, boolean wasUncontended) {
    int h;
    if ((h = ThreadLocalRandom.getProbe()) == 0) {
      ThreadLocalRandom.localInit();      // force initialization
      h = ThreadLocalRandom.getProbe();
      wasUncontended = true;
    }
    boolean collide = false;                // True if last slot nonempty
    for (; ; ) {
      ConcurrentHashMap.CounterCell[] as;
      ConcurrentHashMap.CounterCell a;
      int n;
      long v;
      if ((as = counterCells) != null && (n = as.length) > 0) {
        if ((a = as[(n - 1) & h]) == null) {
          if (cellsBusy == 0) {            // Try to attach new Cell
            ConcurrentHashMap.CounterCell r = new ConcurrentHashMap.CounterCell(x); // Optimistic create
            if (cellsBusy == 0 &&
                CELLSBUSY.compareAndSet(this, 0, 1)) {
              boolean created = false;
              try {               // Recheck under lock
                ConcurrentHashMap.CounterCell[] rs;
                int m, j;
                if ((rs = counterCells) != null &&
                    (m = rs.length) > 0 &&
                    rs[j = (m - 1) & h] == null) {
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
        else if (ConcurrentHashMap.CELLVALUE.compareAndSet(a, v = a.value, v + x)) {
          break;
        }
        else if (counterCells != as || n >= NCPU) {
          collide = false;            // At max size or stale
        }
        else if (!collide) {
          collide = true;
        }
        else if (cellsBusy == 0 &&
                 CELLSBUSY.compareAndSet(this, 0, 1)) {
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
        h = ThreadLocalRandom.advanceProbe(h);
      }
      else if (cellsBusy == 0 && counterCells == as &&
               CELLSBUSY.compareAndSet(this, 0, 1)) {
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
      else if (BASECOUNT.compareAndSet(this, v = baseCount, v + x)) {
        break;                          // Fall back on using base
      }
    }
  }

    /* ---------------- Conversion from/to TreeBins -------------- */

  /**
   * Replaces all linked nodes in bin at given index unless table is
   * too small, in which case resizes instead.
   */
  private void treeifyBin(Node<V>[] tab, int index) {
    Node<V> b;
    int n, sc;
    if (tab != null) {
      if ((n = tab.length) < MIN_TREEIFY_CAPACITY) {
        tryPresize(n << 1);
      }
      else if ((b = tabAt(tab, index)) != null && b.hash >= 0) {
        synchronized (b) {
          if (tabAt(tab, index) == b) {
            TreeNode<V> hd = null, tl = null;
            for (Node<V> e = b; e != null; e = e.next) {
              TreeNode<V> p =
                new TreeNode<>(e.hash, e.key, e.val,
                               null, null);
              if ((p.prev = tl) == null) {
                hd = p;
              }
              else {
                tl.next = p;
              }
              tl = p;
            }
            setTabAt(tab, index, new TreeBin<>(hd));
          }
        }
      }
    }
  }

  /**
   * Returns a list on non-TreeNodes replacing those in given list.
   */
  static <V> Node<V> untreeify(Node<V> b) {
    Node<V> hd = null, tl = null;
    for (Node<V> q = b; q != null; q = q.next) {
      Node<V> p = new Node<>(q.hash, q.key, q.val, null);
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
  static final class TreeNode<V> extends Node<V> {
    TreeNode<V> parent;  // red-black tree links
    TreeNode<V> left;
    TreeNode<V> right;
    TreeNode<V> prev;    // needed to unlink next upon deletion
    boolean red;

    TreeNode(int hash, int key, V val, Node<V> next, TreeNode<V> parent) {
      super(hash, key, val, next);
      this.parent = parent;
    }

    @Override
    Node<V> find(int h, int k) {
      return findTreeNode(h, k);
    }

    /**
     * Returns the TreeNode (or null if not found) for the given key
     * starting at given root.
     */
    TreeNode<V> findTreeNode(int h, int k) {
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
  static final class TreeBin<V> extends Node<V> {
    TreeNode<V> root;
    volatile TreeNode<V> first;
    volatile Thread waiter;
    volatile int lockState;
    // values for lockState
    static final int WRITER = 1; // set while holding write lock
    static final int WAITER = 2; // set when waiting for write lock
    static final int READER = 4; // increment value for setting read lock

    /**
     * Creates bin with initial set of nodes headed by b.
     */
    TreeBin(TreeNode<V> b) {
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
            int dir, ph;
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
            if ((p = (dir <= 0) ? p.left : p.right) == null) {
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
      if (!LOCKSTATE.compareAndSet(this, 0, WRITER)) {
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
      for (int s; ; ) {
        if (((s = lockState) & ~WAITER) == 0) {
          if (LOCKSTATE.compareAndSet(this, s, WRITER)) {
            if (waiting) {
              waiter = null;
            }
            return;
          }
        }
        else if ((s & WAITER) == 0) {
          if (LOCKSTATE.compareAndSet(this, s, s | WAITER)) {
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
    Node<V> find(int h, int k) {
      for (Node<V> e = first; e != null; ) {
        int s;
        if (((s = lockState) & (WAITER | WRITER)) != 0) {
          if ((e.key == k)) {
            return e;
          }
          e = e.next;
        }
        else if (LOCKSTATE.compareAndSet(this, s,
                                     s + READER)) {
          TreeNode<V> r;
          TreeNode<V> p;
          try {
            p = ((r = root) == null ? null :
                 r.findTreeNode(h, k));
          }
          finally {
            Thread w;
            if ((int)LOCKSTATE.getAndAdd(this, -READER) ==
                (READER | WAITER) && (w = waiter) != null) {
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
    TreeNode<V> putTreeVal(int h, int k, V v) {
      boolean searched = false;
      for (TreeNode<V> p = root; ; ) {
        int dir, ph;
        if (p == null) {
          first = root = new TreeNode<>(h, k, v, null, null);
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
            TreeNode<V> q, ch;
            searched = true;
            if (((ch = p.left) != null &&
                 (q = ch.findTreeNode(h, k)) != null) ||
                ((ch = p.right) != null &&
                 (q = ch.findTreeNode(h, k)) != null)) {
              return q;
            }
          }
          dir = 0;
        }

        TreeNode<V> xp = p;
        if ((p = (dir <= 0) ? p.left : p.right) == null) {
          TreeNode<V> x, f = first;
          first = x = new TreeNode<>(h, k, v, f, xp);
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
    boolean removeTreeNode(TreeNode<V> p) {
      TreeNode<V> next = (TreeNode<V>)p.next;
      TreeNode<V> pred = p.prev;  // unlink traversal pointers
      TreeNode<V> r, rl;
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
          TreeNode<V> s = pr, sl;
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
            s.right = pr;
            pr.parent = s;
          }
          p.left = null;
          if ((p.right = sr) != null) {
            sr.parent = p;
          }
          s.left = pl;
          pl.parent = s;
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

        root = (p.red) ? r : balanceDeletion(r, replacement);

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

    static <V> TreeNode<V> rotateLeft(TreeNode<V> root,
                                         TreeNode<V> p) {
      TreeNode<V> r, pp, rl;
      if (p != null && (r = p.right) != null) {
        if ((rl = p.right = r.left) != null) {
          rl.parent = p;
        }
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

    static <V> TreeNode<V> rotateRight(TreeNode<V> root,
                                       TreeNode<V> p) {
      TreeNode<V> l, pp, lr;
      if (p != null && (l = p.left) != null) {
        if ((lr = p.left = l.right) != null) {
          lr.parent = p;
        }
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

    static <V> TreeNode<V> balanceInsertion(TreeNode<V> root,
                                            TreeNode<V> x) {
      x.red = true;
      for (TreeNode<V> xp, xpp, xppl, xppr; ; ) {
        if ((xp = x.parent) == null) {
          x.red = false;
          return x;
        }
        else if (!xp.red || (xpp = xp.parent) == null) {
          return root;
        }
        if (xp == (xppl = xpp.left)) {
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

    static <V> TreeNode<V> balanceDeletion(TreeNode<V> root,
                                              TreeNode<V> x) {
      for (TreeNode<V> xp, xpl, xpr; ; ) {
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
            TreeNode<V> sl = xpr.left, sr = xpr.right;
            if ((sr == null || !sr.red) &&
                (sl == null || !sl.red)) {
              xpr.red = true;
              x = xp;
            }
            else {
              if (sr == null || !sr.red) {
                sl.red = false;
                xpr.red = true;
                root = rotateRight(root, xpr);
                xpr = (xp = x.parent) == null ?
                      null : xp.right;
              }
              if (xpr != null) {
                xpr.red = xp.red;
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
            TreeNode<V> sl = xpl.left, sr = xpl.right;
            if ((sl == null || !sl.red) &&
                (sr == null || !sr.red)) {
              xpl.red = true;
              x = xp;
            }
            else {
              if (sl == null || !sl.red) {
                sr.red = false;
                xpl.red = true;
                root = rotateLeft(root, xpl);
                xpl = (xp = x.parent) == null ?
                      null : xp.left;
              }
              if (xpl != null) {
                xpl.red = xp.red;
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
    static <V> boolean checkInvariants(TreeNode<V> t) {
      TreeNode<V> tp = t.parent, tl = t.left, tr = t.right,
        tb = t.prev, tn = (TreeNode<V>)t.next;
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

    private static final VarHandle LOCKSTATE;
    static {
      try {
        LOCKSTATE = MethodHandles
          .privateLookupIn(ConcurrentIntObjectHashMap.TreeBin.class, MethodHandles.lookup())
          .findVarHandle(ConcurrentIntObjectHashMap.TreeBin.class, "lockState", int.class);
      } catch (Throwable e) {
        throw new Error(e);
      }
    }
  }

    /* ----------------Table Traversal -------------- */

  /**
   * Records the table, its length, and current traversal index for a
   * traverser that must process a region of a forwarded table before
   * proceeding with current table.
   */
  static final class TableStack<V> {
    int length;
    int index;
    Node<V>[] tab;
    TableStack<V> next;
  }

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
  static class Traverser<V> {
    Node<V>[] tab;        // current table; updated if resized
    Node<V> next;         // the next entry to use
    TableStack<V> stack;
    TableStack<V> spare; // to save/restore on ForwardingNodes
    int index;              // index of bin to use next
    int baseIndex;          // current index of initial table
    int baseLimit;          // index bound for initial table
    final int baseSize;     // initial table size

    Traverser(Node<V>[] tab, int size, int index, int limit) {
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
        Node<V>[] t;
        int i;  // must use locals in checks
        int n;
        if (e != null) {
          return next = e;
        }
        if (baseIndex >= baseLimit || (t = tab) == null ||
            (n = t.length) <= (i = index) || i < 0) {
          return next = null;
        }
        if ((e = tabAt(t, i)) != null && e.hash < 0) {
          if (e instanceof ForwardingNode) {
            tab = ((ForwardingNode<V>)e).nextTable;
            e = null;
            pushState(t, i, n);
            continue;
          }
          else if (e instanceof TreeBin) {
            e = ((TreeBin<V>)e).first;
          }
          else {
            e = null;
          }
        }
        if (stack != null) {
          recoverState(n);
        }
        else if ((index = i + baseSize) >= n) {
          index = ++baseIndex; // visit upper slots if present
        }
      }
    }

    /**
     * Saves traversal state upon encountering a forwarding node.
     */
    private void pushState(Node<V>[] t, int i, int n) {
      TableStack<V> s = spare;  // reuse if possible
      if (s != null) {
        spare = s.next;
      }
      else {
        s = new TableStack<>();
      }
      s.tab = t;
      s.length = n;
      s.index = i;
      s.next = stack;
      stack = s;
    }

    /**
     * Possibly pops traversal state.
     *
     * @param n length of current table
     */
    private void recoverState(int n) {
      TableStack<V> s;
      int len;
      while ((s = stack) != null && (index += (len = s.length)) >= n) {
        n = len;
        index = s.index;
        tab = s.tab;
        s.tab = null;
        TableStack<V> next = s.next;
        s.next = spare; // save for reuse
        stack = next;
        spare = s;
      }
      if (s == null && (index += baseSize) >= n) {
        index = ++baseIndex;
      }
    }
  }

  /**
   * Base of key, value, and entry Iterators. Adds fields to
   * Traverser to support iterator.remove.
   */
  static class BaseIterator<V> extends Traverser<V> {
    final ConcurrentIntObjectHashMap<V> map;
    Node<V> lastReturned;

    BaseIterator(Node<V>[] tab, int size, int index, int limit,
                 ConcurrentIntObjectHashMap<V> map) {
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
      // IJ patched:
      // see https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8078645
      map.replaceNode(p.key, null, p.val);
    }
  }


  static final class ValueIterator<V> extends BaseIterator<V>
    implements Iterator<V>, Enumeration<V> {
    ValueIterator(Node<V>[] tab, int index, int size, int limit,
                  ConcurrentIntObjectHashMap<V> map) {
      super(tab, index, size, limit, map);
    }

    @Override
    public V next() {
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
    public V nextElement() {
      return next();
    }
  }

  static final class EntryIterator<V> extends BaseIterator<V>
    implements Iterator<Entry<V>> {
    EntryIterator(Node<V>[] tab, int index, int size, int limit,
                  ConcurrentIntObjectHashMap<V> map) {
      super(tab, index, size, limit, map);
    }

    @Override
    public Entry<V> next() {
      Node<V> p;
      if ((p = next) == null) {
        throw new NoSuchElementException();
      }
      final int k = p.key;
      final V v = p.val;
      lastReturned = p;
      advance();
      return new SimpleEntry<>(k, v);
    }
  }

  // Parallel bulk operations

    /* ----------------Views -------------- */

  /**
   * Base class for views.
   */
  abstract static class CollectionView<V, E> implements Collection<E> {
    final ConcurrentIntObjectHashMap<V> map;

    CollectionView(ConcurrentIntObjectHashMap<V> map) {
      this.map = map;
    }

    /**
     * Returns the map backing this view.
     *
     * @return the map backing this view
     */
    public ConcurrentIntObjectHashMap<V> getMap() {
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
     * Returns an iterator over the elements in this collection.
     * <p/>
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this collection
     */
    @NotNull
    @Override
    public abstract Iterator<E> iterator();

    @Override
    public abstract boolean contains(Object o);

    @Override
    public abstract boolean remove(Object o);

    private static final String oomeMsg = "Required array size too large";

    @Override
    public final Object @NotNull [] toArray() {
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
      return (i == n) ? r : Arrays.copyOf(r, i);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> T @NotNull [] toArray(T @NotNull [] a) {
      long sz = map.mappingCount();
      if (sz > MAX_ARRAY_SIZE) {
        throw new OutOfMemoryError(oomeMsg);
      }
      int m = (int)sz;
      T[] r = (a.length >= m) ? a :
              (T[])java.lang.reflect.Array
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
      return (i == n) ? r : Arrays.copyOf(r, i);
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
   * A view of a ConcurrentHashMap as a {@link Collection} of
   * values, in which additions are disabled. This class cannot be
   * directly instantiated. See {@link #values()}.
   */
  static final class ValuesView<V> extends CollectionView<V, V> implements Collection<V> {

    ValuesView(ConcurrentIntObjectHashMap<V> map) {
      super(map);
    }

    @Override
    public boolean contains(Object o) {
      return map.containsValue(o);
    }

    @Override
    public boolean remove(Object o) {
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
    public Iterator<V> iterator() {
      ConcurrentIntObjectHashMap<V> m = map;
      Node<V>[] t;
      int f = (t = m.table) == null ? 0 : t.length;
      return new ValueIterator<>(t, f, 0, f, m);
    }

    @Override
    public boolean add(V e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends V> c) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * A view of a ConcurrentHashMap as a {@link Set} of (key, value)
   * entries.  This class cannot be directly instantiated. See
   * {@link #entrySet()}.
   */
  static final class EntrySetView<V> extends CollectionView<V, Entry<V>>
    implements Set<Entry<V>> {

    EntrySetView(ConcurrentIntObjectHashMap<V> map) {
      super(map);
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof Map.Entry<?, ?> e) || !(e.getKey() instanceof Integer key)) return false;
      Object r = map.get(key);
      if (r == null) return false;
      Object v = e.getValue();
      return v != null && (v == r || v.equals(r));
    }

    @Override
    public boolean remove(Object o) {
      if (!(o instanceof Map.Entry<?, ?> e) || !(e.getKey() instanceof Integer key)) return false;
      Object v = e.getValue();
      return v != null && map.remove(key, v);
    }

    /**
     * @return an iterator over the entries of the backing map
     */
    @NotNull
    @Override
    public Iterator<Entry<V>> iterator() {
      ConcurrentIntObjectHashMap<V> m = map;
      Node<V>[] t;
      int f = (t = m.table) == null ? 0 : t.length;
      return new EntryIterator<>(t, f, 0, f, m);
    }

    @Override
    public boolean add(Entry<V> e) {
      return map.putVal(e.getKey(), e.getValue(), false) == null;
    }

    @Override
    public boolean addAll(Collection<? extends Entry<V>> c) {
      boolean added = false;
      for (Entry<V> e : c) {
        if (add(e)) {
          added = true;
        }
      }
      return added;
    }

    @Override
    public int hashCode() {
      int h = 0;
      Node<V>[] t;
      if ((t = map.table) != null) {
        Traverser<V> it = new Traverser<>(t, t.length, 0, t.length);
        for (Node<V> p; (p = it.advance()) != null; ) {
          h += p.hashCode();
        }
      }
      return h;
    }

    @Override
    public boolean equals(Object o) {
      Set<?> c;
      return ((o instanceof Set) &&
              ((c = (Set<?>)o) == this ||
               (containsAll(c) && c.containsAll(this))));
    }
  }

  // -------------------------------------------------------


  // Unsafe mechanics
  private static final VarHandle SIZECTL;
  private static final VarHandle TRANSFERINDEX;
  private static final VarHandle BASECOUNT;
  private static final VarHandle CELLSBUSY;
  private static final VarHandle TAB_ARRAY;

  static {
    try {
      SIZECTL = MethodHandles
        .privateLookupIn(ConcurrentIntObjectHashMap.class, MethodHandles.lookup())
        .findVarHandle(ConcurrentIntObjectHashMap.class, "sizeCtl", int.class);

      TRANSFERINDEX = MethodHandles
        .privateLookupIn(ConcurrentIntObjectHashMap.class, MethodHandles.lookup())
        .findVarHandle(ConcurrentIntObjectHashMap.class, "transferIndex", int.class);
      BASECOUNT = MethodHandles
        .privateLookupIn(ConcurrentIntObjectHashMap.class, MethodHandles.lookup())
        .findVarHandle(ConcurrentIntObjectHashMap.class, "baseCount", long.class);
      CELLSBUSY = MethodHandles
        .privateLookupIn(ConcurrentIntObjectHashMap.class, MethodHandles.lookup())
        .findVarHandle(ConcurrentIntObjectHashMap.class, "cellsBusy", int.class);

      TAB_ARRAY = MethodHandles.arrayElementVarHandle(ConcurrentIntObjectHashMap.Node[].class);
    } catch (Throwable e) {
      throw new Error(e);
    }
  }

  /**
   * @return value if there is no entry in the map, or corresponding value if entry already exists
   */
  @Override
  @NotNull
  public V cacheOrGet(final int key, @NotNull final V defaultValue) {
    V v = get(key);
    if (v != null) return v;
    V prev = putIfAbsent(key, defaultValue);
    return prev == null ? defaultValue : prev;
  }
}
