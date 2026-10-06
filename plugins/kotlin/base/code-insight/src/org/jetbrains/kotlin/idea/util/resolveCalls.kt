// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallResolutionAttempt
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleOrMultiCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.resolution.symbols
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveSymbols
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.resolution.KtResolvable
import org.jetbrains.kotlin.resolution.KtResolvableCall

@KaExperimentalApi
context(session: KaSession)
fun KtExpression.resolveSuccessfulExpressionCall(): KaSimpleOrMultiCall? =
    (this as? KtResolvableCall)?.resolveSuccessfulCall()

@KaExperimentalApi
context(session: KaSession)
fun KtExpression.tryResolveExpressionCall(): KaCallResolutionAttempt? =
    (this as? KtResolvableCall)?.tryResolveCall()

////// resolve symbol

@KaExperimentalApi
context(session: KaSession)
fun KtExpression.resolveSuccessfulExpressionSymbol(): KaSymbol? =
    (this as? KtResolvable)?.resolveSuccessfulSymbol()

/**
 * Resolves [this] expression to the symbols of its calls, or to its referenced symbols when the calls give no symbols.
 * The result includes the candidates of red code.
 *
 * The call symbols come first, so the callee of a constructor call gives the constructor, not the class.
 * An operation reference gives only the symbols of its operator.
 */
@KaExperimentalApi
context(session: KaSession)
fun KtExpression.tryResolveSymbolsCallFirst(): List<KaSymbol> {
    val callSymbols = (this as? KtResolvableCall)?.takeUnless { it is KtOperationReferenceExpression }
        ?.tryResolveCall()
        ?.calls
        ?.flatMap(KaSimpleOrMultiCall::symbols)
        ?.takeUnless(List<KaSymbol>::isEmpty)

    return callSymbols ?: (this as? KtResolvable)?.tryResolveSymbols()?.symbols.orEmpty()
}
