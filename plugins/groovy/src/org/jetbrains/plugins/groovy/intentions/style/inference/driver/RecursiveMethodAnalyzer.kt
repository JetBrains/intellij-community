// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.*
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.recursiveSubstitute
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.upperBound
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.OK
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.ApplicableTo.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_OBJECT
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*
import kotlin.LazyThreadSafetyMode.NONE


internal class RecursiveMethodAnalyzer(val method: GrMethod) : GroovyRecursiveElementVisitor() {
  private val requiredTypesCollector = mutableMapOf<PsiTypeParameter, MutableList<BoundConstraint>>()
  private val contravariantTypesCollector = mutableSetOf<PsiType>()
  private val covariantTypesCollector = mutableSetOf<PsiType>()
  private val constraintsCollector = mutableListOf<ConstraintFormula>()
  private val typeParameters = method.typeParameters.map { it.type() }
  private val dependentTypes = mutableSetOf<PsiTypeParameter>()
  private val javaLangObject = getJavaLangObject(method)

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
    val methodResult = result as? GroovyMethodResult ?: return
    val candidate = methodResult.candidate ?: return
    processReceiverConstraints(candidate)
    processReturnTypeConstraints(methodResult, candidate.method)
    val expectedTypes = candidate.argumentMapping?.expectedTypes ?: return
    for ((type, argument) in expectedTypes) {
      processArgumentConstraints(type, argument, methodResult)
    }
  }

  private fun processReceiverConstraints(candidate: GroovyMethodCandidate) {
    val receiverTypeParameter = candidate.smartReceiver()?.typeParameter() ?: return
    val containingType = candidate.smartContainingType() ?: return
    generateRequiredTypes(receiverTypeParameter, containingType, UPPER)
  }

  private fun processReturnTypeConstraints(methodResult: GroovyMethodResult, method: PsiMethod) {
    methodResult.substitutor.substitute(method.returnType)?.run { covariantTypesCollector.add(this) }
  }

  private fun processArgumentConstraints(parameterType: PsiType, argument: Argument, resolveResult: GroovyMethodResult) {
    val argumentTypes = when (val argtype = argument.type) {
      is PsiIntersectionType -> argtype.conjuncts
      else -> arrayOf(argtype)
    }.filterNotNull()
    val erasureSubstitutor = lazy(NONE) { methodTypeParametersErasureSubstitutor(resolveResult.element) }
    val callContextSubstitutor = resolveResult.substitutor
    argumentTypes.forEach { argtype ->
      val upperType = callContextSubstitutor.substitute(parameterType).run {
        if (argtype == this) callContextSubstitutor.substitute(erasureSubstitutor.value.substitute(parameterType)) else this
      }
      processRequiredParameters(argtype, upperType)
    }
    resolveResult.contextSubstitutor.substitute(parameterType)?.run { contravariantTypesCollector.add(this) }
  }


  /**
   * Visits every parameter of [lowerType] (which is probably parameterized with type parameters) and sets restrictions found in [upperType]
   * We need to distinguish containing and subtyping relations, so this is why there are [UPPER] and [EQUAL] bounds
   */
  private fun processRequiredParameters(lowerType: PsiType, upperType: PsiType) {
    var currentLowerType = lowerType as? PsiClassType ?: return
    var firstVisit = true
    val context = lowerType.resolve()?.context ?: return
    upperType.accept(object : PsiTypeVisitor<Unit>() {

      fun visitClassParameters(currentUpperType: PsiClassType) {
        val candidateLowerTypes = if (currentLowerType.isTypeParameter()) {
          currentLowerType.typeParameter()!!.extendsListTypes.asList()
        }
        else {
          listOf(currentLowerType)
        }
        val matchedLowerBound =
          candidateLowerTypes.find { lowerType ->
            TypesUtil.canAssign(currentUpperType.rawType(), lowerType.rawType(), context, METHOD_PARAMETER) == OK
          } ?: return
        for (classParameterIndex in currentUpperType.parameters.indices) {
          currentLowerType = matchedLowerBound.parameters.getOrNull(classParameterIndex) as? PsiClassType ?: continue
          currentUpperType.parameters[classParameterIndex].accept(this)
        }
      }

      override fun visitClassType(classType: PsiClassType?) {
        classType ?: return
        if (firstVisit) {
          if (classType != javaLangObject && currentLowerType.isTypeParameter()) {
            generateRequiredTypes(currentLowerType.typeParameter()!!, classType, UPPER)
          }
          if (classType.isTypeParameter()) {
            generateRequiredTypes(classType.typeParameter()!!, currentLowerType, LOWER)
          }
        }
        else {
          if (currentLowerType.isTypeParameter()) {
            generateRequiredTypes(currentLowerType.typeParameter()!!, classType, EQUAL)
          }
        }
        // firstVisit is necessary because java generics are invariant
        firstVisit = false
        visitClassParameters(classType)
        super.visitClassType(classType)
      }

      override fun visitIntersectionType(intersectionType: PsiIntersectionType?) {
        intersectionType ?: return
        val memorizedFirstVisit = firstVisit
        intersectionType.conjuncts.forEach {
          firstVisit = memorizedFirstVisit
          it.accept(this)
        }
      }

      override fun visitWildcardType(wildcardType: PsiWildcardType?) {
        wildcardType ?: return
        if (wildcardType.isExtends) {
          val extendsBound = wildcardType.extendsBound as PsiClassType
          if (currentLowerType.isTypeParameter()) {
            generateRequiredTypes(currentLowerType.typeParameter()!!, extendsBound, UPPER)
          }
          visitClassParameters(extendsBound)
        }
        else if (wildcardType.isSuper) {
          val superBound = wildcardType.superBound as PsiClassType
          if (currentLowerType.isTypeParameter()) {
            generateRequiredTypes(currentLowerType.typeParameter()!!, superBound, LOWER)
          }
          visitClassParameters(superBound)
        }
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
        processRequiredParameters(argument.type ?: return@forEach, type)
      }
      val fieldResult = lValueReference?.resolve() as? GrField
      if (fieldResult != null) {
        val leftType = fieldResult.type
        val rightType = expression.rValue?.type
        processRequiredParameters(rightType ?: return@run, leftType)
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
      processRequiredParameters(it.initializerGroovy?.type ?: return@forEach, it.type)
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

  fun visitOuterCalls(originalMethod: GrMethod, scope: SearchScope) {
    val mapping = originalMethod.parameters.map { it.name }.zip(method.parameters).toMap()
    val calls = ReferencesSearch.search(originalMethod, scope).findAll()
    for (parameter in method.parameters) {
      setConstraints(parameter.type, parameter.initializerGroovy?.type ?: continue, dependentTypes, requiredTypesCollector,
                     method.typeParameters.toSet(), INHABIT)
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
                         method.typeParameters.toSet(), INHABIT)
        }
      }
    }
  }

  companion object {

    private fun <K, V> MutableMap<K, MutableList<V>>.safePut(key: K, value: V) = computeIfAbsent(key) { mutableListOf() }.add(value)


    private fun expandWildcards(type: PsiType, context: PsiElement): List<PsiType> =
      when (type) {
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
                       variableParameters: Set<PsiTypeParameter>,
                       targetMarker: ContainMarker) {
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
        //typeSet.forEach { requiredTypesCollector.safePut(typeParameter, BoundConstraint(it, LOWER)) }
        typeSet.forEach { requiredTypesCollector.safePut(typeParameter, BoundConstraint(it, targetMarker)) }
        val newLeftType = leftType.typeParameter()!!.extendsListTypes.firstOrNull() ?: return
        newLeftType
      }
      else {
        leftType
      }
      if (leftType is PsiArrayType && rightType is PsiArrayType) {
        setConstraints(leftType.componentType, rightType.componentType, dependentTypes, requiredTypesCollector, variableParameters,
                       targetMarker)
      }
      (leftBound as? PsiClassType)?.parameters?.zip((rightType as? PsiClassType)?.parameters ?: return)?.forEach {
        setConstraints(it.first ?: return, it.second ?: return, dependentTypes, requiredTypesCollector, variableParameters, targetMarker)
      }
    }

    fun methodTypeParametersErasureSubstitutor(method: PsiMethod): PsiSubstitutor {
      val typeParameters = method.typeParameters
      val bounds = typeParameters.map { it.upperBound() }
      val draftSubstitutor = PsiSubstitutor.EMPTY.putAll(typeParameters, bounds.toTypedArray())
      val completeBounds = typeParameters.map { draftSubstitutor.recursiveSubstitute(it.type()) }
      return PsiSubstitutor.EMPTY.putAll(typeParameters, completeBounds.toTypedArray())
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
