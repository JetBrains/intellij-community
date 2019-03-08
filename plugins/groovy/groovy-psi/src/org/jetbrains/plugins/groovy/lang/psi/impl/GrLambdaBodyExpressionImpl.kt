// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaBody
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder

class GrLambdaBodyExpressionImpl(node: ASTNode) : GroovyPsiElementImpl(node), GrLambdaBody {
  override fun getReturnType(): PsiType? = getExpression().type

  override fun getLambdaExpression(): GrLambdaExpression = requireNotNull(parentOfType())

  fun getExpression() : GrExpression = findNotNullChildByClass(GrExpression::class.java)

  override fun accept(visitor: GroovyElementVisitor) = visitor.visitLambdaBody(this)

  override fun toString(): String = "Lambda body"

  override fun getControlFlow(): Array<Instruction> = ControlFlowBuilder(project).buildControlFlow(this)

  override fun isTopControlFlowOwner(): Boolean = true

  override fun addStatementBefore(statement: GrStatement, anchor: GrStatement?): GrStatement {
    throw IncorrectOperationException("Can't add statement in a single expression form of body")
  }

  override fun getStatements(): Array<GrStatement> = arrayOf(getExpression())
}
