// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.OK
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.lang.resolve.references.GrIndexPropertyReference
import kotlin.LazyThreadSafetyMode.NONE


internal class RecursiveMethodAnalyzer(val method: GrMethod) : GroovyRecursiveElementVisitor() {
  val builder = TypeUsageInformationBuilder(method)

  private fun processMethod(result: GroovyResolveResult, arguments: Arguments = emptyList()) {
    val methodResult = result as? GroovyMethodResult ?: return
    val candidate = methodResult.candidate
    if (candidate != null) {
      processReceiverConstraints(candidate)
      val expectedTypes = candidate.argumentMapping?.expectedTypes ?: return
      for ((type, argument) in expectedTypes) {
        processArgumentConstraints(type, argument, methodResult)
      }
    }
    else {
      val method = methodResult.element.run { if (this is GrGdkMethod) staticMethod else this }
      val parameterTypes = method.parameters.mapNotNull { it.type as? PsiType }.takeIf { it.size == arguments.size } ?: return
      for ((type, argument) in parameterTypes.zip(arguments)) {
        processArgumentConstraints(type, argument, methodResult)
      }
    }
  }

  private fun processReceiverConstraints(candidate: GroovyMethodCandidate) {
    val receiverTypeParameter = candidate.smartReceiver()?.typeParameter() ?: return
    val containingType = candidate.smartContainingType() ?: return
    builder.generateRequiredTypes(receiverTypeParameter, containingType, UPPER)
  }

  private fun processArgumentConstraints(parameterType: PsiType, argument: Argument, resolveResult: GroovyMethodResult) {
    val argumentTypes = when (argument) {
      is ExpressionArgument ->
        unwrapElvisExpression(argument.expression).flatMap { it.type?.flattenComponents() ?: emptyList() }
      else -> argument.type?.flattenComponents() ?: emptyList()
    }.filterNotNull()
    val erasureSubstitutor = lazy(NONE) { methodTypeParametersErasureSubstitutor(resolveResult.element) }
    val callContextSubstitutor = resolveResult.substitutor
    for (argtype in argumentTypes) {
      val upperType = callContextSubstitutor.substitute(parameterType).run {
        if (argtype == this) callContextSubstitutor.substitute(erasureSubstitutor.value.substitute(parameterType)) else this
      }
      processRequiredParameters(argtype, upperType)
    }
  }

