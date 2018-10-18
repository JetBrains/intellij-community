// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.InferenceContext
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolver
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyReferenceBase
import org.jetbrains.plugins.groovy.lang.resolve.doResolveStatic

class GrStaticExpressionReference(element: GrReferenceExpression) : GroovyReferenceBase<GrReferenceExpression>(element) {

  override fun resolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    // results of this reference doesn't depend on types and inference, and can be cached once and for all
    return InferenceContext.TOP_CONTEXT.resolve(this, false, Resolver)
  }

  private object Resolver : GroovyResolver<GrStaticExpressionReference> {

    override fun resolve(ref: GrStaticExpressionReference, incomplete: Boolean): Collection<GroovyResolveResult> {
      return ref.element.doResolveStatic()?.let(::listOf) ?: emptyList()
    }
  }
}

