// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.ValidityTokenOwner
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion

internal class WeighingContext private constructor(
    override val token: ValidityToken,
    private val myExpectedType: KtType?,
) : ValidityTokenOwner {
    val expectedType: KtType?
        get() = withValidityAssertion {
            myExpectedType
        }

    fun withoutExpectedType(): WeighingContext = withValidityAssertion { WeighingContext(token, null) }

    companion object {
        fun KtAnalysisSession.createWeighingContext(expectedType: KtType?) = WeighingContext(token, expectedType)
        fun KtAnalysisSession.empty(project: Project) = WeighingContext(token, null)
    }
}

internal object Weighers {
    fun KtAnalysisSession.applyWeighsToLookupElement(context: WeighingContext, lookupElement: LookupElement, symbol: KtSymbol) {
        with(ExpectedTypeWeigher) { addWeight(context, lookupElement, symbol) }
        with(DeprecatedWeigher) { addWeight(lookupElement, symbol) }
        with(PreferGetSetMethodsToPropertyWeigher) { addWeight(lookupElement, symbol) }
        with(KindWeigher) { addWeight(lookupElement, symbol) }
    }

    fun addWeighersToCompletionSorter(sorter: CompletionSorter): CompletionSorter =
        sorter
            .weighBefore(
                PlatformWeighersIds.STATS,
                ExpectedTypeWeigher.Weigher,
                DeprecatedWeigher.Weigher,
                PreferGetSetMethodsToPropertyWeigher.Weigher,
                KindWeigher.Weigher,
            )
            .weighBefore(
                PlatformWeighersIds.STATS,
                PriorityWeigher.Weigher,
                ExpectedTypeWeigher.Weigher
            )
            .weighBefore(ExpectedTypeWeigher.WEIGHER_ID, CompletionContributorGroupWeigher.Weigher)

    private object PlatformWeighersIds {
        const val PREFIX = "prefix"
        const val STATS = "stats"
    }
}