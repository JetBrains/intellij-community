/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectCallCandidates
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.Weighers
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import kotlin.collections.get

internal class FirNamedArgumentCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinExpressionNameReferencePositionContext>(basicContext, priority) {

    context(KaSession)
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

        // with `analyze` invoked on `fakeKtFile`:
        // - use-site is `fakeKtFile`;
        // - `collectCallCandidates` collects functions from `originalKtFile`.
        // if a function has `private` modifier then collected call candidate hav INVISIBLE_REFERENCE diagnostic, which leads to KTIJ-29748;
        // TODO: when KT-68929 is implemented, rewrite `KotlinFirCompletionProvider` so that it uses `analyzeCopy` with `IGNORE_ORIGIN`
        // as a temporary workaround, use `analyzeCopy` while collecting call candidate for named argument completion
        val lookupElements = analyzeCopy(callElement, resolutionMode = KaDanglingFileResolutionMode.PREFER_SELF) {
            val candidates = collectCallCandidates(callElement)
                .mapNotNull { it.candidate as? KaFunctionCall<*> }
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

            val elements = buildList {
                for ((name, indexedTypes) in namedArgumentInfos) {
                    with(KotlinFirLookupElementFactory) {
                        add(createNamedArgumentLookupElement(name, indexedTypes.map { it.value }))

                        // suggest default values only for types from parameters with matching positions to not clutter completion
                        val typesAtCurrentPosition = indexedTypes.filter { it.index == currentArgumentIndex }.map { it.value }
                        if (typesAtCurrentPosition.any { it.isBooleanType }) {
                            add(createNamedArgumentWithValueLookupElement(name, KtTokens.TRUE_KEYWORD.value))
                            add(createNamedArgumentWithValueLookupElement(name, KtTokens.FALSE_KEYWORD.value))
                        }
                        if (typesAtCurrentPosition.any { it.isMarkedNullable }) {
                            add(createNamedArgumentWithValueLookupElement(name, KtTokens.NULL_KEYWORD.value))
                        }
                    }
                }
            }

            elements
        }

        lookupElements.forEach { Weighers.applyWeighsToLookupElement(weighingContext, it, symbolWithOrigin = null) }

        sink.addAllElements(lookupElements)
    }

    /**
     * @property indexedTypes types of all parameter candidates that match [name] with indexes of their positions in signatures
     */
    private data class NamedArgumentInfo(
        val name: Name,
        val indexedTypes: List<IndexedValue<KaType>>
    )

    context(KaSession)
    private fun collectNamedArgumentInfos(
        callElement: KtCallElement,
        candidates: List<KaFunctionCall<*>>,
        currentArgumentIndex: Int
    ): List<NamedArgumentInfo> {
        val argumentsBeforeCurrent = callElement.valueArgumentList?.arguments?.take(currentArgumentIndex) ?: return emptyList()

        val nameToTypes = mutableMapOf<Name, MutableSet<IndexedValue<KaType>>>()
        for (candidate in candidates) {
            val indexedParameterCandidates = collectNotUsedIndexedParameterCandidates(callElement, candidate, argumentsBeforeCurrent)
            indexedParameterCandidates.forEach { (index, parameter) ->
                nameToTypes.getOrPut(parameter.name) { HashSet() }.add(IndexedValue(index, parameter.symbol.returnType))
            }
        }
        return nameToTypes.map { (name, types) -> NamedArgumentInfo(name, types.toList()) }
    }

    context(KaSession)
    private fun collectNotUsedIndexedParameterCandidates(
        callElement: KtCallElement,
        candidate: KaFunctionCall<*>,
        argumentsBeforeCurrent: List<KtValueArgument>
    ): List<IndexedValue<KaVariableSignature<KaValueParameterSymbol>>> {
        val signature = candidate.partiallyAppliedSymbol.signature
        val argumentMapping = candidate.argumentMapping

        val argumentToParameterIndex = CallParameterInfoProvider.mapArgumentsToParameterIndices(callElement, signature, argumentMapping)
        if (argumentsBeforeCurrent.any { it.getArgumentExpression() !in argumentToParameterIndex }) return emptyList()

        val alreadyPassedParameters = argumentsBeforeCurrent.mapNotNull { argumentMapping[it.getArgumentExpression()] }.toSet()
        return signature.valueParameters.withIndex().filterNot { (_, parameter) -> parameter in alreadyPassedParameters }
    }
}