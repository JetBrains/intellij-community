/*
	* Copyright (C) 2002-2024 Sebastiano Vigna
	*
	* Licensed under the Apache License, Version 2.0 (the "License");
	* you may not use this file except in compliance with the License.
	* You may obtain a copy of the License at
	*
	*     http://www.apache.org/licenses/LICENSE-2.0
	*
	* Unless required by applicable law or agreed to in writing, software
	* distributed under the License is distributed on an "AS IS" BASIS,
	* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	* See the License for the specific language governing permissions and
	* limitations under the License.
	*/
package com.intellij.platform.syntax.impl.fastutil.ints

import com.intellij.platform.syntax.impl.fastutil.Arrays
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min

/** A type-specific array-based list; provides some additional methods that use polymorphism to avoid (un)boxing.
 *
 *
 * This class implements a lightweight, fast, open, optimized,
 * reuse-oriented version of array-based lists. Instances of this class
 * represent a list with an array that is enlarged as needed when new entries
 * are created (by increasing its current length by 50%), but is
 * *never* made smaller (even on a [.clear]). A family of
 * [trimming methods][.trim] lets you control the size of the
 * backing array; this is particularly useful if you reuse instances of this class.
 * Range checks are equivalent to those of `java.util`'s classes, but
 * they are delayed as much as possible. The backing array is exposed by the
 * [.elements] method.
 *
 *
 * This class implements the bulk methods `removeElements()`,
 * `addElements()` and `getElements()` using
 * high-performance system calls (e.g., [ ][copyInto]) instead of
 * expensive loops.
 */

@ApiStatus.Internal
class IntArrayList: MutableIntList, Comparable<IntList> {
  /** The backing array.  */
  private lateinit var a: IntArray

  /** The current actual size of the list (never greater than the backing-array length).  */
  override var size = 0

  /** Creates a new array list using a given array.
   *
   * This constructor is only meant to be used by the wrapping methods.
   *
   * @param a the array that will be used to back this array list.
   */
  private constructor(a: IntArray, @Suppress("unused") wrapped: Boolean) {
    this.a = a
  }

  /** Creates a new array list and fills it with the elements of a given array.
   *
   * @param a an array whose elements will be used to fill the array list.
   * @param offset the first element to use.
   * @param length the number of elements to use.
   **/
  constructor(a: IntArray, offset: Int = 0, length: Int = a.size) : this(length) {
    a.copyInto(this.a, 0, offset, offset + length)
    size = length
  }

  private fun initArrayFromCapacity(capacity: Int) {
    if (capacity < 0) throw IllegalArgumentException("Initial capacity ($capacity) is negative")
    a = if (capacity == 0) IntArray(0)
    else IntArray(capacity)
  }


  /** Creates a new array list with [.DEFAULT_INITIAL_CAPACITY] capacity.  */
  constructor() {
    a = IntArrays.DEFAULT_EMPTY_ARRAY // We delay allocation
  }

  /** Creates a new array list and fills it with the elements returned by an iterator..
   *
   * @param i an iterator whose returned elements will fill the array list.
   */
  constructor(i: Iterator<Int>) : this() {
    while (i.hasNext()) this.add((i.next()))
  }

  constructor(list: IntList): this() {
    for (i in list.indices) this.add(list[i])
  }

  /** Creates a new array list with given capacity.
   *
   * @param capacity the initial capacity of the array list (may be 0).
   */
  constructor(capacity: Int) {
    initArrayFromCapacity(capacity)
  }

  /** Returns the backing array of this list.
   *
   * @return the backing array.
   */
  fun elements(): IntArray {
    return a
  }

  /** Ensures that this array list can contain the given number of entries without resizing.
   *
   * @param capacity the new minimum capacity for this array list.
   */
  fun ensureCapacity(capacity: Int) {
    if (capacity <= a.size || (a.contentEquals(IntArrays.DEFAULT_EMPTY_ARRAY) && capacity <= DEFAULT_INITIAL_CAPACITY)) return
    a = IntArrays.ensureCapacity(a, capacity, size)
    check(size <= a.size)
  }

