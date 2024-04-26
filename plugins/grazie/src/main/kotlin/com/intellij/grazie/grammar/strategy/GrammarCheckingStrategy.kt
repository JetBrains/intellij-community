// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

package com.intellij.grazie.grammar.strategy

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.ElementBehavior.*
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain.*
import com.intellij.grazie.grammar.strategy.impl.ReplaceCharRule
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.grazie.utils.LinkedSet
import com.intellij.grazie.utils.orTrue
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

/**
 * Strategy extracting elements for grammar checking used by Grazie plugin
 *
 * You need to implement [isMyContextRoot] and add com.intellij.grazie.grammar.strategy extension in your .xml config
 */
@Deprecated("Use TextExtractor and ProblemFilter instead")
@JvmDefaultWithCompatibility
interface GrammarCheckingStrategy {

  /**
   * Possible PsiElement behavior during grammar check
   *
   * [TEXT] - element contains text
   * [STEALTH] - element's text is ignored
   * [ABSORB] - element's text is ignored, as well as the typos that contain this element
   *
   * [ABSORB] and [STEALTH] behavior also prevents visiting children of these elements.
   *
   * Examples:
   *  Text: This is a <bold>error</bold> sample.
   *  PsiElements: ["This is a ", "<bold>", "error", "</bold>", " sample"]
   *
   *  If this text pass as is to the grammar checker, then there are no errors.
   *  The reason are tags framing word 'error', these tags are not part of the sentence and used only for formatting.
   *  So you can add [STEALTH] behavior to these tags PsiElements, which made text passed to grammar checker 'This is a error sample.'
   *  and checker will find an error - 'Use "an" instead of 'a' if the following word starts with a vowel sound'
   *
   *
   *  Text: There is raining <br> Days passing by
   *  PsiElements: ["There is raining ", "<br>", "Days passing by"]
   *
   *  There is <br> tag. Like in previous example this tag not used in the result text.
   *  But if we make tag <br> [STEALTH] there will be an error - 'There *are* raining days'
   *  By the way, 'Days passing by' is a new sentence that doesn't applies to previous one.
   *  In that case you can use [ABSORB] behavior, then this kind of errors will be filtered.
   */
  enum class ElementBehavior {
    TEXT,
    STEALTH,
    ABSORB
  }

  /**
   * Possible domains for grammar check.
   *
   * [NON_TEXT]   - domain for elements accepted as context root, but not containing reasonable text for check (IDs, file paths, etc.)
   * [LITERALS]   - domain for string literals of programming language
   * [COMMENTS]   - domain for general comments of programming languages (excluding doc comments)
   * [DOCS]       - domain for in-code documentation (JavaDocs, Python DocStrings, etc.)
   * [PLAIN_TEXT] - domain for plain text (Markdown, HTML or LaTeX) and UI strings
   */
  enum class TextDomain {
    NON_TEXT,
    LITERALS,
    COMMENTS,
    DOCS,
    PLAIN_TEXT
  }

  /**
   * Visible name of strategy for settings window.
   *
   * @return name of this strategy
   */
  @NlsSafe
  fun getName(): String {
    val extension = StrategyUtils.getStrategyExtensionPoint(this)
    return Language.findLanguageByID(extension.language)?.displayName ?: extension.language
  }

  /**
   * Return unique ID for grammar strategy. Used to distinguish strategies for user settings.
   *
   * NOTE: you need to override this method if you intend to use several strategies for one language.
   *
   * @return unique ID
   */
  fun getID(): String {
    val extension = StrategyUtils.getStrategyExtensionPoint(this)
    return "${extension.pluginDescriptor.pluginId}:${extension.language}"
  }

  /**
   * Determine text block root, for example a paragraph of text.
   *
   * @param element visited element
   * @return true if context root
   */
  fun isMyContextRoot(element: PsiElement): Boolean

  /**
   * Determine tokens which should be treated as whitespaces for the current [Language].
   * Default implementation considers that these tokens are the same as [ParserDefinition.getWhitespaceTokens].
   *
   * @return [TokenSet] of whitespace tokens
   */
  fun getWhiteSpaceTokens(): TokenSet {
    val extension = StrategyUtils.getStrategyExtensionPoint(this)
    val language = Language.findLanguageByID(extension.language) ?: return TokenSet.WHITE_SPACE
    return LanguageParserDefinitions.INSTANCE.forLanguage(language).whitespaceTokens
  }

