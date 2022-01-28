// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrExpressionLambdaBody
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction

class GrExpressionLambdaBodyImpl(node: ASTNode) : GroovyPsiElementImpl(node), GrExpressionLambdaBody {
  override fun getReturnType(): PsiType? = expression.type

  override fun getLambdaExpression(): GrLambdaExpression = requireNotNull(parentOfType())

  override fun getExpression() : GrExpression = findNotNullChildByClass(GrExpression::class.java)

  override fun accept(visitor: GroovyElementVisitor) = visitor.visitExpressionLambdaBody(this)

  override fun toString(): String = "Lambda body"

  override fun getControlFlow(): Array<Instruction> = ControlFlowUtils.getGroovyControlFlow(this).flow

  override fun isTopControlFlowOwner(): Boolean = true
}
