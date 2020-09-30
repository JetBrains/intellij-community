// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar.ide

import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.ElementBehavior.*
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.grammar.strategy.impl.ReplaceCharRule
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.grazie.utils.processElements
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import java.util.*
import kotlin.collections.ArrayList

internal class GraziePsiElementProcessor<T : PsiElement>(
  private val commonParent: PsiElement,
  private val strategy: GrammarCheckingStrategy) : PsiElementProcessor<T> {

  companion object {
    data class RootWithText(val root: PsiElement, val text: StringBuilder)
    data class Result(val parent: PsiElement, val tokens: Collection<TokenInfo>, val shifts: List<ElementShift>,
                      val rootsWithText: List<RootWithText>)

    fun processElements(roots: List<PsiElement>, strategy: GrammarCheckingStrategy): Result {
      val parent = PsiTreeUtil.findCommonParent(roots)
      require(parent != null) { "Chained roots must have a common parent" }
      val processor = GraziePsiElementProcessor<PsiElement>(parent, strategy)
      val whitespaceTokens = strategy.getWhiteSpaceTokens()

      with (processor) {
        var prevOffset = parent.startOffset
        var isWhitespaceNeeded = false
        for (root in roots) {
          if (root.elementType in whitespaceTokens) {
            shifts.add(ElementShift(cumulativeTextLength, root.endOffset - prevOffset))
            isWhitespaceNeeded = true
            prevOffset = root.endOffset
            continue
          }

          require(strategy.isMyContextRoot(root)) { "PsiElement must be a context root or be in whitespaceTokens of strategy" }

          if (isWhitespaceNeeded) {
            isWhitespaceNeeded = false
            text.lastOrNull()?.let {
              val index = shifts.size - 1
              cumulativeTextLength += 1
              shifts[index] = ElementShift(cumulativeTextLength, shifts[index].length - 1)
              it.append(' ')
            }
          }

          text.add(StringBuilder())
          currentRoot = root
          shifts.add(ElementShift(cumulativeTextLength, root.startOffset - prevOffset))
          lastNonTextTokenShiftIndex = -1
          replaces = strategy.getReplaceCharRules(root)
          processElements(root)
          cumulativeTextLength += text.last().length
          prevOffset = root.endOffset
        }
      }

      val rootsWithText = roots.filter { it.elementType !in whitespaceTokens }.zip(processor.text) { root, text -> RootWithText(root, text) }
      return Result(parent, processor.tokens, processor.shifts, rootsWithText)
    }
  }

  data class ElementShift(val start: Int, val length: Int)

  data class TokenInfo(val range: IntRange,
                       val behavior: GrammarCheckingStrategy.ElementBehavior,
                       val ignoredGroup: RuleGroup,
                       val ignoredCategories: Set<Typo.Category>) {
    constructor(root: PsiElement,
                element: PsiElement,
                behavior: GrammarCheckingStrategy.ElementBehavior,
                ignoredGroup: RuleGroup,
                ignoredCategories: Set<Typo.Category>) :
      this(IntRange(element.startOffset - root.startOffset, element.startOffset - root.startOffset + element.textLength - 1), behavior,
           ignoredGroup, ignoredCategories)
  }

  private lateinit var currentRoot: PsiElement
  private val tokens = Collections.synchronizedCollection(ArrayList<TokenInfo>())
  private val pointers = IdentityHashMap<PsiElement, TokenInfo>()
  private val shifts = ArrayList<ElementShift>()
  private var lastNonTextTokenShiftIndex = -1
  private val text = ArrayList<StringBuilder>()
  private var cumulativeTextLength = 0

  private var replaces = emptyList<ReplaceCharRule>()
  private fun StringBuilder.appendElementText(element: PsiElement) {
    if (replaces.isEmpty()) {
      append(element.text)
    }
    else {
      element.text.forEach {
        append(replaces.fold(it) { char, rule -> rule(this, char) })
      }
    }
  }

  private fun GrammarCheckingStrategy.determineElementBehavior(element: PsiElement) = when {
    element !== currentRoot && isMyContextRoot(element) -> ABSORB // absorbing nested context root
    else -> getElementBehavior(currentRoot, element)
  }

  override fun execute(element: T): Boolean {
    val behavior = strategy.determineElementBehavior(element)
    val group = strategy.getIgnoredRuleGroup(currentRoot, element) ?: pointers[element.parent]?.ignoredGroup ?: RuleGroup.EMPTY
    val categories = strategy.getIgnoredTypoCategories(currentRoot, element) ?: pointers[element.parent]?.ignoredCategories ?: emptySet()

    val info = TokenInfo(commonParent, element, behavior, group, categories)
    pointers[element] = info

    when (behavior) {
      TEXT -> if (element.node != null && element.node.getChildren(null).isEmpty()) {
        tokens.add(info)

        val position = text.last().length + 1
        text.last().appendElementText(element)

        if (lastNonTextTokenShiftIndex != -1) {
          if (StrategyUtils.deleteRedundantSpace(text.last(), position)) {
            val shift = shifts[lastNonTextTokenShiftIndex]
            shifts[lastNonTextTokenShiftIndex] = ElementShift(shift.start - 1, shift.length + 1)
          }

          lastNonTextTokenShiftIndex = -1
        }
      }

      ABSORB, STEALTH -> {
        tokens.add(info)
        shifts.add(ElementShift(cumulativeTextLength + text.last().length, element.textLength))

        if (lastNonTextTokenShiftIndex == -1) {
          lastNonTextTokenShiftIndex = shifts.size - 1
        }
      }
    }

    // no need to process elements with ignored text
    return behavior == TEXT
  }
}