  /**
   * Determine PsiElement roots that should be considered as a continuous text including [root].
   * [root] element MUST be present in chain.
   * Passing any sub-element in chain must return the same list of all the elements in the chain.
   * Chain roots must be in the same [TextDomain] and have the same [GrammarCheckingStrategy]
   * or be one of [getWhiteSpaceTokens].
   * For example, this method can be used to combine single-line comments into
   * a single block of text for grammar check.
   *
   * @param root root element previously selected in [isMyContextRoot]
   * @return list of root elements that should be considered as a continuous text with [getWhiteSpaceTokens] elements
   */
  fun getRootsChain(root: PsiElement): List<PsiElement> = listOf(root)

  /**
   * Determine if this strategy enabled by default.
   *
   * @return true if enabled else false
   */
  fun isEnabledByDefault(): Boolean = !GraziePlugin.isBundled || ApplicationManager.getApplication()?.isUnitTestMode.orTrue()

  /**
   * Determine root PsiElement domain @see [TextDomain].
   *
   * @param root root element previously selected in [isMyContextRoot]
   * @return [TextDomain] for [root] element
   */
  fun getContextRootTextDomain(root: PsiElement) = StrategyUtils.getTextDomainOrDefault(this, root, default = PLAIN_TEXT)

  /**
   * Determine PsiElement behavior @see [ElementBehavior].
   *
   * @param root root element previously selected in [isMyContextRoot]
   * @param child current checking element for which behavior is specified
   * @return [ElementBehavior] for [child] element
   */
  fun getElementBehavior(root: PsiElement, child: PsiElement) = TEXT

  /**
   * Specify ranges, which will be removed from text before checking (like STEALTH behavior).
   * You can use [StrategyUtils.indentIndexes] to hide the indentation of each line of text.
   *
   * @param root root element previously selected in [isMyContextRoot]
   * @param text extracted text from root element without [ABSORB] and [STEALTH] ones
   * in which you need to specify the ranges to remove from the grammar checking
   * @return set of ranges in the [text] to be ignored
   */
  fun getStealthyRanges(root: PsiElement, text: CharSequence): LinkedSet<IntRange> = StrategyUtils.emptyLinkedSet()

  /**
   * Determine if typo will be shown to user. The final check before add typo to [ProblemsHolder].
   *
   * @param root root element previously selected in [isMyContextRoot]
   * @param typoRange range of the typo inside [root] element
   * @param ruleRange range of elements needed for rule to find typo
   * @return true if typo should be accepted
   */
  fun isTypoAccepted(root: PsiElement, typoRange: IntRange, ruleRange: IntRange) = true

  /**
   * Determine if typo will be shown to user. The final check before add typo to [ProblemsHolder].
   *
   * @param parent common parent of [roots]
   * @param roots roots from [getRootsChain] method
   * @param typoRange range of the typo inside [parent] element
   * @param ruleRange range of elements needed for rule to find typo
   * @return true if typo should be accepted
   */
  fun isTypoAccepted(parent: PsiElement, roots: List<PsiElement>, typoRange: IntRange, ruleRange: IntRange) = true

  /**
   * Get ignored typo categories for [child] element @see [Typo.Category].
   *
   * @param root root element previously selected in [isMyContextRoot]
   * @param child current checking element for which ignored categories are specified
   * @return set of the ignored categories for [child]
   */
  @ScheduledForRemoval
  @Deprecated("Override getIgnoredRuleGroup instead", ReplaceWith("getIgnoredRuleGroup(root, child)"))
  fun getIgnoredTypoCategories(root: PsiElement, child: PsiElement): Set<Typo.Category>? = null


  /**
   * Get ignored rules for [child] element @see [RuleGroup].
   *
   * @param root root element previously selected in [isMyContextRoot]
   * @param child current checking element for which ignored rules are specified
   * @return RuleGroup with ignored rules for [child]
   */
  fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement): RuleGroup? = null

  /**
   * Get rules for char replacement in PSI elements text @see [ReplaceCharRule].
   * (In most cases you don't want to change this)
   *
   * @param root root element previously selected in [isMyContextRoot]
   * @return list of char replacement rules for whole root context
   */
  fun getReplaceCharRules(root: PsiElement): List<ReplaceCharRule> = emptyList()
}
