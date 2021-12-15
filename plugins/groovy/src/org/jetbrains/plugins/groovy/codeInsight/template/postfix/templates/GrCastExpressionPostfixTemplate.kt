// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
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
  PostfixTemplateWithExpressionSelector("groovy.postfix.template.cast", "cast", "expr as SomeType",
                                        GroovyPostfixTemplateUtils.EXPRESSION_SELECTOR, provider) {


  override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
    LOG.assertTrue(expression is GrExpression)
    val parent = expression.parent
    val shouldParenthesizeCast = shouldParenthesizeCastExpression(parent)
    val text = buildString {
      val surround = GroovyPostfixTemplateUtils.shouldBeParenthesized(expression as GrExpression)
      if (surround) append('(')
      append(expression.text)
      if (surround) append(')')
    }
    val templateManager = TemplateManager.getInstance(expression.project)
    val template = templateManager.createTemplate("", "")
    val templateExpression = ConstantNode(null)
    template.isToReformat = true
    if (shouldParenthesizeCast) template.addTextSegment("(")
    template.addTextSegment("$text as ")
    template.addVariable("type", templateExpression, true)
    if (shouldParenthesizeCast) template.addTextSegment(")")
    template.addEndVariable()
    val range = expression.textRange
    with(editor) {
      document.deleteString(range.startOffset, range.endOffset)
      caretModel.moveToOffset(range.startOffset)
      editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }
    templateManager.startTemplate(editor, template)
  }
}

private fun shouldParenthesizeCastExpression(parent: PsiElement): Boolean = when (parent) {
  is GrOperatorExpression -> parent.operationTokenType in TokenSet.orSet(GroovyTokenSets.ADDITIVE_OPERATORS,
                                                                         GroovyTokenSets.MULTIPLICATIVE_OPERATORS,
                                                                         GroovyTokenSets.SHIFT_OPERATORS, GroovyTokenSets.OTHER_OPERATORS)
  else -> false
}