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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

class GroovyInferenceSessionBuilder(private val ref: PsiElement, private val candidate: MethodCandidate) {

  private var closureSkipList = mutableListOf<GrMethodCall>()

  private var skipClosureBlock = true

  private var startFromTop = false

  fun resolveMode(skipClosureBlock: Boolean): GroovyInferenceSessionBuilder {
    this.skipClosureBlock = skipClosureBlock
    return this
  }

  fun startFromTop(startFromTop: Boolean): GroovyInferenceSessionBuilder {
    this.startFromTop = startFromTop
    return this
  }

  fun skipClosureIn(call: GrMethodCall): GroovyInferenceSessionBuilder {
    closureSkipList.add(call)
    return this
  }

  fun build(): GroovyInferenceSession {
    if (startFromTop) {
      val session = GroovyInferenceSession(PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY, ref, closureSkipList, skipClosureBlock)
      val methodCall = ref.parent as? GrMethodCall ?: return session
      val mostTopLevelCall = getMostTopLevelCall(methodCall)
      val left = getReturnConstraintType(mostTopLevelCall)
      session.addConstraint(ExpressionConstraint(left, mostTopLevelCall))
      return session
    }
    else {
      val session = GroovyInferenceSession(
        candidate.method.typeParameters, candidate.siteSubstitutor, ref, closureSkipList, skipClosureBlock
      )
      if (ref is GrReferenceExpression) {
        session.addConstraint(ArgumentsConstraint(candidate, ref))
      }
      return session
    }
  }

  private fun getMostTopLevelCall(call: GrMethodCall): GrMethodCall {
    var topLevel: GrMethodCall = call
    while (true) {
      val parent = topLevel.parent
      val grandParent = parent?.parent
      topLevel = if (parent is GrMethodCall) {
        parent
      }
      else if (parent is GrArgumentList && grandParent is GrMethodCall) {
        grandParent
      }
      else {
        return topLevel
      }
    }
  }

  private fun getReturnConstraintType(call: GrMethodCall): PsiType? {
    val parent = call.parent
    val grandParent = parent?.parent
    val parentMethod = PsiTreeUtil.getParentOfType(parent, GrMethod::class.java, true, GrClosableBlock::class.java)

    if (parent is GrReturnStatement && parentMethod != null) {
      return parentMethod.returnType
    }
    else if (isExitPoint(call) && parentMethod != null) {
      val returnType = parentMethod.returnType
      if (TypeConversionUtil.isVoidType(returnType)) return null
      return returnType
    }
    else if (parent is GrAssignmentExpression && call == parent.rValue) {
      val lValue = PsiUtil.skipParentheses(parent.lValue, false)
      return if (lValue is GrExpression && lValue !is GrIndexProperty) lValue.nominalType else null
    }
    else if (parent is GrArgumentList && grandParent is GrNewExpression) { // TODO: fix with moving constructor resolve to new API
      with(grandParent) {
        val resolveResult = advancedResolve()
        if (resolveResult is GroovyMethodResult) {
          val methodCandidate = MethodCandidate(resolveResult.element, resolveResult.partialSubstitutor,
                                                grandParent.getArguments(), call)
          return methodCandidate.argumentMapping[ExpressionArgument(call)]?.second
        }
      }
      return null
    }
    else if (parent is GrVariable) {
      return parent.declaredType
    }
    return null
  }

  private fun isExitPoint(place: GrMethodCall): Boolean {
    return collectExitPoints(place).contains(place)
  }

  private fun collectExitPoints(place: GrMethodCall): List<GrStatement> {
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
}