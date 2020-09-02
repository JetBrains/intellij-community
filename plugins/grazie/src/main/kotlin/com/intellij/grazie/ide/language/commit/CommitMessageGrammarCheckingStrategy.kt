// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.commit

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.psi.PsiElement

internal class CommitMessageGrammarCheckingStrategy : GrammarCheckingStrategy {
  companion object {
    private val IGNORED_RULES = RuleGroup.CASING + RuleGroup.PUNCTUATION

    const val ID = "COMMIT_MESSAGE_GRAMMAR_STRATEGY"
  }

  override fun isMyContextRoot(element: PsiElement) = true

  override fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement) = IGNORED_RULES

  override fun isEnabledByDefault() = true

  override fun getContextRootTextDomain(root: PsiElement) = GrammarCheckingStrategy.TextDomain.PLAIN_TEXT

  override fun getID() = ID

  override fun getName() = "Commit message"
}
