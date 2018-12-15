// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipParentheses
import org.jetbrains.plugins.groovy.lang.resolve.MethodResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate

class GroovyInferenceSessionBuilder constructor(
  private val context: PsiElement,
  private val candidate: GroovyMethodCandidate,
  private val contextSubstitutor: PsiSubstitutor
) {

  private var closureSkipList = mutableListOf<GrMethodCall>()

  private var skipClosureBlock = true

  fun resolveMode(skipClosureBlock: Boolean): GroovyInferenceSessionBuilder {
    this.skipClosureBlock = skipClosureBlock
    return this
  }

  fun skipClosureIn(call: GrMethodCall): GroovyInferenceSessionBuilder {
    closureSkipList.add(call)
    return this
  }

  fun build(): GroovyInferenceSession {
    val session = GroovyInferenceSession(
      candidate.method.typeParameters, contextSubstitutor, context, closureSkipList, skipClosureBlock
    )
    session.initArgumentConstraints(candidate.argumentMapping)
    return session
  }
}

fun buildTopLevelSession(place: PsiElement): GroovyInferenceSession {
  val session = GroovyInferenceSession(PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY, place, emptyList(), false)
  val expression = findExpression(place) ?: return session
  val mostTopLevelExpression = getMostTopLevelExpression(expression)
  val left = getExpectedType(mostTopLevelExpression)
  session.addConstraint(ExpressionConstraint(left, mostTopLevelExpression))
  return session
}

private fun findExpression(place: PsiElement): GrExpression? {
  val parent = place.parent
  return when {
    parent is GrAssignmentExpression && parent.lValue === place -> parent
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
    current = if (parent is GrMethodCall && current in parent.expressionArguments) {
      parent
    }
    else if (parent is GrArgumentList) {
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

private fun getExpectedType(expression: GrExpression): PsiType? {
  val parent = expression.parent
  val parentMethod = PsiTreeUtil.getParentOfType(parent, GrMethod::class.java, true, GrClosableBlock::class.java)

  if (parent is GrReturnStatement && parentMethod != null) {
    return parentMethod.returnType
  }
  else if (isExitPoint(expression) && parentMethod != null) {
    val returnType = parentMethod.returnType
    if (TypeConversionUtil.isVoidType(returnType)) return null
    return returnType
  }
  else if (parent is GrAssignmentExpression && expression == parent.rValue) {
    val lValue = skipParentheses(parent.lValue, false)
    return if (lValue is GrExpression && lValue !is GrIndexProperty) lValue.nominalType else null
  }
  else if (parent is GrVariable) {
    return parent.declaredType
  }
  else if (parent is GrListOrMap) {
    val pParent = parent.parent
    if (pParent is GrVariableDeclaration && pParent.isTuple) {
      val index = parent.initializers.indexOf(expression)
      return pParent.variables.getOrNull(index)?.declaredType
    }
    else if (pParent is GrTupleAssignmentExpression) {
      val index = parent.initializers.indexOf(expression)
      val expressions = pParent.lValue.expressions
      val lValue = expressions.getOrNull(index)
      return (lValue?.staticReference?.resolve() as? GrVariable)?.declaredType
    }
  }
  else if (parent is GrArgumentList) {
    val call = parent.parent as? GrCallExpression ?: return null
    val result = call.advancedResolve() as? GroovyMethodResult ?: return null
    val mapping = result.candidate?.argumentMapping ?: return null
    return result.substitutor.substitute(mapping.expectedType(ExpressionArgument(expression)))
  }

  return null
}

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
    if (place is GrMethod || place is GrClosableBlock || place is GrClassInitializer) return true
    if (place is GrThrowStatement || place is GrTypeDefinitionBody || place is GroovyFile) return false
    place = place.parent
  }
  return false
}
