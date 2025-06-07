// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables

import com.intellij.diff.comparison.CancellationChecker
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.comparison.expand
import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.util.Range
import com.intellij.util.containers.PeekableIteratorWrapper
import com.intellij.util.diff.Diff
import com.intellij.util.diff.FilesTooBigForDiffException
import org.jetbrains.annotations.TestOnly
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmWildcard

object DiffIterableUtil {
  @TestOnly
  private var SHOULD_VERIFY_ITERABLE: Boolean = false

  /*
   * Compare two integer arrays
   */
  @JvmStatic
  @Throws(DiffTooBigException::class)
  fun diff(data1: IntArray, data2: IntArray, indicator: CancellationChecker): FairDiffIterable {
    indicator.checkCanceled()

    try {
      // TODO: use CancellationChecker inside
      val change = Diff.buildChanges(data1, data2)
      return fair(create(change, data1.size, data2.size))
    }
    catch (_: FilesTooBigForDiffException) {
      throw DiffTooBigException()
    }
  }

  /*
   * Compare two arrays, basing on equals() and hashCode() of it's elements
   */
  @JvmStatic
  @Throws(DiffTooBigException::class)
  fun <T> diff(data1: Array<T>, data2: Array<T>, indicator: CancellationChecker): FairDiffIterable {
    indicator.checkCanceled()

    try {
      // TODO: use CancellationChecker inside
      val change = Diff.buildChanges(data1, data2)
      return fair(create(change, data1.size, data2.size))
    }
    catch (_: FilesTooBigForDiffException) {
      throw DiffTooBigException()
    }
  }

  /*
   * Compare two lists, basing on equals() and hashCode() of it's elements
   */
  @JvmStatic
  @Throws(DiffTooBigException::class)
  fun <T> diff(
    objects1: List<T>,
    objects2: List<T>,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    // TODO: compare lists instead of arrays in Diff
    return diff<Any?>(objects1.toTypedArray(), objects2.toTypedArray(), indicator)
  }

  //
  // Iterable
  //
  @JvmStatic
  fun create(change: Diff.Change?, length1: Int, length2: Int): DiffIterable {
    val iterable = DiffChangeDiffIterable(change, length1, length2)
    verify(iterable)
    return iterable
  }

  @JvmStatic
  fun createFragments(fragments: List<DiffFragment>, length1: Int, length2: Int): DiffIterable {
    val iterable: DiffIterable = DiffFragmentsDiffIterable(fragments, length1, length2)
    verify(iterable)
    return iterable
  }

  @JvmStatic
  fun create(ranges: List<@JvmWildcard Range>, length1: Int, length2: Int): DiffIterable {
    val iterable: DiffIterable = RangesDiffIterable(ranges, length1, length2)
    verify(iterable)
    return iterable
  }

  @JvmStatic
  fun createUnchanged(ranges: List<Range>, length1: Int, length2: Int): DiffIterable {
    val invert = invert(create(ranges, length1, length2))
    verify(invert)
    return invert
  }

  @JvmStatic
  fun invert(iterable: DiffIterable): DiffIterable {
    val wrapper: DiffIterable = InvertedDiffIterableWrapper(iterable)
    verify(wrapper)
    return wrapper
  }

  @JvmStatic
  fun fair(iterable: DiffIterable): FairDiffIterable {
    if (iterable is FairDiffIterable) return iterable
    val wrapper: FairDiffIterable = FairDiffIterableWrapper(iterable)
    verifyFair(wrapper)
    return wrapper
  }

  @JvmStatic
  fun expandedIterable(iterable: DiffIterable, offset1: Int, offset2: Int, length1: Int, length2: Int): DiffIterable {
    check(offset1 + iterable.length1 <= length1 &&
           offset2 + iterable.length2 <= length2)
    return ExpandedDiffIterable(iterable, offset1, offset2, length1, length2)
  }

  //
  // Misc
  //
  /**
   * Iterate both changed and unchanged ranges one-by-one.
   */
  @JvmStatic
  fun iterateAll(iterable: DiffIterable): Iterable<Pair<Range, Boolean>> {
    return Iterable { AllRangesIterator(iterable) }
  }

  @JvmStatic
  fun getRangeDelta(range: Range): Int {
    val deleted = range.end1 - range.start1
    val inserted = range.end2 - range.start2
    return inserted - deleted
  }

  //
  // Verification
  //

  @TestOnly
  @JvmStatic
  fun setVerifyEnabled(value: Boolean) {
    SHOULD_VERIFY_ITERABLE = value
  }

  private fun isVerifyEnabled(): Boolean {
    return SHOULD_VERIFY_ITERABLE
  }


  @JvmStatic
  fun verify(iterable: DiffIterable) {
    if (!isVerifyEnabled()) return

    verify(iterable.iterateChanges())
    verify(iterable.iterateUnchanged())

    verifyFullCover(iterable)
  }

  @JvmStatic
  fun verifyFair(iterable: DiffIterable) {
    if (!isVerifyEnabled()) return

    verify(iterable)

    for (range in iterable.iterateUnchanged()) {
      check(range.end1 - range.start1 == range.end2 - range.start2)
    }
  }

