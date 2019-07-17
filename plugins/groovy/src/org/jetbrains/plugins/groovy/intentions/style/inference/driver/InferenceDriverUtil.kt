// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.ParameterizedClosure
import org.jetbrains.plugins.groovy.intentions.style.inference.isClosureType
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.OperatorExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

fun collectClosureArguments(method: GrMethod, virtualMethod: GrMethod): Map<GrParameter, List<GrExpression>> {
  val allAcceptedExpressions = extractAcceptedExpressions(method,
                                                          method.parameters.filter { it.typeElement == null },
                                                          mutableSetOf())
  val proxyMapping = method.parameters.zip(virtualMethod.parameters).toMap()
  return allAcceptedExpressions
    .map { (parameter, expressions) -> proxyMapping.getValue(parameter) to expressions }
    .filter { (_, acceptedTypes) -> acceptedTypes.all { it is GrClosableBlock } && acceptedTypes.isNotEmpty() }
    .toMap()

}

private fun extractAcceptedExpressions(method: GrMethod,
                                       targetParameters: Collection<GrParameter>,
                                       visitedMethods: MutableSet<GrMethod>): Map<GrParameter, List<GrExpression>> {
  if (targetParameters.isEmpty()) {
    return emptyMap()
  }
  visitedMethods.add(method)
  val referencesStorage = mutableMapOf<GrParameter, MutableList<GrExpression>>()
  targetParameters.forEach { referencesStorage[it] = mutableListOf() }
  for (call in ReferencesSearch.search(method).findAll().mapNotNull { it.element.parent as? GrCall }) {
    val argumentList = call.expressionArguments + call.closureArguments
    val targetExpressions = argumentList.zip(method.parameters).filter { it.second in targetParameters }
    val insufficientExpressions = targetExpressions.filter { it.first.type?.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ?: true }
    (targetExpressions - insufficientExpressions).forEach { referencesStorage[it.second]!!.add(it.first) }
    val enclosingMethodParameterMapping = insufficientExpressions.mapNotNull { (expression, targetParameter) ->
      val resolved = expression.reference?.resolve() as? GrParameter
      resolved?.run {
        Pair(this, targetParameter)
      }
    }.toMap()
    val enclosingMethod = call.parentOfType(GrMethod::class)
    if (enclosingMethod != null && !visitedMethods.contains(enclosingMethod)) {
      val acceptedExpressionsForEnclosingParameters = extractAcceptedExpressions(
        enclosingMethod, enclosingMethodParameterMapping.keys,
        visitedMethods)
      acceptedExpressionsForEnclosingParameters.forEach { referencesStorage[enclosingMethodParameterMapping[it.key]]!!.addAll(it.value) }
    }
  }
  return referencesStorage
}

/**
 * Reaches type parameter that does not extend other type parameter
 * @param type should be a type parameter
 */
tailrec fun extractEndpointType(type: PsiClassType, typeParameters: List<PsiType>): PsiClassType =
  if (type.superTypes.size == 1 && type.superTypes.single() in typeParameters) {
    extractEndpointType(type.superTypes.single() as PsiClassType, typeParameters)
  }
  else {
    type
  }


data class TypeUsageInformation(val contravariantTypes: Set<PsiType>,
                                val requiredClassTypes: Map<String, List<PsiClass>>,
                                val constraints: Collection<ConstraintFormula>)


internal class RecursiveMethodAnalyzer(val method: GrMethod,
                                       private val closureParameters: Map<GrParameter, ParameterizedClosure>) : GroovyRecursiveElementVisitor() {
  private val boundCollector = mutableMapOf<String, MutableList<PsiClass>>()
  private val contravariantTypesCollector = mutableSetOf<PsiType>()
  private val constraintsCollector = mutableListOf<ConstraintFormula>()

  override fun visitCallExpression(callExpression: GrCallExpression) {
    val resolveResult = callExpression.advancedResolve() as? GroovyMethodResult
    val candidate = resolveResult?.candidate
    val receiver = candidate?.receiver as? PsiClassType
    receiver?.run {
      val endpointClassName = extractEndpointType(receiver, method.typeParameters.map { it.type() }).className
      if (endpointClassName != null) {
        boundCollector.computeIfAbsent(endpointClassName) { mutableListOf() }.add(candidate.method.containingClass ?: return)
      }
    }
    candidate?.argumentMapping?.expectedTypes?.forEach { (type, argument) ->
      val argumentType = (argument.type as? PsiClassType)
      argumentType?.run {
        boundCollector.computeIfAbsent(argumentType.className) { mutableListOf() }.add(type.resolve() ?: return)
      }
      resolveResult.contextSubstitutor.substitute(type)?.run { contravariantTypesCollector.add(this) }
    }
    if (receiver.isClosureType()) {
      val parameter = callExpression.firstChild.run {
        closureParameters[reference?.resolve() as? GrParameter ?: firstChild.reference?.resolve()]
      }
      parameter?.run {
        callExpression.expressionArguments.zip(parameter.types).forEach { (expression, parameterType) ->
          constraintsCollector.add(ExpressionConstraint(parameterType, expression))
        }
      }
    }
    else {
      constraintsCollector.add(ExpressionConstraint(null, callExpression))
    }
    super.visitCallExpression(callExpression)
  }

  override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
    constraintsCollector.add(ExpressionConstraint(null, expression))
    super.visitAssignmentExpression(expression)
  }

  override fun visitVariable(variable: GrVariable) {
    variable.initializerGroovy?.run {
      constraintsCollector.add(ExpressionConstraint(variable.declaredType, this))
    }
    super.visitVariable(variable)
  }

  override fun visitExpression(expression: GrExpression) {
    if (expression is GrOperatorExpression) {
      constraintsCollector.add(OperatorExpressionConstraint(expression))
    }
    super.visitExpression(expression)
  }

  fun buildUsageInformation(): TypeUsageInformation =
    TypeUsageInformation(contravariantTypesCollector, boundCollector, constraintsCollector)
}


fun setUpParameterMapping(sourceMethod: GrMethod, sinkMethod: GrMethod) = sourceMethod.parameters.zip(sinkMethod.parameters).toMap()