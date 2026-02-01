// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.NameUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectCallCandidates
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.LazyCompletionSessionProperty
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupObject
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.psi.psiUtil.parents

internal object PreferMatchingArgumentNameWeigher {
    private const val WEIGHER_ID = "kotlin.preferMatchingArgumentName"

    private var K2CompletionSectionContext<*>.callCandidates: List<KaFunctionCall<*>> by LazyCompletionSessionProperty {
        val scopeContext = contextOf<K2CompletionSectionContext<*>>()
        val nameExpression = scopeContext.positionContext.position.parent as? KtSimpleNameExpression

        val valueArgument = nameExpression?.let(::findValueArgument)
        val valueArgumentList = valueArgument?.parent as? KtValueArgumentList
        val callElement = valueArgumentList?.parent as? KtCallElement
        val callCandidates = callElement?.let { collectCallCandidates(it) } ?: emptyList()

        callCandidates.mapNotNull { it.candidate as? KaFunctionCall<*> }
    }

    private const val WEIGHT_MATCHING_NAME = 0.0f
    private const val WEIGHT_MATCHING_ALL_WORDS = 0.01f
    private const val WEIGHT_UNRELATED = 1.0f

    // Lower number means matching more
    private fun calcNameSimilarity(parameterName: String, variableName: String): Float {
        if (parameterName.equals(variableName, ignoreCase = true)) return WEIGHT_MATCHING_NAME

        val parameterNameParts = NameUtil.nameToWordsLowerCase(parameterName)
        val variableNameParts = NameUtil.nameToWordsLowerCase(variableName)

        val matchedWords = parameterNameParts.intersect(variableNameParts).filter { !StringUtil.isNumeric(it) }
        if (matchedWords.isEmpty()) return WEIGHT_UNRELATED

        val matchingProportion = matchedWords.size.toFloat() / parameterNameParts.size.toFloat()

        // This happens when a variable contains all names of the parameter but in a different order.
        // Example: someLongWord for someWordLong.
        // In these cases, the match is not as good as an exact match, so we need to slightly deprioritize.
        if (matchingProportion >= 1) return WEIGHT_MATCHING_ALL_WORDS

        // Since ordering is lower numbers first, we need to do subtract our proportion from 1
        return 1f - matchingProportion
    }

    /**
     * Returns the names of the parameters associated with the [nameExpression].
     * This can be either the names of parameters of the function we are passing arguments to
     * or the name of the parameter we are specifying the type of.
     */
    context(_: KaSession, scopeContext: K2CompletionSectionContext<*>)
    private fun getAvailableReferenceNames(nameExpression: KtSimpleNameExpression): List<Name> {
        // Get the first parent above the actual type declaration.
        // If we have a parameter like `someUserType: <caret>`, or some value like `val someUserType: <caret>`,
        // then we want to prioritize types that match the words of `someUserType`.
        val typeOwnerParent = nameExpression.parents.dropWhile { it is KtTypeReference || it is KtUserType }.firstOrNull()

        return if (typeOwnerParent is KtNamedDeclaration) {
            listOf(typeOwnerParent.nameAsSafeName)
        } else {
            val candidates = scopeContext.callCandidates
            if (candidates.isEmpty()) return emptyList()

            val availableNames = candidates.mapNotNull { it.argumentMapping[nameExpression]?.name }
            availableNames
        }
    }

    context(_: KaSession, scopeContext: K2CompletionSectionContext<*>)
    fun addWeight(element: LookupElement) {
        if (element.`object` is NamedArgumentLookupObject) return
        val nameExpression = scopeContext.positionContext.position.parent as? KtSimpleNameExpression ?: return

        val availableReferenceNames = getAvailableReferenceNames(nameExpression)
        if (availableReferenceNames.isEmpty()) return

        val bestMatch = availableReferenceNames.minOf { calcNameSimilarity(it.asString(), element.lookupString) }
        if (bestMatch != WEIGHT_UNRELATED) {
            element.matchingArgumentName = bestMatch
        }
    }

    private var LookupElement.matchingArgumentName: Float? by UserDataProperty(Key("KOTLIN_MATCHING_ARGUMENT_NAME_WEIGHT"))

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*> = element.matchingArgumentName ?: WEIGHT_UNRELATED
    }
}
