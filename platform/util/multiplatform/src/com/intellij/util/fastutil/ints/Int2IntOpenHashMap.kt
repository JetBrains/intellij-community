// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fastutil.ints

import com.intellij.util.fastutil.Hash
import com.intellij.util.fastutil.Hash.Companion
import com.intellij.util.fastutil.HashCommon
import org.jetbrains.annotations.ApiStatus
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Deprecated(
  "This API is temporary multiplatform shim. Please make sure you are not using it by accident",
  replaceWith = ReplaceWith("it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap"),
  level = DeprecationLevel.WARNING
)
@ApiStatus.Internal
class Int2IntOpenHashMap : Hash, MutableInt2IntMap {
  /** The array of keys.  */
  private var key: IntArray

  /** The array of values.  */
  private var value: IntArray

  /** The mask for wrapping a position counter.  */
  private var mask: Int

  /** Whether this map contains the key zero.  */
  private var containsNullKey: Boolean = false

  /** The current table size.  */
  private var n: Int

  /** Threshold after which we rehash. It must be the table size times [.f].  */
  private var maxFill: Int

  /** We never resize below this threshold, which is the construction-time {#n}.  */
  private var minN: Int

  /** Number of entries in the set (including the key zero, if present).  */
  override var size: Int = 0

  /** The acceptable load factor.  */
  private var f: Float

  private var defaultValue: Int = 0


  /** Iterator over entries. */
  override val entries: Iterator<Int2IntEntry> = EntryIterator()

  /** Iterator over keys. */
  override val keys: IntIterator = KeyIterator()

  /** Iterator over values.  */
  override val values: IntIterator = ValueIterator()

  constructor(expected: Int, f: Float) {
    if (f <= 0 || f >= 1) throw IllegalArgumentException("Load factor must be greater than 0 and smaller than 1")
    if (expected < 0) throw IllegalArgumentException("The expected number of elements must be nonnegative")
    this.f = f
    n = HashCommon.arraySize(expected, f)
    minN = n
    mask = n - 1
    maxFill = HashCommon.maxFill(n, f)
    key = IntArray(n + 1)
    value = IntArray(n + 1)
  }

  constructor() : this(Hash.Companion.DEFAULT_INITIAL_SIZE, Hash.Companion.DEFAULT_LOAD_FACTOR)

  constructor(expected: Int) : this(expected, Hash.Companion.DEFAULT_LOAD_FACTOR)

  /**
   * Creates a new hash map with [Companion.DEFAULT_LOAD_FACTOR] as load factor copying a given one.
   *
   * @param m a [Map] to be copied into the new hash map.
   */
  constructor(m: Int2IntMap, f: Float = Hash.Companion.DEFAULT_LOAD_FACTOR) : this(m.size, f) {
    putAll(m)
  }

  constructor(k: IntArray, v: IntArray, f: Float = Hash.Companion.DEFAULT_LOAD_FACTOR) : this(k.size, f) {
    if (k.size != v.size) throw IllegalArgumentException("The key array and the value array have different lengths (" + k.size + " and " + v.size + ")")
    for (i in k.indices) this[k[i]] = v[i]
  }

  fun isEmpty(): Boolean = size == 0

  private fun realSize(): Int = if (containsNullKey) size - 1 else size

  /** Ensures that this map can hold a certain number of keys without rehashing.
   *
   * @param capacity a number of keys; there will be no rehashing unless
   * the map [size][.size] exceeds this number.
   */
  private fun ensureCapacity(capacity: Int) {
    val needed: Int = HashCommon.arraySize(capacity, f)
    if (needed > n) rehash(needed)
  }

  private fun tryCapacity(capacity: Long) {
    val needed = min(1 shl 30, max(2, HashCommon.nextPowerOfTwo(ceil(capacity / f).toInt())))
    if (needed > n) rehash(needed)
  }

  private fun removeEntry(pos: Int): Int {
    val oldValue = value[pos]
    size--
    shiftKeys(pos)
    if (n > minN && size < maxFill / 4 && n > Hash.Companion.DEFAULT_INITIAL_SIZE) rehash(n / 2)
    return oldValue
  }

