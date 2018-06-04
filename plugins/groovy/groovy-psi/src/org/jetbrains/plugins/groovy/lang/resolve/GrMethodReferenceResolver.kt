// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMethodReferenceExpressionImpl
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodReferenceProcessor

internal object GrMethodReferenceResolver : GroovyResolver<GrMethodReferenceExpressionImpl> {

  override fun resolve(ref: GrMethodReferenceExpressionImpl, incomplete: Boolean): Collection<GroovyResolveResult> {
    val name = ref.referenceName ?: return emptyList()
    val type = ref.qualifier?.type ?: return emptyList()
    val processor = MethodReferenceProcessor(name)
    type.processReceiverType(processor, ResolveState.initial(), ref)
    return processor.results
  }
}
