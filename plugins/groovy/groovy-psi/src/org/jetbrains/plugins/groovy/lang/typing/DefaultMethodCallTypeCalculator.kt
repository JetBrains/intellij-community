// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.getSmartReturnTypeInContext

class DefaultMethodCallTypeCalculator : GrTypeCalculator<GrMethodCall> {

  override fun getType(expression: GrMethodCall): PsiType? {
    val results = expression.multiResolve(false)
    if (results.isEmpty()) {
      return null
    }
    val arguments = expression.getArguments()
    var type: PsiType? = null
    for (result in results) {
      type = TypesUtil.getLeastUpperBoundNullable(type, getTypeFromResult(result, arguments, expression), expression.manager)
    }
    return type?.boxIfNecessary(expression)
  }
}

fun getTypeFromResult(result: GroovyResolveResult, arguments: Arguments?, context: GrExpression): PsiType? {
  val baseType = getBaseTypeFromResult(result, arguments, context).devoid(context) ?: return null
  val substitutor = if (baseType !is GrLiteralClassType && hasGenerics(baseType)) result.substitutor else PsiSubstitutor.EMPTY
  return TypesUtil.substituteAndNormalizeType(baseType, substitutor, result.spreadState, context)
}

private fun getBaseTypeFromResult(result: GroovyResolveResult, arguments: Arguments?, context: PsiElement): PsiType? {
  return when {
    result.isInvokedOnProperty -> getTypeFromPropertyCall(result.element, arguments, context)
    result is GroovyMethodResult -> getTypeFromCandidate(result, context)
    else -> null
  }
}

fun getTypeFromCandidate(result: GroovyMethodResult, context: PsiElement): PsiType? {
  val candidate = result.candidate ?: return null
  val method = candidate.method
  val receiverType = candidate.receiverType
  val arguments = candidate.argumentMapping?.arguments
  for (ext in ep.extensions) {
    return ext.getType(receiverType, method, arguments, context) ?: continue
  }
  return getSmartReturnTypeInContext(method, context)
}

private val ep: ExtensionPointName<GrCallTypeCalculator> = ExtensionPointName.create("org.intellij.groovy.callTypeCalculator")

private fun getTypeFromPropertyCall(element: PsiElement?, arguments: Arguments?, context: PsiElement): PsiType? {
  val type = when (element) { // TODO introduce property concept, resolve into it and get its type
    is GrField -> element.typeGroovy
    is GrMethod -> element.inferredReturnType
    is GrAccessorMethod -> element.inferredReturnType
    is PsiField -> element.type
    is PsiMethod -> element.returnType
    else -> null
  }
  if (type !is GroovyClosureType) {
    return null
  }
  return type.returnType(arguments)
}

fun PsiType?.devoid(context: PsiElement): PsiType? {
  return if (this == PsiType.VOID && !isCompileStatic(context)) PsiType.NULL else this
}

private fun PsiType.boxIfNecessary(call: GrMethodCall) : PsiType {
  if (this !is PsiPrimitiveType) {
    return this
  }
  return if (call.invokedExpression.asSafely<GrReferenceExpression>()?.dotTokenType == GroovyElementTypes.T_SAFE_DOT) {
    this.box(call)
  } else {
    this
  }
}

private fun hasGenerics(type: PsiType): Boolean {
  return !Registry.`is`("groovy.return.type.optimization") || type.accept(GenericsSearcher) == true
}

private object GenericsSearcher : PsiTypeVisitor<Boolean?>() {

  override fun visitClassType(classType: PsiClassType): Boolean? {
    if (classType.resolve() is PsiTypeParameter) {
      return true
    }
    if (!classType.hasParameters()) {
      return null
    }
    for (parameter in classType.parameters) {
      if (parameter.accept(this) == true) return true
    }
    return null
  }

  override fun visitArrayType(arrayType: PsiArrayType): Boolean? = arrayType.componentType.accept(this)

  override fun visitWildcardType(wildcardType: PsiWildcardType): Boolean? = wildcardType.bound?.accept(this)
}
