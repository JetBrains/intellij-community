// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.implCommon.weighers.PreferKotlinClassesWeigher
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSuperReceiverNameReferencePositionContext

internal object Weighers {

    private val weighers: List<KotlinSectionContextWeigher> = listOf(
        ExpectedTypeWeigher,
        KindWeigher,
        DeprecatedWeigher,
        PreferGetSetMethodsToPropertyWeigher,
        NotImportedWeigher,
        ClassifierWeigher,
        VariableOrFunctionWeigher,
        PreferredSubtypeWeigher,
        // Prefer Duration-based overloads for specific time-related APIs
        DurationPreferringWeigher,
        K2SoftDeprecationWeigher,
        PreferContextualCallablesWeigher,
        PreferFewerParametersWeigher,
        PreferMatchingArgumentNameWeigher,
        PreferNamedArgumentCompletionWeigher,
    )

    context(_: KaSession, sectionContext: K2CompletionSectionContext<*>)
    fun <E : LookupElement> E.applyWeighs(
        symbolWithOrigin: KtSymbolWithOrigin<*>? = null,
    ): E = also { lookupElement -> // todo replace everything with apply
        weighers.forEach { it.addWeight(lookupElement, symbolWithOrigin) }
    }

    fun CompletionSorter.applyWeighers(positionContext: KotlinRawPositionContext): CompletionSorter =
        weighBefore(
            PlatformWeighersIds.STATS,
            TrailingLambdaParameterNameWeigher,
            CompletionContributorGroupWeigher,
            PreferNamedArgumentCompletionWeigher,
            ExpectedTypeWeigher,
            DeprecatedWeigher,
            PriorityWeigher,
            PreferredSubtypeWeigher,
            PreferGetSetMethodsToPropertyWeigher,
            NotImportedWeigher,
            PreferMatchingArgumentNameWeigher,
            KindWeigher,
            CallableWeigher,
            ClassifierWeigher,
            PreferAbstractForOverrideWeigher,
        ).weighAfter(
            PlatformWeighersIds.STATS,
            VariableOrFunctionWeigher,
        ).weighBefore(
            PlatformWeighersIds.PREFIX,
            K2SoftDeprecationWeigher,
            VariableOrParameterNameWithTypeWeigher,
        ).weighAfter(
            PlatformWeighersIds.PROXIMITY,
            ByNameAlphabeticalWeigher,
            PreferKotlinClassesWeigher,
            // Prefer Duration-based overloads over Long-based ones for known time-related APIs
            DurationPreferringWeigher,
            PreferFewerParametersWeigher,
            TrailingLambdaWeigher,
        ).weighBefore(
            getBeforeIdForContextualCallablesWeigher(positionContext),
            PreferContextualCallablesWeigher,
        )

    private fun getBeforeIdForContextualCallablesWeigher(positionContext: KotlinRawPositionContext): String =
        when (positionContext) {
            // prefer contextual callable when completing reference after "super."
            is KotlinSuperReceiverNameReferencePositionContext -> ExpectedTypeWeigher.id
            else -> PlatformWeighersIds.PROXIMITY
        }

    private object PlatformWeighersIds {
        const val PREFIX = "prefix"
        const val STATS = "stats"
        const val PROXIMITY = "proximity"
    }
}

internal data class CompoundWeight2<W1 : Comparable<*>, W2 : Comparable<*>>(
    val weight1: W1,
    val weight2: W2
) : Comparable<CompoundWeight2<W1, W2>> {
    override fun compareTo(other: CompoundWeight2<W1, W2>): Int {
        return compareValuesBy(this, other, { it.weight1 }, { it.weight2 })
    }
}

internal data class CompoundWeight3<W1 : Comparable<*>, W2 : Comparable<*>, W3 : Comparable<*>>(
    val weight1: W1,
    val weight2: W2,
    val weight3: W3
) : Comparable<CompoundWeight3<W1, W2, W3>> {
    override fun compareTo(other: CompoundWeight3<W1, W2, W3>): Int {
        return compareValuesBy(this, other, { it.weight1 }, { it.weight2 }, { it.weight3 })
    }
}
