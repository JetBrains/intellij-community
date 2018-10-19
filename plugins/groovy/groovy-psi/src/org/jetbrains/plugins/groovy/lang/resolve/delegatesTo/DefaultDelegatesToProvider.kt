// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.delegatesTo

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.FromStringHintProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

class DefaultDelegatesToProvider : GrDelegatesToProvider {

  override fun getDelegatesToInfo(closableBlock: GrClosableBlock): DelegatesToInfo? {
    val call = getContainingCall(closableBlock) ?: return null

    val result = resolveActualCall(call)
    val element = result.element

    if (GdkMethodUtil.isWithOrIdentity(element)) {
      val qualifier = inferCallQualifier(call as GrMethodCall) ?: return null

      return DelegatesToInfo(qualifier.type, Closure.DELEGATE_FIRST)
    }

    val signature = inferSignature(element) ?: return null

    val map = mapArgs(closableBlock, call, signature) ?: return null

    if (element !is PsiMethod) return null
    val method = element as PsiMethod?
    val parameterList = method!!.parameterList
    val parameter = findParameter(parameterList, closableBlock, map) ?: return null

    val delegateFqnData = parameter.getUserData(DELEGATES_TO_KEY)
    val strategyData = parameter.getUserData(DELEGATES_TO_STRATEGY_KEY)
    if (delegateFqnData != null) {
      return DelegatesToInfo(
        TypesUtil.createType(delegateFqnData, closableBlock),
        strategyData ?: Closure.OWNER_FIRST
      )
    }

    val modifierList = parameter.modifierList ?: return null

    val delegatesTo = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO) ?: return null

    var delegateType = getFromValue(delegatesTo)
    if (delegateType == null) delegateType = getFromTarget(parameterList, delegatesTo, signature, map)
    if (delegateType == null) delegateType = getFromType(result, delegatesTo)

    val strategyValue = getStrategyValue(delegatesTo.findAttributeValue("strategy"))
    return DelegatesToInfo(delegateType, strategyValue)
  }

  private fun mapArgs(place: PsiElement, call: GrCall, signature: GrClosureSignature): Array<GrClosureSignatureUtil.ArgInfo<PsiElement>>? {
    val rawSignature = GrClosureSignatureUtil.rawSignature(signature)
    return GrClosureSignatureUtil.mapParametersToArguments(
      rawSignature, call.namedArguments, call.expressionArguments, call.closureArguments, place, false, false
    )
  }

  private fun inferSignature(element: PsiElement?): GrClosureSignature? {
    if (element is PsiMethod) {
      return GrClosureSignatureUtil.createSignature((element as PsiMethod?)!!, PsiSubstitutor.EMPTY)
    }
    else if (element is GrVariable) {
      val type = element.typeGroovy
      if (type is GrClosureType) {
        val signature = type.signature
        if (signature is GrClosureSignature) {
          return signature
        }
      }
    }
    return null
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
      return extractTypeFromClassType(value.type)
    }
    else if (value is PsiClassObjectAccessExpression) {
      return extractTypeFromClassType(value.type)
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

  private fun inferGenericArgType(signature: GrClosureSignature,
                                  targetType: PsiType?,
                                  genericIndex: Int,
                                  param: Int): PsiType? {
    if (targetType is PsiClassType) {
      val result = targetType.resolveGenerics()
      val psiClass = result.element
      if (psiClass != null) {
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
      }
    }
    return null
  }

  private fun getFromType(result: GroovyResolveResult, delegatesTo: PsiAnnotation): PsiType? {
    val element = result.element as? PsiMethod ?: return null

    val typeValue = GrAnnotationUtil.inferStringAttribute(delegatesTo, "type")
    if (StringUtil.isEmptyOrSpaces(typeValue)) return null

    val context = FromStringHintProcessor.createContext(element)
    val type = JavaPsiFacade.getElementFactory(context.project).createTypeFromText(typeValue!!, context)

    return if (result is GroovyMethodResult) {
      result.partialSubstitutor.substitute(type)
    }
    else result.substitutor.substitute(type)
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

  private fun extractTypeFromClassType(type: PsiType?): PsiType? {
    if (type is PsiClassType) {
      val resolved = type.resolve()
      if (resolved != null && CommonClassNames.JAVA_LANG_CLASS == resolved.qualifiedName) {
        val parameters = type.parameters
        if (parameters.size == 1) {
          return parameters[0]
        }
      }
    }
    return null
  }

  private fun getStrategyValue(strategy: PsiAnnotationMemberValue?): Int {
    if (strategy == null) return -1

    val text = strategy.text
    if ("0" == text) return 0
    if ("1" == text) return 1
    if ("2" == text) return 2
    if ("3" == text) return 3
    if ("4" == text) return 4

    if (text.endsWith("OWNER_FIRST")) return Closure.OWNER_FIRST
    if (text.endsWith("DELEGATE_FIRST")) return Closure.DELEGATE_FIRST
    if (text.endsWith("OWNER_ONLY")) return Closure.OWNER_ONLY
    if (text.endsWith("DELEGATE_ONLY")) return Closure.DELEGATE_ONLY
    return if (text.endsWith("TO_SELF")) Closure.TO_SELF else -1

  }


}
