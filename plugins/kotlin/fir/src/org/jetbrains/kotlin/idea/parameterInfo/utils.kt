// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KtApplicableCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KtCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.psi.KtExpression

context(KaSession)
internal val KtCallCandidateInfo.withMapping: CandidateWithMapping
    get() {
        val functionCall = candidate as KaFunctionCall<*>
        return CandidateWithMapping(
            functionCall.partiallyAppliedSymbol.signature,
            functionCall.argumentMapping,
            isApplicableBestCandidate = this is KtApplicableCallCandidateInfo && this.isInBestCandidates,
            token,
        )
    }

internal data class CandidateWithMapping(
    val candidate: KaFunctionSignature<KaFunctionSymbol>,
    val argumentMapping: LinkedHashMap<KtExpression, KaVariableSignature<KaValueParameterSymbol>>,
    val isApplicableBestCandidate: Boolean,
    override val token: KaLifetimeToken,
) : KtLifetimeOwner