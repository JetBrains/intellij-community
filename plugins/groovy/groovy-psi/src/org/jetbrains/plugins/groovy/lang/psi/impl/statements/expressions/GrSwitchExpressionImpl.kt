// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrSwitchElementBase

class GrSwitchExpressionImpl(node : ASTNode) : GrSwitchElementBase(node), GrSwitchExpression {

  override fun accept(visitor: GroovyElementVisitor) {
    visitor.visitSwitchExpression(this)
  }

  override fun getNominalType(): PsiType? = type

  override fun replaceWithExpression(newExpr: GrExpression, removeUnnecessaryParentheses: Boolean): GrExpression? {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses)
  }


  override fun toString(): String {
    return "Switch expression"
  }
}