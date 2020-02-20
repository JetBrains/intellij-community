// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.yaml

import com.intellij.grazie.grammar.strategy.BaseGrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.impl.ReplaceCharRule
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.YAMLTokenTypes.*
import org.jetbrains.yaml.psi.YAMLScalarList
import org.jetbrains.yaml.psi.YAMLScalarText

class YamlGrammarCheckingStrategy : BaseGrammarCheckingStrategy {
  override fun isMyContextRoot(element: PsiElement) = element is YAMLScalarText || element is YAMLScalarList ||
                                                      element.node.elementType in setOf(SCALAR_KEY, TEXT, SCALAR_STRING, SCALAR_DSTRING)

  override fun isStealth(element: PsiElement) = when (element.node.elementType) {
    INDENT -> true
    SCALAR_LIST -> element.textLength == 1 && element.textContains('|')
    SCALAR_TEXT -> element.textLength == 1 && element.textContains('>')
    else -> false
  }

  override fun getReplaceCharRules(root: PsiElement) = emptyList<ReplaceCharRule>()
}
