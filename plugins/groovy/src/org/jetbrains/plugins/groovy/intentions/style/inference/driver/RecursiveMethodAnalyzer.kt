// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.*
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.OK
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.ApplicableTo.METHOD_PARAMETER
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

  private fun generateRequiredTypes(typeParameter: PsiTypeParameter, type: PsiType, marker: ContainMarker) {
    val bindingTypes = expandWildcards(type, typeParameter)
    bindingTypes.forEach { addRequiredType(typeParameter, BoundConstraint(it, marker)) }
  }

  private fun addRequiredType(typeParameter: PsiTypeParameter, constraint: BoundConstraint) {
    if (typeParameter.type() in typeParameters && !constraint.type.equalsToText(GROOVY_OBJECT)) {
      if (constraint.type.typeParameter() !in typeParameters.map { it.resolve() }) {
        requiredTypesCollector.safePut(typeParameter, constraint)
      }
      else {
        dependentTypes.add(typeParameter)
        dependentTypes.add(constraint.type.typeParameter()!!)
      }
    }
  }

  private fun processMethod(result: GroovyResolveResult) {
    val methodResult = result as? GroovyMethodResult
    val candidate = methodResult?.candidate
    val receiver = candidate?.smartReceiver()
    receiver?.run {
      val containingType = candidate.smartContainingType()
      generateRequiredTypes(typeParameter() ?: return@run, containingType ?: return@run, UPPER)
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
        processRequiredParameters(argtype.typeParameter() ?: return@forEach, methodResult.substitutor.substitute(type))
      }
      val typeParameter = methodResult.substitutor.substitute(type).typeParameter() ?: continue
      argumentTypes.forEach { argtype ->
        generateRequiredTypes(typeParameter, methodResult.substitutor.substitute(argtype) ?: return@forEach, LOWER)
      }
      methodResult.contextSubstitutor.substitute(type)?.run { contravariantTypesCollector.add(this) }
    }
  }

  /**
   * Calculates required supertypes for all type arguments of [type].
   * We need to distinguish containing and subtyping relations, so this is why there is two types of bounds
   */
  private fun processRequiredParameters(argumentTypeParameter: PsiTypeParameter, type: PsiType) {
    var currentRestrictedTypeParameter = argumentTypeParameter
    var firstVisit = true
    type.accept(object : PsiTypeVisitor<Unit>() {

      fun visitClassParameters(classType: PsiClassType) {
        val matchedBound =
          currentRestrictedTypeParameter.extendsListTypes
            .find {
              TypesUtil.canAssign(classType.rawType(), it.rawType(), argumentTypeParameter, METHOD_PARAMETER) == OK
            } ?: return
        for (classParameterIndex in classType.parameters.indices) {
          currentRestrictedTypeParameter = matchedBound.parameters[classParameterIndex]?.typeParameter() ?: continue
          classType.parameters[classParameterIndex].accept(this)
        }
      }

      override fun visitClassType(classType: PsiClassType?) {
        classType ?: return
        val primaryConstraint = if (firstVisit) UPPER else EQUAL
        // firstVisit is necessary because parameters can accept their subtypes, while type arguments (without wildcard) are not
        firstVisit = false
        run {
          generateRequiredTypes(currentRestrictedTypeParameter, classType, primaryConstraint)
        }
        visitClassParameters(classType)
        super.visitClassType(classType)
      }

      override fun visitWildcardType(wildcardType: PsiWildcardType?) {
        wildcardType ?: return
        val extendsBound = wildcardType.extendsBound as PsiClassType
        run {
          generateRequiredTypes(currentRestrictedTypeParameter, extendsBound, UPPER)
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

  override fun visitReturnStatement(returnStatement: GrReturnStatement) {
    returnStatement.returnValue?.apply { processExitExpression(this) }
    super.visitReturnStatement(returnStatement)
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
      setConstraints(parameter.type, parameter.initializerGroovy?.type ?: continue, dependentTypes, requiredTypesCollector,
                     method.typeParameters.toSet())
    }
    for (usage in calls.mapNotNull { it.element.parent }) {
      val resolveResult = when (usage) {
        is GrAssignmentExpression -> (usage.lValue as? GrReferenceExpression)?.lValueReference?.advancedResolve()
        is GrConstructorInvocation -> usage.advancedResolve()
        else -> (usage as? GrCall)?.advancedResolve()
      }
      val methodResult = resolveResult as? GroovyMethodResult
      val candidate = methodResult?.candidate
      candidate?.argumentMapping?.expectedTypes?.forEach { (_, argument) ->
        run {
          val param = mapping[candidate.argumentMapping?.targetParameter(argument)?.name] ?: return@run
          val argtype = argument.type
          val correctArgumentType = when {
            argtype.isTypeParameter() -> PsiIntersectionType.createIntersection(
              *argtype.typeParameter()!!.extendsListTypes.takeIf { it.isNotEmpty() } ?: arrayOf(argtype))
            else -> argtype
          }
          setConstraints(param.type, correctArgumentType ?: PsiType.NULL, dependentTypes, requiredTypesCollector,
                         method.typeParameters.toSet())
        }
      }
    }
  }

  companion object {

    private fun <K, V> MutableMap<K, MutableList<V>>.safePut(key: K, value: V) = computeIfAbsent(key) { mutableListOf() }.add(value)


    private fun expandWildcards(type: PsiType, context: PsiElement): List<PsiType> = when (type) {
      is PsiWildcardType -> when {
        type.isSuper -> listOf(type.superBound, getJavaLangObject(context))
        type.isExtends -> listOf(type.extendsBound, PsiType.NULL)
        else -> listOf(getJavaLangObject(context), PsiType.NULL)
      }
      else -> listOf(type)
    }


    fun setConstraints(leftType: PsiType, rightType: PsiType,
                       dependentTypes: MutableSet<PsiTypeParameter>,
                       requiredTypesCollector: MutableMap<PsiTypeParameter, MutableList<BoundConstraint>>,
                       variableParameters: Set<PsiTypeParameter>) {
      if (rightType == PsiType.NULL) {
        return
      }
      val leftBound = if (leftType.isTypeParameter()) {
        val typeParameter = leftType.typeParameter()!!
        val correctRightType = if (rightType.typeParameter() !in variableParameters) {
          rightType
        }
        else {
          dependentTypes.add(typeParameter)
          dependentTypes.add(rightType.typeParameter()!!)
          rightType.typeParameter()!!.extendsListTypes.firstOrNull() ?: return
        }
        val typeSet = expandWildcards(correctRightType, typeParameter)
        typeSet.forEach { requiredTypesCollector.safePut(typeParameter, BoundConstraint(it, LOWER)) }
        typeSet.forEach { requiredTypesCollector.safePut(typeParameter, BoundConstraint(it, INHABIT)) }
        val newLeftType = leftType.typeParameter()!!.extendsListTypes.firstOrNull() ?: return
        newLeftType
      }
      else {
        leftType
      }
      if (leftType is PsiArrayType && rightType is PsiArrayType) {
        setConstraints(leftType.componentType, rightType.componentType, dependentTypes, requiredTypesCollector, variableParameters)
      }
      (leftBound as? PsiClassType)?.parameters?.zip((rightType as? PsiClassType)?.parameters ?: return)?.forEach {
        setConstraints(it.first ?: return, it.second ?: return, dependentTypes, requiredTypesCollector, variableParameters)
      }
    }
  }

  fun runAnalyzer(method: GrMethod) {
    method.accept(this)
    method.block?.statements?.lastOrNull()?.apply { processExitExpression(this as? GrExpression ?: return@apply) }
  }

  private fun processExitExpression(expression: GrExpression) {
    val returnType = expression.parentOfType<GrMethod>()?.returnType?.takeIf { it != PsiType.NULL && it != PsiType.VOID } ?: return
    constraintsCollector.add(ExpressionConstraint(returnType, expression))
    val typeParameter = expression.type.typeParameter() ?: return
    contravariantTypesCollector.add(typeParameter.type())
    covariantTypesCollector.add(typeParameter.type())
    addRequiredType(typeParameter, BoundConstraint(returnType, UPPER))
  }


  fun buildUsageInformation(): TypeUsageInformation =
    TypeUsageInformation(contravariantTypesCollector, requiredTypesCollector, constraintsCollector, covariantTypesCollector, dependentTypes)
}
