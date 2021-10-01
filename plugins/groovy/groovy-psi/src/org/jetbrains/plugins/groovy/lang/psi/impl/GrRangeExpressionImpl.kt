// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression.BoundaryType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl

class GrRangeExpressionImpl(node: ASTNode) : GrExpressionImpl(node), GrRangeExpression {

  override fun getFrom(): GrExpression = findNotNullChildByClass(GrExpression::class.java)

  override fun getTo(): GrExpression? = lastChild as? GrExpression

  override fun getBoundaryType(): BoundaryType? {
    val rangeToken : PsiElement? = findChildByType(GroovyTokenSets.RANGES)
    return when (rangeToken.elementType) {
      GroovyElementTypes.T_RANGE -> BoundaryType.CLOSED
      GroovyElementTypes.T_RANGE_LEFT_OPEN -> BoundaryType.LEFT_OPEN
      GroovyElementTypes.T_RANGE_RIGHT_OPEN -> BoundaryType.RIGHT_OPEN
      GroovyElementTypes.T_RANGE_BOTH_OPEN -> BoundaryType.BOTH_OPEN
      else -> null
    }
  }

  override fun accept(visitor: GroovyElementVisitor) = visitor.visitRangeExpression(this)

  override fun toString(): String = "Range expression"
}
