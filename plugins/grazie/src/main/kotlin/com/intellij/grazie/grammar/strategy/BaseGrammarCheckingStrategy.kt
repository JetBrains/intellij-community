// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar.strategy

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.ElementBehavior.*
import com.intellij.psi.PsiElement

interface BaseGrammarCheckingStrategy : GrammarCheckingStrategy {

  /**
   * See [GrammarCheckingStrategy.ElementBehavior.ABSORB]
   */
  fun isAbsorb(element: PsiElement) = false

  /**
   * See [GrammarCheckingStrategy.ElementBehavior.STEALTH]
   */
  fun isStealth(element: PsiElement) = false

  override fun getElementBehavior(root: PsiElement, child: PsiElement) = when {
    isAbsorb(child) -> ABSORB
    isStealth(child) -> STEALTH
    else -> TEXT
  }
}
