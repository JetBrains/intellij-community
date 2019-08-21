// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.*
import org.jetbrains.plugins.groovy.intentions.style.inference.properResolve
import org.jetbrains.plugins.groovy.intentions.style.inference.recursiveSubstitute
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.upperBound
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
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
  private val constraintsCollector = mutableListOf<ConstraintFormula>()
  private val variableTypeParameters = method.typeParameters
  private val dependentTypes = mutableSetOf<PsiTypeParameter>()
  private val javaLangObject = getJavaLangObject(method)

  private fun generateRequiredTypes(typeParameter: PsiTypeParameter, type: PsiType, marker: ContainMarker) {
    val bindingTypes = expandWildcards(type, typeParameter)
    bindingTypes.forEach { addRequiredType(typeParameter, BoundConstraint(it, marker)) }
  }

  private fun addRequiredType(typeParameter: PsiTypeParameter, constraint: BoundConstraint) {
    if (typeParameter in variableTypeParameters && !constraint.type.equalsToText(GROOVY_OBJECT)) {
      val constraintTypeParameter = constraint.type.typeParameter()
      if (constraintTypeParameter != null && constraintTypeParameter in variableTypeParameters) {
        dependentTypes.add(typeParameter)
        dependentTypes.add(constraintTypeParameter)
      }
      else {
        requiredTypesCollector.safePut(typeParameter, constraint)
      }
    }
  }

  private fun processMethod(result: GroovyResolveResult) {
    val methodResult = result as? GroovyMethodResult ?: return
    val candidate = methodResult.candidate ?: return
    processReceiverConstraints(candidate)
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
        val lowerTypeParameter = currentLowerType.typeParameter()
        val candidateLowerTypes = lowerTypeParameter?.extendsListTypes?.asList() ?: listOf(currentLowerType)
        val matchedLowerBound = candidateLowerTypes.find { lowerType ->
          TypesUtil.canAssign(currentUpperType.rawType(), lowerType.rawType(), context, METHOD_PARAMETER) == OK
        } ?: return
        for (classParameterIndex in currentUpperType.parameters.indices) {
          currentLowerType = matchedLowerBound.parameters.getOrNull(classParameterIndex) as? PsiClassType ?: continue
          currentUpperType.parameters[classParameterIndex].accept(this)
        }
      }

      override fun visitClassType(classType: PsiClassType?) {
        classType ?: return
        val lowerTypeParameter = currentLowerType.typeParameter()
        val upperTypeParameter = classType.typeParameter()
        if (firstVisit) {
          if (classType != javaLangObject && lowerTypeParameter != null) {
            generateRequiredTypes(lowerTypeParameter, classType, UPPER)
          }
          if (upperTypeParameter != null) {
            generateRequiredTypes(upperTypeParameter, currentLowerType, LOWER)
          }
        }
        else {
          if (lowerTypeParameter != null) {
            generateRequiredTypes(lowerTypeParameter, classType, EQUAL)
          }
          if (upperTypeParameter != null) {
            generateRequiredTypes(upperTypeParameter, currentLowerType, EQUAL)
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
        val bound = wildcardType.bound as? PsiClassType ?: return
        val lowerTypeParameter = currentLowerType.typeParameter()
        val upperTypeParameter = bound.typeParameter()
        if (wildcardType.isExtends) {
          if (lowerTypeParameter != null) {
            generateRequiredTypes(lowerTypeParameter, bound, UPPER)
          }
          if (upperTypeParameter != null) {
            generateRequiredTypes(upperTypeParameter, currentLowerType, LOWER)
          }
        }
        else if (wildcardType.isSuper) {
          if (lowerTypeParameter != null) {
            generateRequiredTypes(lowerTypeParameter, bound, LOWER)
          }
          if (upperTypeParameter != null) {
            generateRequiredTypes(upperTypeParameter, currentLowerType, UPPER)
          }
        }
        visitClassParameters(bound)
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
    constraintsCollector.add(ExpressionConstraint(null, expression))
    val lValueReference = (expression.lValue as? GrReferenceExpression)?.lValueReference ?: return
    processSetter(lValueReference)
    processFieldAssignment(lValueReference, expression)
  }

  private fun processSetter(setterReference: GroovyReference) {
    val accessorResult = setterReference.advancedResolve() as? GroovyMethodResult ?: return
    val mapping = accessorResult.candidate?.argumentMapping ?: return
    for ((expectedType, argument) in mapping.expectedTypes) {
      val argumentType = argument.type ?: continue
      processRequiredParameters(argumentType, expectedType)
    }
  }

  private fun processFieldAssignment(fieldReference: GroovyReference, expression: GrAssignmentExpression) {
    val fieldResult = fieldReference.resolve() as? GrField ?: return
    val leftType = fieldResult.type
    val rightType = expression.rValue?.type ?: return
    processRequiredParameters(rightType, leftType)
    constraintsCollector.add(TypeConstraint(leftType, rightType, method))
  }

  override fun visitReturnStatement(returnStatement: GrReturnStatement) {
    returnStatement.returnValue?.apply { processExitExpression(this) }
    super.visitReturnStatement(returnStatement)
  }

  override fun visitVariableDeclaration(variableDeclaration: GrVariableDeclaration) {
    for (variable in variableDeclaration.variables) {
      val initializer = variable.initializerGroovy ?: continue
      val initializerType = initializer.type ?: continue
      processRequiredParameters(initializerType, variable.type)
      constraintsCollector.add(ExpressionConstraint(variable.declaredType, initializer))
    }
    super.visitVariableDeclaration(variableDeclaration)
  }


  override fun visitExpression(expression: GrExpression) {
    if (expression is GrOperatorExpression) {
      val operatorMethodResolveResult = expression.reference?.advancedResolve()
      if (operatorMethodResolveResult != null) {
        processMethod(operatorMethodResolveResult)
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
    processCallInitializers()
    val calls = ReferencesSearch.search(originalMethod, scope).findAll()
    for (outerCall in calls.mapNotNull { it.element.parent }) {
      val candidate = (outerCall.properResolve() as? GroovyMethodResult)?.candidate ?: continue
      val argumentMapping = candidate.argumentMapping ?: continue
      argumentMapping.expectedTypes.forEach { (_, argument) ->
        val param = mapping[argumentMapping.targetParameter(argument)?.name] ?: return@forEach
        processOuterArgument(argument, param)
      }
    }
  }

  private fun processCallInitializers() {
    for (parameter in method.parameters) {
      val initializerType = parameter.initializerGroovy?.type ?: continue
      induceDeepConstraints(parameter.type,
                            initializerType, dependentTypes, requiredTypesCollector,
                            method.typeParameters.toSet(), INHABIT)
    }
  }

  private fun processOuterArgument(argument: Argument, parameter: GrParameter) {
    val argtype = argument.type ?: return
    val correctArgumentType = argtype.typeParameter()?.upperBound() ?: argtype
    induceDeepConstraints(parameter.type, correctArgumentType, dependentTypes, requiredTypesCollector, method.typeParameters.toSet(),
                          INHABIT)
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


    fun induceDeepConstraints(leftType: PsiType, rightType: PsiType,
                              dependentTypes: MutableSet<PsiTypeParameter>,
                              requiredTypesCollector: MutableMap<PsiTypeParameter, MutableList<BoundConstraint>>,
                              variableParameters: Set<PsiTypeParameter>,
                              targetMarker: ContainMarker) {
      val leftTypeParameter = leftType.typeParameter()
      val rightTypeParameter = rightType.typeParameter()
      if (leftTypeParameter != null && rightTypeParameter != null && rightTypeParameter in variableParameters) {
        dependentTypes.run {
          add(leftTypeParameter)
          add(rightTypeParameter)
        }
        rightTypeParameter.extendsListTypes.firstOrNull()
      }
      else if (leftTypeParameter != null) {
        val typeSet = expandWildcards(rightType, leftTypeParameter)
        typeSet.forEach { requiredTypesCollector.safePut(leftTypeParameter, BoundConstraint(it, targetMarker)) }
      }
      if (leftType is PsiArrayType && rightType is PsiArrayType) {
        induceDeepConstraints(leftType.componentType, rightType.componentType, dependentTypes, requiredTypesCollector, variableParameters,
                              targetMarker)
      }
      val leftBound = leftTypeParameter?.upperBound() ?: leftType
      val rightBound = rightTypeParameter?.upperBound() ?: rightType
      val leftTypeArguments = (leftBound as? PsiClassType)?.parameters ?: return
      val rightTypeArguments = (rightBound as? PsiClassType)?.parameters ?: return
      for ((leftTypeArgument, rightTypeArgument) in leftTypeArguments.zip(rightTypeArguments)) {
        leftTypeArgument ?: continue
        rightTypeArgument ?: continue
        induceDeepConstraints(leftTypeArgument, rightTypeArgument, dependentTypes, requiredTypesCollector, variableParameters, targetMarker)
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
    addRequiredType(typeParameter, BoundConstraint(returnType, UPPER))
  }


  fun buildUsageInformation(): TypeUsageInformation =
    TypeUsageInformation(requiredTypesCollector, constraintsCollector, dependentTypes)
}
