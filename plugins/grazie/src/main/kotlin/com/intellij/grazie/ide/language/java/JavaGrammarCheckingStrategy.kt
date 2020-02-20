// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.java

import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.grammar.strategy.BaseGrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.grammar.strategy.impl.ReplaceCharRule
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.psi.JavaDocTokenType.*
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTag

class JavaGrammarCheckingStrategy : BaseGrammarCheckingStrategy {
  private fun isTag(token: PsiElement) = token.parent is PsiDocTag
  private fun isCodeTag(token: PsiElement) = isTag(token) && ((token.parent as PsiDocTag).nameElement.text == "@code")
  private fun isCommentData(token: PsiElement) = token is LeafPsiElement && token.elementType == DOC_COMMENT_DATA

  override fun isMyContextRoot(element: PsiElement) = element is PsiDocComment
    || element is PsiLiteralExpressionImpl && element.literalElementType in setOf(JavaTokenType.STRING_LITERAL, JavaTokenType.TEXT_BLOCK_LITERAL)

  override fun isAbsorb(element: PsiElement) = isTag(element) && (!isCommentData(element) || isCodeTag(element))

  override fun isStealth(element: PsiElement) = element is LeafPsiElement
    && element.elementType in listOf(DOC_COMMENT_START, DOC_COMMENT_LEADING_ASTERISKS, DOC_COMMENT_END)

  override fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement) = RuleGroup.LITERALS.takeIf { root is PsiLiteralExpression }

  override fun getIgnoredTypoCategories(root: PsiElement, child: PsiElement) = setOf(Typo.Category.CASING).takeIf { isTag(child) }

  override fun getReplaceCharRules(root: PsiElement) = emptyList<ReplaceCharRule>()

  override fun getStealthyRanges(root: PsiElement, text: CharSequence) = StrategyUtils.indentIndexes(text, setOf(' ', '\t'))
}
