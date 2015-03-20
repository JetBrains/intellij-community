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

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package com.intellij.util.containers;

import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.LockSupport;

// IJ specific:
// copied from Doug Lea ConcurrentHashMap (see http://gee.cs.oswego.edu/dl/concurrency-interest/index.html) except:
// added hashing strategy argument
// Null keys are NOT allowed
// Null values are NOT allowed
// NOT serializable

/**
 * A hash table supporting full concurrency of retrievals and
 * high expected concurrency for updates. This class obeys the
 * same functional specification as {@link java.util.Hashtable}, and
 * includes versions of methods corresponding to each method of
 * {@code Hashtable}. However, even though all operations are
 * thread-safe, retrieval operations do <em>not</em> entail locking,
 * and there is <em>not</em> any support for locking the entire table
 * in a way that prevents all access.  This class is fully
 * interoperable with {@code Hashtable} in programs that rely on its
 * thread safety but not on its synchronization details.
 * <p/>
 * <p>Retrieval operations (including {@code get}) generally do not
 * block, so may overlap with update operations (including {@code put}
 * and {@code remove}). Retrievals reflect the results of the most
 * recently <em>completed</em> update operations holding upon their
 * onset. (More formally, an update operation for a given key bears a
 * <em>happens-before</em> relation with any (non-null) retrieval for
 * that key reporting the updated value.)  For aggregate operations
 * such as {@code putAll} and {@code clear}, concurrent retrievals may
 * reflect insertion or removal of only some entries.  Similarly,
 * Iterators, Spliterators and Enumerations return elements reflecting the
 * state of the hash table at some point at or since the creation of the
 * iterator/enumeration.  They do <em>not</em> throw {@link
 * java.util.ConcurrentModificationException ConcurrentModificationException}.
 * However, iterators are designed to be used by only one thread at a time.
 * Bear in mind that the results of aggregate status methods including
 * {@code size}, {@code isEmpty}, and {@code containsValue} are typically
 * useful only when a map is not undergoing concurrent updates in other threads.
 * Otherwise the results of these methods reflect transient states
 * that may be adequate for monitoring or estimation purposes, but not
 * for program control.
 * <p/>
 * <p>The table is dynamically expanded when there are too many
 * collisions (i.e., keys that have distinct hash codes but fall into
 * the same slot modulo the table size), with the expected average
 * effect of maintaining roughly two bins per mapping (corresponding
 * to a 0.75 load factor threshold for resizing). There may be much
 * variance around this average as mappings are added and removed, but
 * overall, this maintains a commonly accepted time/space tradeoff for
 * hash tables.  However, resizing this or any other kind of hash
 * table may be a relatively slow operation. When possible, it is a
 * good idea to provide a size estimate as an optional {@code
 * initialCapacity} constructor argument. An additional optional
 * {@code loadFactor} constructor argument provides a further means of
 * customizing initial table capacity by specifying the table density
 * to be used in calculating the amount of space to allocate for the
 * given number of elements.  Also, for compatibility with previous
 * versions of this class, constructors may optionally specify an
 * expected {@code concurrencyLevel} as an additional hint for
 * internal sizing.  Note that using many keys with exactly the same
 * {@code hashCode()} is a sure way to slow down performance of any
 * hash table. To ameliorate impact, when keys are {@link Comparable},
 * this class may use comparison order among keys to help break ties.
 * <p/>
 * <p>A ConcurrentHashMap can be used as a scalable frequency map (a
 * form of histogram or multiset) by using {@link
 * java.util.concurrent.atomic.LongAdder} values and initializing via
 * {@link #computeIfAbsent computeIfAbsent}. For example, to add a count
 * to a {@code ConcurrentHashMap<String,LongAdder> freqs}, you can use
 * {@code freqs.computeIfAbsent(key, k -> new LongAdder()).increment();}
 * <p/>
 * <p>This class and its views and iterators implement all of the
 * <em>optional</em> methods of the {@link Map} and {@link Iterator}
 * interfaces.
 * <p/>
 * <p>Like {@link Hashtable} but unlike {@link HashMap}, this class
 * does <em>not</em> allow {@code null} to be used as a key or value.
 * <p/>
 * <p>ConcurrentHashMaps support a set of sequential and parallel bulk
 * operations that, unlike most Stream methods, are designed
 * to be safely, and often sensibly, applied even with maps that are
 * being concurrently updated by other threads; for example, when
 * computing a snapshot summary of the values in a shared registry.
 * There are three kinds of operation, each with four forms, accepting
 * functions with Keys, Values, Entries, and (Key, Value) arguments
 * and/or return values. Because the elements of a ConcurrentHashMap
 * are not ordered in any particular way, and may be processed in
 * different orders in different parallel executions, the correctness
 * of supplied functions should not depend on any ordering, or on any
 * other objects or values that may transiently change while
 * computation is in progress; and except for forEach actions, should
 * ideally be side-effect-free. Bulk operations on {@link java.util.Map.Entry}
 * objects do not support method {@code setValue}.
 * <p/>
 * <ul>
 * <li> forEach: Perform a given action on each element.
 * A variant form applies a given transformation on each element
 * before performing the action.</li>
 * <p/>
 * <li> search: Return the first available non-null result of
 * applying a given function on each element; skipping further
 * search when a result is found.</li>
 * <p/>
 * <li> reduce: Accumulate each element.  The supplied reduction
 * function cannot rely on ordering (more formally, it should be
 * both associative and commutative).  There are five variants:
 * <p/>
 * <ul>
 * <p/>
 * <li> Plain reductions. (There is not a form of this method for
 * (key, value) function arguments since there is no corresponding
 * return type.)</li>
 * <p/>
 * <li> Mapped reductions that accumulate the results of a given
 * function applied to each element.</li>
 * <p/>
 * <li> Reductions to scalar doubles, longs, and ints, using a
 * given basis value.</li>
 * <p/>
 * </ul>
 * </li>
 * </ul>
 * <p/>
 * <p>These bulk operations accept a {@code parallelismThreshold}
 * argument. Methods proceed sequentially if the current map size is
 * estimated to be less than the given threshold. Using a value of
 * {@code Long.MAX_VALUE} suppresses all parallelism.  Using a value
 * of {@code 1} results in maximal parallelism by partitioning into
 * enough subtasks to fully utilize the
 * ForkJoinPool#commonPool() that is used for all parallel
 * computations. Normally, you would initially choose one of these
 * extreme values, and then measure performance of using in-between
 * values that trade off overhead versus throughput.
 * <p/>
 * <p>The concurrency properties of bulk operations follow
 * from those of ConcurrentHashMap: Any non-null result returned
 * from {@code get(key)} and related access methods bears a
 * happens-before relation with the associated insertion or
 * update.  The result of any bulk operation reflects the
 * composition of these per-element relations (but is not
 * necessarily atomic with respect to the map as a whole unless it
 * is somehow known to be quiescent).  Conversely, because keys
 * and values in the map are never null, null serves as a reliable
 * atomic indicator of the current lack of any result.  To
 * maintain this property, null serves as an implicit basis for
 * all non-scalar reduction operations. For the double, long, and
 * int versions, the basis should be one that, when combined with
 * any other value, returns that other value (more formally, it
 * should be the identity element for the reduction). Most common
 * reductions have these properties; for example, computing a sum
 * with basis 0 or a minimum with basis MAX_VALUE.
 * <p/>
 * <p>Search and transformation functions provided as arguments
 * should similarly return null to indicate the lack of any result
 * (in which case it is not used). In the case of mapped
 * reductions, this also enables transformations to serve as
 * filters, returning null (or, in the case of primitive
 * specializations, the identity basis) if the element should not
 * be combined. You can create compound transformations and
 * filterings by composing them yourself under this "null means
 * there is nothing there now" rule before using them in search or
 * reduce operations.
 * <p/>
 * <p>Methods accepting and/or returning Entry arguments maintain
 * key-value associations. They may be useful for example when
 * finding the key for the greatest value. Note that "plain" Entry
 * arguments can be supplied using {@code new
 * AbstractMap.SimpleEntry(k,v)}.
 * <p/>
 * <p>Bulk operations may complete abruptly, throwing an
 * exception encountered in the application of a supplied
 * function. Bear in mind when handling such exceptions that other
 * concurrently executing functions could also have thrown
 * exceptions, or would have done so if the first exception had
 * not occurred.
 * <p/>
 * <p>Speedups for parallel compared to sequential forms are common
 * but not guaranteed.  Parallel operations involving brief functions
 * on small maps may execute more slowly than sequential forms if the
 * underlying work to parallelize the computation is more expensive
 * than the computation itself.  Similarly, parallelization may not
 * lead to much actual parallelism if all processors are busy
 * performing unrelated tasks.
 * <p/>
 * <p>All arguments to all task methods must be non-null.
 * <p/>
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @author Doug Lea
 * @since 1.5
 * @deprecated Use {@link ContainerUtil#newConcurrentMap()} instead
 */
