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
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.parameterInfo.collectCallCandidates
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class FirNamedArgumentCompletionContributor(basicContext: FirBasicCompletionContext, priority: Int) :
    FirCompletionContributorBase<FirNameReferencePositionContext>(basicContext, priority) {

    override fun KtAnalysisSession.complete(positionContext: FirNameReferencePositionContext) {
        if (positionContext.explicitReceiver != null) return

        val valueArgument = findValueArgument(positionContext.nameExpression) ?: return
        val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return
        val callElement = valueArgumentList.parent as? KtCallElement ?: return

        if (valueArgument.getArgumentName() != null) return

        val candidates = collectCallCandidates(callElement)
            .mapNotNull { it.candidate as? KtFunctionCall<*> }
            .filter { it.partiallyAppliedSymbol.symbol.hasStableParameterNames }

        val namedArgumentInfos = buildList {
            val currentArgumentIndex = valueArgumentList.arguments.indexOf(valueArgument)
            val (candidatesWithTypeMismatches, candidatesWithNoTypeMismatches) = candidates.partition {
                CallParameterInfoProvider.hasTypeMismatchBeforeCurrent(callElement, it.argumentMapping, currentArgumentIndex)
            }

            addAll(collectNamedArgumentInfos(callElement, candidatesWithNoTypeMismatches, currentArgumentIndex))
            // if no candidates without type mismatches have any candidate parameters, try searching among remaining candidates
            if (isEmpty()) {
                addAll(collectNamedArgumentInfos(callElement, candidatesWithTypeMismatches, currentArgumentIndex))
            }
        }

        for ((name, types) in namedArgumentInfos) {
            with(lookupElementFactory) {
                sink.addElement(createNamedArgumentLookupElement(name, types))
                if (types.any { it.isBoolean }) {
                    sink.addElement(createNamedArgumentWithValueLookupElement(name, KtTokens.TRUE_KEYWORD.value))
                    sink.addElement(createNamedArgumentWithValueLookupElement(name, KtTokens.FALSE_KEYWORD.value))
                }
                if (types.any { it.isMarkedNullable }) {
                    sink.addElement(createNamedArgumentWithValueLookupElement(name, KtTokens.NULL_KEYWORD.value))
                }
            }
        }
    }

    /**
     * @property types types of all parameter candidates that match [name]
     */
    private data class NamedArgumentInfo(
        val name: Name,
        val types: List<KtType>
    )

    private fun KtAnalysisSession.collectNamedArgumentInfos(
        callElement: KtCallElement,
        candidates: List<KtFunctionCall<*>>,
        currentArgumentIndex: Int
    ): List<NamedArgumentInfo> {
        val argumentsBeforeCurrent = callElement.valueArgumentList?.arguments?.take(currentArgumentIndex) ?: return emptyList()

        val nameToTypes = mutableMapOf<Name, MutableSet<KtType>>()
        for (candidate in candidates) {
            val parameterCandidates = collectNotUsedParameterCandidates(callElement, candidate, argumentsBeforeCurrent)
            parameterCandidates.forEach { parameter ->
                nameToTypes.getOrPut(parameter.name) { HashSet() }.add(parameter.symbol.returnType)
            }
        }
        return nameToTypes.map { (name, types) -> NamedArgumentInfo(name, types.toList()) }
    }

    private fun KtAnalysisSession.collectNotUsedParameterCandidates(
        callElement: KtCallElement,
        candidate: KtFunctionCall<*>,
        argumentsBeforeCurrent: List<KtValueArgument>
    ): List<KtVariableLikeSignature<KtValueParameterSymbol>> {
        val signature = candidate.partiallyAppliedSymbol.signature
        val argumentMapping = candidate.argumentMapping

        val argumentToParameterIndex = CallParameterInfoProvider.mapArgumentsToParameterIndices(callElement, signature, argumentMapping)
        if (argumentsBeforeCurrent.any { it.getArgumentExpression() !in argumentToParameterIndex }) return emptyList()

        val alreadyPassedParameters = argumentsBeforeCurrent.mapNotNull { argumentMapping[it.getArgumentExpression()] }.toSet()
        return signature.valueParameters.filterNot { it in alreadyPassedParameters }
    }
}