  /** Grows this array list, ensuring that it can contain the given number of entries without resizing,
   * and in case increasing the current capacity at least by a factor of 50%.
   *
   * @param capacity the new minimum capacity for this array list.
   */
  private fun grow(capacity: Int) {
    @Suppress("NAME_SHADOWING") var capacity = capacity
    if (capacity <= a.size) return
    if (!a.contentEquals(IntArrays.DEFAULT_EMPTY_ARRAY)) capacity = max(min((a.size + (a.size shr 1)), Arrays.MAX_ARRAY_SIZE), capacity)
    else if (capacity < DEFAULT_INITIAL_CAPACITY) capacity = DEFAULT_INITIAL_CAPACITY
    a = IntArrays.forceCapacity(a, capacity, size)
    check(size <= a.size)
  }

  override fun add(index: Int, element: Int) {
    ensureIndex(index)
    grow(size + 1)
    if (index != size) a.copyInto(a, index + 1, index, size)
    a[index] = element
    size++
    check(size <= a.size)
  }

  override fun add(element: Int) {
    grow(size + 1)
    a[size++] = element
    check(size <= a.size)
  }

  override fun addAll(index: Int, elements: IntList): Boolean {
    ensureIndex(index)
    var index = index
    var n: Int = elements.size
    if (n == 0) return false
    grow(size + n)
    a.copyInto(a, destinationOffset = index + n, startIndex = index, endIndex = index + (size - index))
    var i = elements.indices.first
    size += n
    while (n-- != 0) a[index++] = elements[i++]
    check(size <= a.size)
    return true
  }

  override fun set(index: Int, element: Int): Int {
    if (index >= size || index < 0) throw IndexOutOfBoundsException("Index ($index) is out of bounds for size ($size)")
    val old = a[index]
    a[index] = element
    return old
  }

  override fun clear() {
    size = 0
    check(size <= a.size)
  }

  override operator fun get(index: Int): Int {
    if (index >= size || index < 0) throw IndexOutOfBoundsException("Index $index out of bounds for length $size")
    return a[index]
  }


  override fun removeAt(index: Int): Int {
    if (index >= size || index < 0) throw IndexOutOfBoundsException("Index ($index) out of bounds for length ($size)")
    val a = this.a
    val old = a[index]
    size -= 1
    if (index != size) a.copyInto(a, index, index + 1, size + 1)
    check(size <= a.size)
    return old
  }

  override fun resize(size: Int) {
    if (size > a.size) a = IntArrays.forceCapacity(a, size, this.size)
    if (size > this.size) a.fill(0, this.size, size)
    this.size = size
  }

  /** Trims the backing array if it is too large.
   *
   * If the current array length is smaller than or equal to
   * `n`, this method does nothing. Otherwise, it trims the
   * array length to the maximum between `n` and [.size].
   *
   *
   * This method is useful when reusing lists.  [Clearing a][.clear] leaves the array length untouched. If you are reusing a list
   * many times, you can call this method with a typical
   * size to avoid keeping around a very large array just
   * because of a few large transient lists.
   *
   * @param n the threshold for the trimming.
   */
  /** Trims this array list so that the capacity is equal to the size.
   *
   */
  fun trim(n: Int = 0) {
    if (n >= a.size || size == a.size) return
    val t = IntArray(max(n, size))
    a.copyInto(t, 0, 0, size)
    a = t
    check(size <= a.size)
  }

  override fun sort() {
    this.a.sort(0, this.size)
  }

