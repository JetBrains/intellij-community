// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.*
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipParentheses
import org.jetbrains.plugins.groovy.lang.resolve.MethodResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getContainingCall

open class GroovyInferenceSessionBuilder constructor(
  private val context: PsiElement,
  private val candidate: GroovyMethodCandidate,
  private val contextSubstitutor: PsiSubstitutor
) {

  protected var expressionFilters = mutableSetOf<ExpressionPredicate>()

  protected var skipClosureBlock = true

  fun resolveMode(skipClosureBlock: Boolean): GroovyInferenceSessionBuilder {
    //TODO:add explicit typed closure constraints
    this.skipClosureBlock = skipClosureBlock
    return this
  }

  fun ignoreArguments(arguments: Collection<Argument>): GroovyInferenceSessionBuilder {
    expressionFilters.add {
      ExpressionArgument(it) !in arguments
    }
    return this
  }

  fun skipClosureIn(call: GrCall): GroovyInferenceSessionBuilder {
    expressionFilters.add {
      it !is GrFunctionalExpression || call != getContainingCall(it)
    }
    return this
  }

  protected fun collectExpressionFilters() {
    if (skipClosureBlock) expressionFilters.add(ignoreFunctionalExpressions)
  }

  open fun build(): GroovyInferenceSession {
    collectExpressionFilters()

    val session = GroovyInferenceSession(candidate.method.typeParameters, contextSubstitutor, context, skipClosureBlock, expressionFilters)
    return doBuild(session)
  }

  protected fun doBuild(basicSession: GroovyInferenceSession): GroovyInferenceSession {
    basicSession.initArgumentConstraints(candidate.argumentMapping)
    return basicSession
  }
}

fun buildTopLevelSession(place: PsiElement): GroovyInferenceSession =
  GroovyInferenceSession(PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY, place, false).apply { addExpression(place) }

fun InferenceSession.addExpression(place : PsiElement) {
  val expression = findExpression(place) ?: return
  val startConstraint = if (expression is GrBinaryExpression || expression is GrAssignmentExpression && expression.isOperatorAssignment) {
    OperatorExpressionConstraint(expression as GrOperatorExpression)
  }
  else if (expression is GrSafeCastExpression && expression.operand !is GrFunctionalExpression) {
    val result = expression.reference.advancedResolve() as? GroovyMethodResult ?: return
    MethodCallConstraint(null, result, expression)
  }
  else {
    val mostTopLevelExpression = getMostTopLevelExpression(expression)
    val typeAndPosition = getExpectedTypeAndPosition(mostTopLevelExpression)
    ExpressionConstraint(typeAndPosition, mostTopLevelExpression)
  }
  addConstraint(startConstraint)
}

fun findExpression(place: PsiElement): GrExpression? {
  val parent = place.parent
  return when {
    parent is GrAssignmentExpression && parent.lValue === place -> parent
    place is GrIndexProperty -> place
    parent is GrMethodCall -> parent
    parent is GrNewExpression -> parent
    parent is GrClassTypeElement -> parent.parent as? GrSafeCastExpression
    place is GrExpression -> place
    else -> null
  }
}

fun getMostTopLevelExpression(start: GrExpression): GrExpression {
  var current: GrExpression = start
  while (true) {
    val parent = current.parent
    current = if (parent is GrArgumentList) {
      val grandParent = parent.parent
      if (grandParent is GrCallExpression && grandParent.advancedResolve() is MethodResolveResult) {
        grandParent
      }
      else {
        return current
      }
    }
    else {
      return current
    }
  }
}

fun getExpectedType(expression: GrExpression): PsiType? {
  return getExpectedTypeAndPosition(expression)?.type
}

private fun getExpectedTypeAndPosition(expression: GrExpression): ExpectedType? {
  return getAssignmentOrReturnExpectedTypeAndPosition(expression)
         ?: getArgumentExpectedType(expression)
}

private fun getAssignmentOrReturnExpectedTypeAndPosition(expression: GrExpression): ExpectedType? {
  return getAssignmentExpectedType(expression)?.let { ExpectedType(it, ASSIGNMENT) }
         ?: getReturnExpectedType(expression)?.let { ExpectedType(it, RETURN_VALUE) }
}

fun getAssignmentOrReturnExpectedType(expression: GrExpression): PsiType? {
  return getAssignmentExpectedType(expression)
         ?: getReturnExpectedType(expression)
}

fun getAssignmentExpectedType(expression: GrExpression): PsiType? {
  val parent: PsiElement = expression.parent
  if (parent is GrAssignmentExpression && expression == parent.rValue) {
    val lValue: PsiElement? = skipParentheses(parent.lValue, false)
    return if (lValue is GrExpression && lValue !is GrIndexProperty) lValue.nominalType else null
  }
  else if (parent is GrVariable) {
    return parent.declaredType
  }
  else if (parent is GrListOrMap) {
    val pParent: PsiElement? = parent.parent
    if (pParent is GrVariableDeclaration && pParent.isTuple) {
      val index: Int = parent.initializers.indexOf(expression)
      return pParent.variables.getOrNull(index)?.declaredType
    }
    else if (pParent is GrTupleAssignmentExpression) {
      val index: Int = parent.initializers.indexOf(expression)
      val expressions: Array<out GrReferenceExpression> = pParent.lValue.expressions
      val lValue: GrReferenceExpression = expressions.getOrNull(index) ?: return null
      val variable: GrVariable? = lValue.staticReference.resolve() as? GrVariable
      return variable?.declaredType
    }
  }
  return null
}

private fun getReturnExpectedType(expression: GrExpression): PsiType? {
  val parent: PsiElement = expression.parent
  val parentMethod: GrMethod? = PsiTreeUtil.getParentOfType(parent, GrMethod::class.java, false, GrFunctionalExpression::class.java)
  if (parentMethod == null) {
    return null
  }
  if (parent is GrReturnStatement) {
    return parentMethod.returnType
  }
  else if (isExitPoint(expression)) {
    val returnType: PsiType = parentMethod.returnType ?: return null
    if (TypeConversionUtil.isVoidType(returnType)) return null
    return returnType
  }
  return null
}

private fun getArgumentExpectedType(expression: GrExpression): ExpectedType? {
  val parent = expression.parent as? GrArgumentList ?: return null
  val call = parent.parent as? GrCallExpression ?: return null
  val result = call.advancedResolve() as? GroovyMethodResult ?: return null
  val mapping = result.candidate?.argumentMapping ?: return null
  val type = result.substitutor.substitute(mapping.expectedType(ExpressionArgument(expression))) ?: return null
  return ExpectedType(type, METHOD_PARAMETER)
}

internal typealias ExpressionPredicate = (GrExpression) -> Boolean

private val ignoreFunctionalExpressions: ExpressionPredicate = { it !is GrFunctionalExpression }

private fun isExitPoint(place: GrExpression): Boolean {
  return collectExitPoints(place).contains(place)
}

private fun collectExitPoints(place: GrExpression): List<GrStatement> {
  return if (canBeExitPoint(place)) {
    val flowOwner = ControlFlowUtils.findControlFlowOwner(place)
    ControlFlowUtils.collectReturns(flowOwner)
  }
  else {
    emptyList()
  }
}

private fun canBeExitPoint(element: PsiElement?): Boolean {
  var place = element
  while (place != null) {
    if (place is GrMethod || place is GrFunctionalExpression || place is GrClassInitializer) return true
    if (place is GrThrowStatement || place is GrTypeDefinitionBody || place is GroovyFile) return false
    place = place.parent
  }
  return false
}
