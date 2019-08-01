// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_OBJECT
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.OperatorExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


internal class RecursiveMethodAnalyzer(val method: GrMethod) : GroovyRecursiveElementVisitor() {
  private val requiredTypesCollector = mutableMapOf<PsiTypeParameter, MutableList<BoundConstraint>>()
  private val contravariantTypesCollector = mutableSetOf<PsiType>()
  private val covariantTypesCollector = mutableSetOf<PsiType>()
  private val constraintsCollector = mutableListOf<ConstraintFormula>()
  private val typeParameters = method.typeParameters.map { it.type() }
  private val dependentTypes = mutableSetOf<PsiTypeParameter>()
  private val inhabitedTypes = mutableMapOf<PsiTypeParameter, MutableList<PsiClass>>()

  private fun addRequiredType(typeParameter: PsiTypeParameter, constraint: BoundConstraint) {
    if (typeParameter.type() in typeParameters && !constraint.clazz.qualifiedName.equals(GROOVY_OBJECT)) {
      if (constraint.clazz !in typeParameters.map { it.resolve() }) {
        requiredTypesCollector.computeIfAbsent(typeParameter) { mutableListOf() }.add(constraint)
      }
      else {
        dependentTypes.add(typeParameter)
        dependentTypes.add(constraint.clazz as PsiTypeParameter)
      }
    }
  }

  private fun processMethod(result: GroovyResolveResult) {
    val methodResult = result as? GroovyMethodResult
    val candidate = methodResult?.candidate
    val receiver = candidate?.receiver as? PsiClassType
    receiver?.run {
      // todo: receiver might be null because of gdk method
      val endpointClassType = extractEndpointType(receiver, typeParameters).typeParameter() ?: return@run
      addRequiredType(endpointClassType, BoundConstraint(candidate.method.containingClass ?: return@run,
                                                         BoundConstraint.ContainMarker.CONTAINS))
    }
    val invokedMethod = when (val method = methodResult?.element) {
      is GrGdkMethod -> method.staticMethod
      else -> method
    }
    methodResult?.substitutor?.substitute(invokedMethod?.returnType)?.run { covariantTypesCollector.add(this) }
    for ((type, argument) in candidate?.argumentMapping?.expectedTypes ?: return) {
      val argumentTypes = when (val argtype = argument.type) {
        is PsiIntersectionType -> argtype.conjuncts
        else -> arrayOf(argtype)
      }
      argumentTypes.forEach { argtype ->
        val argumentTypeParameter =
          extractEndpointType(argtype as? PsiClassType ?: return@forEach, typeParameters).typeParameter() ?: return@forEach
        processRequiredParameters(argumentTypeParameter, methodResult.substitutor.substitute(type))
      }
      val typeParameter = extractEndpointType((methodResult.substitutor.substitute(type) as? PsiClassType) ?: continue,
                                              typeParameters).typeParameter() ?: continue
      argumentTypes.forEach { argtype ->
        addRequiredType(typeParameter, BoundConstraint(methodResult.substitutor.substitute(argtype)?.resolve() ?: return@forEach,
                                                       BoundConstraint.ContainMarker.LOWER))
      }
      methodResult.contextSubstitutor.substitute(type)?.run { contravariantTypesCollector.add(this) }
    }
  }

  /**
   * Calculates required supertypes for all type arguments of [type].
   * We need to distinguish containing and subtyping relations.
   */
  private fun processRequiredParameters(argumentTypeParameter: PsiTypeParameter, type: PsiType) {
    var currentRestrictedTypeParameter = argumentTypeParameter
    var firstVisit = true
    type.accept(object : PsiTypeVisitor<Unit>() {

      fun visitClassParameters(classType: PsiClassType) {
        val matchedBound =
          currentRestrictedTypeParameter.extendsListTypes
            .find {
              TypesUtil.canAssign(classType.rawType(), it.rawType(), argumentTypeParameter,
                                  GrTypeConverter.ApplicableTo.METHOD_PARAMETER) == ConversionResult.OK
            } ?: return
        for (classParameterIndex in classType.parameters.indices) {
          currentRestrictedTypeParameter = extractEndpointType(matchedBound.parameters[classParameterIndex] as PsiClassType,
                                                               typeParameters).typeParameter() ?: continue
          classType.parameters[classParameterIndex].accept(this)
        }
      }

      override fun visitClassType(classType: PsiClassType?) {
        classType ?: return
        val primaryConstraint = if (firstVisit) BoundConstraint.ContainMarker.CONTAINS else BoundConstraint.ContainMarker.EQUAL
        // first visit necessary because parameter types can accept their subtypes, while type arguments (without wildcard) are not
        firstVisit = false
        run {
          addRequiredType(currentRestrictedTypeParameter, BoundConstraint(classType.resolve() ?: return@run, primaryConstraint))
        }
        visitClassParameters(classType)
        super.visitClassType(classType)
      }

      override fun visitWildcardType(wildcardType: PsiWildcardType?) {
        wildcardType ?: return
        val extendsBound = wildcardType.extendsBound as PsiClassType
        run {
          addRequiredType(currentRestrictedTypeParameter, BoundConstraint(extendsBound.resolve() ?: return@run,
                                                                          BoundConstraint.ContainMarker.CONTAINS))
        }
        visitClassParameters(extendsBound)
        super.visitWildcardType(wildcardType)
      }
    })
  }


