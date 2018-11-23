// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.resolveKinds
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReferenceBase
import org.jetbrains.plugins.groovy.lang.resolve.processReceiverType
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyRValueProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodProcessor

/**
 * Reference to an implicit `.call`.
 *
 * Appears in when method call expression has non-[reference][GrReferenceExpression] invoked expression,
 * such as `(a + b)()` is actually `(a + b).call()`,
 * or when invoked expression is a [reference][GrReferenceExpression]
 * which is an [implicit receiver][GrReferenceExpression.isImplicitCallReceiver].
 */
class GrImplicitCallReference(element: GrMethodCall) : GroovyMethodCallReferenceBase<GrMethodCall>(element) {

  override val receiver: PsiType? get() = element.invokedExpression.type

  override val methodName: String get() = "call"

  override val arguments: Arguments? get() = element.getArguments()

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val place = element

    val receiver = receiver ?: return emptyList()
    val state = ResolveState.initial()

    val methodProcessor = MethodProcessor(methodName, place, arguments, PsiType.EMPTY_ARRAY)
    receiver.processReceiverType(methodProcessor, state, place)
    methodProcessor.applicableCandidates?.let {
      return it
    }

    val propertyProcessor = GroovyRValueProcessor(methodName, place, resolveKinds(true))
    receiver.processReceiverType(propertyProcessor, state, place)
    val properties = propertyProcessor.results
    if (properties.size == 1) {
      return properties
    }

    val methods = filterBySignature(filterByArgumentsCount(methodProcessor.allCandidates, arguments))
    return methods + properties
  }
}
