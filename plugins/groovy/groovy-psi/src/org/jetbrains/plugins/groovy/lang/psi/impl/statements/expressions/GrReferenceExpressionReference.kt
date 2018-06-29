// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyReferenceBase
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolver

class GrReferenceExpressionReference(
  private val ref: GrReferenceExpressionImpl,
  private val forceRValue: Boolean
) : GroovyReferenceBase<GrReferenceExpressionImpl>(ref) {

  override fun resolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    return TypeInferenceHelper.getCurrentContext().resolve(this, incomplete, Resolver)
  }

  private object Resolver : GroovyResolver<GrReferenceExpressionReference> {
    override fun resolve(ref: GrReferenceExpressionReference, incomplete: Boolean): Collection<GroovyResolveResult> {
      return ref.ref.doPolyResolve(incomplete, ref.forceRValue)
    }
  }
}
