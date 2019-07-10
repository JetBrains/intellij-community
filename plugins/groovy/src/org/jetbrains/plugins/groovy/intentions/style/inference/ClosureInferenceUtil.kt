// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.SignatureHintProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*

fun setUpClosuresSignature(inferenceSession: GroovyInferenceSession,
                           closureParameter: ParametrizedClosure,
                           instructions: List<ReadWriteVariableInstruction>) {
  for (call in instructions) {
    val nearestCall = call.element?.parentOfType<GrCall>() ?: continue
    if (nearestCall == call.element?.parent && nearestCall.resolveMethod()?.containingClass?.qualifiedName == GroovyCommonClassNames.GROOVY_LANG_CLOSURE) {
      for (index in nearestCall.expressionArguments.indices) {
        inferenceSession.addConstraint(
          ExpressionConstraint(inferenceSession.substituteWithInferenceVariables(closureParameter.typeParameters[index].type()),
                               nearestCall.expressionArguments[index]))
      }
    }
  }
}

fun collectDeepClosureDependencies(session: GroovyInferenceSession, closureParameter: ParametrizedClosure,
                                   usages: List<ReadWriteVariableInstruction>) {
  val parameter = closureParameter.parameter
  for (call in usages) {
    val nearestCall = call.element!!.parentOfType<GrCall>()!!
    if (nearestCall == call.element!!.parent && nearestCall.resolveMethod()?.containingClass?.qualifiedName == GroovyCommonClassNames.GROOVY_LANG_CLOSURE) {
      continue
    }
    val resolveResult = nearestCall.advancedResolve() as? GroovyMethodResult ?: continue
    val mapping = resolveResult.candidate?.argumentMapping ?: continue
    val innerParameter = mapping.targetParameter(
      mapping.arguments.find { it is ExpressionArgument && it.expression.reference?.resolve() == parameter } ?: continue
    ) ?: continue
    val hint = innerParameter.annotations.mapNotNull {
      GrAnnotationUtil.inferClassAttribute(it, "value")?.qualifiedName
    }.mapNotNull { SignatureHintProcessor.getHintProcessor(it) }.firstOrNull() ?: continue
    val options =
      AnnotationUtil.arrayAttributeValues(innerParameter.annotations.first().findAttributeValue("options")).mapNotNull {
        (it as? PsiLiteral)?.value as? String
      }.toTypedArray()
    val outerMethod = call.element!!.parentOfType<GrMethod>()!!
    val innerMethod = nearestCall.resolveMethod()
    // We cannot use GroovyMethodResult#getSubstitutor because we need to leave type parameters of outerMethod among substitution variants
    val substitutor = collectGenericSubstitutor(resolveResult, outerMethod)
    val gdkGuard = when (innerMethod) {
      is GrGdkMethod -> innerMethod.staticMethod
      else -> innerMethod
    }
    val signatures = hint.inferExpectedSignatures(gdkGuard!!, substitutor, options)
    signatures.first().zip(closureParameter.typeParameters).forEach { (type, typeParameter) ->
      session.addConstraint(TypeConstraint(session.substituteWithInferenceVariables(typeParameter.type()), type, outerMethod))
    }
  }
}

private fun collectGenericSubstitutor(resolveResult: GroovyMethodResult, outerMethod: GrMethod): PsiSubstitutor {
  val outerParameters = outerMethod.typeParameters.map { it.type() }.toSet()
  val resolveSession = CollectingGroovyInferenceSession(outerMethod.typeParameters.filter {
    (it.extendsListTypes.run { isEmpty() || first() in outerParameters })
  }.toTypedArray(), PsiSubstitutor.EMPTY, outerMethod, mirrorBounds = true)
  resolveSession.addConstraint(MethodCallConstraint(null, resolveResult, outerMethod))
  for (typeParameter in outerMethod.typeParameters) {
    resolveSession.getInferenceVariable(
      resolveSession.substituteWithInferenceVariables(typeParameter.type()))?.instantiation = typeParameter.type()
  }
  return resolveSession.inferSubst(resolveResult)
}


fun tryToExtractUnqualifiedName(className: String): String {
  val location = GroovyFileBase.IMPLICITLY_IMPORTED_PACKAGES.firstOrNull { className.startsWith(it) }
  return when {
    location != null -> className.substringAfter("$location.")
    (className in GroovyFileBase.IMPLICITLY_IMPORTED_CLASSES) -> className.substringAfterLast(".")
    else -> className
  }

}
