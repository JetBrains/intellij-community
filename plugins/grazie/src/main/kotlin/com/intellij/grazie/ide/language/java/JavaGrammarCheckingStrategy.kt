// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.java

import com.intellij.grazie.grammar.strategy.BaseGrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.psi.JavaDocTokenType.*
import com.intellij.psi.JavaTokenType.C_STYLE_COMMENT
import com.intellij.psi.JavaTokenType.END_OF_LINE_COMMENT
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.impl.source.tree.JavaDocElementType.DOC_COMMENT
import com.intellij.psi.impl.source.tree.JavaElementType.LITERAL_EXPRESSION
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType

class JavaGrammarCheckingStrategy : BaseGrammarCheckingStrategy {
  private fun isTag(token: PsiElement) = token.parent is PsiDocTag
  private fun isCodeTag(token: PsiElement) = isTag(token) && ((token.parent as PsiDocTag).nameElement.text == "@code")
  private fun isCommentData(token: PsiElement) = token is LeafPsiElement && token.elementType == DOC_COMMENT_DATA

  override fun isMyContextRoot(element: PsiElement) = getContextRootTextDomain(element) != TextDomain.NON_TEXT

  override fun getContextRootTextDomain(root: PsiElement) = when (root.elementType) {
    DOC_COMMENT -> TextDomain.DOCS
    C_STYLE_COMMENT, END_OF_LINE_COMMENT -> TextDomain.COMMENTS
    LITERAL_EXPRESSION -> TextDomain.LITERALS
    else -> TextDomain.NON_TEXT
  }

  override fun isAbsorb(element: PsiElement) = isTag(element) && (!isCommentData(element) || isCodeTag(element))

  private val STEALTH_TYPES = setOf(DOC_COMMENT_START, DOC_COMMENT_LEADING_ASTERISKS, DOC_COMMENT_END)
  override fun isStealth(element: PsiElement) = element is LeafPsiElement && element.elementType in STEALTH_TYPES

  override fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement) = when {
    root is PsiLiteralExpression -> RuleGroup.LITERALS
    isTag(child) -> RuleGroup.CASING + RuleGroup.PUNCTUATION
    else -> null
  }

  override fun getStealthyRanges(root: PsiElement, text: CharSequence) = when (root) {
    is PsiCommentImpl -> StrategyUtils.indentIndexes(text, setOf(' ', '\t', '*', '/'))
    else -> StrategyUtils.indentIndexes(text, setOf(' ', '\t'))
  }

  private fun IElementType?.isSingleLineCommentType() = when (this) {
    END_OF_LINE_COMMENT, C_STYLE_COMMENT -> true
    else -> false
  }

  override fun getRootsChain(root: PsiElement): List<PsiElement> {
    return if (root.elementType.isSingleLineCommentType()) {
      StrategyUtils.getNotSoDistantSiblingsOfTypes(this, root) { type -> type.isSingleLineCommentType() }.toList()
    }
    else super.getRootsChain(root)
  }
}
