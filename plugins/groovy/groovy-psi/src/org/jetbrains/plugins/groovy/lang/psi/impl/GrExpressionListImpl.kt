// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiListLikeElement
import com.intellij.psi.util.childrenOfType
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrExpressionList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

class GrExpressionListImpl(node: ASTNode) : GroovyPsiElementImpl(node), GrExpressionList, PsiListLikeElement {

  override fun toString(): String = "Expression list"

  override fun accept(visitor: GroovyElementVisitor): Unit = visitor.visitExpressionList(this)

  override fun getExpressions(): List<GrExpression> = childrenOfType()

  override fun getComponents(): List<PsiElement> = expressions
}
