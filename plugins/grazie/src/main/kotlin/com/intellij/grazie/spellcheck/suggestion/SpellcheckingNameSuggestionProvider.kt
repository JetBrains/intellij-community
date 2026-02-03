// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellcheck.suggestion

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.spellchecker.SpellCheckerManager

class SpellcheckingNameSuggestionProvider : NameSuggestionProvider {

  private val minimalWordLength = 4

  override fun getSuggestedNames(element: PsiElement, context: PsiElement?, result: MutableSet<String>): SuggestedNameInfo? {
    if (!Registry.`is`("spellchecker.suggestion.provider.enabled")) return null
    val name = getName(element) ?: return null
    if (name.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) || name.length < minimalWordLength) return null

    val manager = SpellCheckerManager.getInstance(element.project)
    if (!manager.hasProblem(name)) return null

    val suggestions = manager.getSuggestions(name)
      .filter { RenameUtil.isValidName(element.project, element, it) }
      .toList()
    if (suggestions.isEmpty()) return null

    result.addAll(suggestions)
    return SuggestedNameInfo.NULL_INFO
  }

  private fun getName(element: PsiElement): String? = if (element is PsiNamedElement) element.name else null
}