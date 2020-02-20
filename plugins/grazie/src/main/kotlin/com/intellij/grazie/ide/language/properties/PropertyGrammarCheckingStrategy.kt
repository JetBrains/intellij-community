// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.properties

import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.lang.properties.psi.impl.PropertyValueImpl
import com.intellij.psi.PsiElement

class PropertyGrammarCheckingStrategy : GrammarCheckingStrategy {
  override fun isMyContextRoot(element: PsiElement) = element is PropertyValueImpl

  override fun getIgnoredTypoCategories(root: PsiElement, child: PsiElement) = setOf(Typo.Category.CASING)
}
