// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.properties

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.grazie.utils.LinkedSet
import com.intellij.lang.properties.parsing.PropertiesTokenTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl

class PropertyGrammarCheckingStrategy : GrammarCheckingStrategy {
  override fun isMyContextRoot(element: PsiElement) = getContextRootTextDomain(element) != TextDomain.NON_TEXT

  override fun getContextRootTextDomain(root: PsiElement): TextDomain {
    return when (root.node.elementType) {
      in PropertiesTokenTypes.COMMENTS -> TextDomain.COMMENTS
      PropertiesTokenTypes.VALUE_CHARACTERS -> TextDomain.LITERALS
      else -> TextDomain.NON_TEXT
    }
  }

  override fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement) = RuleGroup.CASING

  override fun getStealthyRanges(root: PsiElement, text: CharSequence): LinkedSet<IntRange> {
    return when (root) {
      is PsiCommentImpl -> StrategyUtils.indentIndexes(text, setOf(' ', '\t', '#'))
      else -> super.getStealthyRanges(root, text)
    }
  }
}
