// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.delegatesTo

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import groovy.lang.Closure.*
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureAsAnonymousParameterEnhancer.Companion.substitutorIgnoringClosures
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.FromStringHintProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.unwrapClassType
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument

class DefaultDelegatesToProvider : GrDelegatesToProvider {

  override fun getDelegatesToInfo(expression: GrFunctionalExpression): DelegatesToInfo? {
    val call = getContainingCall(expression) ?: return null
    val result = call.advancedResolve()
    val method = result.element as? PsiMethod ?: return null

    if (GdkMethodUtil.isWithOrIdentity(method)) {
      val qualifier = inferCallQualifier(call as GrMethodCall) ?: return null
      return DelegatesToInfo(qualifier.type, DELEGATE_FIRST)
    }

    val argumentMapping = (result as? GroovyMethodResult)?.candidate?.argumentMapping ?: return null

    val parameter = argumentMapping.targetParameter(ExpressionArgument(expression)) ?: return null

    parameter.getUserData(DELEGATES_TO_KEY)?.let {
      return it
    }

    val delegateFqnData = parameter.getUserData(DELEGATES_TO_TYPE_KEY)
    val strategyData = parameter.getUserData(DELEGATES_TO_STRATEGY_KEY)
    if (delegateFqnData != null) {
      return DelegatesToInfo(
        TypesUtil.createType(delegateFqnData, expression),
        strategyData ?: OWNER_FIRST
      )
    }

    val modifierList = parameter.modifierList ?: return null
    val delegatesTo = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO) ?: return null
    val strategyValue = getStrategyValue(delegatesTo.findAttributeValue("strategy"))
    val delegateType = if (strategyValue == OWNER_ONLY || strategyValue == TO_SELF) {
      null
    }
    else {
      getFromValue(delegatesTo)
      ?: getFromTarget(method.parameterList, delegatesTo, argumentMapping)
      ?: getFromType(call, result, delegatesTo)
    }
    return DelegatesToInfo(delegateType, strategyValue)
  }

  private fun getFromValue(delegatesTo: PsiAnnotation): PsiType? {
    val value = delegatesTo.findDeclaredAttributeValue("value")
    if (value is GrReferenceExpression) {
      return unwrapClassType(value.type)
    }
    else if (value is PsiClassObjectAccessExpression) {
      return unwrapClassType(value.type)
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

  private fun getFromTarget(parameterList: PsiParameterList,
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

  private fun getFromType(call: GrCall, result: GroovyResolveResult, delegatesTo: PsiAnnotation): PsiType? {
    val element = result.element as? PsiMethod ?: return null
    val typeValue = GrAnnotationUtil.inferStringAttribute(delegatesTo, "type") ?: return null
    if (typeValue.isBlank()) return null
    val context = FromStringHintProcessor.createContext(element)
    val type = JavaPsiFacade.getElementFactory(context.project).createTypeFromText(typeValue, context)
    val substitutor = if (result is GroovyMethodResult) {
      result.candidate?.let { candidate ->
        substitutorIgnoringClosures(call, candidate, result)
      } ?: result.partialSubstitutor
    }
    else {
      result.substitutor
    }
    return substitutor.substitute(type)
  }

  private fun findTargetParameter(list: PsiParameterList, target: String): PsiParameter? {
    val parameters = list.parameters
    for (parameter in parameters) {
      val modifierList = parameter.modifierList ?: continue
      val targetAnnotation = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO_TARGET) ?: continue
      val value = GrAnnotationUtil.inferStringAttribute(targetAnnotation, "value") ?: continue
      if (value == target) return parameter
    }
    return null
  }

  private fun inferCallQualifier(call: GrMethodCall): GrExpression? {
    val expression = call.invokedExpression
    return if (expression !is GrReferenceExpression) null else expression.qualifier
  }

  private fun getStrategyValue(strategy: PsiAnnotationMemberValue?): Int {
    if (strategy == null) return OWNER_FIRST
    val text = strategy.text
    return when (text) {
      "0" -> OWNER_FIRST
      "1" -> DELEGATE_FIRST
      "2" -> OWNER_ONLY
      "3" -> DELEGATE_ONLY
      "4" -> TO_SELF
      else -> when {
        text.endsWith("OWNER_FIRST") -> OWNER_FIRST
        text.endsWith("DELEGATE_FIRST") -> DELEGATE_FIRST
        text.endsWith("OWNER_ONLY") -> OWNER_ONLY
        text.endsWith("DELEGATE_ONLY") -> DELEGATE_ONLY
        text.endsWith("TO_SELF") -> TO_SELF
        else -> OWNER_FIRST
      }
    }
  }
}
