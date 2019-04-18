// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.rValueProcessor
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.resolveKinds
import org.jetbrains.plugins.groovy.lang.resolve.GrReferenceResolveRunner
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCachingReference
import org.jetbrains.plugins.groovy.lang.resolve.impl.filterByArgumentsCount
import org.jetbrains.plugins.groovy.lang.resolve.impl.filterBySignature
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodProcessor

class GrExplicitMethodCallReference(ref: GrReferenceExpressionImpl) : GroovyCachingReference<GrReferenceExpressionImpl>(ref) {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    require(!incomplete)
    val ref = element
    val name = ref.referenceName ?: return emptyList()
    val methodCall = ref.parent as GrMethodCall
    val arguments = methodCall.getArguments()

    val methodProcessor = MethodProcessor(name, ref, arguments, ref.typeArguments)
    GrReferenceResolveRunner(ref, methodProcessor).resolveReferenceExpression()
    methodProcessor.applicableCandidates?.let {
      return it
    }

    val propertyProcessor = rValueProcessor(name, ref, ref.resolveKinds())
    GrReferenceResolveRunner(ref, propertyProcessor).resolveReferenceExpression()
    val properties = propertyProcessor.results
    if (properties.size == 1) {
      return properties
    }
    val methods = filterBySignature(filterByArgumentsCount(methodProcessor.allCandidates, arguments))
    return methods + properties
  }
}
