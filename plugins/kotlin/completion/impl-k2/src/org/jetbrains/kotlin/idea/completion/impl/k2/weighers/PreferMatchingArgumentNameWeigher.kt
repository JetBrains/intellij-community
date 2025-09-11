// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectCallCandidates
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.completionSessionProperty
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.UserDataProperty

internal object PreferMatchingArgumentNameWeigher {
    private const val WEIGHER_ID = "kotlin.preferMatchingArgumentName"

    private var K2CompletionSectionContext<*>.callCandidates: List<KaFunctionCall<*>>? by completionSessionProperty()

    context(_: KaSession, scopeContext: K2CompletionSectionContext<*>)
    private fun initializeOrGetCallCandidates(nameExpression: KtSimpleNameExpression): List<KaFunctionCall<*>> {
        scopeContext.callCandidates?.let { return it }

        val candidates: List<KaFunctionCall<*>> = run {

            val valueArgument = findValueArgument(nameExpression) ?: return emptyList()

            val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return@run emptyList()

            val callElement = valueArgumentList.parent as? KtCallElement ?: return@run emptyList()

            collectCallCandidates(callElement)
                .mapNotNull { it.candidate as? KaFunctionCall<*> }
        }

        scopeContext.callCandidates = candidates

        return candidates
    }

    @Serializable
    enum class Weight {
        MATCHING_NAME,
        CONTAINS_NAME,
        UNRELATED,
    }

    private fun String.getMatchingWeight(variableName: String): Weight {
        if (equals(variableName, ignoreCase = true)) return Weight.MATCHING_NAME
        if (contains(variableName, ignoreCase = true)) return Weight.CONTAINS_NAME
        if (variableName.contains(this, ignoreCase = true)) return Weight.CONTAINS_NAME
        return Weight.UNRELATED
    }

    context(_: KaSession, scopeContext: K2CompletionSectionContext<*>)
    fun addWeight(element: LookupElement) {
        val nameExpression = scopeContext.positionContext.position.parent as? KtSimpleNameExpression ?: return

        val candidates = initializeOrGetCallCandidates(nameExpression)

        if (candidates.isEmpty()) return

        val availableNames = candidates.mapNotNull { it.argumentMapping[nameExpression]?.name }
        if (availableNames.isEmpty()) return

        val bestMatch = availableNames.minOf { it.asString().getMatchingWeight(element.lookupString) }
        if (bestMatch != Weight.UNRELATED) {
            element.matchingArgumentName = bestMatch
        }
    }

    private var LookupElement.matchingArgumentName: Weight? by UserDataProperty(Key("KOTLIN_MATCHING_ARGUMENT_NAME_WEIGHT"))

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*> = element.matchingArgumentName ?: Weight.UNRELATED
    }
}
