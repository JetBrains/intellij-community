/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectCallCandidates
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2ContributorSectionPriority
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.isAfterRangeOperator
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

internal class K2NamedArgumentCompletionContributor : K2SimpleCompletionContributor<KotlinExpressionNameReferencePositionContext>(
    positionContextClass = KotlinExpressionNameReferencePositionContext::class,
    priority = K2ContributorSectionPriority.HEURISTIC,
) {

    context(_: KaSession, context: K2CompletionSectionContext<KotlinExpressionNameReferencePositionContext>)
    override fun shouldExecute(): Boolean {
        return !context.positionContext.isAfterRangeOperator()
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinExpressionNameReferencePositionContext>)
    override fun complete() {
        if (context.positionContext.explicitReceiver != null) return

        val valueArgument = findValueArgument(context.positionContext.nameExpression) ?: return
        val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return
        val currentArgumentIndex = valueArgumentList.arguments.indexOf(valueArgument)
        val callElement = valueArgumentList.parent as? KtCallElement ?: return

        if (valueArgument.getArgumentName() != null) return
        val completionType = context.completionContext.parameters.completionType

        // with `analyze` invoked on `fakeKtFile`:
        // - use-site is `fakeKtFile`;
        // - `collectCallCandidates` collects functions from `originalKtFile`.
        // if a function has `private` modifier then collected call candidate hav INVISIBLE_REFERENCE diagnostic, which leads to KTIJ-29748;
        // TODO: when KT-68929 is implemented, rewrite `KotlinFirCompletionProvider` so that it uses `analyzeCopy` with `IGNORE_ORIGIN`
        // as a temporary workaround, use `analyzeCopy` while collecting call candidate for named argument completion
        analyzeCopy(callElement, resolutionMode = KaDanglingFileResolutionMode.PREFER_SELF) {
            val candidates = collectCallCandidates(callElement)
                .mapNotNull { it.candidate as? KaFunctionCall<*> }
                .filter { it.symbol.hasStableParameterNames }
                .filter {
                    val constructorPsi = it.symbol.psi as? KtPrimaryConstructor ?: return@filter true
                    if (!constructorPsi.isPrivate()) return@filter true
                    val constructorClass = constructorPsi.containingClass() ?: return@filter false
                    constructorClass.isParentClassForELement(callElement)
                }

            val namedArgumentInfos = buildList {
                val (candidatesWithTypeMismatches, candidatesWithNoTypeMismatches) = candidates.partition {
                    CallParameterInfoProvider.hasTypeMismatchBeforeCurrent(callElement, it.valueArgumentMapping, currentArgumentIndex)
                }

                val argumentsBeforeCurrent = valueArgumentList.arguments.take(currentArgumentIndex)
                addAll(collectNamedArgumentInfos(callElement, argumentsBeforeCurrent, candidatesWithNoTypeMismatches))
                // if no candidates without type mismatches have any candidate parameters, try searching among remaining candidates
                if (isEmpty()) {
                    addAll(collectNamedArgumentInfos(callElement, argumentsBeforeCurrent, candidatesWithTypeMismatches))
                }
            }

            // Local variables with the same name as any of the currently edited arguments
            val potentiallyRelevantLocalVariables by lazy(LazyThreadSafetyMode.NONE) {
                val scopeContext = context.completionContext.originalFile.scopeContext(callElement)
                val namesAtCurrentIndex = namedArgumentInfos
                    .filter { namedArgument -> namedArgument.missingParameters.any { it.isFirstUnpassedParameter } }
                    .mapTo(mutableSetOf()) { it.name }
                getNonImportedAvailableVariables(namesAtCurrentIndex, scopeContext)
            }

            buildList {
                for ((name, missingParameters) in namedArgumentInfos) {
                    with(KotlinFirLookupElementFactory) {
                        if (completionType != CompletionType.SMART) {
                            // For smart completion, we do not want to show incomplete named argument items
                            add(createNamedArgumentLookupElement(name, missingParameters))
                        }

                        // suggest default values only for types from parameters with matching positions to not clutter completion
                        val typesAtCurrentPosition = missingParameters.filter { it.isFirstUnpassedParameter }

                        val booleanPosition = typesAtCurrentPosition.firstOrNull { it.type.isBooleanType }
                        if (booleanPosition != null) {
                            add(createNamedArgumentWithValueLookupElement(name, KtTokens.TRUE_KEYWORD.value, booleanPosition.index))
                            add(createNamedArgumentWithValueLookupElement(name, KtTokens.FALSE_KEYWORD.value, booleanPosition.index))
                        }

                        val nullablePosition = typesAtCurrentPosition.firstOrNull { it.type.isMarkedNullable }
                        if (nullablePosition != null) {
                            add(createNamedArgumentWithValueLookupElement(name, KtTokens.NULL_KEYWORD.value, nullablePosition.index))
                        }

                        // We only check matching names and types if there is only a single type at the current position.
                        val singleTypeAtPosition = typesAtCurrentPosition.singleOrNull()
                        if (singleTypeAtPosition != null) {
                            // Try and find a _local_ variable with the same name and matching type to prefill it
                            val variableTypeWithSameName = potentiallyRelevantLocalVariables[name]?.returnType
                            if (variableTypeWithSameName?.isPossiblySubTypeOf(singleTypeAtPosition.type) == true) {
                                add(createNamedArgumentWithValueLookupElement(name, name.asString(), singleTypeAtPosition.index))
                            }
                        }
                    }
                }
            }
        }.map { it.applyWeighs() }
            .forEach { addElement(it) }
    }

    private fun KtClass.isParentClassForELement(expression: KtElement): Boolean {
        val parentClass: KtClass? = expression.findParentOfType<KtClass>()
        when (parentClass) {
            null -> return false
            this -> return true
            else -> return isParentClassForELement(parentClass)
        }
    }

    internal data class NamedParameterInfo(
        val name: Name,
        val missingParameters: List<MissingParameterInfo>
    )

    internal data class MissingParameterInfo(
        val name: Name,
        val type: KaType,
        val isFirstUnpassedParameter: Boolean,
        val index: Int,
    )

    context(_: KaSession)
    private fun collectNamedArgumentInfos(
        callElement: KtCallElement,
        argumentsBeforeCurrent: List<KtValueArgument>,
        candidates: List<KaFunctionCall<*>>,
    ): List<NamedParameterInfo> {
        val nameToParameterInfo = mutableMapOf<Name, MutableSet<MissingParameterInfo>>()

        candidates.flatMap {
            collectNotUsedIndexedParameterCandidates(callElement, it, argumentsBeforeCurrent)
        }.forEach { parameterInfo ->
            nameToParameterInfo.getOrPut(parameterInfo.name) { HashSet() }.add(parameterInfo)
        }
        return nameToParameterInfo.map { (name, types) ->
            NamedParameterInfo(name, types.toList())
        }
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun collectNotUsedIndexedParameterCandidates(
        callElement: KtCallElement,
        candidate: KaFunctionCall<*>,
        argumentsBeforeCurrent: List<KtValueArgument>,
    ): Sequence<MissingParameterInfo> {
        val signature = candidate.signature
        val valueArgumentMapping = candidate.valueArgumentMapping

        val contextArgumentMapping = candidate.contextArgumentMapping
        val contextParameterIndexes = signature.contextParameters.mapIndexed { index, signature -> signature to index }.toMap()

        val argumentToValueParameterIndex =
            CallParameterInfoProvider.mapArgumentsToParameterIndices(callElement, signature, valueArgumentMapping)

        val argumentToContextParameterIndex = contextArgumentMapping.toList().mapNotNull { (argument, variableSignature) ->
            val indexOfArgument = contextParameterIndexes[variableSignature] ?: return@mapNotNull null
            argument to indexOfArgument
        }.toMap()

        if (argumentsBeforeCurrent.any {
                it.getArgumentExpression() !in argumentToValueParameterIndex &&
                        it.getArgumentExpression() !in argumentToContextParameterIndex
            }) return emptySequence()

        val alreadyPassedParameters = argumentsBeforeCurrent.mapNotNull {
            valueArgumentMapping[it.getArgumentExpression()] ?: contextArgumentMapping[it.getArgumentExpression()]
        }.toSet()

        val parametersToConsider = if (callElement.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments)) {
            // We put the context parameters after the value parameters because they are less likely to be chosen by the user
            signature.valueParameters + signature.contextParameters
        } else {
            signature.valueParameters
        }

        val unpassedParametersWithIndex = parametersToConsider
            .asSequence()
            .withIndex()
            .filterNot { (_, parameter) ->
                parameter in alreadyPassedParameters
            }

        val firstUnpassedIndex = unpassedParametersWithIndex.firstOrNull()?.index
        return unpassedParametersWithIndex.map { (index, parameter) ->
                MissingParameterInfo(
                    name = parameter.name,
                    type = parameter.returnType,
                    isFirstUnpassedParameter = index == firstUnpassedIndex,
                    index = index
                )
            }
    }
}