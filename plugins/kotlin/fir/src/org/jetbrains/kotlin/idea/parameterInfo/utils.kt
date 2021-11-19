// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.psi.*

// Analogous to Call.resolveCandidates() in plugins/kotlin/core/src/org/jetbrains/kotlin/idea/core/Utils.kt
internal fun KtAnalysisSession.resolveCallCandidates(callElement: KtElement): List<CandidateWithMapping> {
    // TODO: FE 1.0 plugin collects all candidates (i.e., all overloads), even if arguments do not match. Not just resolved call.
    // See Call.resolveCandidates() in core/src/org/jetbrains/kotlin/idea/core/Utils.kt. Note `replaceCollectAllCandidates(true)`.

    val (resolvedCall, receiver) = when (callElement) {
        is KtCallElement -> {
            val parent = callElement.parent
            val receiver = if (parent is KtDotQualifiedExpression && parent.selectorExpression == callElement) {
                parent.receiverExpression
            } else null
            Pair(callElement.resolveCall(), receiver)
        }
        is KtArrayAccessExpression -> Pair(callElement.resolveCall(), callElement.arrayExpression)
        else -> return emptyList()
    }
    if (resolvedCall == null) return emptyList()


    val fileSymbol = callElement.containingKtFile.getFileSymbol()
    return resolvedCall.targetFunction.candidates.filter { filterCandidate(it, callElement, fileSymbol, receiver) }.map {
        // TODO: The argument mapping and substitutor should also be per-candidate once we have all candidates available.
        CandidateWithMapping(it, resolvedCall.argumentMapping, resolvedCall.substitutor)
    }
}

internal fun KtAnalysisSession.filterCandidate(
    candidateSymbol: KtSymbol,
    callElement: KtElement,
    fileSymbol: KtFileSymbol,
    receiver: KtExpression?
): Boolean {
    if (callElement is KtConstructorDelegationCall) {
        // Exclude caller from candidates for `this(...)` delegated constructor calls.
        // The parent of KtDelegatedConstructorCall should be the KtConstructor. We don't need to get the symbol for the constructor
        // to determine if it's a self-call; we can just compare the candidate's PSI.
        val candidatePsi = candidateSymbol.psi
        if (candidatePsi != null && candidatePsi == callElement.parent) {
            return false
        }
    }

    if (receiver != null && candidateSymbol is KtCallableSymbol) {
        // Filter out candidates with wrong receiver
        val receiverType = receiver.getKtType() ?: error("Receiver should have a KtType")
        val candidateReceiverType = candidateSymbol.receiverType
        if (candidateReceiverType != null && receiverType.isNotSubTypeOf(candidateReceiverType)) return false
    }

    // Filter out candidates not visible from call site
    if (candidateSymbol is KtSymbolWithVisibility && !isVisible(candidateSymbol, fileSymbol, receiver, callElement)) return false

    return true
}

internal data class CandidateWithMapping(
    val candidate: KtFunctionLikeSymbol,
    val argumentMapping: LinkedHashMap<KtExpression, KtValueParameterSymbol>,
    val substitutor: KtSubstitutor,
)