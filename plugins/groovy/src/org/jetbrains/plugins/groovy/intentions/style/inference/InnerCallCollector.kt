// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.OperatorExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

data class TypeUsageMetaInfo(val contravariantTypes: Set<PsiType>, val requiredClassTypes: Map<String, List<PsiClass>>)

fun collectInnerMethodCalls(virtualMethod: GrMethod,
                            closureParameters: Map<GrParameter, ParameterizedClosure>,
                            varargParameters: Set<GrParameter>,
                            inferenceSession: GroovyInferenceSession): TypeUsageMetaInfo {
  val requiredClassCollector = mutableMapOf<String, MutableList<PsiClass>>()
  val contravariantTypes = mutableSetOf<PsiType>()
  virtualMethod.accept(object : GroovyRecursiveElementVisitor() {
    override fun visitCallExpression(callExpression: GrCallExpression) {
      val resolveResult = callExpression.advancedResolve() as? GroovyMethodResult
      val candidate = resolveResult?.candidate
      val receiver = candidate?.receiver as? PsiClassType
      receiver?.run {
        val endpointClassName = extractEndpointType(receiver, virtualMethod.typeParameters.map { it.type() }).className
        if (endpointClassName != null) {
          requiredClassCollector.computeIfAbsent(endpointClassName) { mutableListOf() }.add(candidate.method.containingClass ?: return@run)
        }
      }
      candidate?.argumentMapping?.expectedTypes?.forEach { (type, argument) ->
        val argumentType = (argument.type as? PsiClassType)
        argumentType?.run {
          requiredClassCollector.computeIfAbsent(argumentType.className) { mutableListOf() }.add(
            (type as? PsiClassType)?.resolve() ?: return)
        }
        resolveResult.contextSubstitutor.substitute(type)?.run { contravariantTypes.add(this) }
      }
      if (isClosureType(receiver)) {
        val parameter = callExpression.firstChild.run {
          closureParameters[reference?.resolve() as? GrParameter ?: firstChild.reference?.resolve()]
        }
        parameter?.run {
          callExpression.expressionArguments.zip(parameter.types).forEach { (expression, parameterType) ->
            inferenceSession.addConstraint(
              ExpressionConstraint(inferenceSession.substituteWithInferenceVariables(parameterType), expression))
          }
        }
      }
      else {
        inferenceSession.addConstraint(ExpressionConstraint(null, callExpression))
      }
      super.visitCallExpression(callExpression)
    }

    override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
      inferenceSession.addConstraint(ExpressionConstraint(null, expression))
      super.visitAssignmentExpression(expression)
    }

    override fun visitVariable(variable: GrVariable) {
      variable.initializerGroovy?.run {
        inferenceSession.addConstraint(ExpressionConstraint(variable.declaredType, this))
      }
      super.visitVariable(variable)
    }

    override fun visitExpression(expression: GrExpression) {
      if (expression is GrOperatorExpression) {
        inferenceSession.addConstraint(OperatorExpressionConstraint(expression))
      }
      super.visitExpression(expression)
    }
  })
  virtualMethod.block?.controlFlow
    ?.filterIsInstance<ReadWriteVariableInstruction>()
    ?.groupBy { it.element?.reference?.resolve() }
    ?.forEach { (parameter, usages) ->
      if (parameter is GrParameter && parameter in closureParameters.keys) {
        collectDeepClosureDependencies(inferenceSession, closureParameters.getValue(parameter), usages)
      }
    }
  return TypeUsageMetaInfo(
    contravariantTypes +
    virtualMethod.parameters.mapNotNull { (it.type as? PsiArrayType)?.componentType } +
    varargParameters.map { it.type } +
    closureParameters.flatMap { it.value.types },
    requiredClassCollector.toMap())
}

/**
 * Reaches type parameter that does not extend other type parameter
 * @param type should be a type parameter
 */
private tailrec fun extractEndpointType(type: PsiClassType, typeParameters: List<PsiType>): PsiClassType =
  if (type.superTypes.size == 1 && type.superTypes.single() in typeParameters) {
    extractEndpointType(type.superTypes.single() as PsiClassType, typeParameters)
  }
  else {
    type
  }
