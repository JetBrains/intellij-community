// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.ide.language.commit.CommitMessageGrammarCheckingStrategy
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

private const val EXTENSION_POINT_NAME = "com.intellij.grazie.grammar.strategy"

object LanguageGrammarChecking : LanguageExtension<GrammarCheckingStrategy>(EXTENSION_POINT_NAME) {
  val EP_NAME = ExtensionPointName<LanguageExtensionPoint<GrammarCheckingStrategy>>(EXTENSION_POINT_NAME)
  private val INTERNAL_STRATEGIES_IDS = setOf(CommitMessageGrammarCheckingStrategy.ID)

  /**
   * @return all registered GrammarCheckingStrategy without internal ones
   */
  fun getStrategies(): Set<GrammarCheckingStrategy> = EP_NAME.extensionList
    .map { it.instance }
    .filter { it.getID() !in INTERNAL_STRATEGIES_IDS }
    .toSortedSet(Comparator { f, s -> f.getName().compareTo(s.getName()) })

  fun getStrategyByID(id: String) = EP_NAME.extensionList.firstOrNull { it.instance.getID() == id }?.instance

  fun getExtensionPointByStrategy(strategy: GrammarCheckingStrategy) = EP_NAME.extensionList.firstOrNull { it.instance == strategy }

  fun getEnabledLanguages(): Set<Language> = EP_NAME.extensionList.asSequence()
    .mapNotNull { Language.findLanguageByID(it.language) }
    .toSet()

  @Deprecated("Use getEnabledStrategiesForElement")
  fun getStrategiesForElement(element: PsiElement, enabledIDs: Set<String>, disabledIDs: Set<String>): Set<GrammarCheckingStrategy> {
    return getEnabledStrategiesForElement(element)
  }

  /**
   * @param element [PsiElement] with text to check
   * @return all strategies without internal ones which match element language, if the checking in it isn't disabled by the user.
   */
  fun getEnabledStrategiesForElement(element: PsiElement): Set<GrammarCheckingStrategy> {
    val disabledLanguages = GrazieConfig.get().checkingContext.disabledLanguages
    return allForLanguage(element.language)
      .asSequence()
      .filter {
        val language = Language.findLanguageByID(StrategyUtils.getStrategyExtensionPoint(it).language)
        language != null && language.id !in disabledLanguages &&
        it.getID() !in INTERNAL_STRATEGIES_IDS &&
        it.isMyContextRoot(element) &&
        it.getContextRootTextDomain(element) != TextDomain.NON_TEXT
      }.toSet()
  }
}