public final class ConcurrentHashMap<K, V> extends AbstractMap<K, V>
  implements ConcurrentMap<K, V>, TObjectHashingStrategy<K> {

    /*
     * Overview:
     *
     * The primary design goal of this hash table is to maintain
     * concurrent readability (typically method get(), but also
     * iterators and related methods) while minimizing update
     * contention. Secondary goals are to keep space consumption about
     * the same or better than java.util.HashMap, and to support high
     * initial insertion rates on an empty table by many threads.
     *
     * This map usually acts as a binned (bucketed) hash table.  Each
     * key-value mapping is held in a Node.  Most nodes are instances
     * of the basic Node class with hash, key, value, and next
     * fields. However, various subclasses exist: TreeNodes are
     * arranged in balanced trees, not lists.  TreeBins hold the roots
     * of sets of TreeNodes. ForwardingNodes are placed at the heads
     * of bins during resizing. ReservationNodes are used as
     * placeholders while establishing values in computeIfAbsent and
     * related methods.  The types TreeBin, ForwardingNode, and
     * ReservationNode do not hold normal user keys, values, or
     * hashes, and are readily distinguishable during search etc
     * because they have negative hash fields and null key and value
     * fields. (These special nodes are either uncommon or transient,
     * so the impact of carrying around some unused fields is
     * insignificant.)
     *
     * The table is lazily initialized to a power-of-two size upon the
     * first insertion.  Each bin in the table normally contains a
     * list of Nodes (most often, the list has only zero or one Node).
     * Table accesses require volatile/atomic reads, writes, and
     * CASes.  Because there is no other way to arrange this without
     * adding further indirections, we use intrinsics
     * (sun.misc.Unsafe) operations.
     *
     * We use the top (sign) bit of Node hash fields for control
     * purposes -- it is available anyway because of addressing
     * constraints.  Nodes with negative hash fields are specially
     * handled or ignored in map methods.
     *
     * Insertion (via put or its variants) of the first node in an
     * empty bin is performed by just CASing it to the bin.  This is
     * by far the most common case for put operations under most
     * key/hash distributions.  Other update operations (insert,
     * delete, and replace) require locks.  We do not want to waste
     * the space required to associate a distinct lock object with
     * each bin, so instead use the first node of a bin list itself as
     * a lock. Locking support for these locks relies on builtin
     * "synchronized" monitors.
     *
     * Using the first node of a list as a lock does not by itself
     * suffice though: When a node is locked, any update must first
     * validate that it is still the first node after locking it, and
     * retry if not. Because new nodes are always appended to lists,
     * once a node is first in a bin, it remains first until deleted
     * or the bin becomes invalidated (upon resizing).
     *
     * The main disadvantage of per-bin locks is that other update
     * operations on other nodes in a bin list protected by the same
     * lock can stall, for example when user equals() or mapping
     * functions take a long time.  However, statistically, under
     * random hash codes, this is not a common problem.  Ideally, the
     * frequency of nodes in bins follows a Poisson distribution
     * (http://en.wikipedia.org/wiki/Poisson_distribution) with a
     * parameter of about 0.5 on average, given the resizing threshold
     * of 0.75, although with a large variance because of resizing
     * granularity. Ignoring variance, the expected occurrences of
     * list size k are (exp(-0.5) * pow(0.5, k) / factorial(k)). The
     * first values are:
     *
     * 0:    0.60653066
     * 1:    0.30326533
     * 2:    0.07581633
     * 3:    0.01263606
     * 4:    0.00157952
     * 5:    0.00015795
     * 6:    0.00001316
     * 7:    0.00000094
     * 8:    0.00000006
     * more: less than 1 in ten million
     *
     * Lock contention probability for two threads accessing distinct
     * elements is roughly 1 / (8 * #elements) under random hashes.
     *
     * Actual hash code distributions encountered in practice
     * sometimes deviate significantly from uniform randomness.  This
     * includes the case when N > (1<<30), so some keys MUST collide.
     * Similarly for dumb or hostile usages in which multiple keys are
     * designed to have identical hash codes or ones that differs only
     * in masked-out high bits. So we use a secondary strategy that
     * applies when the number of nodes in a bin exceeds a
     * threshold. These TreeBins use a balanced tree to hold nodes (a
     * specialized form of red-black trees), bounding search time to
     * O(log N).  Each search step in a TreeBin is at least twice as
     * slow as in a regular list, but given that N cannot exceed
     * (1<<64) (before running out of addresses) this bounds search
     * steps, lock hold times, etc, to reasonable constants (roughly
     * 100 nodes inspected per operation worst case) so long as keys
     * are Comparable (which is very common -- String, Long, etc).
     * TreeBin nodes (TreeNodes) also maintain the same "next"
     * traversal pointers as regular nodes, so can be traversed in
     * iterators in the same way.
     *
     * The table is resized when occupancy exceeds a percentage
     * threshold (nominally, 0.75, but see below).  Any thread
     * noticing an overfull bin may assist in resizing after the
     * initiating thread allocates and sets up the replacement array.
     * However, rather than stalling, these other threads may proceed
     * with insertions etc.  The use of TreeBins shields us from the
     * worst case effects of overfilling while resizes are in
     * progress.  Resizing proceeds by transferring bins, one by one,
     * from the table to the next table. However, threads claim small
     * blocks of indices to transfer (via field transferIndex) before
     * doing so, reducing contention.  A generation stamp in field
     * sizeCtl ensures that resizings do not overlap. Because we are
     * using power-of-two expansion, the elements from each bin must
     * either stay at same index, or move with a power of two
     * offset. We eliminate unnecessary node creation by catching
     * cases where old nodes can be reused because their next fields
     * won't change.  On average, only about one-sixth of them need
     * cloning when a table doubles. The nodes they replace will be
     * garbage collectable as soon as they are no longer referenced by
     * any reader thread that may be in the midst of concurrently
     * traversing table.  Upon transfer, the old table bin contains
     * only a special forwarding node (with hash field "MOVED") that
     * contains the next table as its key. On encountering a
     * forwarding node, access and update operations restart, using
     * the new table.
     *
     * Each bin transfer requires its bin lock, which can stall
     * waiting for locks while resizing. However, because other
     * threads can join in and help resize rather than contend for
     * locks, average aggregate waits become shorter as resizing
     * progresses.  The transfer operation must also ensure that all
     * accessible bins in both the old and new table are usable by any
     * traversal.  This is arranged in part by proceeding from the
     * last bin (table.length - 1) up towards the first.  Upon seeing
     * a forwarding node, traversals (see class Traverser) arrange to
     * move to the new table without revisiting nodes.  To ensure that
     * no intervening nodes are skipped even when moved out of order,
     * a stack (see class TableStack) is created on first encounter of
     * a forwarding node during a traversal, to maintain its place if
     * later processing the current table. The need for these
     * save/restore mechanics is relatively rare, but when one
     * forwarding node is encountered, typically many more will be.
     * So Traversers use a simple caching scheme to avoid creating so
     * many new TableStack nodes. (Thanks to Peter Levart for
     * suggesting use of a stack here.)
     *
     * The traversal scheme also applies to partial traversals of
     * ranges of bins (via an alternate Traverser constructor)
     * to support partitioned aggregate operations.  Also, read-only
     * operations give up if ever forwarded to a null table, which
     * provides support for shutdown-style clearing, which is also not
     * currently implemented.
     *
     * Lazy table initialization minimizes footprint until first use,
     * and also avoids resizings when the first operation is from a
     * putAll, constructor with map argument, or deserialization.
     * These cases attempt to override the initial capacity settings,
     * but harmlessly fail to take effect in cases of races.
     *
     * The element count is maintained using a specialization of
     * LongAdder. We need to incorporate a specialization rather than
     * just use a LongAdder in order to access implicit
     * contention-sensing that leads to creation of multiple
     * CounterCells.  The counter mechanics avoid contention on
     * updates but can encounter cache thrashing if read too
     * frequently during concurrent access. To avoid reading so often,
     * resizing under contention is attempted only upon adding to a
     * bin already holding two or more nodes. Under uniform hash
     * distributions, the probability of this occurring at threshold
     * is around 13%, meaning that only about 1 in 8 puts check
     * threshold (and after resizing, many fewer do so).
     *
     * TreeBins use a special form of comparison for search and
     * related operations (which is the main reason we cannot use
     * existing collections such as TreeMaps). TreeBins contain
     * Comparable elements, but may contain others, as well as
     * elements that are Comparable but not necessarily Comparable for
     * the same T, so we cannot invoke compareTo among them. To handle
     * this, the tree is ordered primarily by hash value, then by
     * Comparable.compareTo order if applicable.  On lookup at a node,
     * if elements are not comparable or compare as 0 then both left
     * and right children may need to be searched in the case of tied
     * hash values. (This corresponds to the full list search that
     * would be necessary if all elements were non-Comparable and had
     * tied hashes.) On insertion, to keep a total ordering (or as
     * close as is required here) across rebalancings, we compare
     * classes and identityHashCodes as tie-breakers. The red-black
     * balancing code is updated from pre-jdk-collections
     * (http://gee.cs.oswego.edu/dl/classes/collections/RBCell.java)
     * based in turn on Cormen, Leiserson, and Rivest "Introduction to
     * Algorithms" (CLR).
     *
     * TreeBins also require an additional locking mechanism.  While
     * list traversal is always possible by readers even during
     * updates, tree traversal is not, mainly because of tree-rotations
     * that may change the root node and/or its linkages.  TreeBins
     * include a simple read-write lock mechanism parasitic on the
     * main bin-synchronization strategy: Structural adjustments
     * associated with an insertion or removal are already bin-locked
     * (and so cannot conflict with other writers) but must wait for
     * ongoing readers to finish. Since there can be only one such
     * waiter, we use a simple scheme using a single "waiter" field to
     * block writers.  However, readers need never block.  If the root
     * lock is held, they proceed along the slow traversal path (via
     * next-pointers) until the lock becomes available or the list is
     * exhausted, whichever comes first. These cases are not fast, but
     * maximize aggregate expected throughput.
     *
     * Maintaining API and serialization compatibility with previous
     * versions of this class introduces several oddities. Mainly: We
     * leave untouched but unused constructor arguments refering to
     * concurrencyLevel. We accept a loadFactor constructor argument,
     * but apply it only to initial table capacity (which is the only
     * time that we can guarantee to honor it.) We also declare an
     * unused "Segment" class that is instantiated in minimal form
     * only when serializing.
     *
     * Also, solely for compatibility with previous versions of this
     * class, it extends AbstractMap, even though all of its methods
     * are overridden, so it is just useless baggage.
     *
     * This file is organized to make things a little easier to follow
     * while reading than they might otherwise: First the main static
     * declarations and utilities, then fields, then main public
     * methods (with a few factorings of multiple public methods into
     * internal ones), then sizing methods, trees, traversers, and
     * bulk operations.
     */

    /* ---------------- Constants -------------- */

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
  static final int DEFAULT_CAPACITY = 16;

  /**
   * The largest possible (non-power of two) array size.
   * Needed by toArray and related methods.
   */
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

  /**
   * The load factor for this table. Overrides of this value in
   * constructors affect only the initial table capacity.  The
   * actual floating point value isn't normally used -- it is
   * simpler to use expressions such as {@code n - (n >>> 2)} for
   * the associated resizing threshold.
   */
  static final float LOAD_FACTOR = 0.75f;

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
  private static final int MOVED = -1; // hash for forwarding nodes
  private static final int TREEBIN = -2; // hash for roots of trees
  private static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash

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
  private static class Node<K, V> implements Map.Entry<K, V> {
    final int hash;
    final K key;
    volatile V val;
    volatile Node<K, V> next;
    @NotNull final TObjectHashingStrategy<K> myHashingStrategy;

    Node(int hash, K key, V val, Node<K, V> next, @NotNull TObjectHashingStrategy<K> hashingStrategy) {
      this.hash = hash;
      this.key = key;
      this.val = val;
      this.next = next;
      myHashingStrategy = hashingStrategy;
    }

    @Override
    public final K getKey() {
      return key;
    }

    @Override
    public final V getValue() {
      return val;
    }

    @Override
    public final int hashCode() {
      return key.hashCode() ^ val.hashCode();
    }

    @Override
    public final String toString() {
      return key + "=" + val;
    }

    @Override
    public final V setValue(V value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public final boolean equals(Object o) {
      Object k;
      Object v;
      Object u;
      Map.Entry<?, ?> e;
      return o instanceof Entry &&
             (k = (e = (Entry<?, ?>)o).getKey()) != null &&
             (v = e.getValue()) != null &&
             (k == key || myHashingStrategy.equals((K)k, key)) &&
             (v == (u = val) || v.equals(u));
    }

    /**
     * Virtualized support for map.get(); overridden in subclasses.
     */
    Node<K, V> find(int h, Object k) {
      Node<K, V> e = this;
      if (k != null) {
        do {
          K ek;
          if (e.hash == h &&
              ((ek = e.key) == k || ek != null && myHashingStrategy.equals((K)k, ek))) {
            return e;
          }
        }
        while ((e = e.next) != null);
      }
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
  private static int spread(int h) {
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

  /**
   * Returns x's Class if it is of the form "class C implements
   * Comparable<C>", else null.
   */
  private static Class<?> comparableClassFor(Object x) {
    if (x instanceof Comparable) {
      Class<?> c;
      Type[] ts, as;
      Type t;
      ParameterizedType p;
      if ((c = x.getClass()) == String.class) // bypass checks
      {
        return c;
      }
      if ((ts = c.getGenericInterfaces()) != null) {
        for (int i = 0; i < ts.length; ++i) {
          if (((t = ts[i]) instanceof ParameterizedType) &&
              ((p = (ParameterizedType)t).getRawType() ==
               Comparable.class) &&
              (as = p.getActualTypeArguments()) != null &&
              as.length == 1 && as[0] == c) // type arg is c
          {
            return c;
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns k.compareTo(x) if x matches kc (k's screened comparable
   * class), else 0.
   */
  @SuppressWarnings({"rawtypes", "unchecked"}) // for cast to Comparable
  private static int compareComparables(Class<?> kc, Object k, Object x) {
    return (x == null || x.getClass() != kc ? 0 :
            ((Comparable)k).compareTo(x));
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
  private static <K, V> Node<K, V> tabAt(Node<K, V>[] tab, int i) {
    return (Node<K, V>)U.getObjectVolatile(tab, ((long)i << ASHIFT) + ABASE);
  }

  private static <K, V> boolean casTabAt(Node<K, V>[] tab, int i,
                                         Node<K, V> c, Node<K, V> v) {
    return U.compareAndSwapObject(tab, ((long)i << ASHIFT) + ABASE, c, v);
  }

  private static <K, V> void setTabAt(Node<K, V>[] tab, int i, Node<K, V> v) {
    U.putObjectVolatile(tab, ((long)i << ASHIFT) + ABASE, v);
  }

    /* ---------------- Fields -------------- */

  /**
   * The array of bins. Lazily initialized upon first insertion.
   * Size is always a power of two. Accessed directly by iterators.
   */
  private transient volatile Node<K, V>[] table;

  /**
   * The next table to use; non-null only while resizing.
   */
  private transient volatile Node<K, V>[] nextTable;

  /**
   * Base counter value, used mainly when there is no contention,
   * but also as a fallback during table initialization
   * races. Updated via CAS.
   */
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
  private transient volatile CounterCell[] counterCells;

  // views
  private transient KeySetView<K, V> keySet;
  private transient ValuesView<K, V> values;
  private transient EntrySetView<K, V> entrySet;

  @NotNull private final TObjectHashingStrategy<K> myHashingStrategy;

    /* ---------------- Public operations -------------- */

  /**
   * Creates a new, empty map with the default initial table size (16).
   */
  public ConcurrentHashMap() {
    this(DEFAULT_CAPACITY);
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
  public ConcurrentHashMap(int initialCapacity) {
    this(initialCapacity, LOAD_FACTOR);
  }

  /**
   * Creates a new map with the same mappings as the given map.
   *
   * @param m the map
   */
  public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
    this(DEFAULT_CAPACITY);
    putAll(m);
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
  public ConcurrentHashMap(int initialCapacity, float loadFactor) {
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
  public ConcurrentHashMap(int initialCapacity,
             float loadFactor, int concurrencyLevel) {
    this(initialCapacity, loadFactor, concurrencyLevel, THIS);
  }

  private static final TObjectHashingStrategy THIS = new TObjectHashingStrategy() {
    @Override
    public int computeHashCode(Object object) {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean equals(Object o1, Object o2) {
      throw new IncorrectOperationException();
    }
  };

  public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel, @NotNull TObjectHashingStrategy<K> hashingStrategy) {
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
    this.sizeCtl = cap;
    myHashingStrategy = hashingStrategy == THIS ? this : hashingStrategy;
  }

  public ConcurrentHashMap(@NotNull TObjectHashingStrategy<K> hashingStrategy) {
    this(DEFAULT_CAPACITY, LOAD_FACTOR, NCPU, hashingStrategy);
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
   *
   * @throws NullPointerException if the specified key is null
   */
  @Override
  public V get(@NotNull Object key) {
    Node<K, V>[] tab;
    Node<K, V> e, p;
    int n, eh;
    int h = hash((K)key);
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (e = tabAt(tab, (n - 1) & h)) != null) {
      if ((eh = e.hash) == h) {
        if (isEqual((K)key, e.key)) {
          return e.val;
        }
      }
      else if (eh < 0) {
        return (p = e.find(h, key)) != null ? p.val : null;
      }
      while ((e = e.next) != null) {
        if (e.hash == h &&
            (isEqual((K)key, e.key))) {
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
  public boolean containsKey(Object key) {
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
  public boolean containsValue(@NotNull Object value) {
    Node<K, V>[] t;
    if ((t = table) != null) {
      Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
      for (Node<K, V> p; (p = it.advance()) != null; ) {
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
   * @throws NullPointerException if the specified key or value is null
   */
  @Override
  public V put(@NotNull K key, @NotNull V value) {
    return putVal(key, value, false);
  }

  /**
   * Implementation for put and putIfAbsent
   */
  private V putVal(@NotNull K key, @NotNull V value, boolean onlyIfAbsent) {
    int hash = hash((K)key);
    int binCount = 0;
    for (Node<K, V>[] tab = table; ; ) {
      Node<K, V> f;
      int n, i, fh;
      if (tab == null || (n = tab.length) == 0) {
        tab = initTable();
      }
      else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
        if (casTabAt(tab, i, null,
                     new Node<K, V>(hash, key, value, null, myHashingStrategy))) {
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
              for (Node<K, V> e = f; ; ++binCount) {
                if (e.hash == hash &&
                    (isEqual((K)key, e.key))) {
                  oldVal = e.val;
                  if (!onlyIfAbsent) {
                    e.val = value;
                  }
                  break;
                }
                Node<K, V> pred = e;
                if ((e = e.next) == null) {
                  pred.next = new Node<K, V>(hash, key,
                                             value, null, myHashingStrategy);
                  break;
                }
              }
            }
            else if (f instanceof TreeBin) {
              Node<K, V> p;
              binCount = 2;
              if ((p = ((TreeBin<K, V>)f).putTreeVal(hash, key,
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
   * Copies all of the mappings from the specified map to this one.
   * These mappings replace any mappings that this map had for any of the
   * keys currently in the specified map.
   *
   * @param m mappings to be stored in this map
   */
  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    tryPresize(m.size());
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      putVal(e.getKey(), e.getValue(), false);
    }
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
  public V remove(Object key) {
    return replaceNode(key, null, null);
  }

  /**
   * Implementation for the four public remove/replace methods:
   * Replaces node value with v, conditional upon match of cv if
   * non-null.  If resulting value is null, delete.
   */
  private V replaceNode(Object key, V value, Object cv) {
    int hash = hash((K)key);
    for (Node<K, V>[] tab = table; ; ) {
      Node<K, V> f;
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
              for (Node<K, V> e = f, pred = null; ; ) {
                if (e.hash == hash &&
                    isEqual((K)key, e.key)) {
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
            else if (f instanceof TreeBin) {
              validated = true;
              TreeBin<K, V> t = (TreeBin<K, V>)f;
              TreeNode<K, V> r, p;
              if ((r = t.root) != null &&
                  (p = r.findTreeNode(hash, key, null)) != null) {
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
    if (isEmpty()) return;
    
    long delta = 0L; // negative number of deletions
    int i = 0;
    Node<K, V>[] tab = table;
    while (tab != null && i < tab.length) {
      int fh;
      Node<K, V> f = tabAt(tab, i);
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
            Node<K, V> p = (fh >= 0 ? f :
                            (f instanceof TreeBin) ?
                            ((TreeBin<K, V>)f).first : null);
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
   * Returns a {@link Set} view of the keys contained in this map.
   * The set is backed by the map, so changes to the map are
   * reflected in the set, and vice-versa. The set supports element
   * removal, which removes the corresponding mapping from this map,
   * via the {@code Iterator.remove}, {@code Set.remove},
   * {@code removeAll}, {@code retainAll}, and {@code clear}
   * operations.  It does not support the {@code add} or
   * {@code addAll} operations.
   * <p/>
   * <p>The view's iterators and spliterators are
   * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
   * <p/>
   * <p>The view's {@code spliterator} reports {@link Spliterator#CONCURRENT},
   * {@link Spliterator#DISTINCT}, and {@link Spliterator#NONNULL}.
   *
   * @return the set view
   */
  @Override
  public KeySetView<K, V> keySet() {
    KeySetView<K, V> ks;
    return (ks = keySet) != null ? ks : (keySet = new KeySetView<K, V>(this, null));
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
   * <p>The view's {@code spliterator} reports {@link Spliterator#CONCURRENT}
   * and {@link Spliterator#NONNULL}.
   *
   * @return the collection view
   */
  @Override
  public Collection<V> values() {
    ValuesView<K, V> vs;
    return (vs = values) != null ? vs : (values = new ValuesView<K, V>(this));
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
   * <p>The view's {@code spliterator} reports {@link Spliterator#CONCURRENT},
   * {@link Spliterator#DISTINCT}, and {@link Spliterator#NONNULL}.
   *
   * @return the set view
   */
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    EntrySetView<K, V> es;
    return (es = entrySet) != null ? es : (entrySet = new EntrySetView<K, V>(this));
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
    Node<K, V>[] t;
    if ((t = table) != null) {
      Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
      for (Node<K, V> p; (p = it.advance()) != null; ) {
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
    Node<K, V>[] t;
    int f = (t = table) == null ? 0 : t.length;
    Traverser<K, V> it = new Traverser<K, V>(t, f, 0, f);
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    Node<K, V> p;
    if ((p = it.advance()) != null) {
      for (; ; ) {
        K k = p.key;
        V v = p.val;
        sb.append(k == this ? "(this Map)" : k);
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
      if (!(o instanceof Map)) {
        return false;
      }
      Map<?, ?> m = (Map<?, ?>)o;
      Node<K, V>[] t;
      int f = (t = table) == null ? 0 : t.length;
      Traverser<K, V> it = new Traverser<K, V>(t, f, 0, f);
      for (Node<K, V> p; (p = it.advance()) != null; ) {
        V val = p.val;
        Object v = m.get(p.key);
        if (v == null || (v != val && !v.equals(val))) {
          return false;
        }
      }
      for (Map.Entry<?, ?> e : m.entrySet()) {
        Object mk, mv, v;
        if ((mk = e.getKey()) == null ||
            (mv = e.getValue()) == null ||
            (v = get(mk)) == null ||
            (mv != v && !mv.equals(v))) {
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
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    return putVal(key, value, true);
  }

  /**
   * {@inheritDoc}
   *
   * @throws NullPointerException if the specified key is null
   */
  @Override
  public boolean remove(@NotNull Object key, Object value) {
    return value != null && replaceNode(key, null, value) != null;
  }

  /**
   * {@inheritDoc}
   *
   * @throws NullPointerException if any of the arguments are null
   */
  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
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
  public V replace(@NotNull K key, @NotNull V value) {
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
   * @throws NullPointerException if the specified key is null
   */
  @SuppressWarnings("override") //no method in JDK6
  public V getOrDefault(@NotNull Object key, V defaultValue) {
    V v;
    return (v = get(key)) == null ? defaultValue : v;
  }


  // Hashtable legacy methods


  /**
   * Returns an enumeration of the keys in this table.
   *
   * @return an enumeration of the keys in this table
   * @see #keySet()
   */
  public Enumeration<K> keys() {
    Node<K, V>[] t;
    int f = (t = table) == null ? 0 : t.length;
    return new KeyIterator<K, V>(t, f, 0, f, this);
  }

  /**
   * Returns an enumeration of the values in this table.
   *
   * @return an enumeration of the values in this table
   * @see #values()
   */
  public Enumeration<V> elements() {
    Node<K, V>[] t;
    int f = (t = table) == null ? 0 : t.length;
    return new ValueIterator<K, V>(t, f, 0, f, this);
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
   * @since 1.8
   */
  private long mappingCount() {
    long n = sumCount();
    return (n < 0L) ? 0L : n; // ignore transient negative values
  }

  /**
   * Creates a new {@link Set} backed by a ConcurrentHashMap
   * from the given type to {@code Boolean.TRUE}.
   *
   * @param <K> the element type of the returned set
   * @return the new set
   * @since 1.8
   */
  private static <K> KeySetView<K, Boolean> newKeySet() {
    return new KeySetView<K, Boolean>
      (new ConcurrentHashMap<K, Boolean>(), Boolean.TRUE);
  }


    /* ---------------- Special Nodes -------------- */

  /**
   * A node inserted at head of bins during transfer operations.
   */
  private static final class ForwardingNode<K, V> extends Node<K, V> {
    private final Node<K, V>[] nextTable;

    private ForwardingNode(Node<K, V>[] tab, @NotNull TObjectHashingStrategy<K> hashingStrategy) {
      super(MOVED, null, null, null, hashingStrategy);
      this.nextTable = tab;
    }

    @Override
    Node<K, V> find(int h, Object k) {
      // loop to avoid arbitrarily deep recursion on forwarding nodes
      outer:
      for (Node<K, V>[] tab = nextTable; ; ) {
        Node<K, V> e;
        int n;
        if (k == null || tab == null || (n = tab.length) == 0 ||
            (e = tabAt(tab, (n - 1) & h)) == null) {
          return null;
        }
        for (; ; ) {
          int eh;
          if ((eh = e.hash) == h &&
              (isEqual((K)k, e.key, myHashingStrategy))) {
            return e;
          }
          if (eh < 0) {
            if (e instanceof ForwardingNode) {
              tab = ((ForwardingNode<K, V>)e).nextTable;
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
  private static int resizeStamp(int n) {
    return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
  }

  /**
   * Initializes table, using the size recorded in sizeCtl.
   */
  private Node<K, V>[] initTable() {
    Node<K, V>[] tab;
    int sc;
    while ((tab = table) == null || tab.length == 0) {
      if ((sc = sizeCtl) < 0) {
        Thread.yield(); // lost initialization race; just spin
      }
      else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
        try {
          if ((tab = table) == null || tab.length == 0) {
            int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
            @SuppressWarnings("unchecked")
            Node<K, V>[] nt = (Node<K, V>[])new Node<?, ?>[n];
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
    CounterCell[] as;
    long b, s;
    if ((as = counterCells) != null ||
        !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
      CounterCell a;
      long v;
      int m;
      boolean uncontended = true;
      if (as == null || (m = as.length - 1) < 0 ||
          (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
          !(uncontended =
              U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
        fullAddCount(x, uncontended);
        return;
      }
      if (check <= 1) {
        return;
      }
      s = sumCount();
    }
    if (check >= 0) {
      Node<K, V>[] tab, nt;
      int n, sc;
      while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
             (n = tab.length) < MAXIMUM_CAPACITY) {
        int rs = resizeStamp(n);
        if (sc < 0) {
          if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
              sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
              transferIndex <= 0) {
            break;
          }
          if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) {
            transfer(tab, nt);
          }
        }
        else if (U.compareAndSwapInt(this, SIZECTL, sc,
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
  private Node<K, V>[] helpTransfer(Node<K, V>[] tab, Node<K, V> f) {
    Node<K, V>[] nextTab;
    int sc;
    if (tab != null && (f instanceof ForwardingNode) &&
        (nextTab = ((ForwardingNode<K, V>)f).nextTable) != null) {
      int rs = resizeStamp(tab.length);
      while (nextTab == nextTable && table == tab &&
             (sc = sizeCtl) < 0) {
        if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
            sc == rs + MAX_RESIZERS || transferIndex <= 0) {
          break;
        }
        if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) {
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
      Node<K, V>[] tab = table;
      int n;
      if (tab == null || (n = tab.length) == 0) {
        n = (sc > c) ? sc : c;
        if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
          try {
            if (table == tab) {
              @SuppressWarnings("unchecked")
              Node<K, V>[] nt = (Node<K, V>[])new Node<?, ?>[n];
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
        if (sc < 0) {
            Node<K,V>[] nt;
            if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
                transferIndex <= 0)
                break;
            if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                transfer(tab, nt);
        }
        else if (U.compareAndSwapInt(this, SIZECTL, sc,
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
  private void transfer(Node<K, V>[] tab, Node<K, V>[] nextTab) {
    int n = tab.length, stride;
    if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE) {
      stride = MIN_TRANSFER_STRIDE; // subdivide range
    }
    if (nextTab == null) {            // initiating
      try {
        @SuppressWarnings("unchecked")
        Node<K, V>[] nt = (Node<K, V>[])new Node<?, ?>[n << 1];
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
    ForwardingNode<K, V> fwd = new ForwardingNode<K, V>(nextTab, myHashingStrategy);
    boolean advance = true;
    boolean finishing = false; // to ensure sweep before committing nextTab
    for (int i = 0, bound = 0; ; ) {
      Node<K, V> f;
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
        else if (U.compareAndSwapInt
          (this, TRANSFERINDEX, nextIndex,
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
        if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
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
            Node<K, V> ln, hn;
            if (fh >= 0) {
              int runBit = fh & n;
              Node<K, V> lastRun = f;
              for (Node<K, V> p = f.next; p != null; p = p.next) {
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
              for (Node<K, V> p = f; p != lastRun; p = p.next) {
                int ph = p.hash;
                K pk = p.key;
                V pv = p.val;
                if ((ph & n) == 0) {
                  ln = new Node<K, V>(ph, pk, pv, ln, myHashingStrategy);
                }
                else {
                  hn = new Node<K, V>(ph, pk, pv, hn, myHashingStrategy);
                }
              }
              setTabAt(nextTab, i, ln);
              setTabAt(nextTab, i + n, hn);
              setTabAt(tab, i, fwd);
              advance = true;
            }
            else if (f instanceof TreeBin) {
              TreeBin<K, V> t = (TreeBin<K, V>)f;
              TreeNode<K, V> lo = null, loTail = null;
              TreeNode<K, V> hi = null, hiTail = null;
              int lc = 0, hc = 0;
              for (Node<K, V> e = t.first; e != null; e = e.next) {
                int h = e.hash;
                TreeNode<K, V> p = new TreeNode<K, V>(h, e.key, e.val, null, null, myHashingStrategy);
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
                   (hc != 0) ? new TreeBin<K, V>(lo, myHashingStrategy) : t;
              hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
                   (lc != 0) ? new TreeBin<K, V>(hi, myHashingStrategy) : t;
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

  /**
   * A padded cell for distributing counts.  Adapted from LongAdder
   * and Striped64.  See their internal docs for explanation.
   */
  static final class CounterCell {
    volatile long p0;
    volatile long p1;
    volatile long p2;
    volatile long p3;
    volatile long p4;
    volatile long p5;
    volatile long p6;
    volatile long value;
    volatile long q0;
    volatile long q1;
    volatile long q2;
    volatile long q3;
    volatile long q4;
    volatile long q5;
    volatile long q6;

    CounterCell(long x) {
      value = x;
    }
  }

  private long sumCount() {
    CounterCell[] as = counterCells;
    CounterCell a;
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
      CounterCell[] as;
      CounterCell a;
      int n;
      long v;
      if ((as = counterCells) != null && (n = as.length) > 0) {
        if ((a = as[(n - 1) & h]) == null) {
          if (cellsBusy == 0) {            // Try to attach new Cell
            CounterCell r = new CounterCell(x); // Optimistic create
            if (cellsBusy == 0 &&
                U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
              boolean created = false;
              try {               // Recheck under lock
                CounterCell[] rs;
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
              CounterCell[] rs = new CounterCell[n << 1];
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
               U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
        boolean init = false;
        try {                           // Initialize table
          if (counterCells == as) {
            CounterCell[] rs = new CounterCell[2];
            rs[h & 1] = new CounterCell(x);
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
  }

    /* ---------------- Conversion from/to TreeBins -------------- */

  /**
   * Replaces all linked nodes in bin at given index unless table is
   * too small, in which case resizes instead.
   */
  private void treeifyBin(Node<K, V>[] tab, int index) {
    Node<K, V> b;
    int n;
    if (tab != null) {
      if ((n = tab.length) < MIN_TREEIFY_CAPACITY) {
        tryPresize(n << 1);
      }
      else if ((b = tabAt(tab, index)) != null && b.hash >= 0) {
        synchronized (b) {
          if (tabAt(tab, index) == b) {
            TreeNode<K, V> hd = null, tl = null;
            for (Node<K, V> e = b; e != null; e = e.next) {
              TreeNode<K, V> p =
                new TreeNode<K, V>(e.hash, e.key, e.val,
                                   null, null, myHashingStrategy);
              if ((p.prev = tl) == null) {
                hd = p;
              }
              else {
                tl.next = p;
              }
              tl = p;
            }
            setTabAt(tab, index, new TreeBin<K, V>(hd, myHashingStrategy));
          }
        }
      }
    }
  }

  /**
   * Returns a list on non-TreeNodes replacing those in given list.
   */
  private static <K, V> Node<K, V> untreeify(Node<K, V> b) {
    Node<K, V> hd = null, tl = null;
    for (Node<K, V> q = b; q != null; q = q.next) {
      Node<K, V> p = new Node<K, V>(q.hash, q.key, q.val, null, q.myHashingStrategy);
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
  private static final class TreeNode<K, V> extends Node<K, V> {
    private TreeNode<K, V> parent;  // red-black tree links
    private TreeNode<K, V> left;
    private TreeNode<K, V> right;
    private TreeNode<K, V> prev;    // needed to unlink next upon deletion
    private boolean red;

    TreeNode(int hash, K key, V val, Node<K, V> next,
             TreeNode<K, V> parent, TObjectHashingStrategy<K> hashingStrategy) {
      super(hash, key, val, next, hashingStrategy);
      this.parent = parent;
    }

    @Override
    Node<K, V> find(int h, Object k) {
      return findTreeNode(h, k, null);
    }

    /**
     * Returns the TreeNode (or null if not found) for the given key
     * starting at given root.
     */
    private TreeNode<K, V> findTreeNode(int h, Object k, Class<?> kc) {
      if (k != null) {
        TreeNode<K, V> p = this;
        do {
          int ph, dir;
          K pk = p.key;
          TreeNode<K, V> q;
          TreeNode<K, V> pl = p.left, pr = p.right;
          if ((ph = p.hash) > h) {
            p = pl;
          }
          else if (ph < h) {
            p = pr;
          }
          else if (isEqual((K)k, pk, myHashingStrategy)) {
            return p;
          }
          else if (pl == null) {
            p = pr;
          }
          else if (pr == null) {
            p = pl;
          }
          else if ((kc != null ||
                    (kc = comparableClassFor(k)) != null) &&
                   (dir = compareComparables(kc, k, pk)) != 0) {
            p = (dir < 0) ? pl : pr;
          }
          else if ((q = pr.findTreeNode(h, k, kc)) != null) {
            return q;
          }
          else {
            p = pl;
          }
        }
        while (p != null);
      }
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
  private static final class TreeBin<K, V> extends Node<K, V> {
    private TreeNode<K, V> root;
    private volatile TreeNode<K, V> first;
    private volatile Thread waiter;
    private volatile int lockState;
    // values for lockState
    private static final int WRITER = 1; // set while holding write lock
    private static final int WAITER = 2; // set when waiting for write lock
    private static final int READER = 4; // increment value for setting read lock

    /**
     * Tie-breaking utility for ordering insertions when equal
     * hashCodes and non-comparable. We don't require a total
     * order, just a consistent insertion rule to maintain
     * equivalence across rebalancings. Tie-breaking further than
     * necessary simplifies testing a bit.
     */
    private static int tieBreakOrder(Object a, Object b) {
      int d;
      if (a == null || b == null ||
          (d = a.getClass().getName().
            compareTo(b.getClass().getName())) == 0) {
        d = (System.identityHashCode(a) <= System.identityHashCode(b) ?
             -1 : 1);
      }
      return d;
    }

    /**
     * Creates bin with initial set of nodes headed by b.
     */
    private TreeBin(TreeNode<K, V> b, TObjectHashingStrategy<K> hashingStrategy) {
      super(TREEBIN, null, null, null, hashingStrategy);
      this.first = b;
      TreeNode<K, V> r = null;
      for (TreeNode<K, V> x = b, next; x != null; x = next) {
        next = (TreeNode<K, V>)x.next;
        x.left = x.right = null;
        if (r == null) {
          x.parent = null;
          x.red = false;
          r = x;
        }
        else {
          K k = x.key;
          int h = x.hash;
          Class<?> kc = null;
          for (TreeNode<K, V> p = r; ; ) {
            int dir, ph;
            K pk = p.key;
            if ((ph = p.hash) > h) {
              dir = -1;
            }
            else if (ph < h) {
              dir = 1;
            }
            else if ((kc == null &&
                      (kc = comparableClassFor(k)) == null) ||
                     (dir = compareComparables(kc, k, pk)) == 0) {
              dir = tieBreakOrder(k, pk);
            }
            TreeNode<K, V> xp = p;
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
      this.root = r;
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
      for (int s; ; ) {
        if (((s = lockState) & ~WAITER) == 0) {
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
    final Node<K, V> find(int h, Object k) {
      if (k != null) {
        for (Node<K, V> e = first; e != null; ) {
          int s;
          if (((s = lockState) & (WAITER | WRITER)) != 0) {
            if (e.hash == h &&
                isEqual((K)k, e.key, myHashingStrategy)) {
              return e;
            }
            e = e.next;
          }
          else if (U.compareAndSwapInt(this, LOCKSTATE, s,
                                       s + READER)) {
            TreeNode<K, V> r, p;
            try {
              p = ((r = root) == null ? null :
                   r.findTreeNode(h, k, null));
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
      }
      return null;
    }

    /**
     * Finds or adds a node.
     *
     * @return null if added
     */
    private TreeNode<K, V> putTreeVal(int h, K k, V v) {
      Class<?> kc = null;
      boolean searched = false;
      for (TreeNode<K, V> p = root; ; ) {
        int dir, ph;
        if (p == null) {
          first = root = new TreeNode<K, V>(h, k, v, null, null, myHashingStrategy);
          break;
        }
        K pk = p.key;
        if ((ph = p.hash) > h) {
          dir = -1;
        }
        else if (ph < h) {
          dir = 1;
        }
        else if (isEqual(k, pk, myHashingStrategy)) {
          return p;
        }
        else if ((kc == null &&
                  (kc = comparableClassFor(k)) == null) ||
                 (dir = compareComparables(kc, k, pk)) == 0) {
          if (!searched) {
            TreeNode<K, V> q, ch;
            searched = true;
            if (((ch = p.left) != null &&
                 (q = ch.findTreeNode(h, k, kc)) != null) ||
                ((ch = p.right) != null &&
                 (q = ch.findTreeNode(h, k, kc)) != null)) {
              return q;
            }
          }
          dir = tieBreakOrder(k, pk);
        }

        TreeNode<K, V> xp = p;
        if ((p = (dir <= 0) ? p.left : p.right) == null) {
          TreeNode<K, V> x, f = first;
          first = x = new TreeNode<K, V>(h, k, v, f, xp, myHashingStrategy);
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
    private boolean removeTreeNode(TreeNode<K, V> p) {
      TreeNode<K, V> next = (TreeNode<K, V>)p.next;
      TreeNode<K, V> pred = p.prev;  // unlink traversal pointers
      TreeNode<K, V> r, rl;
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
        TreeNode<K, V> replacement;
        TreeNode<K, V> pl = p.left;
        TreeNode<K, V> pr = p.right;
        if (pl != null && pr != null) {
          TreeNode<K, V> s = pr, sl;
          while ((sl = s.left) != null) // find successor
          {
            s = sl;
          }
          boolean c = s.red;
          s.red = p.red;
          p.red = c; // swap colors
          TreeNode<K, V> sr = s.right;
          TreeNode<K, V> pp = p.parent;
          if (s == pr) { // p was s's direct parent
            p.parent = s;
            s.right = p;
          }
          else {
            TreeNode<K, V> sp = s.parent;
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
          TreeNode<K, V> pp = replacement.parent = p.parent;
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
          TreeNode<K, V> pp;
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

    private static <K, V> TreeNode<K, V> rotateLeft(TreeNode<K, V> root,
                                                    TreeNode<K, V> p) {
      TreeNode<K, V> r, pp, rl;
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

    private static <K, V> TreeNode<K, V> rotateRight(TreeNode<K, V> root,
                                                     TreeNode<K, V> p) {
      TreeNode<K, V> l, pp, lr;
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

    private static <K, V> TreeNode<K, V> balanceInsertion(TreeNode<K, V> root,
                                                          TreeNode<K, V> x) {
      x.red = true;
      for (TreeNode<K, V> xp, xpp, xppl, xppr; ; ) {
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

    private static <K, V> TreeNode<K, V> balanceDeletion(TreeNode<K, V> root,
                                                         TreeNode<K, V> x) {
      for (TreeNode<K, V> xp, xpl, xpr; ; ) {
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
            TreeNode<K, V> sl = xpr.left, sr = xpr.right;
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
                xpr.red = (xp == null) ? false : xp.red;
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
            TreeNode<K, V> sl = xpl.left, sr = xpl.right;
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
                xpl.red = (xp == null) ? false : xp.red;
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
    private static <K, V> boolean checkInvariants(TreeNode<K, V> t) {
      TreeNode<K, V> tp = t.parent, tl = t.left, tr = t.right,
        tb = t.prev, tn = (TreeNode<K, V>)t.next;
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
        U = AtomicFieldUpdater.getUnsafe();
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
   * Records the table, its length, and current traversal index for a
   * traverser that must process a region of a forwarded table before
   * proceeding with current table.
   */
  private static final class TableStack<K, V> {
    private int length;
    private int index;
    private Node<K, V>[] tab;
    private TableStack<K, V> next;
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
  private static class Traverser<K, V> {
    private Node<K, V>[] tab;        // current table; updated if resized
    Node<K, V> next;         // the next entry to use
    private TableStack<K, V> stack, spare; // to save/restore on ForwardingNodes
    private int index;              // index of bin to use next
    private int baseIndex;          // current index of initial table
    private final int baseLimit;          // index bound for initial table
    private final int baseSize;     // initial table size

    private Traverser(Node<K, V>[] tab, int size, int index, int limit) {
      this.tab = tab;
      this.baseSize = size;
      this.baseIndex = this.index = index;
      this.baseLimit = limit;
      this.next = null;
    }

    /**
     * Advances if possible, returning next valid node, or null if none.
     */
    final Node<K, V> advance() {
      Node<K, V> e;
      if ((e = next) != null) {
        e = e.next;
      }
      for (; ; ) {
        Node<K, V>[] t;
        int i, n;  // must use locals in checks
        if (e != null) {
          return next = e;
        }
        if (baseIndex >= baseLimit || (t = tab) == null ||
            (n = t.length) <= (i = index) || i < 0) {
          return next = null;
        }
        if ((e = tabAt(t, i)) != null && e.hash < 0) {
          if (e instanceof ForwardingNode) {
            tab = ((ForwardingNode<K, V>)e).nextTable;
            e = null;
            pushState(t, i, n);
            continue;
          }
          else if (e instanceof TreeBin) {
            e = ((TreeBin<K, V>)e).first;
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
    private void pushState(Node<K, V>[] t, int i, int n) {
      TableStack<K, V> s = spare;  // reuse if possible
      if (s != null) {
        spare = s.next;
      }
      else {
        s = new TableStack<K, V>();
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
      TableStack<K, V> s;
      int len;
      while ((s = stack) != null && (index += (len = s.length)) >= n) {
        n = len;
        index = s.index;
        tab = s.tab;
        s.tab = null;
        TableStack<K, V> next = s.next;
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
  private static class BaseIterator<K, V> extends Traverser<K, V> {
    final ConcurrentHashMap<K, V> map;
    Node<K, V> lastReturned;

    private BaseIterator(Node<K, V>[] tab, int size, int index, int limit,
                         ConcurrentHashMap<K, V> map) {
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
      Node<K, V> p;
      if ((p = lastReturned) == null) {
        throw new IllegalStateException();
      }
      lastReturned = null;
      map.replaceNode(p.key, null, null);
    }
  }

  private static final class KeyIterator<K, V> extends BaseIterator<K, V>
    implements Iterator<K>, Enumeration<K> {
    KeyIterator(Node<K, V>[] tab, int index, int size, int limit,
                ConcurrentHashMap<K, V> map) {
      super(tab, index, size, limit, map);
    }

    @Override
    public final K next() {
      Node<K, V> p;
      if ((p = next) == null) {
        throw new NoSuchElementException();
      }
      K k = p.key;
      lastReturned = p;
      advance();
      return k;
    }

    @Override
    public final K nextElement() {
      return next();
    }
  }

  private static final class ValueIterator<K, V> extends BaseIterator<K, V>
    implements Iterator<V>, Enumeration<V> {
    ValueIterator(Node<K, V>[] tab, int index, int size, int limit,
                  ConcurrentHashMap<K, V> map) {
      super(tab, index, size, limit, map);
    }

    @Override
    public final V next() {
      Node<K, V> p;
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

  private static final class EntryIterator<K, V> extends BaseIterator<K, V>
    implements Iterator<Map.Entry<K, V>> {
    EntryIterator(Node<K, V>[] tab, int index, int size, int limit,
                  ConcurrentHashMap<K, V> map) {
      super(tab, index, size, limit, map);
    }

    @Override
    public final Map.Entry<K, V> next() {
      Node<K, V> p;
      if ((p = next) == null) {
        throw new NoSuchElementException();
      }
      K k = p.key;
      V v = p.val;
      lastReturned = p;
      advance();
      return new MapEntry<K, V>(k, v, map);
    }
  }

  /**
   * Exported Entry for EntryIterator
   */
  private static final class MapEntry<K, V> implements Map.Entry<K, V> {
    private final K key; // non-null
    private V val;       // non-null
    private final ConcurrentHashMap<K, V> map;

    MapEntry(K key, V val, ConcurrentHashMap<K, V> map) {
      this.key = key;
      this.val = val;
      this.map = map;
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
    public int hashCode() {
      return map.hash((K)key) ^ val.hashCode();
    }

    @Override
    public String toString() {
      return key + "=" + val;
    }

    @Override
    public boolean equals(Object o) {
      Object k, v;
      Map.Entry<?, ?> e;
      return ((o instanceof Map.Entry) &&
              (k = (e = (Map.Entry<?, ?>)o).getKey()) != null &&
              (v = e.getValue()) != null &&
              (map.isEqual((K)k, key)) &&
              (v == val || v.equals(val)));
    }

    /**
     * Sets our entry's value and writes through to the map. The
     * value to return is somewhat arbitrary here. Since we do not
     * necessarily track asynchronous changes, the most recent
     * "previous" value could be different from what we return (or
     * could even have been removed, in which case the put will
     * re-establish). We do not and cannot guarantee more.
     */
    @Override
    public V setValue(@NotNull V value) {
      V v = val;
      val = value;
      map.put(key, value);
      return v;
    }
  }

    /* ----------------Views -------------- */

  /**
   * Base class for views.
   */
  private abstract static class CollectionView<K, V, E>
    implements Collection<E> {
    final ConcurrentHashMap<K, V> map;

    CollectionView(@NotNull ConcurrentHashMap<K, V> map) {
      this.map = map;
    }

    /**
     * Returns the map backing this view.
     *
     * @return the map backing this view
     */
    public ConcurrentHashMap<K, V> getMap() {
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
      return (i == n) ? r : Arrays.copyOf(r, i);
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
   * A view of a ConcurrentHashMap as a {@link Set} of keys, in
   * which additions may optionally be enabled by mapping to a
   * common value.  This class cannot be directly instantiated.
   * See {@link #keySet() keySet()},
   * @since 1.8
   */
  private static class KeySetView<K, V> extends CollectionView<K, V, K>
    implements Set<K> {
    private final V value;

    KeySetView(ConcurrentHashMap<K, V> map, V value) {  // non-public
      super(map);
      this.value = value;
    }

    /**
     * Returns the default mapped value for additions,
     * or {@code null} if additions are not supported.
     *
     * @return the default mapped value for additions, or {@code null}
     * if not supported
     */
    public V getMappedValue() {
      return value;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified key is null
     */
    @Override
    public boolean contains(Object o) {
      return map.containsKey(o);
    }

    /**
     * Removes the key from this map view, by removing the key (and its
     * corresponding value) from the backing map.  This method does
     * nothing if the key is not in the map.
     *
     * @param o the key to be removed from the backing map
     * @return {@code true} if the backing map contained the specified key
     * @throws NullPointerException if the specified key is null
     */
    @Override
    public boolean remove(Object o) {
      return map.remove(o) != null;
    }

    /**
     * @return an iterator over the keys of the backing map
     */
    @NotNull
    @Override
    public Iterator<K> iterator() {
      Node<K, V>[] t;
      ConcurrentHashMap<K, V> m = map;
      int f = (t = m.table) == null ? 0 : t.length;
      return new KeyIterator<K, V>(t, f, 0, f, m);
    }

    /**
     * Adds the specified key to this set view by mapping the key to
     * the default mapped value in the backing map, if defined.
     *
     * @param e key to be added
     * @return {@code true} if this set changed as a result of the call
     * @throws NullPointerException          if the specified key is null
     * @throws UnsupportedOperationException if no default mapped value
     *                                       for additions was provided
     */
    @Override
    public boolean add(@NotNull K e) {
      V v;
      if ((v = value) == null) {
        throw new UnsupportedOperationException();
      }
      return map.putVal(e, v, true) == null;
    }

    /**
     * Adds all of the elements in the specified collection to this set,
     * as if by calling {@link #add} on each one.
     *
     * @param c the elements to be inserted into this set
     * @return {@code true} if this set changed as a result of the call
     * @throws NullPointerException          if the collection or any of its
     *                                       elements are {@code null}
     * @throws UnsupportedOperationException if no default mapped value
     *                                       for additions was provided
     */
    @Override
    public boolean addAll(@NotNull Collection<? extends K> c) {
      boolean added = false;
      V v;
      if ((v = value) == null) {
        throw new UnsupportedOperationException();
      }
      for (K e : c) {
        if (map.putVal(e, v, true) == null) {
          added = true;
        }
      }
      return added;
    }

    @Override
    public int hashCode() {
      int h = 0;
      for (K e : this) {
        h += map.hash(e);
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

  /**
   * A view of a ConcurrentHashMap as a {@link Collection} of
   * values, in which additions are disabled. This class cannot be
   * directly instantiated. See {@link #values()}.
   */
  private static final class ValuesView<K, V> extends CollectionView<K, V, V>
    implements Collection<V> {
    ValuesView(ConcurrentHashMap<K, V> map) {
      super(map);
    }

    @Override
    public final boolean contains(Object o) {
      return map.containsValue(o);
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
      ConcurrentHashMap<K, V> m = map;
      Node<K, V>[] t;
      int f = (t = m.table) == null ? 0 : t.length;
      return new ValueIterator<K, V>(t, f, 0, f, m);
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
   * A view of a ConcurrentHashMap as a {@link Set} of (key, value)
   * entries.  This class cannot be directly instantiated. See
   * {@link #entrySet()}.
   */
  private static final class EntrySetView<K, V> extends CollectionView<K, V, Map.Entry<K, V>>
    implements Set<Map.Entry<K, V>> {
    private EntrySetView(ConcurrentHashMap<K, V> map) {
      super(map);
    }

    @Override
    public boolean contains(Object o) {
      Object k, v, r;
      Map.Entry<?, ?> e;
      return ((o instanceof Map.Entry) &&
              (k = (e = (Map.Entry<?, ?>)o).getKey()) != null &&
              (r = map.get(k)) != null &&
              (v = e.getValue()) != null &&
              (v == r || v.equals(r)));
    }

    @Override
    public boolean remove(Object o) {
      Object k, v;
      Map.Entry<?, ?> e;
      return ((o instanceof Map.Entry) &&
              (k = (e = (Map.Entry<?, ?>)o).getKey()) != null &&
              (v = e.getValue()) != null &&
              map.remove(k, v));
    }

    /**
     * @return an iterator over the entries of the backing map
     */
    @NotNull
    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
      ConcurrentHashMap<K, V> m = map;
      Node<K, V>[] t;
      int f = (t = m.table) == null ? 0 : t.length;
      return new EntryIterator<K, V>(t, f, 0, f, m);
    }

    @Override
    public boolean add(Entry<K, V> e) {
      return map.putVal(e.getKey(), e.getValue(), false) == null;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Entry<K, V>> c) {
      boolean added = false;
      for (Entry<K, V> e : c) {
        if (add(e)) {
          added = true;
        }
      }
      return added;
    }

    @Override
    public final int hashCode() {
      int h = 0;
      Node<K, V>[] t;
      if ((t = map.table) != null) {
        Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
        for (Node<K, V> p; (p = it.advance()) != null; ) {
          h += p.hashCode();
        }
      }
      return h;
    }

    @Override
    public final boolean equals(Object o) {
      Set<?> c;
      return ((o instanceof Set) &&
              ((c = (Set<?>)o) == this ||
               (containsAll(c) && c.containsAll(this))));
    }
  }

  // -------------------------------------------------------

  // Unsafe mechanics
  private static final Unsafe U;
  private static final long SIZECTL;
  private static final long TRANSFERINDEX;
  private static final long BASECOUNT;
  private static final long CELLSBUSY;
  private static final long CELLVALUE;
  private static final long ABASE;
  private static final int ASHIFT;

  static {
    try {
      U = AtomicFieldUpdater.getUnsafe();
      Class<?> k = ConcurrentHashMap.class;
      SIZECTL = U.objectFieldOffset
        (k.getDeclaredField("sizeCtl"));
      TRANSFERINDEX = U.objectFieldOffset
        (k.getDeclaredField("transferIndex"));
      BASECOUNT = U.objectFieldOffset
        (k.getDeclaredField("baseCount"));
      CELLSBUSY = U.objectFieldOffset
        (k.getDeclaredField("cellsBusy"));
      Class<?> ck = CounterCell.class;
      CELLVALUE = U.objectFieldOffset
        (ck.getDeclaredField("value"));
      Class<?> ak = Node[].class;
      ABASE = U.arrayBaseOffset(ak);
      int scale = U.arrayIndexScale(ak);
      if ((scale & (scale - 1)) != 0) {
        throw new Error("data type scale not a power of two");
      }
      ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
    }
    catch (Exception e) {
      throw new Error(e);
    }
  }
  //////////////// IJ specific

  @Override
  public int computeHashCode(final K object) {
    return object == null ? 0 : object.hashCode();
  }

  @Override
  public boolean equals(final K o1, final K o2) {
    return o1.equals(o2);
  }

  private int hash(K key) {
    return spread(myHashingStrategy.computeHashCode(key));
  }

  private boolean isEqual(@NotNull K key1, K key2) {
    return isEqual(key1, key2, myHashingStrategy);
  }

  private static <K> boolean isEqual(@NotNull K key1, K key2, @NotNull TObjectHashingStrategy<K> hashingStrategy) {
    return key1 == key2 || key2 != null && hashingStrategy.equals(key1, key2);
  }
}