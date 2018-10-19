// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.delegatesTo

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import groovy.lang.Closure.*
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.createSignature
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.FromStringHintProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.unwrapClassType

class DefaultDelegatesToProvider : GrDelegatesToProvider {

  override fun getDelegatesToInfo(closableBlock: GrClosableBlock): DelegatesToInfo? {
    val call = getContainingCall(closableBlock) ?: return null
    val result = call.advancedResolve()
    val method = result.element as? PsiMethod ?: return null

    if (GdkMethodUtil.isWithOrIdentity(method)) {
      val qualifier = inferCallQualifier(call as GrMethodCall) ?: return null
      return DelegatesToInfo(qualifier.type, DELEGATE_FIRST)
    }

    val signature = createSignature(method, PsiSubstitutor.EMPTY)
    val map = mapArgs(closableBlock, call, signature) ?: return null

    val parameterList = method.parameterList
    val parameter = findParameter(parameterList, closableBlock, map) ?: return null

    val delegateFqnData = parameter.getUserData(DELEGATES_TO_KEY)
    val strategyData = parameter.getUserData(DELEGATES_TO_STRATEGY_KEY)
    if (delegateFqnData != null) {
      return DelegatesToInfo(
        TypesUtil.createType(delegateFqnData, closableBlock),
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
      ?: getFromTarget(parameterList, delegatesTo, signature, map)
      ?: getFromType(result, delegatesTo)
    }
    return DelegatesToInfo(delegateType, strategyValue)
  }

  private fun mapArgs(place: PsiElement, call: GrCall, signature: GrClosureSignature): Array<GrClosureSignatureUtil.ArgInfo<PsiElement>>? {
    val rawSignature = GrClosureSignatureUtil.rawSignature(signature)
    return GrClosureSignatureUtil.mapParametersToArguments(
      rawSignature, call.namedArguments, call.expressionArguments, call.closureArguments, place, false, false
    )
  }

  private fun findParameter(parameterList: PsiParameterList,
                            closableBlock: GrClosableBlock,
                            map: Array<GrClosureSignatureUtil.ArgInfo<PsiElement>>): PsiParameter? {
    val parameters = parameterList.parameters

    for (i in map.indices) {
      if (map[i].args.contains(closableBlock)) return parameters[i]
    }

    return null
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
                            signature: GrClosureSignature,
                            map: Array<GrClosureSignatureUtil.ArgInfo<PsiElement>>): PsiType? {
    val target = GrAnnotationUtil.inferStringAttribute(delegatesTo, "target") ?: return null

    val parameter = findTargetParameter(parameterList, target)
    if (parameter < 0) return null

    val type = map[parameter].type
    val index = GrAnnotationUtil.inferIntegerAttribute(delegatesTo, "genericTypeIndex")
    return if (index != null) {
      inferGenericArgType(signature, type, index, parameter)
    }
    else {
      type
    }
  }

  private fun inferGenericArgType(signature: GrClosureSignature, targetType: PsiType?, genericIndex: Int, param: Int): PsiType? {
    if (targetType !is PsiClassType) return null
    val result = targetType.resolveGenerics()
    val psiClass = result.element ?: return null
    val substitutor = result.substitutor
    val baseType = signature.parameters[param].type
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

  private fun getFromType(result: GroovyResolveResult, delegatesTo: PsiAnnotation): PsiType? {
    val element = result.element as? PsiMethod ?: return null
    val typeValue = GrAnnotationUtil.inferStringAttribute(delegatesTo, "type") ?: return null
    if (typeValue.isBlank()) return null
    val context = FromStringHintProcessor.createContext(element)
    val type = JavaPsiFacade.getElementFactory(context.project).createTypeFromText(typeValue, context)
    val substitutor = if (result is GroovyMethodResult) result.partialSubstitutor else result.substitutor
    return substitutor.substitute(type)
  }

  private fun findTargetParameter(list: PsiParameterList, target: String): Int {
    val parameters = list.parameters
    for (i in parameters.indices) {
      val modifierList = parameters[i].modifierList ?: continue
      val targetAnnotation = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO_TARGET) ?: continue
      val value = GrAnnotationUtil.inferStringAttribute(targetAnnotation, "value") ?: continue
      if (value == target) return i
    }
    return -1
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
