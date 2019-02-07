// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCachingReference
import org.jetbrains.plugins.groovy.lang.resolve.initialState
import org.jetbrains.plugins.groovy.lang.resolve.processReceiverType
import org.jetbrains.plugins.groovy.lang.resolve.processSpread
import org.jetbrains.plugins.groovy.lang.resolve.processors.FirstFieldProcessor

class GrAttributeExpressionReference(element: GrReferenceExpression) : GroovyCachingReference<GrReferenceExpression>(element) {

  init {
    require(element.hasAt())
    require(element.isQualified)
  }

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val expression = element
    val attributeName = expression.referenceName ?: return emptyList()
    val receiver = requireNotNull(expression.qualifier).type ?: return emptyList()
    val processor = FirstFieldProcessor(attributeName, expression)
    val state = initialState(false)
    if (expression.dotTokenType == GroovyElementTypes.T_SPREAD_DOT) {
      receiver.processSpread(processor, state, expression)
    }
    else {
      receiver.processReceiverType(processor, state, expression)
    }
    return processor.results
  }
}
