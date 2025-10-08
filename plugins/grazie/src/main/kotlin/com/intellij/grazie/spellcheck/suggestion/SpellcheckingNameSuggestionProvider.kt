// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellcheck.suggestion

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider
import com.intellij.refactoring.rename.RenameUtil

class SpellcheckingNameSuggestionProvider : NameSuggestionProvider {

  private val suggestionLimit = 3
  private val minimalWordLength = 4

  override fun getSuggestedNames(element: PsiElement, context: PsiElement?, result: MutableSet<String>): SuggestedNameInfo? {
    val name = getName(element) ?: return null
    if (name.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) || name.length < minimalWordLength) return null

    val engine = GrazieSpellCheckerEngine.getInstance(element.project)
    val speller = engine.getSpeller() ?: return null
    if (!speller.isMisspelled(name)) return null

    val suggestions = speller.suggestAndRank(name, suggestionLimit)
      .toList()
      .sortedByDescending { it.second }
      .map { it.first }
      .filter { RenameUtil.isValidName(element.project, element, it) }
      .toList()
    if (suggestions.isEmpty()) return null

    result.addAll(suggestions)
    return object : SuggestedNameInfo(suggestions.toTypedArray()) {
      override fun nameChosen(name: String) {}
    }
  }

  private fun getName(element: PsiElement): String? = if (element is PsiNamedElement) element.name else null
}