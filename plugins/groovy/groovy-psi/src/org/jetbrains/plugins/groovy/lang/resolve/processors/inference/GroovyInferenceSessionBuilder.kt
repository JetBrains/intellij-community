// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
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
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

class GroovyInferenceSessionBuilder(val ref: GrReferenceExpression, val candidate: MethodCandidate) {

  private var left: PsiType? = null

  private var skipClosureBlock = true

  private var startFromTop = false

  private var siteTypeParams: Array<PsiTypeParameter> = PsiTypeParameter.EMPTY_ARRAY

  fun resolveMode(skipClosureBlock: Boolean): GroovyInferenceSessionBuilder {
    this.skipClosureBlock = skipClosureBlock
    return this
  }

  fun startFromTop(startFromTop: Boolean): GroovyInferenceSessionBuilder {
    this.startFromTop = startFromTop
    return this
  }

  fun addReturnConstraint(returnType: PsiType?): GroovyInferenceSessionBuilder {
    left = returnType
    return this
  }

  fun addReturnConstraint(): GroovyInferenceSessionBuilder {
    val methodCall = ref.parent as? GrMethodCall ?: return this
    left = getReturnConstraintType(getMostTopLevelCall(methodCall))
    return this
  }

  fun addTypeParams(typeParams: Array<PsiTypeParameter>): GroovyInferenceSessionBuilder {
    siteTypeParams = ArrayUtil.mergeArrays(siteTypeParams, typeParams)
    return this
  }

  fun build(): GroovyInferenceSession {
    if (!startFromTop) {
      val typeParameters = ArrayUtil.mergeArrays(siteTypeParams, candidate.method.typeParameters)
      val session = GroovyInferenceSession(typeParameters, candidate.siteSubstitutor, ref, skipClosureBlock)
      session.addConstraint(MethodCallConstraint(ref, candidate))

      val returnType = PsiUtil.getSmartReturnType(candidate.method) //TODO: Fix with startFromTop in GroovyResolveProcessor
      val left = left
      if (left == null || returnType == null || PsiType.VOID == returnType) return session
      session.repeatInferencePhases()
      session.addConstraint(TypeConstraint(left, returnType, ref))
      return session
    } else {
      val session = GroovyInferenceSession(siteTypeParams, PsiSubstitutor.EMPTY, ref, skipClosureBlock)
      val methodCall = ref.parent as? GrMethodCall ?: return session
      session.addConstraint(ExpressionConstraint(getMostTopLevelCall(methodCall), left))
      return session
    }
  }

  private fun getMostTopLevelCall(call: GrMethodCall): GrMethodCall {
    var topLevel: GrMethodCall = call
    while (true) {
      val parent = topLevel.parent
      val gparent = parent?.parent
      topLevel = if (parent is GrMethodCall) {
        parent
      } else if (parent is GrArgumentList && gparent is GrMethodCall) {
        gparent
      } else {
        return topLevel
      }
    }
  }

  private fun getReturnConstraintType(call: GrMethodCall): PsiType? {
    val parent = call.parent
    val gparent = parent?.parent
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
    } else if (parent is GrArgumentList && gparent is GrNewExpression) { // TODO: fix with moving constructor resolve to new API
      val resolveResult = gparent.advancedResolve()
      val parameters = GrClosureSignatureUtil.mapArgumentsToParameters(
        resolveResult,
        gparent,
        false,
        true,
        gparent.namedArguments,
        gparent.expressionArguments,
        gparent.closureArguments)
      return parameters?.get(call)?.second
    } else if (parent is GrVariable) {
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