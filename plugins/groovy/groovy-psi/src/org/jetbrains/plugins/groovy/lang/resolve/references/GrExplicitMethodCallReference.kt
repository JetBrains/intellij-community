// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.references

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.rValueProcessor
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.resolveKinds
import org.jetbrains.plugins.groovy.lang.resolve.GrReferenceResolveRunner
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCachingReference
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference
import org.jetbrains.plugins.groovy.lang.resolve.impl.filterByArgumentsCount
import org.jetbrains.plugins.groovy.lang.resolve.impl.filterBySignature
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments
import org.jetbrains.plugins.groovy.lang.resolve.impl.resolveIncomplete
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodProcessor

class GrExplicitMethodCallReference(call: GrMethodCall) : GroovyCachingReference<GrMethodCall>(call),
                                                          GroovyMethodCallReference {

  override val receiverArgument: Argument get() = TODO()
  override val methodName: String get() = requireNotNull((element.invokedExpression as GrReferenceExpression).referenceName)
  override val arguments: Arguments? get() = element.getArguments()

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val ref = element.invokedExpression as GrReferenceExpression
    if (incomplete) {
      return ref.resolveIncomplete()
    }
    val methodName = methodName
    val arguments = arguments

    val methodProcessor = MethodProcessor(methodName, ref, arguments, ref.typeArguments)
    GrReferenceResolveRunner(ref, methodProcessor).resolveReferenceExpression()
    methodProcessor.applicableCandidates?.let {
      return it
    }

    val propertyProcessor = rValueProcessor(methodName, ref, ref.resolveKinds())
    GrReferenceResolveRunner(ref, propertyProcessor).resolveReferenceExpression()
    val properties = propertyProcessor.results
    if (properties.size == 1) {
      return properties
    }
    val methods = filterBySignature(filterByArgumentsCount(methodProcessor.allCandidates, arguments))
    return methods + properties
  }
}
