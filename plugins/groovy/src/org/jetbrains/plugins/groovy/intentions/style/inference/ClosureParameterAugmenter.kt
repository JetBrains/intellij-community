// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter.Companion.createInferenceResult
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.compose
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeAugmenter
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.SignatureHintProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder

class ClosureParameterAugmenter : TypeAugmenter() {

  override fun inferType(variable: GrVariable): PsiType? {
    if (variable !is GrParameter || variable.typeElement != null) {
      return null
    }
    val (index, closure) = variable.getIndexAndClosure() ?: return null
    val call = closure.parentOfType<GrCall>()?.takeIf { (it.expressionArguments + it.closureArguments).contains(closure) } ?: return null
    val method = (call.resolveMethod() as? GrMethod)?.takeIf { it.parameters.any(GrParameter::eligibleForExtendedInference) }
                 ?: return null
    val signatures = computeSignatures(call, closure, method) ?: return null
    val parameters = closure.allParameters
    return signatures.singleOrNull { it.size == parameters.size }?.getOrNull(index)?.unwrapBound()
  }

  private fun GrParameter.getIndexAndClosure(): Pair<Int, GrFunctionalExpression>? {
    if (this is ClosureSyntheticParameter) {
      val closure = this.closure ?: return null
      return 0 to closure
    }
    else {
      val closure = this.parent?.parent as? GrFunctionalExpression ?: return null
      val index = closure.parameterList.getParameterNumber(this)
      return index to closure
    }
  }

  private fun PsiType.unwrapBound(): PsiType? = if (this is PsiWildcardType && isSuper) bound else this

  private val closureParamsShort = GroovyCommonClassNames.GROOVY_TRANSFORM_STC_CLOSURE_PARAMS.substringAfterLast('.')


  private fun computeAnnotationBasedSubstitutor(call: GrCall,
                                                builder: GroovyInferenceSessionBuilder): PsiSubstitutor {
    return builder.skipClosureIn(call).resolveMode(false).build().inferSubst()
  }


  private fun computeSignatures(methodCall: GrCall,
                                closureBlock: GrFunctionalExpression,
                                method: GrMethod): List<Array<out PsiType>>? {
    val (virtualMethod, virtualToActualSubstitutor) = createInferenceResult(method) ?: return null
    virtualMethod ?: return null
    val resolveResult = methodCall.advancedResolve() as? GroovyMethodResult ?: return null
    val methodCandidate = resolveResult.candidate ?: return null
    val sessionBuilder = CollectingGroovyInferenceSessionBuilder(methodCall, methodCandidate, virtualMethod,
                                                                 resolveResult.contextSubstitutor).addProxyMethod(method)
    val annotationBasedSubstitutor = computeAnnotationBasedSubstitutor(methodCall, sessionBuilder)
    val completeContextSubstitutor = virtualToActualSubstitutor.putAll(annotationBasedSubstitutor) compose resolveResult.substitutor
    val annotatedClosureParameter = findAnnotatedClosureParameter(resolveResult, closureBlock, virtualMethod) ?: return null
    val anno = annotatedClosureParameter.modifierList.annotations.find { it.shortName == closureParamsShort } ?: return null
    return getSignatures(anno, completeContextSubstitutor, virtualMethod)
  }


  private fun findAnnotatedClosureParameter(resolveResult: GroovyMethodResult,
                                            closureBlock: GrFunctionalExpression,
                                            virtualMethod: GrMethod): GrParameter? {
    val method = resolveResult.candidate?.method ?: return null
    val methodParameter = (resolveResult.candidate?.argumentMapping?.targetParameter(
      ExpressionArgument(closureBlock))?.psi as? GrParameter)?.takeIf { it.eligibleForExtendedInference() } ?: return null
    return virtualMethod.parameters.getOrNull(method.parameterList.getParameterIndex(methodParameter))
  }

  private fun getSignatures(anno: PsiAnnotation, substitutor: PsiSubstitutor, virtualMethod: GrMethod): List<Array<out PsiType>>? {
    val className = (anno.findAttributeValue("value") as? GrReferenceExpression)?.qualifiedReferenceName ?: return null
    val processor = SignatureHintProcessor.getHintProcessor(className) ?: return null
    val options = AnnotationUtil.arrayAttributeValues(anno.findAttributeValue("options")).mapNotNull { (it as? PsiLiteral)?.stringValue() }
    return processor.inferExpectedSignatures(virtualMethod, substitutor, options.toTypedArray())
  }

}