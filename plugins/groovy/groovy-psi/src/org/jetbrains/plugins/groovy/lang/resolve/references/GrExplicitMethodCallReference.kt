// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCachingReference
import org.jetbrains.plugins.groovy.lang.resolve.resolveReferenceExpression

class GrExplicitMethodCallReference(ref: GrReferenceExpressionImpl) : GroovyCachingReference<GrReferenceExpressionImpl>(ref) {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    element.referenceName ?: return emptyList()
    return element.resolveReferenceExpression(incomplete)
  }
}
