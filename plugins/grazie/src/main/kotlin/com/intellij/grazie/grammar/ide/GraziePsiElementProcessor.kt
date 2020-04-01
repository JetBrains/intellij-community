// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar.ide

import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.ElementBehavior.*
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.grazie.utils.processElements
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiElementProcessor
import java.util.*
import kotlin.collections.ArrayList

class GraziePsiElementProcessor<T : PsiElement>(private val root: PsiElement,
                                                private val strategy: GrammarCheckingStrategy) : PsiElementProcessor<T> {
  companion object {
    data class Result(val tokens: Collection<TokenInfo>, val shifts: List<ElementShift>, val text: StringBuilder)

    fun processElements(root: PsiElement, strategy: GrammarCheckingStrategy): Result {
      val processor = GraziePsiElementProcessor<PsiElement>(root, strategy)
      processor.processElements(root)
      return Result(processor.tokens, processor.shifts, processor.text)
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
      this(IntRange(element.textOffset - root.textOffset, element.textOffset - root.textOffset + element.textLength - 1), behavior,
           ignoredGroup, ignoredCategories)
  }

  private val tokens = Collections.synchronizedCollection(ArrayList<TokenInfo>())
  private val pointers = IdentityHashMap<PsiElement, TokenInfo>()
  private val shifts = ArrayList<ElementShift>()
  private var lastNonTextTokenShiftIndex = -1
  private val text = StringBuilder()

  private val replaces = strategy.getReplaceCharRules(root)
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
    element !== root && isMyContextRoot(element) -> ABSORB // absorbing nested context root
    else -> getElementBehavior(root, element)
  }

  override fun execute(element: T): Boolean {
    val behavior = strategy.determineElementBehavior(element)
    val group = strategy.getIgnoredRuleGroup(root, element) ?: pointers[element.parent]?.ignoredGroup ?: RuleGroup.EMPTY
    val categories = strategy.getIgnoredTypoCategories(root, element) ?: pointers[element.parent]?.ignoredCategories ?: emptySet()

    val info = TokenInfo(root, element, behavior, group, categories)
    pointers[element] = info

    when (behavior) {
      TEXT -> if (element.node != null && element.node.getChildren(null).isEmpty()) {
        tokens.add(info)

        val position = text.length + 1
        text.appendElementText(element)

        if (lastNonTextTokenShiftIndex != -1) {
          if (StrategyUtils.deleteRedundantSpace(text, position)) {
            val shift = shifts[lastNonTextTokenShiftIndex]
            shifts[lastNonTextTokenShiftIndex] = ElementShift(shift.start - 1, shift.length + 1)
          }

          lastNonTextTokenShiftIndex = -1
        }
      }

      ABSORB, STEALTH -> {
        tokens.add(info)
        shifts.add(ElementShift(text.length, element.textLength))

        if (lastNonTextTokenShiftIndex == -1) {
          lastNonTextTokenShiftIndex = shifts.size - 1
        }
      }
    }

    // no need to process elements with ignored text
    return behavior == TEXT
  }
}
