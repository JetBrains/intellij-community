// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

private const val EXTENSION_POINT_NAME = "com.intellij.grazie.grammar.strategy"

@Suppress("unused", "UNUSED_PARAMETER")
@Deprecated("Use TextExtractor API instead of strategies")
@ApiStatus.ScheduledForRemoval
object LanguageGrammarChecking : LanguageExtension<GrammarCheckingStrategy>(EXTENSION_POINT_NAME) {
  @JvmField
  val EP_NAME = ExtensionPointName<LanguageExtensionPoint<GrammarCheckingStrategy>>(EXTENSION_POINT_NAME)

  /**
   * @return all registered GrammarCheckingStrategy without internal ones
   */
  fun getStrategies(): Set<GrammarCheckingStrategy> = EP_NAME.extensionList
    .map { it.instance }
    .toSortedSet(Comparator { f, s -> f.getName().compareTo(s.getName()) })

  fun getStrategyByID(id: String) = EP_NAME.extensionList.firstOrNull { it.instance.getID() == id }?.instance

  fun getExtensionPointByStrategy(strategy: GrammarCheckingStrategy) = EP_NAME.extensionList.firstOrNull { it.instance == strategy }

  /**
   * @param element [PsiElement] with text to check
   * @return all strategies without internal ones which match element language, if the checking in it isn't disabled by the user.
   */
  fun getStrategiesForElement(element: PsiElement, enabledIDs: Set<String>, disabledIDs: Set<String>): Set<GrammarCheckingStrategy> {
    val disabledLanguages = GrazieConfig.get().checkingContext.getEffectivelyDisabledLanguageIds()
    return allForLanguage(element.language)
      .asSequence()
      .filter {
        val language = Language.findLanguageByID(StrategyUtils.getStrategyExtensionPoint(it).language)
        language != null && language.id !in disabledLanguages &&
        it.isMyContextRoot(element) &&
        it.getContextRootTextDomain(element) != TextDomain.NON_TEXT
      }.toSet()
  }
}
