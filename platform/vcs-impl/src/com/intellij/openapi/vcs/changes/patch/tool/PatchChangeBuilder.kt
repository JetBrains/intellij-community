// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch.tool

import com.intellij.diff.comparison.ByWord
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.tools.fragmented.LineNumberConvertor
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffRangeUtil
import com.intellij.diff.util.LineRange
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.AppliedSplitPatchHunk
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.HunkStatus
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList

class PatchChangeBuilder {
  private val textBuilder = StringBuilder()
  private val myHunks: MutableList<Hunk> = ArrayList()
  private val convertor1 = LineNumberConvertor.Builder()
  private val convertor2 = LineNumberConvertor.Builder()
  val separatorLines: IntList = IntArrayList()

  private var totalLines = 0

  fun exec(splitHunks: List<AppliedSplitPatchHunk>) {
    var lastBeforeLine = -1
    for (hunk in splitHunks) {
      val contextBefore = hunk.contextBefore
      val contextAfter = hunk.contextAfter

      val beforeRange = hunk.getLineRangeBefore()
      val afterRange = hunk.getLineRangeAfter()

      var overlappedContext = 0
      if (lastBeforeLine != -1) {
        if (lastBeforeLine >= beforeRange.start) {
          overlappedContext = lastBeforeLine - beforeRange.start + 1
        }
        else if (lastBeforeLine < beforeRange.start - 1) {
          appendSeparator()
        }
      }

      val trimContext: List<String> = contextBefore.subList(overlappedContext, contextBefore.size)
      addContext(trimContext, beforeRange.start + overlappedContext, afterRange.start + overlappedContext)


      val deletion = totalLines
      appendLines(hunk.deletedLines)
      val insertion = totalLines
      appendLines(hunk.insertedLines)
      val hunkEnd = totalLines

      convertor1.put(deletion, beforeRange.start + contextBefore.size, insertion - deletion)
      convertor2.put(insertion, afterRange.start + contextBefore.size, hunkEnd - insertion)


      addContext(contextAfter, beforeRange.end - contextAfter.size, afterRange.end - contextAfter.size)
      lastBeforeLine = beforeRange.end - 1


      val deletionRange = LineRange(deletion, insertion)
      val insertionRange = LineRange(insertion, hunkEnd)

      myHunks.add(Hunk(deletionRange, insertionRange, hunk.getAppliedTo(), hunk.status))
    }
  }

  private fun addContext(context: List<String>, beforeLineNumber: Int, afterLineNumber: Int) {
    convertor1.put(totalLines, beforeLineNumber, context.size)
    convertor2.put(totalLines, afterLineNumber, context.size)
    appendLines(context)
  }

  private fun appendLines(lines: List<String>) {
    for (line in lines) {
      textBuilder.append(line).append("\n")
    }
    totalLines += lines.size
  }

  private fun appendSeparator() {
    separatorLines.add(totalLines)
    textBuilder.append("\n")
    totalLines++
  }

  val patchContent: CharSequence
    get() = textBuilder

  val hunks: List<Hunk>
    get() = myHunks

  val lineConvertor1: LineNumberConvertor
    get() = convertor1.build()

  val lineConvertor2: LineNumberConvertor
    get() = convertor2.build()

  class Hunk(val patchDeletionRange: LineRange,
             val patchInsertionRange: LineRange,
             val appliedToLines: LineRange?,
             val status: HunkStatus)

  companion object {
    @JvmStatic
    fun computeInnerDifferences(patchContent: Document,
                                hunk: Hunk): List<DiffFragment>? {
      return computeInnerDifferences(patchContent.getImmutableCharSequence(), LineOffsetsUtil.create(patchContent), hunk)
    }

    @JvmStatic
    fun computeInnerDifferences(patchContent: CharSequence,
                                lineOffsets: LineOffsets,
                                hunk: Hunk): List<DiffFragment>? {
      val deletionRange = hunk.patchDeletionRange
      val insertionRange = hunk.patchInsertionRange

      if (deletionRange.isEmpty || insertionRange.isEmpty) return null

      try {
        val deleted = DiffRangeUtil.getLinesContent(patchContent, lineOffsets, deletionRange.start, deletionRange.end)
        val inserted = DiffRangeUtil.getLinesContent(patchContent, lineOffsets, insertionRange.start, insertionRange.end)

        return ByWord.compare(deleted, inserted, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)
      }
      catch (e: DiffTooBigException) {
        return null
      }
    }
  }
}