  private fun verify(iterable: Iterable<Range>) {
    for (range in iterable) {
      // verify range
      check(range.start1 <= range.end1)
      check(range.start2 <= range.end2)
      check(range.start1 != range.end1 || range.start2 != range.end2)
    }
  }

  private fun verifyFullCover(iterable: DiffIterable) {
    var last1 = 0
    var last2 = 0
    var lastEquals: Boolean? = null

    for (pair in iterateAll(iterable)) {
      val range: Range = pair.first
      val equal: Boolean = pair.second

      check(last1 == range.start1)
      check(last2 == range.start2)
      check(lastEquals != equal)

      last1 = range.end1
      last2 = range.end2
      lastEquals = equal
    }

    check(last1 == iterable.length1)
    check(last2 == iterable.length2)
  }

  //
  // Debug
  //
  @JvmStatic
  @Suppress("unused")
  fun <T> extractDataRanges(
    objects1: List<T>,
    objects2: List<T>,
    iterable: DiffIterable,
  ): MutableList<LineRangeData<*>> {
    val result: MutableList<LineRangeData<*>> = ArrayList()

    for (pair in iterateAll(iterable)) {
      val range: Range = pair.first
      val equals: Boolean = pair.second

      val data1: MutableList<T> = ArrayList()
      val data2: MutableList<T> = ArrayList()

      for (i in range.start1..<range.end1) {
        data1.add(objects1[i])
      }
      for (i in range.start2..<range.end2) {
        data2.add(objects2[i])
      }

      result.add(LineRangeData(data1, data2, equals))
    }

    return result
  }

  //
  // Helpers
  //
  abstract class ChangeBuilderBase(open val length1: Int, open val length2: Int) {
    private var _index1 = 0
    open val index1: Int
      get() = _index1

    private var _index2 = 0
    open val index2: Int
      get() = _index2


    open fun markEqual(index1: Int, index2: Int) {
      markEqual(index1, index2, 1)
    }

    open fun markEqual(index1: Int, index2: Int, count: Int) {
      markEqual(index1, index2, index1 + count, index2 + count)
    }

    open fun markEqual(index1: Int, index2: Int, end1: Int, end2: Int) {
      if (index1 == end1 && index2 == end2) return

      check(this.index1 <= index1)
      check(this.index2 <= index2)
      check(index1 <= end1)
      check(index2 <= end2)

      if (this.index1 != index1 || this.index2 != index2) {
        addChange(this.index1, this.index2, index1, index2)
      }
      this._index1 = end1
      this._index2 = end2
    }

    protected open fun doFinish() {
      check(this.index1 <= this.length1)
      check(this.index2 <= this.length2)

      if (this.length1 != this.index1 || this.length2 != this.index2) {
        addChange(this.index1, this.index2, this.length1, this.length2)
        this._index1 = this.length1
        this._index2 = this.length2
      }
    }

    protected abstract fun addChange(start1: Int, start2: Int, end1: Int, end2: Int)
  }

  open class ChangeBuilder(length1: Int, length2: Int) : ChangeBuilderBase(length1, length2) {
    private val myChanges: MutableList<Range> = ArrayList()

    override fun addChange(start1: Int, start2: Int, end1: Int, end2: Int) {
      myChanges.add(Range(start1, end1, start2, end2))
    }

    open fun finish(): DiffIterable {
      doFinish()
      return create(myChanges, this.length1, this.length2)
    }
  }

  open class ExpandChangeBuilder(private val myObjects1: List<*>, private val myObjects2: List<*>) : ChangeBuilder(
    myObjects1.size, myObjects2.size) {
    override fun addChange(start1: Int, start2: Int, end1: Int, end2: Int) {
      val range = expand(myObjects1, myObjects2, start1, start2, end1, end2)
      if (!range.isEmpty) super.addChange(range.start1, range.start2, range.end1, range.end2)
    }
  }

  open class LineRangeData<T>(@JvmField val objects1: List<T>, @JvmField val objects2: List<T>, @JvmField val equals: Boolean)

  private class AllRangesIterator(private val myIterable: DiffIterable) : Iterator<Pair<Range, Boolean>> {
    private val myChanges: PeekableIteratorWrapper<Range> = PeekableIteratorWrapper<Range>(myIterable.changes())

    private var myNextUnchanged: Range?

    init {
      myNextUnchanged = peekNextUnchanged(0, 0)
    }

    fun peekNextUnchanged(start1: Int, start2: Int): Range? {
      val nextChange = if (myChanges.hasNext()) myChanges.peek() else null
      val range = if (nextChange != null)
        Range(start1, nextChange.start1, start2, nextChange.start2)
      else
        Range(start1, myIterable.length1, start2, myIterable.length2)
      if (range.isEmpty) return null
      return range
    }

    override fun hasNext(): Boolean {
      return myChanges.hasNext() || myNextUnchanged != null
    }

    override fun next(): Pair<Range, Boolean> {
      myNextUnchanged?.let { result ->
        myNextUnchanged = null
        return Pair(result, true)
      }

      val range = myChanges.next()
      myNextUnchanged = peekNextUnchanged(range.end1, range.end2)
      return Pair(range, false)
    }
  }
}
