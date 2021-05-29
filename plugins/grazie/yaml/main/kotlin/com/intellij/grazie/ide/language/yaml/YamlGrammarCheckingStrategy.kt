// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.yaml

import com.intellij.grazie.grammar.strategy.BaseGrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import org.jetbrains.yaml.YAMLTokenTypes.*

class YamlGrammarCheckingStrategy : BaseGrammarCheckingStrategy {
  override fun isMyContextRoot(element: PsiElement) = getContextRootTextDomain(element) != TextDomain.NON_TEXT

  override fun getContextRootTextDomain(root: PsiElement) = when (root.node.elementType) {
    COMMENT -> TextDomain.COMMENTS
    TEXT, SCALAR_STRING, SCALAR_DSTRING, SCALAR_LIST, SCALAR_TEXT -> TextDomain.LITERALS
    else -> TextDomain.NON_TEXT
  }

  override fun isStealth(element: PsiElement) = when (element.node.elementType) {
    INDENT -> true
    SCALAR_LIST -> element.textLength == 1 && element.textContains('|')
    SCALAR_TEXT -> element.textLength == 1 && element.textContains('>')
    else -> false
  }

  override fun getStealthyRanges(root: PsiElement, text: CharSequence) = when (root) {
    is PsiCommentImpl -> StrategyUtils.indentIndexes(text, setOf(' ', '\t', '#'))
    else -> StrategyUtils.emptyLinkedSet()
  }

  override fun getWhiteSpaceTokens() = TokenSet.create(WHITESPACE, INDENT, EOL)

  override fun getRootsChain(root: PsiElement): List<PsiElement> {
    return if (root.elementType == COMMENT) {
      StrategyUtils.getNotSoDistantSiblingsOfTypes(this, root, setOf(COMMENT)).toList()
    }
    else super.getRootsChain(root)
  }
}
