// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl

class GrRangeExpressionImpl(node: ASTNode) : GrExpressionImpl(node), GrRangeExpression {

  override fun getFrom(): GrExpression = findNotNullChildByClass(GrExpression::class.java)

  override fun getTo(): GrExpression? = lastChild as? GrExpression

  override fun accept(visitor: GroovyElementVisitor) = visitor.visitRangeExpression(this)

  override fun toString(): String = "Range expression"
}
