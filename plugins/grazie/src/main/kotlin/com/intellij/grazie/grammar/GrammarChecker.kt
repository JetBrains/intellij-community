// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.grammar.ide.GraziePsiElementProcessor
import com.intellij.grazie.grammar.ide.GraziePsiElementProcessor.ElementShift
import com.intellij.grazie.grammar.ide.GraziePsiElementProcessor.TokenInfo
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.utils.length
import com.intellij.grazie.utils.toPointer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.startOffset
import java.util.*
import kotlin.collections.ArrayList

object GrammarChecker {
  private data class ShiftInText(val start: Int, val length: Int, val totalDeleted: Int)

  private fun GrammarCheckingStrategy.determineStealthyRanges(textsWithRoots: List<GraziePsiElementProcessor.Companion.RootWithText>):
    Pair<StringBuilder, List<IntRange>> {
    val resultText = StringBuilder()
    val ranges = ArrayList<IntRange>()
    var offset = 0
    for ((root, text) in textsWithRoots) {
      ranges.addAll(getStealthyRanges(root, text).map { IntRange(it.start + offset, it.endInclusive + offset) })
      resultText.append(text)
      offset += text.length
    }

    return resultText to ranges.sortedWith(Comparator.comparingInt { it.start })
  }

  private fun determineTextShifts(textsWithRoots: List<GraziePsiElementProcessor.Companion.RootWithText>, strategy: GrammarCheckingStrategy,
                                  shifts: List<ElementShift>): Pair<StringBuilder, ArrayList<ShiftInText>> {
    fun ArrayList<ShiftInText>.addShift(position: Int, length: Int, totalDeleted: Int) {
      if (isNotEmpty() && last().start == position) {
        val last = removeAt(size - 1)
        // combine shifts from same position
        add(ShiftInText(position, last.length + length, last.totalDeleted + length))
      }
      else {
        add(ShiftInText(position, length, totalDeleted))
      }
    }

    val result = ArrayList<ShiftInText>()
    var stealthed = 0 // count of newly removed characters from text after getResultedShifts()
    var total = 0     // total deleted chars from text
    val iterator = shifts.listIterator()

    // Iterate over ignored ranges determined by PsiElement behaviour and
    // ranges determined by strategy.getStealthyRanges, which working with text
    // while combining intersecting ranges
    val (text, ranges) = strategy.determineStealthyRanges(textsWithRoots)
    for (range in ranges) {
      var deleted = 0
      for ((position, length) in iterator) {
        if (position < range.start) {   // shift before range (remains the same, just add)
          result.addShift(position - stealthed, length, total + length)
        }
        else if (position in range) { // shift inside range (combine in one)
          deleted += length
        }
        else {                        // shift after range - need a step back
          iterator.previous()
          break
        }

        total += length
      }

      // delete text from stealthy ranges (we need stealthed to track offset in text)
      text.delete(range.start - stealthed, range.endInclusive + 1 - stealthed)

      total += range.length
      result.addShift(range.start - stealthed, deleted + range.length, total)
      stealthed += range.length
    }

    // after processing all ranges there still can be shifts after them
    for ((position, length) in iterator) {
      total += length
      result.addShift(position, length, total)
    }

    return text to result
  }

  fun check(roots: List<PsiElement>, strategy: GrammarCheckingStrategy): Set<Typo> {
    val (parent, tokens, shifts, texts) = GraziePsiElementProcessor.processElements(roots, strategy)
    if (tokens.isEmpty()) return emptySet()

    val (text, textShifts) = determineTextShifts(texts, strategy, shifts)
    val offset = StrategyUtils.trimLeadingQuotesAndSpaces(text)

    return check(parent, roots, text, textShifts, offset, tokens, strategy)
  }

  private fun findPositionInsideParent(position: Int, shifts: List<ShiftInText>): Int {
    val index = shifts.binarySearch { it.start.compareTo(position) }
    return when {
             index >= 0 -> shifts[index].totalDeleted
             -(index + 1) > 0 -> shifts[-(index + 1) - 1].totalDeleted
             else -> 0
           } + position
  }

