// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.ValidityTokenOwner
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiver
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.psi.KtExpression

internal class WeighingContext private constructor(
    override val token: ValidityToken,
    val explicitReceiver: KtExpression?,
    private val myExpectedType: KtType?,
    private val myImplicitReceivers: List<KtImplicitReceiver>,
) : ValidityTokenOwner {
    val expectedType: KtType?
        get() = withValidityAssertion {
            myExpectedType
        }

    /** All implicit receivers in the current resolution context. The receiver declared in the inner most scope appears first. */
    val implicitReceiver: List<KtImplicitReceiver>
        get() = withValidityAssertion {
            return myImplicitReceivers
        }

    fun withoutExpectedType(): WeighingContext =
        withValidityAssertion {
            WeighingContext(token, explicitReceiver, null, myImplicitReceivers)
        }

    companion object {
        fun KtAnalysisSession.createWeighingContext(
            receiver: KtExpression?,
            expectedType: KtType?,
            implicitReceivers: List<KtImplicitReceiver>,
        ) = WeighingContext(token, receiver, expectedType, implicitReceivers)

        fun KtAnalysisSession.empty() = WeighingContext(token, null, null, emptyList())
    }
}

internal object Weighers {
    fun KtAnalysisSession.applyWeighsToLookupElement(
        context: WeighingContext,
        lookupElement: LookupElement,
        symbol: KtSymbol,
        substitutor: KtSubstitutor = KtSubstitutor.Empty(token)
    ) {
        with(ExpectedTypeWeigher) { addWeight(context, lookupElement, symbol) }
        with(DeprecatedWeigher) { addWeight(lookupElement, symbol) }
        with(PreferGetSetMethodsToPropertyWeigher) { addWeight(lookupElement, symbol) }
        with(KindWeigher) { addWeight(lookupElement, symbol) }
        with(CallableWeigher) { addWeight(context, lookupElement, symbol, substitutor) }
        with(VariableOrFunctionWeigher) { addWeight(lookupElement, symbol) }
    }

    fun addWeighersToCompletionSorter(sorter: CompletionSorter): CompletionSorter =
        sorter
            .weighBefore(
                PlatformWeighersIds.STATS,
                ExpectedTypeWeigher.Weigher,
                DeprecatedWeigher.Weigher,
                PriorityWeigher.Weigher,
                PreferGetSetMethodsToPropertyWeigher.Weigher,
                KindWeigher.Weigher,
                CallableWeigher.Weigher,
            )
            .weighAfter(PlatformWeighersIds.STATS, VariableOrFunctionWeigher.Weigher)
            .weighBefore(ExpectedTypeWeigher.WEIGHER_ID, CompletionContributorGroupWeigher.Weigher)

    private object PlatformWeighersIds {
        const val PREFIX = "prefix"
        const val STATS = "stats"
    }
}