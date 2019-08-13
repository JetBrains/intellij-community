// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.delegatesTo

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.*
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.util.ArrayUtil
import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureAsAnonymousParameterEnhancer
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.FromStringHintProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping

@JvmField
val DELEGATES_TO_KEY: Key<DelegatesToInfo> = Key.create("groovy.closure.delegatesTo")
@JvmField
val DELEGATES_TO_TYPE_KEY: Key<String> = Key.create<String>("groovy.closure.delegatesTo.type")
@JvmField
val DELEGATES_TO_STRATEGY_KEY: Key<Int> = Key.create<Int>("groovy.closure.delegatesTo.strategy")

val defaultDelegatesToInfo: DelegatesToInfo = DelegatesToInfo(null, Closure.OWNER_ONLY)

fun getDelegatesToInfo(closure: GrFunctionalExpression): DelegatesToInfo? = CachedValuesManager.getCachedValue(closure) {
  Result.create(doGetDelegatesToInfo(closure), PsiModificationTracker.MODIFICATION_COUNT)
}

private fun doGetDelegatesToInfo(expression: GrFunctionalExpression): DelegatesToInfo? {
  for (ext in GrDelegatesToProvider.EP_NAME.extensions) {
    val info = ext.getDelegatesToInfo(expression)
    if (info != null) {
      return info
    }
  }
  return null
}

fun getContainingCall(expression: GrFunctionalExpression): GrCall? {
  val parent = expression.parent
  if (parent is GrCall && ArrayUtil.contains(expression, *parent.closureArguments)) {
    return parent
  }

  if (parent is GrArgumentList) {
    val grandParent = parent.parent
    return grandParent as? GrCall
  }

  return null
}


fun getFromValue(delegatesTo: PsiAnnotation): PsiType? {
  val value = delegatesTo.findDeclaredAttributeValue("value")
  if (value is GrReferenceExpression) {
    return ResolveUtil.unwrapClassType(value.type)
  }
  else if (value is PsiClassObjectAccessExpression) {
    return ResolveUtil.unwrapClassType(value.type)
  }
  else if (value == null ||
           value is PsiLiteralExpression && value.type === PsiType.NULL ||
           value is GrLiteral && value.type === PsiType.NULL) {
    return null
  }
  else if (value is PsiExpression) {
    return value.type
  }
  return null
}

fun getFromTarget(parameterList: PsiParameterList,
                  delegatesTo: PsiAnnotation,
                  mapping: ArgumentMapping): PsiType? {
  val target = GrAnnotationUtil.inferStringAttribute(delegatesTo, "target") ?: return null

  val parameter = findTargetParameter(parameterList, target) ?: return null

  val type = mapping.arguments.firstOrNull {
    mapping.targetParameter(it) == parameter
  }?.type ?: return null

  val index = GrAnnotationUtil.inferIntegerAttribute(delegatesTo, "genericTypeIndex")
  return if (index != null) {
    inferGenericArgType(type, index, parameter)
  }
  else {
    type
  }
}

private fun inferGenericArgType(targetType: PsiType?, genericIndex: Int, param: PsiParameter): PsiType? {
  if (targetType !is PsiClassType) return null
  val result = targetType.resolveGenerics()
  val psiClass = result.element ?: return null
  val substitutor = result.substitutor
  val baseType = param.type
  val baseClass = PsiUtil.resolveClassInClassTypeOnly(baseType)
  if (baseClass != null && InheritanceUtil.isInheritorOrSelf(psiClass, baseClass, true)) {
    val typeParameters = baseClass.typeParameters
    if (genericIndex < typeParameters.size) {
      val superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, psiClass, substitutor)
      return superClassSubstitutor.substitute(typeParameters[genericIndex])
    }
  }
  return null
}

fun findTargetParameter(list: PsiParameterList, target: String): PsiParameter? {
  val parameters = list.parameters
  for (parameter in parameters) {
    val modifierList = parameter.modifierList ?: continue
    val targetAnnotation = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO_TARGET) ?: continue
    val value = GrAnnotationUtil.inferStringAttribute(targetAnnotation, "value") ?: continue
    if (value == target) return parameter
  }
  return null
}

fun getFromType(call: GrCall, result: GroovyResolveResult, delegatesTo: PsiAnnotation): PsiType? {
  val element = result.element as? PsiMethod ?: return null
  val typeValue = GrAnnotationUtil.inferStringAttribute(delegatesTo, "type") ?: return null
  if (typeValue.isBlank()) return null
  val context = FromStringHintProcessor.createContext(element)
  val type = JavaPsiFacade.getElementFactory(context.project).createTypeFromText(typeValue, context)
  val substitutor = if (result is GroovyMethodResult) {
    result.candidate?.let { candidate ->
      ClosureAsAnonymousParameterEnhancer.substitutorIgnoringClosures(call, candidate, result)
    } ?: result.partialSubstitutor
  }
  else {
    result.substitutor
  }
  return substitutor.substitute(type)
}

fun getStrategyValue(strategy: PsiAnnotationMemberValue?): Int {
  if (strategy == null) return Closure.OWNER_FIRST
  val text = strategy.text
  return when (text) {
    "0" -> Closure.OWNER_FIRST
    "1" -> Closure.DELEGATE_FIRST
    "2" -> Closure.OWNER_ONLY
    "3" -> Closure.DELEGATE_ONLY
    "4" -> Closure.TO_SELF
    else -> when {
      text.endsWith("OWNER_FIRST") -> Closure.OWNER_FIRST
      text.endsWith("DELEGATE_FIRST") -> Closure.DELEGATE_FIRST
      text.endsWith("OWNER_ONLY") -> Closure.OWNER_ONLY
      text.endsWith("DELEGATE_ONLY") -> Closure.DELEGATE_ONLY
      text.endsWith("TO_SELF") -> Closure.TO_SELF
      else -> Closure.OWNER_FIRST
    }
  }
}