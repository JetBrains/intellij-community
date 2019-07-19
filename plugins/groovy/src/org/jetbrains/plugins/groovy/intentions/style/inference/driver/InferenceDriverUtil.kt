// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.OperatorExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

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
                                val requiredClassTypes: Map<PsiTypeParameter, List<PsiClass>>,
                                val constraints: Collection<ConstraintFormula>) {
  companion object {
    fun merge(data: Collection<TypeUsageInformation>): TypeUsageInformation {
      val contravariantTypes = data.flatMap { it.contravariantTypes }.toSet()
      val requiredClassTypes = data.flatMap {
        it.requiredClassTypes.entries.map { entry ->
          entry.key to entry.value
        }
      }
      val constraints = data.flatMap { it.constraints }
      return TypeUsageInformation(contravariantTypes, requiredClassTypes.toMap(), constraints)
    }
  }
}

internal class RecursiveMethodAnalyzer(val method: GrMethod) : GroovyRecursiveElementVisitor() {
  private val requiredTypesCollector = mutableMapOf<PsiTypeParameter, MutableList<PsiClass>>()
  private val contravariantTypesCollector = mutableSetOf<PsiType>()
  private val constraintsCollector = mutableListOf<ConstraintFormula>()

  private fun addRequiredType(typeParameter: PsiTypeParameter, clazz: PsiClass) =
    requiredTypesCollector.computeIfAbsent(typeParameter) { mutableListOf() }.add(clazz)

  override fun visitCallExpression(callExpression: GrCallExpression) {
    val resolveResult = callExpression.advancedResolve() as? GroovyMethodResult
    val candidate = resolveResult?.candidate
    val receiver = candidate?.receiver as? PsiClassType
    receiver?.run {
      val endpointClassType = extractEndpointType(receiver, method.typeParameters.map { it.type() })
      if (endpointClassType.isTypeParameter()) {
        addRequiredType(endpointClassType.typeParameter()!!, candidate.method.containingClass ?: return@run)
      }
    }
    candidate?.argumentMapping?.expectedTypes?.forEach { (type, argument) ->
      val argumentTypeParameter = argument.type.typeParameter()
      argumentTypeParameter?.run {
        addRequiredType(argumentTypeParameter, type.resolve() ?: return@run)
      }
      resolveResult.contextSubstitutor.substitute(type)?.run { contravariantTypesCollector.add(this) }
    }
    constraintsCollector.add(ExpressionConstraint(null, callExpression))
    super.visitCallExpression(callExpression)
  }

  override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
    run {
      val lValueReference = (expression.lValue as? GrReferenceExpression)?.lValueReference
      val accessorResult = lValueReference?.advancedResolve() as? GroovyMethodResult
      val mapping = accessorResult?.candidate?.argumentMapping
      mapping?.expectedTypes?.forEach { (type, argument) ->
        addRequiredType(argument.type?.typeParameter() ?: return@forEach, type.resolve() ?: return@forEach)
      }
    }
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
    TypeUsageInformation(contravariantTypesCollector, requiredTypesCollector, constraintsCollector)
}


fun setUpParameterMapping(sourceMethod: GrMethod, sinkMethod: GrMethod) = sourceMethod.parameters.zip(sinkMethod.parameters).toMap()