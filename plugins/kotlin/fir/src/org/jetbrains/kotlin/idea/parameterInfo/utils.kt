// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtApplicableCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.calls.KtCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.psi.KtExpression

context(KtAnalysisSession)
internal val KtCallCandidateInfo.withMapping: CandidateWithMapping
    get() {
        val functionCall = candidate as KtFunctionCall<*>
        return CandidateWithMapping(
            functionCall.partiallyAppliedSymbol.signature,
            functionCall.argumentMapping,
            isApplicableBestCandidate = this is KtApplicableCallCandidateInfo && this.isInBestCandidates,
            token,
        )
    }

internal data class CandidateWithMapping(
    val candidate: KtFunctionLikeSignature<KtFunctionLikeSymbol>,
    val argumentMapping: LinkedHashMap<KtExpression, KtVariableLikeSignature<KtValueParameterSymbol>>,
    val isApplicableBestCandidate: Boolean,
    override val token: KtLifetimeToken,
) : KtLifetimeOwner