  /** Copies element of this type-specific list into the given array using optimized system calls.
   *
   * @param from the start index (inclusive).
   * @param a the destination array.
   * @param offset the offset into the destination array where to store the first element copied.
   * @param length the number of elements to be copied.
   */
  override fun toArray(from: Int, a: IntArray, offset: Int, length: Int): IntArray {
    ensureIndex(from)
    Arrays.ensureOffsetLength(a, offset, length)
    this.a.copyInto(a, destinationOffset = offset, startIndex = from, endIndex = from + length)
    return a;
  }

  fun getElements(from: Int, targetList: IntArrayList, offset: Int, length: Int) {
    ensureIndex(from)
    Arrays.ensureOffsetLength(a, offset, length)
    this.a.copyInto(targetList.a, destinationOffset = offset, startIndex = from, endIndex = from + length)
  }

  /** Removes elements of this type-specific list using optimized system calls.
   *
   * @param from the start index (inclusive).
   * @param to the end index (exclusive).
   */
  override fun removeElements(from: Int, to: Int) {
    Arrays.ensureFromTo(size, from, to)
    a.copyInto(a, destinationOffset = from, startIndex = to, endIndex = size)
    size -= (to - from)
  }


  /** Adds elements to this type-specific list using optimized system calls.
   *
   * @param index the index at which to add elements.
   * @param a the array containing the elements.
   * @param offset the offset of the first element to add.
   * @param length the number of elements to add.
   */
  override fun addElements(index: Int, a: IntArray, offset: Int, length: Int) {
    ensureIndex(index)
    Arrays.ensureOffsetLength(a, offset, length)
    grow(size + length)
    this.a.copyInto(destination = this.a, destinationOffset = index + length, startIndex = index, endIndex = index + (size - index))
    a.copyInto(destination = this.a, destinationOffset = index, startIndex = offset, endIndex = offset + length)
    size += length
  }

  override fun equals(o: Any?): Boolean {
    return o === this || (o is IntList && compareTo(o) == 0)
  }

  /** Compares this array list to another array list.
   *
   * @apiNote This method exists only for sake of efficiency. The implementation
   * inherited from the abstract implementation would already work.
   *
   * @param l an array list.
   * @return a negative integer,
   * zero, or a positive integer as this list is lexicographically less than, equal
   * to, or greater than the argument.
   */
  override fun compareTo(l: IntList): Int {
    val s1 = length()
    val s2 = l.length()
    var e1: Int
    var e2: Int
    var r: Int
    var i = 0
    while (i < s1 && i < s2) {
      e1 = this[i]
      e2 = l[i]
      r = when {
        e1 > e2 -> 1
        e1 < e2 -> -1
        else -> 0
      }
      if (r != 0) return r
      i++
    }
    return if (i < s2) -1 else (if (i < s1) 1 else 0)
  }

  override fun hashCode(): Int {
    var i = indices.first
    var h = 1
    var s = size
    while (s-- != 0) {
      val k = get(i++)
      h = 31 * h + (k)
    }
    return h
  }

  fun clone(): IntArrayList {
    val clone = IntArrayList()
    clone.size = size
    clone.a = this.a.copyOf()
    return clone
  }

  override fun toString(): String {
    val s = StringBuilder()
    var i = indices.first
    var n = size
    var k: Int
    var first = true
    s.append("[")
    while (n-- != 0) {
      if (first) first = false
      else s.append(", ")
      k = get(i)
      i += 1
      s.append(k.toString())
    }
    s.append("]")
    return s.toString()
  }

  companion object {

    const val DEFAULT_INITIAL_CAPACITY: Int = 10

    fun wrap(a: IntArray): IntArrayList {
      return wrap(a, a.size)
    }

    fun wrap(a: IntArray, length: Int): IntArrayList {
      if (length > a.size) throw IllegalArgumentException("The specified length (" + length + ") is greater than the array size (" + a.size + ")")
      val l = IntArrayList(a, true)
      l.size = length
      return l
    }

    fun of(): IntArrayList {
      return IntArrayList()
    }

    fun of(vararg init: Int): IntArrayList {
      return wrap(init)
    }
  }

}