// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiListLikeElement
import com.intellij.psi.PsiType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.childrenOfType
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_LBRACE
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl

class GrArrayInitializerImpl(node: ASTNode) : GrExpressionImpl(node), GrArrayInitializer, PsiListLikeElement {

  override fun toString(): String = "Array initializer"

  override fun accept(visitor: GroovyElementVisitor): Unit = visitor.visitArrayInitializer(this)

  override fun getLBrace(): PsiElement = findNotNullChildByType(T_LBRACE)

  override fun isEmpty(): Boolean = node.getChildren(TokenSet.ANY).none { it.psi is GrExpression }

  override fun getExpressions(): List<GrExpression> = childrenOfType()

  override fun getRBrace(): PsiElement? = findChildByType(GroovyElementTypes.T_RBRACE)

  override fun getComponents(): List<PsiElement> = expressions

  override fun getType(): PsiType? {
    val parentElement = parent
    if (parentElement is GrNewExpression) return parentElement.type
    else if (parentElement is GrArrayInitializer) {
      val parentType = parentElement.type
      if (parentType !is PsiArrayType) return null
      val componentType = parentType.componentType
      return componentType as? PsiArrayType
    }
    return null
  }
}
