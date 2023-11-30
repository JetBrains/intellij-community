/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectCallCandidates
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.weighers.Weighers
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class FirNamedArgumentCompletionContributor(basicContext: FirBasicCompletionContext, priority: Int) :
    FirCompletionContributorBase<KotlinExpressionNameReferencePositionContext>(basicContext, priority) {

    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinExpressionNameReferencePositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        if (positionContext.explicitReceiver != null) return

        val valueArgument = findValueArgument(positionContext.nameExpression) ?: return
        val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return
        val currentArgumentIndex = valueArgumentList.arguments.indexOf(valueArgument)
        val callElement = valueArgumentList.parent as? KtCallElement ?: return

        if (valueArgument.getArgumentName() != null) return

        val candidates = collectCallCandidates(callElement)
            .mapNotNull { it.candidate as? KtFunctionCall<*> }
            .filter { it.partiallyAppliedSymbol.symbol.hasStableParameterNames }

        val namedArgumentInfos = buildList {
            val (candidatesWithTypeMismatches, candidatesWithNoTypeMismatches) = candidates.partition {
                CallParameterInfoProvider.hasTypeMismatchBeforeCurrent(callElement, it.argumentMapping, currentArgumentIndex)
            }

            addAll(collectNamedArgumentInfos(callElement, candidatesWithNoTypeMismatches, currentArgumentIndex))
            // if no candidates without type mismatches have any candidate parameters, try searching among remaining candidates
            if (isEmpty()) {
                addAll(collectNamedArgumentInfos(callElement, candidatesWithTypeMismatches, currentArgumentIndex))
            }
        }

        for ((name, indexedTypes) in namedArgumentInfos) {
            val elements = buildList {
                with(lookupElementFactory) {
                    add(createNamedArgumentLookupElement(name, indexedTypes.map { it.value }))

                    // suggest default values only for types from parameters with matching positions to not clutter completion
                    val typesAtCurrentPosition = indexedTypes.filter { it.index == currentArgumentIndex }.map { it.value }
                    if (typesAtCurrentPosition.any { it.isBoolean }) {
                        add(createNamedArgumentWithValueLookupElement(name, KtTokens.TRUE_KEYWORD.value))
                        add(createNamedArgumentWithValueLookupElement(name, KtTokens.FALSE_KEYWORD.value))
                    }
                    if (typesAtCurrentPosition.any { it.isMarkedNullable }) {
                        add(createNamedArgumentWithValueLookupElement(name, KtTokens.NULL_KEYWORD.value))
                    }
                }
            }
            elements.forEach { Weighers.applyWeighsToLookupElement(weighingContext, it, symbolWithOrigin = null) }

            sink.addAllElements(elements)
        }
    }

    /**
     * @property indexedTypes types of all parameter candidates that match [name] with indexes of their positions in signatures
     */
    private data class NamedArgumentInfo(
        val name: Name,
        val indexedTypes: List<IndexedValue<KtType>>
    )

    context(KtAnalysisSession)
    private fun collectNamedArgumentInfos(
        callElement: KtCallElement,
        candidates: List<KtFunctionCall<*>>,
        currentArgumentIndex: Int
    ): List<NamedArgumentInfo> {
        val argumentsBeforeCurrent = callElement.valueArgumentList?.arguments?.take(currentArgumentIndex) ?: return emptyList()

        val nameToTypes = mutableMapOf<Name, MutableSet<IndexedValue<KtType>>>()
        for (candidate in candidates) {
            val indexedParameterCandidates = collectNotUsedIndexedParameterCandidates(callElement, candidate, argumentsBeforeCurrent)
            indexedParameterCandidates.forEach { (index, parameter) ->
                nameToTypes.getOrPut(parameter.name) { HashSet() }.add(IndexedValue(index, parameter.symbol.returnType))
            }
        }
        return nameToTypes.map { (name, types) -> NamedArgumentInfo(name, types.toList()) }
    }

    context(KtAnalysisSession)
    private fun collectNotUsedIndexedParameterCandidates(
        callElement: KtCallElement,
        candidate: KtFunctionCall<*>,
        argumentsBeforeCurrent: List<KtValueArgument>
    ): List<IndexedValue<KtVariableLikeSignature<KtValueParameterSymbol>>> {
        val signature = candidate.partiallyAppliedSymbol.signature
        val argumentMapping = candidate.argumentMapping

        val argumentToParameterIndex = CallParameterInfoProvider.mapArgumentsToParameterIndices(callElement, signature, argumentMapping)
        if (argumentsBeforeCurrent.any { it.getArgumentExpression() !in argumentToParameterIndex }) return emptyList()

        val alreadyPassedParameters = argumentsBeforeCurrent.mapNotNull { argumentMapping[it.getArgumentExpression()] }.toSet()
        return signature.valueParameters.withIndex().filterNot { (_, parameter) -> parameter in alreadyPassedParameters }
    }
}