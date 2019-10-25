// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.CollectingGroovyInferenceSession
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.compose
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.MethodCallConstraint

class InferredClosureParamsEnhancer : AbstractClosureParameterEnhancer() {

  override fun getClosureParameterType(closureBlock: GrFunctionalExpression, index: Int): PsiType? {
    val methodCall = closureBlock.parentOfType<GrCall>() ?: return null
    val method = (methodCall.resolveMethod() as? GrMethod)
    method?.takeIf { it.parameters.any { parameter -> parameter.typeElement == null } } ?: return null
    val (virtualMethod, virtualToActualSubstitutor) = MethodParameterAugmenter.createInferenceResult(method) ?: return null
    virtualMethod ?: return null
    val resolveResult = methodCall.advancedResolve() as? GroovyMethodResult ?: return null
    val virtualParameter = getVirtualParameter(resolveResult, closureBlock, virtualMethod) ?: return null
    val completeContextSubstitutor =
      virtualToActualSubstitutor.putAll(virtualSubstitutor(virtualMethod, resolveResult, methodCall)) compose resolveResult.substitutor
    val anno = virtualParameter.modifierList.annotations.find { it.shortName == closureParamsShort } ?: return null
    val signatures = getSignatures(anno, completeContextSubstitutor, virtualMethod) ?: return null
    val parameters = closureBlock.allParameters
    return signatures.singleOrNull { it.size == parameters.size }?.getOrNull(index)
  }


  private val closureParamsShort = GroovyCommonClassNames.GROOVY_TRANSFORM_STC_CLOSURE_PARAMS.substringAfterLast('.')


  private fun getVirtualParameter(resolveResult: GroovyMethodResult,
                                  closureBlock: GrFunctionalExpression,
                                  virtualMethod: GrMethod): GrParameter? {
    val method = resolveResult.candidate?.method ?: return null
    val methodParameter = (resolveResult.candidate?.argumentMapping?.targetParameter(
      ExpressionArgument(closureBlock)) as? GrParameter)?.takeIf { it.typeElement == null } ?: return null
    return virtualMethod.parameters.getOrNull(method.parameterList.getParameterIndex(methodParameter)) ?: return null
  }

  private fun getSignatures(anno: PsiAnnotation, substitutor: PsiSubstitutor, virtualMethod: GrMethod): List<Array<out PsiType>>? {
    val className = (anno.findAttributeValue("value") as? GrReferenceExpression)?.qualifiedReferenceName ?: return null
    val processor = SignatureHintProcessor.getHintProcessor(className) ?: return null
    val options = AnnotationUtil.arrayAttributeValues(anno.findAttributeValue("options")).mapNotNull { (it as? PsiLiteral)?.stringValue() }
    return processor.inferExpectedSignatures(virtualMethod, substitutor, options.toTypedArray())
  }

  private fun virtualSubstitutor(virtualMethod: GrMethod,
                                 resolveResult: GroovyMethodResult,
                                 methodCall: GrCall): PsiSubstitutor {
    val originalMethod =
      resolveResult.candidate?.method?.takeIf { method -> method.parameters.all { it.name != null } } ?: return PsiSubstitutor.EMPTY
    val proxyMapping = originalMethod.parameters.map { it.name!! }.zip(virtualMethod.parameters).toMap()
    val session = CollectingGroovyInferenceSession(virtualMethod.typeParameters, virtualMethod, resolveResult.contextSubstitutor,
                                                   proxyMapping)
    session.addConstraint(MethodCallConstraint(null, resolveResult, methodCall))
    return session.inferSubst()
  }

}