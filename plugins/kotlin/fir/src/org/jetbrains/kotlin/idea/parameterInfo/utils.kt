// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.psi.*

// Analogous to Call.resolveCandidates() in plugins/kotlin/core/src/org/jetbrains/kotlin/idea/core/Utils.kt
internal fun KtAnalysisSession.resolveCallCandidates(callElement: KtElement): Collection<CandidateWithMapping> {
    // TODO: FE 1.0 plugin collects all candidates (i.e., all overloads), even if arguments do not match. Not just resolved call.
    // See Call.resolveCandidates() in core/src/org/jetbrains/kotlin/idea/core/Utils.kt. Note `replaceCollectAllCandidates(true)`.

    val (resolvedCall, receiver) = when (callElement) {
        is KtCallElement -> {
            val parent = callElement.parent
            val receiver = if (parent is KtDotQualifiedExpression && parent.selectorExpression == callElement) {
                parent.receiverExpression
            } else {
                null
            }
            Pair(callElement.resolveCall(), receiver)
        }
        is KtArrayAccessExpression -> Pair(callElement.resolveCall(), callElement.arrayExpression)
        else -> return emptyList()
    }
    if (resolvedCall == null) return emptyList()


    val fileSymbol = callElement.containingKtFile.getFileSymbol()
    return resolvedCall.targetFunction.candidates.filter { candidateSymbol ->
        // TODO: Filter out candidates with wrong receiver

        // Filter out candidates not visible from call site
        if (candidateSymbol is KtSymbolWithVisibility && !isVisible(candidateSymbol, fileSymbol, receiver, callElement)) return@filter false

        true
    }.map {
        // TODO: The argument mapping should also be per-candidate once we have all candidates available.
        CandidateWithMapping(it, resolvedCall.argumentMapping)
    }
}

internal data class CandidateWithMapping(
    val candidate: KtFunctionLikeSymbol,
    val argumentMapping: LinkedHashMap<KtExpression, KtValueParameterSymbol>
)