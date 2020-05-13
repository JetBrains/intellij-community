// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.plain

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText

class PlainTextGrammarCheckingStrategy : GrammarCheckingStrategy {
  override fun isMyContextRoot(element: PsiElement) = element is PsiPlainText && element.containingFile.name.endsWith(".txt")

  override fun getContextRootTextDomain(root: PsiElement) = GrammarCheckingStrategy.TextDomain.PLAIN_TEXT
}
