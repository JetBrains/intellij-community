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
import java.util.*
import kotlin.collections.ArrayList

object GrammarChecker {
  private data class ShiftInText(val start: Int, val length: Int, val totalDeleted: Int)

  private fun GrammarCheckingStrategy.determineStealthyRanges(root: PsiElement, text: StringBuilder): List<IntRange> {
    return getStealthyRanges(root, text).sortedWith(Comparator.comparingInt { it.start })
  }

  private fun determineTextShifts(root: PsiElement, text: StringBuilder, strategy: GrammarCheckingStrategy,
                                  shifts: List<ElementShift>) = ArrayList<ShiftInText>().apply {
    fun addShift(position: Int, length: Int, totalDeleted: Int) {
      if (isNotEmpty() && last().start == position) {
        val last = removeAt(size - 1)
        // combine shifts from same position
        add(ShiftInText(position, last.length + length, last.totalDeleted + length))
      }
      else {
        add(ShiftInText(position, length, totalDeleted))
      }
    }

    var stealthed = 0 // count of newly removed characters from text after getResultedShifts()
    var total = 0     // total deleted chars from text
    val iterator = shifts.listIterator()

    // Iterate over ignored ranges determined by PsiElement behaviour and
    // ranges determined by strategy.getStealthyRanges, which working with text
    // while combining intersecting ranges
    for (range in strategy.determineStealthyRanges(root, text)) {
      var deleted = 0
      for ((position, length) in iterator) {
        if (position < range.start) {   // shift before range (remains the same, just add)
          addShift(position - stealthed, length, total + length)
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
      addShift(range.start - stealthed, deleted + range.length, total)
      stealthed += range.length
    }

    // after processing all ranges there still can be shifts after them
    for ((position, length) in iterator) {
      total += length
      addShift(position, length, total)
    }
  }

  fun check(root: PsiElement, strategy: GrammarCheckingStrategy): Set<Typo> {
    val (tokens, shifts, text) = GraziePsiElementProcessor.processElements(root, strategy)
    if (tokens.isEmpty()) return emptySet()

    val textShifts = determineTextShifts(root, text, strategy, shifts)
    val offset = StrategyUtils.trimLeadingQuotesAndSpaces(text)

    return check(root, text, textShifts, offset, tokens, strategy)
  }

  private fun findPositionInsideRoot(position: Int, shifts: List<ShiftInText>): Int {
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

  private fun check(root: PsiElement, text: StringBuilder, shifts: List<ShiftInText>, offset: Int,
                    tokens: Collection<TokenInfo>, strategy: GrammarCheckingStrategy) =
    GrammarEngine.getTypos(text.toString(), offset = offset).mapNotNull { typo ->
      val rangeInText = typo.location.errorRange
      val rangeInRoot = rangeInText.convertToRangeInRoot(shifts)

      val textRangesToDelete = findTextRangesToDelete(rangeInRoot, rangeInText, shifts)

      val patternRangeInRoot = typo.location.patternRange.convertToRangeInRoot(shifts)
      val tokensInTypoPatternRange = findTokensInTypoPatternRange(tokens, patternRangeInRoot)

      val category = typo.category
      when {
        !strategy.isTypoAccepted(root, rangeInRoot, patternRangeInRoot) -> null   // typo not accepted by strategy
        tokensInTypoPatternRange.any { token ->
          token.behavior == GrammarCheckingStrategy.ElementBehavior.ABSORB ||     // typo pattern in absorb element
          category in token.ignoredCategories ||                                // typo rule in ignored category
          typo.info.rule.id in token.ignoredGroup.rules                         // typo rule in ignored group
        } -> null

        else -> typo.copy(
          location = typo.location.copy(errorRange = rangeInRoot, patternRange = patternRangeInRoot,
                                        textRanges = textRangesToDelete, pointer = root.toPointer())
        )
      }
    }.toSet()

  private fun IntRange.convertToRangeInRoot(shifts: List<ShiftInText>): IntRange {
    return IntRange(findPositionInsideRoot(start, shifts), findPositionInsideRoot(endInclusive, shifts))
  }
}
