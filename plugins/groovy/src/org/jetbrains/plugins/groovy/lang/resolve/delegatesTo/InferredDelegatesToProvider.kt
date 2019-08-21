// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.delegatesTo

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiType
import groovy.lang.Closure
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.setUpParameterMapping
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument

class InferredDelegatesToProvider : GrDelegatesToProvider {

  override fun getDelegatesToInfo(expression: GrFunctionalExpression): DelegatesToInfo? {
    val call = getContainingCall(expression) ?: return null
    val result = call.advancedResolve() as? GroovyMethodResult ?: return null
    val method = result.element as? GrMethod ?: return null
    if (method.parameters.all { it.typeElement != null }) {
      return null
    }
    val (virtualMethod, substitutor) = MethodParameterAugmenter.createInferenceResult(method) ?: return null
    virtualMethod ?: return null
    val argumentMapping = result.candidate?.argumentMapping ?: return null
    val parameterMapping = setUpParameterMapping(method, virtualMethod)
    val targetParameter = argumentMapping.targetParameter(ExpressionArgument(expression)) as? GrParameter ?: return null
    val virtualParameter = virtualMethod.parameters[method.parameterList.getParameterNumber(targetParameter)] ?: return null
    val delegatesTo = virtualParameter.modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO) ?: return null
    val strategyValue = getStrategyValue(delegatesTo.findAttributeValue("strategy"))
    val delegateType = if (strategyValue != Closure.OWNER_ONLY && strategyValue != Closure.TO_SELF) {
      getFromValue(delegatesTo)
      ?: getFromVirtualTarget(virtualMethod.parameterList, delegatesTo, argumentMapping, parameterMapping)
      ?: getFromType(call, result, delegatesTo)
    }
    else null
    return DelegatesToInfo(substitutor.substitute(delegateType), strategyValue)
  }

  private fun getFromVirtualTarget(parameterList: PsiParameterList,
                                   delegatesTo: PsiAnnotation,
                                   mapping: ArgumentMapping,
                                   parameterMapping: Map<GrParameter, GrParameter>): PsiType? {
    val target = GrAnnotationUtil.inferStringAttribute(delegatesTo, "target") ?: return null
    val parameter = findTargetParameter(parameterList, target) ?: return null
    // genericTypeIndex can not appear after inference process
    return mapping.arguments.firstOrNull {
      parameterMapping[mapping.targetParameter(it)] == parameter
    }?.type ?: return null
  }

}