  private fun PsiType.flattenComponents() =
    when (this) {
      is PsiIntersectionType -> conjuncts.asIterable()
      else -> listOf(this)
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
          if (classType != getJavaLangObject(context) && lowerTypeParameter != null) {
            builder.generateRequiredTypes(lowerTypeParameter, classType, UPPER)
          }
          if (upperTypeParameter != null) {
            builder.generateRequiredTypes(upperTypeParameter, currentLowerType, LOWER)
          }
        }
        else {
          if (lowerTypeParameter != null) {
            builder.generateRequiredTypes(lowerTypeParameter, classType, EQUAL)
          }
          if (upperTypeParameter != null) {
            builder.generateRequiredTypes(upperTypeParameter, currentLowerType, EQUAL)
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
            builder.generateRequiredTypes(lowerTypeParameter, bound, UPPER)
          }
          if (upperTypeParameter != null) {
            builder.generateRequiredTypes(upperTypeParameter, currentLowerType, LOWER)
          }
        }
        else if (wildcardType.isSuper) {
          if (lowerTypeParameter != null) {
            builder.generateRequiredTypes(lowerTypeParameter, bound, LOWER)
          }
          if (upperTypeParameter != null) {
            builder.generateRequiredTypes(upperTypeParameter, currentLowerType, UPPER)
          }
        }
        visitClassParameters(bound)
        super.visitWildcardType(wildcardType)
      }

      override fun visitPrimitiveType(primitiveType: PsiPrimitiveType?) {
        primitiveType?.getBoxedType(context)?.accept(this)
      }
    })
  }


  override fun visitCallExpression(callExpression: GrCallExpression) {
    processMethod(callExpression.advancedResolve())
    builder.addConstrainingExpression(callExpression)
    super.visitCallExpression(callExpression)
  }

  override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
    builder.addConstrainingExpression(expression)
    val lValueReference = (expression.lValue as? GrReferenceExpression)?.lValueReference
    if (lValueReference != null) {
      processSetter(lValueReference)
      processFieldAssignment(lValueReference, expression)
    }
    super.visitAssignmentExpression(expression)
  }

  private fun processSetter(setterReference: GroovyReference) {
    val accessorResult = setterReference.advancedResolve() as? GroovyMethodResult ?: return
    processMethod(accessorResult)
  }

  private fun processFieldAssignment(fieldReference: GroovyReference, expression: GrAssignmentExpression) {
    val fieldResult = fieldReference.resolve() as? GrField ?: return
    val leftType = fieldResult.type
    val rightExpressions = unwrapElvisExpression(expression.rValue)
    for (rightExpression in rightExpressions) {
      val rightType = rightExpression.type ?: continue
      processRequiredParameters(rightType, leftType)
    }
  }

  private fun unwrapElvisExpression(expression: GrExpression?): List<GrExpression> =
    when (expression) {
      null -> emptyList()
      is GrConditionalExpression -> listOfNotNull(expression.thenBranch, expression.elseBranch).flatMap { unwrapElvisExpression(it) }
      else -> listOf(expression)
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
    }
    super.visitVariableDeclaration(variableDeclaration)
  }


  override fun visitExpression(expression: GrExpression) {
    if (expression is GrOperatorExpression) {
      val operatorMethodResolveResult = expression.reference?.advancedResolve()
      if (operatorMethodResolveResult != null) {
        processMethod(operatorMethodResolveResult)
      }
      builder.addConstrainingExpression(expression)
    }
    if (expression is GrIndexProperty) {
      expression.lValueReference?.advancedResolve()?.run {
        val lValueArguments = extractArguments(expression, expression.lValueReference as? GrIndexPropertyReference)
        processMethod(this, lValueArguments)
      }
      expression.rValueReference?.advancedResolve()?.run {
        val rValueArguments = if (element is GrGdkMethod) {
          extractArguments(expression, expression.rValueReference as? GrIndexPropertyReference)
        }
        else {
          extractArguments(null, expression.rValueReference as? GrIndexPropertyReference)
        }
        processMethod(this, rValueArguments)
      }
    }
    super.visitExpression(expression)
  }

  private fun extractArguments(expression: GrIndexProperty?, reference: GrIndexPropertyReference?): Arguments =
    listOfNotNull(expression?.invokedExpression?.run { ExpressionArgument(this) }, *reference?.arguments?.toTypedArray() ?: emptyArray())

  fun visitOuterCalls(originalMethod: GrMethod, calls: Collection<PsiReference>) {
    val mapping = originalMethod.parameters.map { it.name }.zip(method.parameters).toMap()
    processCallInitializers()
    for (outerCall in calls.mapNotNull { it.element.parent }) {
      val candidate = (outerCall.properResolve() as? GroovyMethodResult)?.candidate ?: continue
      val argumentMapping = candidate.argumentMapping ?: continue
      argumentMapping.arguments.forEach { argument ->
        val param = mapping[argumentMapping.targetParameter(argument)?.name] ?: return@forEach
        processOuterArgument(argument, param)
      }
    }
  }

  private fun processCallInitializers() {
    for (parameter in method.parameters) {
      val initializerType = parameter.initializerGroovy?.type ?: continue
      induceDeepConstraints(parameter.type, initializerType, builder, method, INHABIT)
    }
  }

  private fun processOuterArgument(argument: Argument, parameter: GrParameter) {
    val argtype = argument.type ?: return
    val correctArgumentType = argtype.typeParameter()?.upperBound() ?: argtype
    induceDeepConstraints(parameter.type, correctArgumentType, builder, method, INHABIT)
  }

  companion object {


    fun induceDeepConstraints(leftType: PsiType,
                              rightType: PsiType,
                              builder: TypeUsageInformationBuilder,
                              method: GrMethod,
                              targetMarker: ContainMarker) {
      val variableParameters = method.typeParameters.toSet()
      val leftTypeParameter = leftType.typeParameter()
      val rightTypeParameter = rightType.typeParameter()
      if (leftTypeParameter != null && rightTypeParameter != null && rightTypeParameter in variableParameters) {
        builder.run {
          addDependentType(leftTypeParameter)
          addDependentType(rightTypeParameter)
        }
        rightTypeParameter.extendsListTypes.firstOrNull()
      }
      else if (leftTypeParameter != null) {
        builder.generateRequiredTypes(leftTypeParameter, rightType, targetMarker)
      }
      if (leftType is PsiArrayType && rightType is PsiArrayType) {
        induceDeepConstraints(leftType.componentType, rightType.componentType, builder, method, targetMarker)
      }
      val leftBound = leftTypeParameter?.upperBound() ?: leftType
      val rightBound = rightTypeParameter?.upperBound() ?: rightType
      val leftTypeArguments = (leftBound as? PsiClassType)?.parameters ?: return
      val rightTypeArguments = (rightBound as? PsiClassType)?.parameters ?: return
      val rangedRightTypeArguments = rightTypeArguments.map {
        val typeParameter = it.typeParameter()
        when {
          it == null -> PsiWildcardType.createUnbounded(method.manager)
          typeParameter != null && typeParameter !in variableParameters -> typeParameter.upperBound()
          else -> it
        }
      }.toTypedArray()
      for ((leftTypeArgument, rightTypeArgument) in leftTypeArguments.zip(rangedRightTypeArguments)) {
        leftTypeArgument ?: continue
        induceDeepConstraints(leftTypeArgument, rightTypeArgument, builder, method, targetMarker)
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
    builder.addConstrainingExpression(expression)
    val typeParameter = expression.type.typeParameter() ?: return
    builder.generateRequiredTypes(typeParameter, returnType, UPPER)
  }

  fun buildUsageInformation(): TypeUsageInformation = builder.build()
}
