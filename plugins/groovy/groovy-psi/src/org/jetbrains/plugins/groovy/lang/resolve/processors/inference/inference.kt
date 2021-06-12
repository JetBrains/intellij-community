// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import com.intellij.psi.util.PsiUtil.extractIterableTypeParameter
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getQualifierType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.JustTypeArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.UnknownArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.lang.typing.devoid

fun getTopLevelType(expression: GrExpression): PsiType? {
  if (expression is GrFunctionalExpression) {
    return TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, expression)
  }

  val result = when (expression) {
    is GrMethodCall -> expression.advancedResolve() as? GroovyMethodResult
    is GrReferenceExpression -> expression.rValueReference?.advancedResolve() as? GroovyMethodResult ?: return expression.type
    is GrIndexProperty -> expression.rValueReference?.advancedResolve() as? GroovyMethodResult ?: return expression.type
    else -> return expression.type
  }

  return result?.candidate?.let {
    val session = GroovyInferenceSessionBuilder(expression, it, result.contextSubstitutor)
      .resolveMode(false)
      .build()
    val returnType = getSmartReturnTypeInContext(it.method, expression)
    session.inferSubst().substitute(returnType.devoid(expression))
  }
}

val forbidInteriorReturnTypeInference : Key<Unit> = Key.create("shouldInferReturnType")

fun getSmartReturnTypeInContext(method: PsiMethod, context: PsiElement): PsiType? =
  if (context.parentOfType<GrMethod>()?.getUserData(forbidInteriorReturnTypeInference) != null) {
    method.returnType
  }
  else {
    PsiUtil.getSmartReturnType(method)
  }


fun buildQualifier(ref: GrReferenceExpression?, state: ResolveState): Argument {
  val qualifierExpression = ref?.qualifierExpression
  val spreadState = state[SpreadState.SPREAD_STATE]
  if (qualifierExpression != null && spreadState == null) {
    return ExpressionArgument(qualifierExpression)
  }

  val receiver = state[ClassHint.RECEIVER]
  if (receiver != null) {
    return receiver
  }

  val type = ref?.let(::getQualifierType)
  when {
    spreadState == null -> return JustTypeArgument(type)
    type == null -> return UnknownArgument
    else -> return JustTypeArgument(extractIterableTypeParameter(type, false))
  }
}

fun PsiSubstitutor.putAll(parameters: Array<out PsiTypeParameter>, arguments: Array<out PsiType>): PsiSubstitutor {
  if (arguments.size != parameters.size) return this
  return parameters.zip(arguments).fold(this) { acc, (param, arg) ->
    acc.put(param, arg)
  }
}

fun PsiClass.type(): PsiClassType {
  return PsiElementFactory.getInstance(project).createType(this, PsiSubstitutor.EMPTY)
}

fun PsiClass.rawType(): PsiClassType {
  val factory = PsiElementFactory.getInstance(project)
  return factory.createType(this, factory.createRawSubstitutor(this))
}

fun inferDerivedSubstitutor(leftType: PsiType, derived: PsiClass, context: PsiElement): PsiSubstitutor {
  val session = InferenceSession(derived.typeParameters, PsiSubstitutor.EMPTY, context.manager, null)
  session.addConstraint(TypeCompatibilityConstraint(leftType, session.substituteWithInferenceVariables(derived.type())))
  return session.infer()
}

class ExpectedType(val type: PsiType, val position: GrTypeConverter.Position)
