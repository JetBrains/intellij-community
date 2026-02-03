// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.resolution.KaApplicableCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.psi.KtExpression

context(session: KaSession)
internal val KaCallCandidateInfo.withMapping: CandidateWithMapping
    get() {
        val functionCall = candidate as KaFunctionCall<*>
        return CandidateWithMapping(
            functionCall.partiallyAppliedSymbol.signature,
            functionCall.argumentMapping,
            isApplicableBestCandidate = this is KaApplicableCallCandidateInfo && this.isInBestCandidates,
            this@withMapping.token,
        )
    }

internal data class CandidateWithMapping(
    val candidate: KaFunctionSignature<KaFunctionSymbol>,
    val argumentMapping: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>>,
    val isApplicableBestCandidate: Boolean,
    override val token: KaLifetimeToken,
) : KaLifetimeOwner