  private fun findTextRangesToDelete(rangeInRoot: IntRange,
                                     rangeInText: IntRange,
                                     shifts: List<ShiftInText>) = ArrayList<IntRange>().apply {
    var start = rangeInRoot.start
    // take all shifts inside typo and invert them
    shifts.filter { it.start > rangeInText.start && it.start <= rangeInText.endInclusive }.forEach { shift ->
      add(IntRange(start, shift.start + shift.totalDeleted - shift.length - 1))
      start = shift.start + shift.totalDeleted
    }
    add(IntRange(start, rangeInRoot.endInclusive + 1))
  }

  private fun findTokensInTypoPatternRange(tokens: Collection<TokenInfo>, patternRangeInRoot: IntRange): List<TokenInfo> {
    return tokens.filter { it.range.endInclusive >= patternRangeInRoot.start && it.range.start <= patternRangeInRoot.endInclusive }.also {
      check(it.isNotEmpty()) { "No tokens for range in typo: $patternRangeInRoot in ${tokens.map { token -> token.range }}" }
    }
  }

  private fun findRootInParentContainingRange(parent: PsiElement, roots: List<PsiElement>, rangeInParent: IntRange): PsiElement? {
    return roots.map {
      val start = it.startOffset - parent.startOffset
      it to IntRange(start, start + it.textLength)
    }.find { it.second.start < rangeInParent.start && it.second.endInclusive <= rangeInParent.endInclusive }?.first
  }

  private fun check(parent: PsiElement, roots: List<PsiElement>, text: StringBuilder, shifts: List<ShiftInText>, offset: Int,
                    tokens: Collection<TokenInfo>, strategy: GrammarCheckingStrategy) =
    GrammarEngine.getTypos(text.toString(), offset = offset).mapNotNull { typo ->
      val rangeInText = typo.location.errorRange
      val rangeInParent = rangeInText.convertToRangeInParent(shifts)

      val textRangesToDelete = findTextRangesToDelete(rangeInParent, rangeInText, shifts)

      val patternRangeInParent = typo.location.patternRange.convertToRangeInParent(shifts)
      val tokensInTypoPatternRange = findTokensInTypoPatternRange(tokens, patternRangeInParent)

      val root = findRootInParentContainingRange(parent, roots, rangeInParent)
      val isTypoAccepted = if (root == null) {
        strategy.isTypoAccepted(parent, roots, rangeInParent, patternRangeInParent)
      }
      else {
        strategy.isTypoAccepted(
          root,
          rangeInParent.convertToRangeInRoot(root, parent),
          patternRangeInParent.convertToRangeInRoot(root, parent)
        ) && strategy.isTypoAccepted(parent, roots, rangeInParent, patternRangeInParent)
      }

      val category = typo.category
      when {
        !isTypoAccepted -> null                                                 // typo not accepted by strategy
        tokensInTypoPatternRange.any { token ->
          token.behavior == GrammarCheckingStrategy.ElementBehavior.ABSORB ||   // typo pattern in absorb element
          category in token.ignoredCategories ||                                // typo rule in ignored category
          typo.info.rule.id in token.ignoredGroup.rules                         // typo rule in ignored group
        } -> null

        else -> typo.copy(
          location = typo.location.copy(errorRange = rangeInParent, patternRange = patternRangeInParent,
                                        textRanges = textRangesToDelete, pointer = parent.toPointer())
        )
      }
    }.toSet()

  private fun IntRange.convertToRangeInParent(shifts: List<ShiftInText>): IntRange {
    return IntRange(findPositionInsideParent(start, shifts), findPositionInsideParent(endInclusive, shifts))
  }

  private fun IntRange.convertToRangeInRoot(root: PsiElement, parent: PsiElement): IntRange {
    val offset = (root.startOffset - parent.startOffset)
    return IntRange(start - offset, endInclusive - offset)
  }
}
