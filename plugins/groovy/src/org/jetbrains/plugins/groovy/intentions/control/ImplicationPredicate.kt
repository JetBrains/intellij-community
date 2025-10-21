// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mIMPL
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrLogicalExpression
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil

class ImplicationPredicate : PsiElementPredicate {
  override fun satisfiedBy(expression: PsiElement): Boolean {
    if (expression !is GrLogicalExpression) return false
    val tokenType = expression.operationTokenType
    return tokenType == mIMPL && !ErrorUtil.containsError(expression)
  }
}