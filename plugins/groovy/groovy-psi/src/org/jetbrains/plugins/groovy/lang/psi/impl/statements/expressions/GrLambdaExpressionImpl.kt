// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl

class GrLambdaExpressionImpl(node: ASTNode): GrExpressionImpl(node), GrLambdaExpression {
  override fun getParameters(): Array<GrParameter> {
    return parameterList.parameters
  }

  override fun getParameterList(): GrParameterList {
    return findChildByClass(GrParameterListImpl::class.java)!!
  }

  override fun accept(visitor: GroovyElementVisitor) {
    visitor.visitLambdaExpression(this)
  }

  override fun isVarArgs(): Boolean {
    return false
  }

  override fun getBody(): PsiElement? {
    val body = lastChild
    return if (body is GrExpression || body is GrCodeBlock) body else null
  }

  override fun toString(): String {
    return "Lambda expression"
  }
}