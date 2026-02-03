// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison

import com.intellij.diff.comparison.ComparisonUtil.isEquals
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.util.Range
import kotlin.math.max
import kotlin.math.min

/*
 * Given matchings on words, split initial line block into 'logically different' line blocks
 */
internal class LineFragmentSplitter(
  private val myText1: CharSequence,
  private val myText2: CharSequence,
  private val myWords1: List<ByWordRt.InlineChunk>,
  private val myWords2: List<ByWordRt.InlineChunk>,
  private val myIterable: FairDiffIterable,
  private val myIndicator: CancellationChecker
) {
  private val myResult: MutableList<WordBlock> = ArrayList()

  private var last1 = -1
  private var last2 = -1
  private var pendingChunk: PendingChunk? = null

  // indexes here are a bit tricky
  // -1 - the beginning of file, words.size() - end of file, everything in between - InlineChunks (words or newlines)
  fun run(): MutableList<WordBlock> {
    var hasEqualWords = false
    for (range in myIterable.iterateUnchanged()) {
      val count = range.end1 - range.start1
      for (i in 0..<count) {
        val index1 = range.start1 + i
        val index2 = range.start2 + i

        if (isNewline(myWords1, index1) && isNewline(myWords2, index2)) { // split by matched newlines
          addLineChunk(index1, index2, hasEqualWords)
          hasEqualWords = false
        }
        else {
          if (isFirstInLine(myWords1, index1) && isFirstInLine(myWords2, index2)) { // split by matched first word in line
            addLineChunk(index1 - 1, index2 - 1, hasEqualWords)
            hasEqualWords = false
          }
          // TODO: split by 'last word in line' + 'last word in whole sequence' ?
          hasEqualWords = true
        }
      }
    }
    addLineChunk(myWords1.size, myWords2.size, hasEqualWords)

    pendingChunk?.let {
      myResult.add(it.block)
    }

    return myResult
  }

  private fun addLineChunk(end1: Int, end2: Int, hasEqualWords: Boolean) {
    if (last1 > end1 || last2 > end2) return

    val chunk = createChunk(last1, last2, end1, end2, hasEqualWords)
    if (chunk.block.offsets.isEmpty) return

    pendingChunk = pendingChunk?.let {
      if (shouldMergeChunks(it, chunk)) {
        mergeChunks(it, chunk)
      }
      else {
        myResult.add(it.block)
        chunk
      }
    } ?: run {
      chunk
    }

    last1 = end1
    last2 = end2
  }

  private fun createChunk(start1: Int, start2: Int, end1: Int, end2: Int, hasEqualWords: Boolean): PendingChunk {
    val startOffset1: Int = getOffset(myWords1, myText1, start1)
    val startOffset2: Int = getOffset(myWords2, myText2, start2)
    val endOffset1: Int = getOffset(myWords1, myText1, end1)
    val endOffset2: Int = getOffset(myWords2, myText2, end2)

    val start1 = max(0, start1 + 1)
    val start2 = max(0, start2 + 1)
    val end1 = min(end1 + 1, myWords1.size)
    val end2 = min(end2 + 1, myWords2.size)

    val block = WordBlock(
      Range(start1, end1, start2, end2),
      Range(startOffset1, endOffset1, startOffset2, endOffset2)
    )

    return PendingChunk(block, hasEqualWords, hasWordsInside(block), isEqualsIgnoreWhitespace(block))
  }

  private fun isEqualsIgnoreWhitespace(block: WordBlock): Boolean {
    val sequence1 = myText1.subSequence(block.offsets.start1, block.offsets.end1)
    val sequence2 = myText2.subSequence(block.offsets.start2, block.offsets.end2)
    return isEquals(sequence1, sequence2, ComparisonPolicy.IGNORE_WHITESPACES)
  }

  private fun hasWordsInside(block: WordBlock): Boolean {
    for (i in block.words.start1..<block.words.end1) {
      if (myWords1[i] !is ByWordRt.NewlineChunk) return true
    }
    for (i in block.words.start2..<block.words.end2) {
      if (myWords2[i] !is ByWordRt.NewlineChunk) return true
    }
    return false
  }

  //
  // Helpers
  //
  class WordBlock(val words: Range, val offsets: Range)

  private class PendingChunk(
    val block: WordBlock,
    val hasEqualWords: Boolean,
    val hasWordsInside: Boolean,
    val isEqualIgnoreWhitespaces: Boolean
  )

  companion object {
    private fun shouldMergeChunks(chunk1: PendingChunk, chunk2: PendingChunk): Boolean {
      if (!chunk1.hasEqualWords && !chunk2.hasEqualWords) return true // combine lines, that matched only by '\n'

      if (chunk1.isEqualIgnoreWhitespaces && chunk2.isEqualIgnoreWhitespaces) return true // combine whitespace-only changed lines

      if (!chunk1.hasWordsInside || !chunk2.hasWordsInside) return true // squash block without words in it

      return false
    }

    private fun mergeChunks(chunk1: PendingChunk, chunk2: PendingChunk): PendingChunk {
      val block1 = chunk1.block
      val block2 = chunk2.block
      val newBlock = WordBlock(Range(block1.words.start1, block2.words.end1, block1.words.start2, block2.words.end2),
                               Range(block1.offsets.start1, block2.offsets.end1, block1.offsets.start2, block2.offsets.end2))
      return PendingChunk(newBlock,
                          chunk1.hasEqualWords || chunk2.hasEqualWords,
                          chunk1.hasWordsInside || chunk2.hasWordsInside,
                          chunk1.isEqualIgnoreWhitespaces && chunk2.isEqualIgnoreWhitespaces)
    }

    private fun getOffset(words: List<ByWordRt.InlineChunk>, text: CharSequence, index: Int): Int {
      if (index == -1) return 0
      if (index == words.size) return text.length
      val chunk = words[index]
      check(chunk is ByWordRt.NewlineChunk)
      return chunk.offset2
    }

    private fun isNewline(words1: List<ByWordRt.InlineChunk>, index: Int): Boolean {
      return words1[index] is ByWordRt.NewlineChunk
    }

    private fun isFirstInLine(words1: List<ByWordRt.InlineChunk>, index: Int): Boolean {
      if (index == 0) return true
      return words1[index - 1] is ByWordRt.NewlineChunk
    }
  }
}
