// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.xml

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.utils.isAtEnd
import com.intellij.grazie.utils.isAtStart
import com.intellij.lang.Language
import com.intellij.lang.dtd.DTDLanguage
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.xhtml.XHTMLLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.xml.*

class XmlGrammarCheckingStrategy : GrammarCheckingStrategy {
  private fun Language.isDialectEnabled() = when (this::class) {
    XMLLanguage::class, HTMLLanguage::class, XHTMLLanguage::class, DTDLanguage::class -> true
    else -> false
  }

  override fun isMyContextRoot(element: PsiElement) = element.language.isDialectEnabled() &&
                                                      element.parents(false).all { it !is XmlProlog } &&
                                                      getContextRootTextDomain(element) != TextDomain.NON_TEXT

  override fun getContextRootTextDomain(root: PsiElement) = when (root) {
    is XmlText -> TextDomain.PLAIN_TEXT
    is XmlToken -> when (root.tokenType) {
      XmlTokenType.XML_DATA_CHARACTERS -> {
        if (root.parent?.elementType == XmlElementType.XML_CDATA) TextDomain.PLAIN_TEXT else TextDomain.NON_TEXT
      }
      XmlTokenType.XML_COMMENT_CHARACTERS -> TextDomain.COMMENTS
      XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN -> TextDomain.LITERALS
      else -> TextDomain.NON_TEXT
    }
    else -> TextDomain.NON_TEXT
  }

  override fun getElementBehavior(root: PsiElement, child: PsiElement): GrammarCheckingStrategy.ElementBehavior {
    if (root is XmlText && child.elementType == XmlElementType.XML_CDATA) return GrammarCheckingStrategy.ElementBehavior.STEALTH
    return super.getElementBehavior(root, child)
  }

  override fun isTypoAccepted(root: PsiElement, typoRange: IntRange, ruleRange: IntRange): Boolean {
    return !typoRange.isAtStart(root) && !typoRange.isAtEnd(root)
  }

  override fun getStealthyRanges(root: PsiElement, text: CharSequence) = StrategyUtils.indentIndexes(text, setOf(' ', '\t'))

  override fun getName() = "XML & HTML"
}
