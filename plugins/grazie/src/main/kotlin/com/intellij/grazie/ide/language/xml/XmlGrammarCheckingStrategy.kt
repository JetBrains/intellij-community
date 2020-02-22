// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.xml

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.utils.isAtEnd
import com.intellij.grazie.utils.isAtStart
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlText
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType

class XmlGrammarCheckingStrategy : GrammarCheckingStrategy {
  override fun isMyContextRoot(element: PsiElement) = getContextRootTextDomain(element) != TextDomain.NON_TEXT

  override fun getContextRootTextDomain(root: PsiElement) = when (root) {
    is XmlText -> TextDomain.PLAIN_TEXT
    is XmlToken -> when (root.tokenType) {
      XmlTokenType.XML_COMMENT_CHARACTERS -> TextDomain.COMMENTS
      XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN -> TextDomain.LITERALS
      else -> TextDomain.NON_TEXT
    }
    else -> TextDomain.NON_TEXT
  }

  override fun isTypoAccepted(root: PsiElement, typoRange: IntRange, ruleRange: IntRange): Boolean {
    return !typoRange.isAtStart(root) && !typoRange.isAtEnd(root)
  }

  override fun getStealthyRanges(root: PsiElement, text: CharSequence) = StrategyUtils.indentIndexes(text, setOf(' ', '\t'))
}
