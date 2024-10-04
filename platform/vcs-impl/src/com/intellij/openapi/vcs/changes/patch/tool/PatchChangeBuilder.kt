// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch.tool

import com.intellij.diff.comparison.ByWord
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.tools.fragmented.LineNumberConvertor
import com.intellij.diff.tools.simple.AlignableChange
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.*
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.AppliedSplitPatchHunk
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.HunkStatus
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PatchChangeBuilder {
  private val textBuilder = StringBuilder()
  private val convertor1 = LineNumberConvertor.Builder()
  private val convertor2 = LineNumberConvertor.Builder()
  private val separatorLines: IntList = IntArrayList()

  private var totalLines = 0

  fun buildFromApplied(splitHunks: List<AppliedSplitPatchHunk>): AppliedPatchState {
    val hunks = mutableListOf<AppliedHunk>()

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
      addChangedLines(hunk.deletedLines, beforeRange.start + contextBefore.size, false)
      val insertion = totalLines
      addChangedLines(hunk.insertedLines, afterRange.start + contextBefore.size, true)
      val hunkEnd = totalLines

      addContext(contextAfter, beforeRange.end - contextAfter.size, afterRange.end - contextAfter.size)
      lastBeforeLine = beforeRange.end - 1


      val deletionRange = LineRange(deletion, insertion)
      val insertionRange = LineRange(insertion, hunkEnd)

      hunks.add(AppliedHunk(deletionRange, insertionRange, hunk.getAppliedTo(), hunk.status))
    }

    return AppliedPatchState(textBuilder, hunks, convertor1.build(), convertor2.build(), separatorLines)
  }

  fun build(patchHunks: List<PatchHunk>): PatchState {
    val hunks = mutableListOf<Hunk>()

    for (hunk in patchHunks) {
      if (totalLines > 0) {
        appendSeparator()
      }

      val beforeRange = LineRange(hunk.startLineBefore, hunk.endLineBefore)
      val afterRange = LineRange(hunk.startLineAfter, hunk.endLineAfter)

      var beforeBlockLines = 0
      var afterBlockLines = 0
      cutIntoBlocks(hunk.lines) { preContextLines, deletedLines, insertedLines ->
        addContext(preContextLines.map { line -> line.text }, beforeRange.start + beforeBlockLines, afterRange.start + afterBlockLines)
        beforeBlockLines += preContextLines.size
        afterBlockLines += preContextLines.size

        val deletion = totalLines
        addChangedLines(deletedLines.map { line -> line.text }, beforeRange.start + beforeBlockLines, false)
        beforeBlockLines += deletedLines.size

        val insertion = totalLines
        addChangedLines(insertedLines.map { line -> line.text }, afterRange.start + afterBlockLines, true)
        afterBlockLines += insertedLines.size
        val hunkEnd = totalLines

        val deletionRange = LineRange(deletion, insertion)
        val insertionRange = LineRange(insertion, hunkEnd)

        if (!deletionRange.isEmpty || !insertionRange.isEmpty) {
          hunks.add(Hunk(deletionRange, insertionRange))
        }
      }
    }

    return PatchState(textBuilder, hunks, convertor1.build(), convertor2.build(), separatorLines)
  }

  private fun addChangedLines(lines: List<String>, lineNumber: Int, isAddition: Boolean) {
    if (isAddition) {
      convertor2.put(totalLines, lineNumber, lines.size)
    }
    else {
      convertor1.put(totalLines, lineNumber, lines.size)
    }
    appendLines(lines)
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

  class PatchState(
    val patchContent: CharSequence,
    val hunks: List<Hunk>,
    val lineConvertor1: LineNumberConvertor,
    val lineConvertor2: LineNumberConvertor,
    val separatorLines: IntList
  )

  class AppliedPatchState(
    val patchContent: CharSequence,
    val hunks: List<AppliedHunk>,
    val lineConvertor1: LineNumberConvertor,
    val lineConvertor2: LineNumberConvertor,
    val separatorLines: IntList
  )

  open class Hunk(val patchDeletionRange: LineRange,
                  val patchInsertionRange: LineRange)

  class AppliedHunk(patchDeletionRange: LineRange,
                    patchInsertionRange: LineRange,
                    val appliedToLines: LineRange?,
                    val status: HunkStatus) : Hunk(patchDeletionRange, patchInsertionRange)

  class PatchSideChange(val range: Range) : AlignableChange {
    override val diffType: TextDiffType get() = DiffUtil.getDiffType(range)
    override fun getStartLine(side: Side): Int = side.select(range.start1, range.start2)
    override fun getEndLine(side: Side): Int = side.select(range.end1, range.end2)
  }

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

@ApiStatus.Internal
class SideBySidePatchChangeBuilder {
  private val textBuilder1 = StringBuilder()
  private val textBuilder2 = StringBuilder()
  private val convertor1 = LineNumberConvertor.Builder()
  private val convertor2 = LineNumberConvertor.Builder()
  private val separatorLines1: IntList = IntArrayList()
  private val separatorLines2: IntList = IntArrayList()

  private var totalLines1 = 0
  private var totalLines2 = 0

  fun build(patchHunks: List<PatchHunk>): SideBySidePatchState {
    val changes = mutableListOf<PatchChangeBuilder.PatchSideChange>()

    for (hunk in patchHunks) {
      if (totalLines1 > 0 || totalLines2 > 0) {
        appendSeparator()
      }

      val beforeRange = LineRange(hunk.startLineBefore, hunk.endLineBefore)
      val afterRange = LineRange(hunk.startLineAfter, hunk.endLineAfter)

      var beforeBlockLines = 0
      var afterBlockLines = 0
      cutIntoBlocks(hunk.lines) { preContextLines, deletedLines, insertedLines ->
        addContext(preContextLines.map { line -> line.text }, beforeRange.start + beforeBlockLines, afterRange.start + afterBlockLines)
        beforeBlockLines += preContextLines.size
        afterBlockLines += preContextLines.size

        val deletion = totalLines1
        addChangedLines(deletedLines.map { line -> line.text }, beforeRange.start + beforeBlockLines, false)
        beforeBlockLines += deletedLines.size

        val insertion = totalLines2
        addChangedLines(insertedLines.map { line -> line.text }, afterRange.start + afterBlockLines, true)
        afterBlockLines += insertedLines.size

        val range = Range(deletion, totalLines1, insertion, totalLines2)
        if (!range.isEmpty) {
          changes.add(PatchChangeBuilder.PatchSideChange(range))
        }
      }
    }

    return SideBySidePatchState(textBuilder1, textBuilder2, changes,
                                convertor1.build(), convertor2.build(),
                                separatorLines1, separatorLines2)
  }

  private fun addChangedLines(lines: List<String>, lineNumber: Int, isAddition: Boolean) {
    if (isAddition) {
      convertor2.put(totalLines2, lineNumber, lines.size)
      appendLines2(lines)
    }
    else {
      convertor1.put(totalLines1, lineNumber, lines.size)
      appendLines1(lines)
    }
  }

  private fun addContext(context: List<String>, beforeLineNumber: Int, afterLineNumber: Int) {
    convertor1.put(totalLines1, beforeLineNumber, context.size)
    convertor2.put(totalLines2, afterLineNumber, context.size)
    appendLines1(context)
    appendLines2(context)
  }

  private fun appendSeparator() {
    separatorLines1.add(totalLines1)
    separatorLines2.add(totalLines2)
    textBuilder1.append("\n")
    textBuilder2.append("\n")
    totalLines1++
    totalLines2++
  }

  private fun appendLines1(lines: List<String>) {
    for (line in lines) {
      textBuilder1.append(line).append("\n")
    }
    totalLines1 += lines.size
  }

  private fun appendLines2(lines: List<String>) {
    for (line in lines) {
      textBuilder2.append(line).append("\n")
    }
    totalLines2 += lines.size
  }


  class SideBySidePatchState(
    val patchContent1: CharSequence,
    val patchContent2: CharSequence,
    val changes: List<PatchChangeBuilder.PatchSideChange>,
    val lineConvertor1: LineNumberConvertor,
    val lineConvertor2: LineNumberConvertor,
    val separatorLines1: IntList,
    val separatorLines2: IntList
  )
}

private fun cutIntoBlocks(lines: List<PatchLine>,
                          consumer: (preContext: List<PatchLine>, deletions: List<PatchLine>, additions: List<PatchLine>) -> Unit) {
  var lastType = PatchLine.Type.CONTEXT

  val preContext = mutableListOf<PatchLine>()
  val deletions = mutableListOf<PatchLine>()
  val additions = mutableListOf<PatchLine>()

  for (line in lines) {
    val type = line.type
    if (lastType != type && lastType != PatchLine.Type.CONTEXT &&
        !(lastType == PatchLine.Type.REMOVE && type == PatchLine.Type.ADD)) {
      consumer(preContext, deletions, additions)
      preContext.clear()
      deletions.clear()
      additions.clear()
    }
    lastType = type

    when (type) {
      PatchLine.Type.CONTEXT -> preContext.add(line)
      PatchLine.Type.REMOVE -> deletions.add(line)
      PatchLine.Type.ADD -> additions.add(line)
    }
  }
  consumer(preContext, deletions, additions)
}
