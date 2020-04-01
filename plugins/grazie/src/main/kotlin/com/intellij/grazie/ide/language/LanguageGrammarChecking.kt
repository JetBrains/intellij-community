// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

class LanguageGrammarChecking : LanguageExtensionPoint<GrammarCheckingStrategy>() {
  companion object : LanguageExtension<GrammarCheckingStrategy>("com.intellij.grazie.grammar.strategy") {
    val EP_NAME: ExtensionPointName<LanguageExtensionPoint<GrammarCheckingStrategy>> = ExtensionPointName.create(
      "com.intellij.grazie.grammar.strategy")

    fun getLanguageExtensionPoints(): List<LanguageExtensionPoint<GrammarCheckingStrategy>> = EP_NAME.extensionList

    fun getStrategies(): Set<GrammarCheckingStrategy> = getLanguageExtensionPoints().map { it.instance }.toSet()

    fun getExtensionPointByStrategy(strategy: GrammarCheckingStrategy) = EP_NAME.extensions.firstOrNull { it.instance == strategy }

    fun getStrategiesForElement(element: PsiElement, enabledIDs: Set<String>, disabledIDs: Set<String>): Set<GrammarCheckingStrategy> {
      return LanguageGrammarChecking.allForLanguage(element.language)
        .asSequence()
        .filter {
          it.isMyContextRoot(element) &&
          it.getContextRootTextDomain(element) != TextDomain.NON_TEXT &&
          (it.getID() in enabledIDs || (it.isEnabledByDefault() && it.getID() !in disabledIDs))
        }.toSet()
    }
  }
}
