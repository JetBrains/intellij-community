// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.siyeh.ig.psiutils.BoolUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.typing.ListLiteralType
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

  private fun booleanTypeCondition(expr: GrExpression): Boolean {
    val type = expr.type
    return type == null || type == PsiType.BOOLEAN || type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)
  }

  private fun nullableTypeCondition(expr: GrExpression): Boolean = expr.type !is PsiPrimitiveType

  fun getGenericExpressionSelector(onlyLast: Boolean, condition: Condition<in GrExpression>)
  = object : PostfixTemplateExpressionSelectorBase({ it is GrExpression && condition.value(it) }) {

    override fun getNonFilteredExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
      val actualOffset = max(offset - 1, 0)
      val file = PsiDocumentManager.getInstance(context.project).getPsiFile(document) ?: return emptyList()
      var currentElement: PsiElement? = PsiTreeUtil.findElementOfClassAtOffset(file, actualOffset, GrExpression::class.java, false)
      val expressions = mutableListOf<GrExpression>()
      val offsetFilter = getBorderOffsetFilter(offset)
      while (currentElement is GrExpression && offsetFilter.value(currentElement)) {
        expressions.add(currentElement)
        currentElement = currentElement.parent
      }
      if (onlyLast) {
        return listOfNotNull(expressions.lastOrNull())
      }
      else {
        return expressions.toList()
      }
    }

  }

  fun getExpressionSelector() = getGenericExpressionSelector(false, Conditions.alwaysTrue())

  fun getTopExpressionSelector() = getGenericExpressionSelector(true, Conditions.alwaysTrue())

  fun getNullableTopExpressionSelector() = getGenericExpressionSelector(true, this::nullableTypeCondition)

  fun getNullableExpressionSelector() = getGenericExpressionSelector(false, this::nullableTypeCondition)

  fun getMethodLocalTopExpressionSelector() = getGenericExpressionSelector(true) { element ->
    PsiTreeUtil.getParentOfType(element, GrMethod::class.java, GrFunctionalExpression::class.java) != null
  }

  fun getTopBooleanExpressionSelector() = getGenericExpressionSelector(true, this::booleanTypeCondition)

  fun getBooleanExpressionSelector() = getGenericExpressionSelector(false, this::booleanTypeCondition)

  fun getSubclassExpressionSelector(baseClassFqn: String) = getGenericExpressionSelector(true) { expr ->
    val type = expr.type
    type == null || InheritanceUtil.isInheritor(type, baseClassFqn)
  }

  fun getIterableExpressionSelector() = getGenericExpressionSelector(true) { expr ->
    val type = expr.type
    type == null || // unknown type may be actually iterable in runtime
    type is GrMapType ||
    type is ListLiteralType ||
    type is PsiArrayType ||
    InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)
  }

  fun getConstructorSelector() = getGenericExpressionSelector(false) { expr ->
    expr is GrMethodCallExpression || (expr is GrReferenceExpression && expr.resolve() is PsiClass)
  }

  fun shouldBeParenthesized(expr: GrExpression): Boolean = when (expr) {
    is GrOperatorExpression -> true
    is GrConditionalExpression -> true
    is GrSafeCastExpression -> true
    is GrMethodCallExpression -> expr.argumentList.leftParen == null && expr.argumentList.rightParen == null
    else -> false
  }

}