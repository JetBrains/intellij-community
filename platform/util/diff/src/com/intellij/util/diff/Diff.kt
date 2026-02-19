// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff

import com.intellij.diff.util.Enumerator
import com.intellij.openapi.util.text.LineTokenizer
import org.jetbrains.annotations.NonNls
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.math.min

object Diff {
  @JvmStatic
  @Throws(FilesTooBigForDiffException::class)
  fun buildChanges(before: CharSequence, after: CharSequence): Change? {
    return buildChanges(splitLines(before), splitLines(after))
  }

  @JvmStatic
  fun splitLines(s: CharSequence): Array<String> {
    return if (s.isEmpty()) arrayOf("") else LineTokenizer.tokenize(s, false, false)
  }

  @JvmStatic
  @Throws(FilesTooBigForDiffException::class)
  fun <T> buildChanges(objects1: Array<T>, objects2: Array<T>): Change? {
    val startShift = getStartShift(objects1, objects2)
    val endCut = getEndCut(objects1, objects2, startShift)

    val changeRef = doBuildChangesFast(objects1.size, objects2.size, startShift, endCut)
    if (changeRef != null) return changeRef.value

    val trimmedLength = objects1.size + objects2.size - 2 * startShift - 2 * endCut
    val enumerator = Enumerator<T>(trimmedLength)
    val ints1 = enumerator.enumerate(objects1, startShift, endCut)
    val ints2 = enumerator.enumerate(objects2, startShift, endCut)
    return doBuildChanges(ints1, ints2, ChangeBuilder(startShift))
  }

  @JvmStatic
  @Throws(FilesTooBigForDiffException::class)
  fun buildChanges(array1: IntArray, array2: IntArray): Change? {
    val startShift = getStartShift(array1, array2)
    val endCut = getEndCut(array1, array2, startShift)

    val changeRef = doBuildChangesFast(array1.size, array2.size, startShift, endCut)
    if (changeRef != null) return changeRef.value

    val copyArray = startShift != 0 || endCut != 0
    val ints1 = if (copyArray) array1.copyOfRange(startShift, array1.size - endCut) else array1
    val ints2 = if (copyArray) array2.copyOfRange(startShift, array2.size - endCut) else array2
    return doBuildChanges(ints1, ints2, ChangeBuilder(startShift))
  }

  private fun doBuildChangesFast(length1: Int, length2: Int, startShift: Int, endCut: Int): Ref<Change>? {
    val trimmedLength1 = length1 - startShift - endCut
    val trimmedLength2 = length2 - startShift - endCut
    if (trimmedLength1 != 0 && trimmedLength2 != 0) return null
    val change = if (trimmedLength1 != 0 || trimmedLength2 != 0) {
      Change(startShift, startShift, trimmedLength1, trimmedLength2, null)
    }
    else {
      null
    }
    return Ref(change)
  }

  private data class Ref<T>(val value: T?)

  @Throws(FilesTooBigForDiffException::class)
  private fun doBuildChanges(ints1: IntArray, ints2: IntArray, builder: ChangeBuilder): Change? {
    val reindexer = Reindexer() // discard unique elements, that have no chance to be matched
    val discarded: Array<IntArray> = reindexer.discardUnique(ints1, ints2)

    if (discarded[0].isEmpty() && discarded[1].isEmpty()) {
      // assert trimmedLength > 0
      builder.addChange(ints1.size, ints2.size)
      return builder.firstChange
    }

    var changes: Array<BitSet>?
    if (DiffConfig.USE_PATIENCE_ALG) {
      val patienceIntLCS = PatienceIntLCS(discarded[0], discarded[1])
      patienceIntLCS.execute()
      changes = patienceIntLCS.changes
    }
    else {
      try {
        val intLCS = MyersLCS(discarded[0], discarded[1])
        intLCS.executeWithThreshold()
        changes = intLCS.changes
      }
      catch (_: FilesTooBigForDiffException) {
        val patienceIntLCS = PatienceIntLCS(discarded[0], discarded[1])
        patienceIntLCS.execute(true)
        changes = patienceIntLCS.changes
      }
    }

    reindexer.reindex(changes, builder)
    return builder.firstChange
  }

  private fun <T> getStartShift(o1: Array<T>, o2: Array<T>): Int {
    val size = min(o1.size, o2.size)
    var idx = 0
    for (i in 0..<size) {
      if (o1[i] != o2[i]) break
      ++idx
    }
    return idx
  }

  private fun <T> getEndCut(o1: Array<T>, o2: Array<T>, startShift: Int): Int {
    val size = min(o1.size, o2.size) - startShift
    var idx = 0

    for (i in 0..<size) {
      if (o1[o1.size - i - 1] != o2[o2.size - i - 1]) break
      ++idx
    }
    return idx
  }

