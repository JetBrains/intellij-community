// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCachingReference

abstract class GrReferenceExpressionReference(ref: GrReferenceExpressionImpl) : GroovyCachingReference<GrReferenceExpressionImpl>(ref) {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val staticResults = element.staticReference.resolve(incomplete)
    if (staticResults.isNotEmpty()) {
      return staticResults
    }
    return doResolveNonStatic(incomplete)
  }

  abstract fun doResolveNonStatic(incomplete: Boolean): Collection<GroovyResolveResult>
}

class GrRValueExpressionReference(ref: GrReferenceExpressionImpl) : GrReferenceExpressionReference(ref) {

  override fun doResolveNonStatic(incomplete: Boolean): Collection<GroovyResolveResult> {
    return element.doPolyResolve(incomplete, true)
  }
}

class GrLValueExpressionReference(ref: GrReferenceExpressionImpl) : GrReferenceExpressionReference(ref) {

  override fun doResolveNonStatic(incomplete: Boolean): Collection<GroovyResolveResult> {
    return element.doPolyResolve(incomplete, false)
  }
}