  override fun visitCallExpression(callExpression: GrCallExpression) {
    processMethod(callExpression.advancedResolve())
    constraintsCollector.add(ExpressionConstraint(null, callExpression))
    super.visitCallExpression(callExpression)
  }

  override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
    run {
      val lValueReference = (expression.lValue as? GrReferenceExpression)?.lValueReference
      val accessorResult = lValueReference?.advancedResolve() as? GroovyMethodResult
      val mapping = accessorResult?.candidate?.argumentMapping
      mapping?.expectedTypes?.forEach { (type, argument) ->
        processRequiredParameters(argument.type?.typeParameter() ?: return@forEach, type)
      }
      val fieldResult = lValueReference?.resolve() as? GrField
      if (fieldResult != null) {
        val leftType = fieldResult.type
        val rightType = expression.rValue?.type
        processRequiredParameters(rightType.typeParameter() ?: return@run, leftType)
        constraintsCollector.add(TypeConstraint(leftType, rightType, method))
      }
    }
    constraintsCollector.add(ExpressionConstraint(null, expression))
    super.visitAssignmentExpression(expression)
  }

  override fun visitVariableDeclaration(variableDeclaration: GrVariableDeclaration) {
    variableDeclaration.variables.forEach {
      processRequiredParameters(it.initializerGroovy?.type?.typeParameter() ?: return@forEach, it.type)
    }
    super.visitVariableDeclaration(variableDeclaration)
  }

  override fun visitVariable(variable: GrVariable) {
    variable.initializerGroovy?.run {
      constraintsCollector.add(ExpressionConstraint(variable.declaredType, this))
    }
    super.visitVariable(variable)
  }

  override fun visitExpression(expression: GrExpression) {
    if (expression is GrOperatorExpression) {
      run {
        processMethod(expression.reference?.advancedResolve() ?: return@run)
      }
      constraintsCollector.add(OperatorExpressionConstraint(expression))
    }
    if (expression is GrIndexProperty) {
      expression.lValueReference?.advancedResolve()?.run { processMethod(this) }
      expression.rValueReference?.advancedResolve()?.run { processMethod(this) }
    }
    super.visitExpression(expression)
  }

  fun visitOuterCalls(originalMethod: GrMethod) {
    val mapping = originalMethod.parameters.map { it.name }.zip(method.parameters).toMap()
    val calls = ReferencesSearch.search(originalMethod).findAll()
    for (parameter in method.parameters) {
      setConstraints(parameter.type, parameter.initializerGroovy?.type ?: continue)
    }
    for (usage in calls.mapNotNull { it.element.parent }) {
      val resolveResult = when (usage) {
        is GrAssignmentExpression -> (usage.lValue as? GrReferenceExpression)?.lValueReference?.advancedResolve()
        is GrConstructorInvocation -> usage.advancedResolve()
        else -> (usage as? GrCall)?.advancedResolve()
      }
      val methodResult = resolveResult as? GroovyMethodResult
      val candidate = methodResult?.candidate
      candidate?.argumentMapping?.expectedTypes?.forEach { (type, argument) ->
        run {
          val param = mapping[candidate.argumentMapping?.targetParameter(argument)?.name] ?: return@run
          val argtype = argument.type
          val correctArgumentType = when {
            argtype.isTypeParameter() -> PsiIntersectionType.createIntersection(*argtype.typeParameter()!!.extendsListTypes)
            else -> argtype
          }
          setConstraints(param.type, correctArgumentType!!)
        }
      }
    }
  }


  private fun setConstraints(leftType: PsiType, rightType: PsiType) {
    var newLeftType = leftType
    if (leftType.isTypeParameter()) {
      addRequiredType(leftType.typeParameter()!!, BoundConstraint(rightType.resolve()!!, BoundConstraint.ContainMarker.LOWER))
      newLeftType = leftType.typeParameter()!!.extendsListTypes.firstOrNull() ?: return
      inhabitedTypes.computeIfAbsent(leftType.typeParameter()!!) { mutableListOf() }.add(rightType.resolve()!!)
    }
    if (leftType is PsiArrayType && rightType is PsiArrayType) {
      setConstraints(leftType.componentType, rightType.componentType)
    }
    (newLeftType as? PsiClassType)?.parameters?.zip((rightType as? PsiClassType)?.parameters ?: return)?.forEach {
      setConstraints(it.first ?: return, it.second ?: return)
    }
  }


  fun buildUsageInformation(): TypeUsageInformation =
    TypeUsageInformation(contravariantTypesCollector, requiredTypesCollector, constraintsCollector, covariantTypesCollector, dependentTypes,
                         inhabitedTypes)
}
