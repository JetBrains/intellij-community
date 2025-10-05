// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import com.intellij.psi.codeStyle.NameUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectCallCandidates
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.completionSessionProperty
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupObject
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

    private const val WEIGHT_MATCHING_NAME = 0.0f
    private const val WEIGHT_MATCHING_ALL_PARTS = 0.01f
    private const val WEIGHT_UNRELATED = 1.0f

    // Lower number means matching more
    private fun calcNameSimilarity(parameterName: String, variableName: String): Float {
        if (parameterName.equals(variableName, ignoreCase = true)) return WEIGHT_MATCHING_NAME

        val parameterNameParts = NameUtil.nameToWordsLowerCase(parameterName)
        val variableNameParts = NameUtil.nameToWordsLowerCase(variableName)

        fun isNonNumber(word: String) = !word[0].isDigit()
        val matchedWords =  parameterNameParts.intersect(variableNameParts).filter { it.isNotBlank() && isNonNumber(it) }
        if (matchedWords.isEmpty()) return WEIGHT_UNRELATED

        val matchingPercentage = matchedWords.size.toFloat() / parameterNameParts.size.toFloat()
        val weight = 1f - matchingPercentage

        if (weight <= WEIGHT_MATCHING_NAME) return WEIGHT_MATCHING_ALL_PARTS
        return weight
    }

    context(_: KaSession, scopeContext: K2CompletionSectionContext<*>)
    fun addWeight(element: LookupElement) {
        if (element.`object` is NamedArgumentLookupObject) return
        val nameExpression = scopeContext.positionContext.position.parent as? KtSimpleNameExpression ?: return

        val candidates = initializeOrGetCallCandidates(nameExpression)

        if (candidates.isEmpty()) return

        val availableNames = candidates.mapNotNull { it.argumentMapping[nameExpression]?.name }
        if (availableNames.isEmpty()) return

        val bestMatch = availableNames.minOf { calcNameSimilarity(it.asString(), element.lookupString) }
        if (bestMatch != WEIGHT_UNRELATED) {
            element.matchingArgumentName = bestMatch
        }
    }

    private var LookupElement.matchingArgumentName: Float? by UserDataProperty(Key("KOTLIN_MATCHING_ARGUMENT_NAME_WEIGHT"))

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*> = element.matchingArgumentName ?: WEIGHT_UNRELATED
    }
}
