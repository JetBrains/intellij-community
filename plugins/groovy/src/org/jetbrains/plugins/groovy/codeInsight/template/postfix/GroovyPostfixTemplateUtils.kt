// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType
import com.siyeh.ig.psiutils.BoolUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import kotlin.math.max

object GroovyPostfixTemplateUtils {

  val LOG: Logger = Logger.getInstance(GroovyPostfixTemplateUtils::class.java)

  val GROOVY_PSI_INFO: PostfixTemplatePsiInfo = object : PostfixTemplatePsiInfo() {
    override fun createExpression(context: PsiElement,
                                  prefix: String,
                                  suffix: String): PsiElement {
      val factory = GroovyPsiElementFactory.getInstance(context.project)
      return factory.createExpressionFromText(prefix + context.text + suffix, context)
    }

    override fun getNegatedExpression(element: PsiElement): GrExpression {
      LOG.assertTrue(element is GrExpression)
      val negatedExpressionText = BoolUtils.getNegatedExpressionText(element as PsiExpression)
      return GroovyPsiElementFactory.getInstance(element.getProject()).createExpressionFromText(negatedExpressionText, element)
    }
  }

  private fun getGenericExpressionSelector(onlyLast : Boolean = false, condition: Condition<in PsiElement> = Conditions.alwaysTrue()) = object : PostfixTemplateExpressionSelectorBase(condition) {

    override fun getNonFilteredExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
      val actualOffset = max(offset - 1, 0)
      val file = PsiDocumentManager.getInstance(context.project).getPsiFile(document) ?: return emptyList()
      val expressions = PsiTreeUtil
        .findElementOfClassAtOffset(file, actualOffset, GrExpression::class.java, false)
        ?.parentsOfType<GrExpression>(true) ?: emptySequence()
      if (onlyLast) {
        return listOfNotNull(expressions.last())
      } else {
        return expressions.toList()
      }
    }

  }

  val EXPRESSION_SELECTOR = getGenericExpressionSelector()

  val TOP_EXPRESSION_SELECTOR = getGenericExpressionSelector(true)

  val CONSTRUCTOR_SELECTOR = getGenericExpressionSelector { element ->
    element is GrMethodCallExpression || (element is GrReferenceExpression && element.resolve() is PsiClass)
  }

  fun shouldBeParenthesized(expr: GrExpression): Boolean = when (expr) {
    is GrOperatorExpression -> true
    is GrConditionalExpression -> true
    is GrSafeCastExpression -> true
    is GrMethodCallExpression -> expr.argumentList.leftParen == null && expr.argumentList.rightParen == null
    else -> false
  }

}