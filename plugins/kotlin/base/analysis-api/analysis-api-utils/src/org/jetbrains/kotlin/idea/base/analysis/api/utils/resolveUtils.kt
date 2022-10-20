// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFileSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

// Analogous to Call.resolveCandidates() in plugins/kotlin/core/src/org/jetbrains/kotlin/idea/core/Utils.kt
fun KtAnalysisSession.collectCallCandidates(callElement: KtElement): List<KtCallCandidateInfo> {
    val (candidates, explicitReceiver) = when (callElement) {
        is KtCallElement -> {
            val explicitReceiver = callElement.getQualifiedExpressionForSelector()?.receiverExpression
            callElement.collectCallCandidates() to explicitReceiver
        }

        is KtArrayAccessExpression -> callElement.collectCallCandidates() to callElement.arrayExpression
        else -> return emptyList()
    }

    if (candidates.isEmpty()) return emptyList()
    val fileSymbol = callElement.containingKtFile.getFileSymbol()

    return candidates.filter { filterCandidate(it, callElement, fileSymbol, explicitReceiver) }
}

private fun KtAnalysisSession.filterCandidate(
    candidateInfo: KtCallCandidateInfo,
    callElement: KtElement,
    fileSymbol: KtFileSymbol,
    explicitReceiver: KtExpression?
): Boolean {
    val candidateCall = candidateInfo.candidate
    if (candidateCall !is KtFunctionCall<*>) return false
    val signature = candidateCall.partiallyAppliedSymbol.signature
    return filterCandidate(signature, callElement, fileSymbol, explicitReceiver)
}

fun KtAnalysisSession.filterCandidate(
    signature: KtFunctionLikeSignature<KtFunctionLikeSymbol>,
    callElement: KtElement,
    fileSymbol: KtFileSymbol,
    explicitReceiver: KtExpression?
): Boolean {
    val candidateSymbol: KtSymbol = signature.symbol
    if (callElement is KtConstructorDelegationCall) {
        // Exclude caller from candidates for `this(...)` delegated constructor calls.
        // The parent of KtDelegatedConstructorCall should be the KtConstructor. We don't need to get the symbol for the constructor
        // to determine if it's a self-call; we can just compare the candidate's PSI.
        val candidatePsi = candidateSymbol.psi
        if (candidatePsi != null && candidatePsi == callElement.parent) {
            return false
        }
    }

    if (candidateSymbol is KtCallableSymbol) {
        // We want only the candidates that match the receiver type. E.g., if you have code like this:
        // ```
        // fun String.foo() {}
        // fun Int.foo() {}
        // fun call(i: Int?) {
        //   <expr>i?.foo()</expr>
        // }
        // ```
        // The available candidates are `String.foo()` and `Int.foo()`. When checking the receiver types for safe calls, we want to compare
        // the non-nullable receiver type against the candidate receiver type. E.g., that `Int` (and not the type of `i` which is `Int?`)
        // is subtype of `Int` (the candidate receiver type).
        val receiverTypes = if (explicitReceiver != null) {
            val isSafeCall = explicitReceiver.parent is KtSafeQualifiedExpression

            val explicitReceiverType = explicitReceiver.getKtType() ?: error("Receiver should have a KtType")
            val adjustedType = if (isSafeCall) {
                explicitReceiverType.withNullability(KtTypeNullability.NON_NULLABLE)
            } else {
                explicitReceiverType
            }

            listOf(adjustedType)
        } else {
            val scopeContext = callElement.containingKtFile.getScopeContextForPosition(callElement)

            scopeContext.implicitReceivers.map { it.type }
        }

        val candidateReceiverType = signature.receiverType
        if (candidateReceiverType != null && receiverTypes.none { it.isSubTypeOf(candidateReceiverType) }) return false
    }

    // Filter out candidates not visible from call site
    if (candidateSymbol is KtSymbolWithVisibility && !isVisible(candidateSymbol, fileSymbol, explicitReceiver, callElement)) return false

    return true
}