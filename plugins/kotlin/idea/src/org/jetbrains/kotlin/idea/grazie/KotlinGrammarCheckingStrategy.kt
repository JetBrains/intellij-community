package org.jetbrains.kotlin.idea.grazie

import com.intellij.grazie.grammar.strategy.BaseGrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.utils.LinkedSet
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
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
        KDocTokens.TAG_NAME,
    )

    override fun isStealth(element: PsiElement): Boolean =
        element.elementType in stealthTokens

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
}