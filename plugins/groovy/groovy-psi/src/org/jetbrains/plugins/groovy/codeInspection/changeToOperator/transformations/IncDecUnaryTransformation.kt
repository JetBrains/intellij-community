// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker.expressionsAreEquivalent
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

class IncDecUnaryTransformation(private val operator: IElementType) : Transformation() {

  override fun needParentheses(methodCall: GrMethodCall, options: ChangeToOperatorInspection.Options): Boolean = false

  override fun couldApplyInternal(methodCall: GrMethodCall, options: ChangeToOperatorInspection.Options): Boolean {
    val base = getBase(methodCall) ?: return false
    if (!checkArgumentsCount(methodCall, 0)) return false
    val assignment = methodCall.parent as? GrAssignmentExpression ?: return false
    if (assignment.rValue !== methodCall) return false
    return expressionsAreEquivalent(assignment.lValue, base)
  }

  override fun apply(methodCall: GrMethodCall, options: ChangeToOperatorInspection.Options) {
    val base = getBase(methodCall) ?: return
    val parent = methodCall.parent as? GrAssignmentExpression ?: return
    replaceExpression(parent, "$operator${base.text}")
  }
}