  private fun getStartShift(o1: IntArray, o2: IntArray): Int {
    val size = min(o1.size, o2.size)
    var idx = 0
    for (i in 0..<size) {
      if (o1[i] != o2[i]) break
      ++idx
    }
    return idx
  }

  private fun getEndCut(o1: IntArray, o2: IntArray, startShift: Int): Int {
    val size = min(o1.size, o2.size) - startShift
    var idx = 0

    for (i in 0..<size) {
      if (o1[o1.size - i - 1] != o2[o2.size - i - 1]) break
      ++idx
    }
    return idx
  }

  @JvmStatic
  @Throws(FilesTooBigForDiffException::class)
  fun translateLine(before: CharSequence, after: CharSequence, line: Int, approximate: Boolean): Int {
    var strings1 = LineTokenizer.tokenize(before, false)
    var strings2 = LineTokenizer.tokenize(after, false)
    if (approximate) {
      strings1 = trim(strings1)
      strings2 = trim(strings2)
    }
    val change = buildChanges(strings1, strings2)
    return translateLine(change, line, approximate)
  }

  private fun trim(lines: Array<String>): Array<String> {
    return Array(lines.size) { i ->
      lines[i].trim { it <= ' ' }
    }
  }

  /**
   * Tries to translate given line that pointed to the text before change to the line that points to the same text after the change.
   *
   * @param change    target change
   * @param line      target line before change
   * @return          translated line if the processing is ok; negative value otherwise
   */
  @JvmStatic
  @JvmOverloads
  fun translateLine(change: Change?, line: Int, approximate: Boolean = false): Int {
    var result = line

    var currentChange = change
    while (currentChange != null) {
      if (line < currentChange.line0) break
      if (line >= currentChange.line0 + currentChange.deleted) {
        result += currentChange.inserted - currentChange.deleted
      }
      else {
        return if (approximate) currentChange.line1 else -1
      }

      currentChange = currentChange.link
    }

    return result
  }

  @JvmStatic
  @Throws(FilesTooBigForDiffException::class)
  fun linesDiff(
    lines1: Array<CharSequence>,
    lines2: Array<CharSequence>
  ): CharSequence? {
    var ch = buildChanges(lines1, lines2)
    if (ch == null) {
      return null
    }
    val sb = StringBuilder()
    while (ch != null) {
      if (sb.isNotEmpty()) {
        sb.append("====================").append("\n")
      }
      for (i in ch.line0..<ch.line0 + ch.deleted) {
        sb.append('-').append(lines1[i]).append('\n')
      }
      for (i in ch.line1..<ch.line1 + ch.inserted) {
        sb.append('+').append(lines2[i]).append('\n')
      }
      ch = ch.link
    }
    return sb.toString()
  }

  /** Cons an additional entry onto the front of an edit script OLD.
   * LINE0 and LINE1 are the first affected lines in the two files (origin 0).
   * DELETED is the number of lines deleted here from file 0.
   * INSERTED is the number of lines inserted here in file 1.
   *
   * If DELETED is 0 then LINE0 is the number of the line before
   * which the insertion was done; vice versa for INSERTED and LINE1.   */
  open class Change(
    /** Line number of 1st deleted line.   */
    @JvmField val line0: Int,
    /** Line number of 1st inserted line.   */
    @JvmField val line1: Int,
    /** # lines of file 0 changed here.   */
    @JvmField val deleted: Int,
    /** # lines of file 1 changed here.   */
    @JvmField val inserted: Int,
    // todo remove. Return lists instead.
    /**
     * Previous or next edit command.
     */
    @JvmField var link: Change?
  ) {

    override fun toString(): @NonNls String {
      return "change[inserted=$inserted, deleted=$deleted, line0=$line0, line1=$line1]"
    }

    open fun toList(): ArrayList<Change> {
      val result = ArrayList<Change>()
      var current: Change? = this
      while (current != null) {
        result.add(current)
        current = current.link
      }
      return result
    }
  }

  open class ChangeBuilder(startShift: Int) : LCSBuilder {
    private var myIndex1 = 0
    private var myIndex2 = 0
    private var _firstChange: Change? = null
    open val firstChange: Change?
      get() = _firstChange
    private var myLastChange: Change? = null

    init {
      skip(startShift, startShift)
    }

    override fun addChange(first: Int, second: Int) {
      val change = Change(myIndex1, myIndex2, first, second, null)
      myLastChange?.let {
        it.link = change
      } ?: run {
        _firstChange = change
      }
      myLastChange = change
      skip(first, second)
    }

    private fun skip(first: Int, second: Int) {
      myIndex1 += first
      myIndex2 += second
    }

    override fun addEqual(length: Int) {
      skip(length, length)
    }
  }
}
