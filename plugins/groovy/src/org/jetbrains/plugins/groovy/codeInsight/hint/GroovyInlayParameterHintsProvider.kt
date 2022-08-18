// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.HintInfo.MethodInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMirrorElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.editor.shouldHideInlayHints
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter

class GroovyInlayParameterHintsProvider : InlayParameterHintsProvider {

  override fun getParameterHints(element: PsiElement): List<InlayInfo> {
    if (element !is GrCall) return emptyList()
    if (shouldHideInlayHints(element)) return emptyList()
    return element.doGetParameterHints() ?: emptyList()
  }

  override fun getHintInfo(element: PsiElement): MethodInfo? {
    val call = element as? GrCall
    val resolved = call?.resolveMethod()
    val method = (resolved as? PsiMirrorElement)?.prototype as? PsiMethod ?: resolved
    return method?.getMethodInfo()
  }

  private fun PsiMethod.getMethodInfo(): MethodInfo? {
    val clazzName = containingClass?.qualifiedName ?: return null
    val fullMethodName = StringUtil.getQualifiedName(clazzName, name)
    val paramNames: List<String> = parameterList.parameters.map { it.name }
    return MethodInfo(fullMethodName, paramNames, if (language == blackListDependencyLanguage) language else null)
  }

  override fun getDefaultBlackList(): Set<String> = blackList

  override fun getBlackListDependencyLanguage(): Language = JavaLanguage.INSTANCE

  override fun getDescription(): String = GroovyBundle.message("shows.parameter.names.at.function.call.sites")

  private companion object {

    private val blackList = setOf(
      "org.codehaus.groovy.runtime.DefaultGroovyMethods.*"
    )

    private fun GrCall.doGetParameterHints(): List<InlayInfo>? {
      val argumentList = argumentList ?: return null
      val result = advancedResolve() as? GroovyMethodResult ?: return null
      val mapping = result.candidate?.argumentMapping ?: return null

      val map: Map<PsiCallParameter, List<GrExpression>> = argumentList.expressionArguments
        .asSequence()
        .map(::ExpressionArgument)
        .mapNotNull { arg ->
          val parameter = mapping.targetParameter(arg)
          parameter?.let { Pair(it, arg.expression) }
        }
        .groupBy({ it.first }, { it.second })

      val varargParameter = mapping.varargParameter
      val inlays = ArrayList<InlayInfo>(map.size)
      for ((parameter, expressions) in map) {
        val name = parameter.parameterName ?: continue
        if (expressions.none(::shouldShowHint)) continue
        val inlayText = if (parameter === varargParameter) "...$name" else name
        inlays += InlayInfo(inlayText, expressions.first().textRange.startOffset)
      }
      return inlays
    }

    /**
     * Show:
     * - regular literal arguments
     * - varargs which contain literals
     * - prefix unary expressions with numeric literal arguments
     */
    private fun shouldShowHint(arg: PsiElement): Boolean = when (arg) {
      is GrFunctionalExpression -> true
      is GrLiteral -> true
      is GrUnaryExpression -> (arg.operand as? GrLiteral)?.value is Number
      else -> false
    }
  }
}
