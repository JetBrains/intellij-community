// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.grazie

import com.intellij.grazie.grammar.strategy.BaseGrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.grazie.utils.LinkedSet
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class KotlinGrammarCheckingStrategy : BaseGrammarCheckingStrategy {

    override fun isMyContextRoot(element: PsiElement): Boolean =
        element is PsiComment || element is KtStringTemplateExpression

    override fun getContextRootTextDomain(root: PsiElement): TextDomain =
        when (root) {
            is KDoc -> TextDomain.DOCS
            is PsiComment -> TextDomain.COMMENTS
            is KtStringTemplateExpression -> TextDomain.LITERALS
            else -> TextDomain.NON_TEXT
        }

    private val stealthTokens: Set<IElementType> = setOf(
        KDocTokens.LEADING_ASTERISK,
        KDocTokens.START,
        KDocTokens.END,
    )

    override fun isStealth(element: PsiElement): Boolean =
        element.elementType in stealthTokens

    /**
     * Checks that [PsiElement] is a direct child of a [KDocTag] section.
     *
     * Checking with `is KtDocTag` does not work, because [KDocSection] is a subclass of [KDocTag] too, and we don't want it.
     */
    private val PsiElement.isInDocTag: Boolean
        get() = parent?.let { it::class } == KDocTag::class

    private val PsiElement.isDocText: Boolean
        get() = this is LeafPsiElement && elementType == KDocTokens.TEXT

    /**
     * We want to drop everything except the regular text from the tags.
     */
    override fun isAbsorb(element: PsiElement): Boolean =
        element.isInDocTag && !element.isDocText

    override fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement): RuleGroup? =
        when {
            root is KtStringTemplateExpression -> RuleGroup.LITERALS
            child.isInDocTag -> RuleGroup.CASING + RuleGroup.PUNCTUATION
            else -> null
        }

    /**
     * For multiline strings, we want to treat `'|'` as an indentation because it is commonly used with [String.trimMargin].
     */
    private val stringIndentations: Set<Char> = setOf(' ', '\t', '|')

    private val commentIndentations: Set<Char> = setOf(' ', '\t', '*', '/')

    override fun getStealthyRanges(root: PsiElement, text: CharSequence): LinkedSet<IntRange> =
        when (root) {
            is KtStringTemplateExpression -> StrategyUtils.indentIndexes(text, stringIndentations)
            is PsiComment -> StrategyUtils.indentIndexes(text, commentIndentations)
            else -> super.getStealthyRanges(root, text)
        }

    private val PsiElement?.isSingleLineComment: Boolean
        get() = this.elementType == KtTokens.EOL_COMMENT

    override fun getRootsChain(root: PsiElement): List<PsiElement> =
        if (root.isSingleLineComment) {
            StrategyUtils.getNotSoDistantSimilarSiblings(this, root) { it.isSingleLineComment }.toList()
        } else {
            super.getRootsChain(root)
        }
}