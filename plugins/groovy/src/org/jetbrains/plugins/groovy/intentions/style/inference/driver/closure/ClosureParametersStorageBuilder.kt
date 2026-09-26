// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeParameterList
import org.jetbrains.plugins.groovy.intentions.closure.isClosureCall
import org.jetbrains.plugins.groovy.intentions.style.inference.NameGenerator
import org.jetbrains.plugins.groovy.intentions.style.inference.createProperTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.setUpParameterMapping
import org.jetbrains.plugins.groovy.intentions.style.inference.isClosureTypeDeep
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_STC_CLOSURE_PARAMS
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument


internal class ClosureParametersStorageBuilder(private val generator: NameGenerator, method: GrMethod) {
  private val typeParameterList: PsiTypeParameterList = method.typeParameterList!!
  private val elementFactory = GroovyPsiElementFactory.getInstance(typeParameterList.project)
  private val closureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()

  private fun acceptParameter(parameter: GrParameter, newParametersAmount: Int, closureArguments: List<GrClosableBlock>) {
    val typeParameters = mutableListOf<PsiTypeParameter>()
    repeat(newParametersAmount) {
      val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, null)
      typeParameterList.add(newTypeParameter)
      typeParameters.add(newTypeParameter)
    }
    closureParameters[parameter] = ParameterizedClosure(parameter, typeParameters, closureArguments, emptyList())
  }

  fun extractClosuresFromOuterCalls(method: GrMethod,
                                    virtualMethod: GrMethod,
                                    callReferences: List<PsiReference>): List<GrParameter> {
    val visitedParameters = mutableListOf<GrParameter>()
    for ((parameter, calls) in collectClosureArguments(method, virtualMethod, callReferences)) {
      // todo: default-valued parameters
      val closableBlockCalls = calls.filterIsInstance<GrClosableBlock>().takeIf { it.isNotEmpty() } ?: continue
      visitedParameters.add(parameter)
      acceptParameter(parameter, closableBlockCalls.first().allParameters.size, closableBlockCalls)
    }
    return visitedParameters
  }

  fun extractClosuresFromCallInvocation(callUsages: Iterable<GrCall>,
                                        parameter: GrParameter): Boolean {
    val directClosureCall = callUsages.firstOrNull { it.isClosureCall(parameter) } ?: return false
    val argumentAmount = directClosureCall.argumentList?.allArguments?.size ?: return false
    acceptParameter(parameter, argumentAmount, emptyList())
    return true
  }


  fun extractClosuresFromOtherMethodInvocations(callUsages: Iterable<GrCall>, parameter: GrParameter) {
    for (call in callUsages) {
      val argumentMapping = (call.advancedResolve() as? GroovyMethodResult)?.candidate?.argumentMapping ?: continue
      val argument = argumentMapping.expectedTypes.find { it.second.isReferenceTo(parameter) }?.second ?: continue
      val targetParameter = argumentMapping.targetParameter(argument)?.psi as? GrParameter ?: continue
      val closureParamsAnno = targetParameter.modifierList.findAnnotation(GROOVY_TRANSFORM_STC_CLOSURE_PARAMS) ?: continue
      acceptParameter(parameter, availableParameterNumber(closureParamsAnno), emptyList())
      break
    }
  }

  fun build(): Map<GrParameter, ParameterizedClosure> {
    return closureParameters
  }


  private fun collectClosureArguments(originalMethod: GrMethod,
                                      virtualMethod: GrMethod,
                                      callReferences: List<PsiReference>): Map<GrParameter, List<GrExpression>> {
    val untypedParameters = originalMethod.parameters.filter { it.typeElement == null }
    val allArgumentExpressions = extractArgumentExpressions(untypedParameters, callReferences)
    val proxyMapping = setUpParameterMapping(originalMethod, virtualMethod)
    return allArgumentExpressions.mapNotNull { (parameter, arguments) ->
      if (arguments.any { !(it.type.isClosureTypeDeep() || it is GrClosableBlock) }) {
        return@mapNotNull null
      }
      val closableBlockArguments = arguments.filterIsInstance<GrClosableBlock>()
      val virtualParameter = proxyMapping[parameter] ?: return@mapNotNull null
      if (closableBlockArguments.isEmpty()) null else virtualParameter to closableBlockArguments
    }.toMap()

  }

  companion object {
    fun Argument?.isReferenceTo(element: PsiElement) =
      this is ExpressionArgument && expression.reference?.resolve() == element
  }

  private fun extractArgumentExpressions(targetParameters: Collection<GrParameter>,
                                         callReferences: List<PsiReference>): Map<GrParameter, List<GrExpression>> {
    val expressionStorage = mutableMapOf<GrParameter, MutableList<GrExpression>>()
    targetParameters.forEach { expressionStorage[it] = mutableListOf() }
    for (call in callReferences.mapNotNull { it.element.parent as? GrCall }) {
      val mapping = (call.advancedResolve() as? GroovyMethodResult)?.candidate?.argumentMapping ?: continue
      mapping.expectedTypes.forEach { (_, arg) ->
        val expression = (arg as? ExpressionArgument)?.expression ?: return@forEach
        val targetParameter = mapping.targetParameter(arg)?.psi ?: return@forEach
        expressionStorage[targetParameter]?.add(expression)
      }
    }
    return expressionStorage
  }
}