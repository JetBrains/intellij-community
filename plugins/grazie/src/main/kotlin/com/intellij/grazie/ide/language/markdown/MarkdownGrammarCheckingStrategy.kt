// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.markdown

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.psi.PsiElement

class MarkdownGrammarCheckingStrategy : GrammarCheckingStrategy {
  override fun isMyContextRoot(element: PsiElement) = MarkdownPsiUtils.isHeaderContent(element) || MarkdownPsiUtils.isParagraph(element)

  override fun isEnabledByDefault() = true

  override fun getContextRootTextDomain(root: PsiElement) = GrammarCheckingStrategy.TextDomain.PLAIN_TEXT

  override fun getElementBehavior(root: PsiElement, child: PsiElement) = when {
    MarkdownPsiUtils.isInline(child) -> GrammarCheckingStrategy.ElementBehavior.ABSORB
    else -> GrammarCheckingStrategy.ElementBehavior.TEXT
  }

  override fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement): RuleGroup? {
    return if (MarkdownPsiUtils.isInOuterListItem(child)) RuleGroup.CASING else null
  }
}
