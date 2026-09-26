// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.util.isLValue
import org.jetbrains.plugins.groovy.lang.psi.util.isRValue
import org.jetbrains.plugins.groovy.lang.resolve.references.GrAttributeExpressionReference

class GrAttributeExpressionImpl(node: ASTNode) : GrReferenceExpressionImpl(node), GrReferenceExpression {

  private val attributeReference by lazy {
    GrAttributeExpressionReference(this)
  }

  override fun getRValueReference(): GroovyReference? = if (isRValue()) attributeReference else null

  override fun getLValueReference(): GroovyReference? = if (isLValue()) attributeReference else null

  override fun resolve(incomplete: Boolean): Collection<GroovyResolveResult> = attributeReference.resolve(incomplete)

  override fun lrResolve(rValue: Boolean): Collection<GroovyResolveResult> = resolve(false)

  override fun hasAt(): Boolean = true

  override fun toString(): String = "${javaClass.simpleName}(${node.elementType})"
}