  private fun removeNullEntry(): Int {
    containsNullKey = false
    val oldValue = value[n]
    size--
    if (n > minN && size < maxFill / 4 && n > Hash.Companion.DEFAULT_INITIAL_SIZE) rehash(n / 2)
    return oldValue
  }


  fun putAll(from: Int2IntMap) {
    if (f <= .5) ensureCapacity(from.size) // The resulting map will be sized for m.size() elements
    else tryCapacity((size + from.size).toLong()) // The resulting map will be tentatively sized for size() + m.size() elements

    var n = from.size
    val i = from.entries.iterator()
    while (n-- != 0) {
      val e = i.next()
      put(e.key, e.value)
    }
  }

  private fun find(k: Int): Int {
    if (k == 0) return if (containsNullKey) n else -(n + 1)
    var curr: Int
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(k) and mask).also {
        pos = it
      }].also { curr = it } == 0) return -(pos + 1)
    if (k == curr) return pos // There's always an unused entry.
    while (true) {
      if (key[((pos + 1) and mask).also { pos = it }].also {
          curr = it
        } == 0) return -(pos + 1)
      if (k == curr) return pos
    }
  }

  private fun insert(pos: Int, k: Int, v: Int) {
    if (pos == n) containsNullKey = true
    key[pos] = k
    value[pos] = v
    if (size++ >= maxFill) rehash(HashCommon.arraySize(size + 1, f))
  }

  override fun put(key: Int, value: Int): Int {
    val pos = find(key)
    if (pos < 0) {
      insert(-pos - 1, key, value)
      return defaultReturnValue()
    }
    val oldValue = this.value[pos]
    this.value[pos] = value
    this.key[pos] = key
    return oldValue
  }

  /** Shifts left entries with the specified hash code, starting at the specified position,
   * and empties the resulting free entry.
   *
   * @param pos a starting position.
   */
  private fun shiftKeys(pos: Int) { // Shift entries with the same hash.
    var pos = pos
    var last: Int
    var slot: Int
    var curr: Int
    val key = this.key
    val value = this.value
    while (true) {
      pos = (pos.also { last = it } + 1) and mask
      while (true) {
        if (key[pos].also { curr = it } == 0) {
          key[last] = 0
          return
        }
        slot = HashCommon.mix(curr) and mask
        if (if (last <= pos) last >= slot || slot > pos else slot in (pos + 1)..last) break
        pos = (pos + 1) and mask
      }
      key[last] = curr
      value[last] = value[pos]
    }
  }

  override fun remove(k: Int): Int {
    if (k == 0) {
      if (containsNullKey) return removeNullEntry()
      return defaultReturnValue()
    }
    var curr: Int
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(k) and mask).also {
        pos = it
      }].also { curr = it } == 0) return defaultReturnValue()
    if (k == curr) return removeEntry(pos)
    while (true) {
      if (key[((pos + 1) and mask).also { pos = it }].also {
          curr = it
        } == 0) return defaultReturnValue()
      if (k == curr) return removeEntry(pos)
    }
  }

  override operator fun get(k: Int): Int {
    if (k == 0) return if (containsNullKey) value[n] else defaultReturnValue()
    var curr: Int
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(k) and mask).also {
        pos = it
      }].also { curr = it } == 0) return defaultReturnValue()
    if (k == curr) return value[pos] // There's always an unused entry.
    while (true) {
      if (key[((pos + 1) and mask).also { pos = it }].also {
          curr = it
        } == 0) return defaultReturnValue()
      if (k == curr) return value[pos]
    }
  }

  fun containsKey(k: Int): Boolean {
    if (k == 0) return containsNullKey
    var curr: Int
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(k) and mask).also {
        pos = it
      }].also { curr = it } == 0) return false
    if (k == curr) return true // There's always an unused entry.
    while (true) {
      if (key[((pos + 1) and mask).also { pos = it }].also {
          curr = it
        } == 0) return false
      if (k == curr) return true
    }
  }

  fun containsValue(v: Int): Boolean {
    if (containsNullKey && value[n] == v) return true
    var i = n
    while (i-- != 0) {
      if (key[i] != 0 && value[i] == v) return true
    }
    return false
  }

  fun getOrDefault(k: Int, defaultValue: Int): Int {
    if (k == 0) return if (containsNullKey) value[n] else defaultValue
    var curr: Int
    val key = this.key
    var pos: Int // The starting point.
    if (key[HashCommon.mix(k) and mask].also { pos = it }.also { curr = it } == 0) return defaultValue
    if (k == curr) return value[pos] // There's always an unused entry.
    while (true) {
      if (key[(pos + 1) and mask].also { pos = it }.also { curr = it } == 0) return defaultValue
      if (k == curr) return value[pos]
    }
  }

  fun remove(k: Int, v: Int): Boolean {
    if (k == 0) {
      if (containsNullKey && v == value[n]) {
        removeNullEntry()
        return true
      }
      return false
    }
    var curr: Int
    val key = this.key
    var pos: Int // The starting point.
    if (key[HashCommon.mix(k) and mask].also { pos = it }.also { curr = it } == 0) return false
    if (k == curr && v == value[pos]) {
      removeEntry(pos)
      return true
    }
    while (true) {
      if (key[(pos + 1) and mask].also { pos = it }.also { curr = it } == 0) return false
      if (k == curr && v == value[pos]) {
        removeEntry(pos)
        return true
      }
    }
  }

  fun defaultReturnValue(): Int {
    return defaultValue
  }

  fun defaultReturnValue(rv: Int) {
    defaultValue = rv
  }

  fun replace(k: Int, oldValue: Int, v: Int): Boolean {
    val pos = find(k)
    if (pos < 0 || oldValue != value[pos]) return false
    value[pos] = v
    return true
  }

  fun replace(k: Int, v: Int): Int {
    val pos = find(k)
    if (pos < 0) return defaultReturnValue()
    val oldValue = value[pos]
    value[pos] = v
    return oldValue
  }


  /** Removes all elements from this map.
   *
   * <p>To increase object reuse, this method does not change the table size.
   * If you want to reduce the table size, you must use {@link #trim()}.
   *
   */
  fun clear() {
    if (size == 0) return
    size = 0
    containsNullKey = false
    key.fill(0)
  }

  /** Rehashes the map, making the table as small as possible.
   *
   *
   * This method rehashes the table to the smallest size satisfying the
   * load factor. It can be used when the set will not be changed anymore, so
   * to optimize access speed and size.
   *
   *
   * If the table size is already the minimum possible, this method
   * does nothing.
   *
   * @return true if there was enough memory to trim the map.
   * @see .trim
   */
  fun trim(n: Int = size): Boolean {
    val l: Int = HashCommon.nextPowerOfTwo(ceil((n / f)).toInt())
    if (l >= this.n || size > HashCommon.maxFill(l, f)) return true
    try {
      rehash(l)
    }
    catch (cantDoIt: Exception) {
      return false
    }
    return true
  }

  /** Rehashes the map.
   *
   *
   * This method implements the basic rehashing strategy, and may be
   * overridden by subclasses implementing different rehashing strategies (e.g.,
   * disk-based rehashing). However, you should not override this method
   * unless you understand the internal workings of this class.
   *
   * @param newN the new size
   */
  private fun rehash(newN: Int) {
    val key = this.key
    val value = this.value
    val mask = newN - 1 // Note that this is used by the hashing macro
    val newKey = IntArray(newN + 1)
    val newValue = IntArray(newN + 1)
    var i = n
    var pos: Int
    var j = realSize()
    while (j-- != 0) {
      while (key[--i] == 0);
      if (newKey[(HashCommon.mix(key[i]) and mask).also { pos = it }] != 0) while (newKey[(pos + 1 and mask).also { pos = it }] != 0);
      newKey[pos] = key[i]
      newValue[pos] = value[i]
    }
    newValue[newN] = value[n]
    n = newN
    this.mask = mask
    maxFill = HashCommon.maxFill(n, f)
    this.key = newKey
    this.value = newValue
  }

  override fun equals(o: Any?): Boolean {
    if (o === this) return true
    if (o !is Int2IntMap) return false

    if (this.size != o.size) return false

    for ((key, value) in this.entries) {
      if (o[key] != value) return false
    }
    return true
  }

  /** Returns a hash code for this map.
   *
   * This method overrides the generic method provided by the superclass.
   * Since `equals()` is not overriden, it is important
   * that the value returned by this method is the same value as
   * the one returned by the overriden method.
   *
   * @return a hash code for this map.
   */
  override fun hashCode(): Int {
    var h = 0
    val key = this.key
    val value = this.value
    var j = realSize()
    var i = 0
    while (j-- != 0) {
      while (key[i] == 0) i++
      var t = key[i]
      t = t xor value[i]
      h += t
      i++
    } // Zero / null keys have hash zero.
    if (containsNullKey) h += value[n]
    return h
  }

  fun computeIfAbsent(k: Int, mappingFunction: (Int) -> Int): Int {
    val pos = find(k)
    if (pos >= 0) return value[pos]
    val newValue = mappingFunction(k)
    insert(-pos - 1, k, newValue)
    return newValue
  }

  /** An iterator over a hash map.  */
  private abstract inner class MapIterator {
    /** The index of the last entry returned, if positive or zero; initially, [.n]. If negative, the last
     * entry returned was that of the key of index `- pos - 1` from the [.wrapped] list.  */
    var pos: Int = n

    /** The index of the last entry that has been returned (more precisely, the value of [.pos] if [.pos] is positive,
     * or [Int.MIN_VALUE] if [.pos] is negative). It is -1 if either
     * we did not return an entry yet, or the last returned entry has been removed.  */
    var last: Int = -1

    /** A downward counter measuring how many entries must still be returned.  */
    var c: Int = size

    /** A boolean telling us whether we should return the entry with the null key.  */
    var mustReturnNullKey: Boolean = this@Int2IntOpenHashMap.containsNullKey

    /** A lazily allocated list containing keys of entries that have wrapped around the table because of removals.  */
    var wrapped: IntArrayList? = null


    fun hasNext(): Boolean {
      return c != 0
    }

    fun nextEntry(): Int {
      if (!hasNext()) throw NoSuchElementException()
      c--
      if (mustReturnNullKey) {
        mustReturnNullKey = false
        return n.also { last = it }
      }
      val key = this@Int2IntOpenHashMap.key
      while (true) {
        if (--pos < 0) { // We are just enumerating elements from the wrapped list.
          last = Int.MIN_VALUE
          if (wrapped == null) throw IllegalStateException()
          val k: Int = wrapped!![-pos - 1]
          var p: Int = HashCommon.mix(k) and mask
          while (k != key[p]) p = (p + 1) and mask
          return p
        }
        if (key[pos] != 0) return pos.also { last = it }
      }
    }

    /** Shifts left entries with the specified hash code, starting at the specified position,
     * and empties the resulting free entry.
     *
     * @param pos a starting position.
     */
    private fun shiftKeys(pos: Int) { // Shift entries with the same hash.
      var pos = pos
      var last: Int
      var slot: Int
      var curr: Int
      val key = this@Int2IntOpenHashMap.key
      val value = this@Int2IntOpenHashMap.value
      while (true) {
        pos = (pos.also { last = it } + 1) and mask
        while (true) {
          if ((key[pos].also { curr = it }) == 0) {
            key[last] = (0)
            value[last] = 0
            return
          }
          slot = HashCommon.mix(curr) and mask
          if (if (last <= pos) last >= slot || slot > pos else slot in (pos + 1)..last) break
          pos = (pos + 1) and mask
        }
        if (pos < last) { // Wrapped entry.
          if (wrapped == null) wrapped = IntArrayList(2).apply {
            add(key[pos])
          }
        }
        key[last] = curr
        value[last] = value[pos]
      }
    }
  }

  // Iterator on entries
  private inner class EntryIterator : MapIterator(), Iterator<Int2IntEntry> {
    override fun next(): Int2IntEntry {
      val nextIndex = nextEntry()
      return Int2IntEntry(key[nextIndex], value[nextIndex])
    }
  }

  // Iterator on keys
  private inner class KeyIterator : MapIterator(), IntIterator {
    override operator fun next(): Int = key[nextEntry()]
  }

  // An iterator on values.
  private inner class ValueIterator : MapIterator(), IntIterator {
    override operator fun next(): Int = value[nextEntry()]
  }
}