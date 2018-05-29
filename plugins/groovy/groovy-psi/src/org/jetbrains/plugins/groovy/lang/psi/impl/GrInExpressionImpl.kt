// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_NOT
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrInExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl

class GrInExpressionImpl(node: ASTNode) : GrBinaryExpressionImpl(node), GrInExpression {

  override fun getNegationToken(): PsiElement? = findChildByType(T_NOT)

  override fun accept(visitor: GroovyElementVisitor): Unit = visitor.visitInExpression(this)

  override fun toString(): String = "${javaClass.simpleName}(${node.elementType})"
}
