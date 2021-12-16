// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateProvider
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression

private val LOG: Logger by lazyPub { Logger.getInstance(GrCastExpressionPostfixTemplate::class.java) }

class GrCastExpressionPostfixTemplate(provider: GroovyPostfixTemplateProvider) :
  GrPostfixTemplateBase("cast", "expr as SomeType", GroovyPostfixTemplateUtils.getExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String {
    LOG.assertTrue(element is GrExpression)
    val parent = element.parent
    val expr = "__expr__".let { if (GroovyPostfixTemplateUtils.shouldBeParenthesized(element as GrExpression)) "($it)" else it }
    return "$expr as __END__".let { if (shouldParenthesizeCastExpression(parent)) "($it)" else it }
  }
}

private fun shouldParenthesizeCastExpression(parent: PsiElement): Boolean = when (parent) {
  is GrOperatorExpression -> parent.operationTokenType in TokenSet.orSet(GroovyTokenSets.ADDITIVE_OPERATORS,
                                                                         GroovyTokenSets.MULTIPLICATIVE_OPERATORS,
                                                                         GroovyTokenSets.SHIFT_OPERATORS, GroovyTokenSets.OTHER_OPERATORS)
  else -> false
}