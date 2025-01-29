// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import com.intellij.util.containers.sequenceOfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaCompletionCandidateChecker
import org.jetbrains.kotlin.analysis.api.components.KaCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.analysis.api.components.KaExtensionApplicabilityResult
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.codeinsight.utils.singleReturnExpressionOrNull
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.checkers.KtCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.TrailingLambdaParameterNameWeigher.isTrailingLambdaParameter
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.TrailingFunctionDescriptor
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleParameterPositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isFirstStatement
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.yieldIfNotNull

internal sealed class FirTrailingFunctionParameterNameCompletionContributorBase<C : KotlinRawPositionContext>(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<C>(parameters, sink, priority) {

    class All(
        parameters: KotlinFirCompletionParameters,
        sink: LookupElementSink,
        priority: Int = 0,
    ) : FirTrailingFunctionParameterNameCompletionContributorBase<KotlinExpressionNameReferencePositionContext>(
        parameters,
        sink,
        priority,
    ) {

        context(KaSession)
        override fun complete(
            positionContext: KotlinExpressionNameReferencePositionContext,
            weighingContext: WeighingContext,
        ) {
            if (positionContext.explicitReceiver != null) return

            val nameExpression = positionContext.nameExpression
            if (!nameExpression.isFirstStatement()) return

            super.complete(
                position = nameExpression,
                existingParameterNames = emptySet(),
                weighingContext = weighingContext,
            )
        }
    }

    class Missing(
        parameters: KotlinFirCompletionParameters,
        sink: LookupElementSink,
        priority: Int = 0,
    ) : FirTrailingFunctionParameterNameCompletionContributorBase<KotlinSimpleParameterPositionContext>(parameters, sink, priority) {

        context(KaSession)
        override fun complete(
            positionContext: KotlinSimpleParameterPositionContext,
            weighingContext: WeighingContext,
        ) {
            val parameter = positionContext.ktParameter

            val parameterList = parameter.parent
            if (parameterList !is KtParameterList) return

            val existingParameterNames = parameterList.parameters
                .asSequence()
                .takeWhile { it != parameter }
                .mapNotNull { it.name }
                .toSet()

            super.complete(
                position = parameter,
                existingParameterNames = existingParameterNames,
                weighingContext = weighingContext,
            )
        }
    }

    context(KaSession)
    protected fun complete(
        position: KtElement,
        existingParameterNames: Set<String>,
        weighingContext: WeighingContext,
    ) {
        val callExpression = position.parentOfType<KtCallExpression>()
            ?: return

        if (callExpression.lambdaArguments
                .firstOrNull()
                ?.getLambdaExpression()
                ?.functionLiteral
                ?.arrow != null
        ) return

        val candidateChecker = originalKtFile.findElementAt(position.startOffset)
            ?.findParentOfType<KtFunctionLiteral>()
            ?.let { createExtensionCandidateChecker(it) }
            ?: return

        callExpression.resolveToCallCandidates()
            .asSequence()
            .map { it.candidate }
            .filterIsInstance<KaSimpleFunctionCall>()
            .map { it.partiallyAppliedSymbol }
            .map { it.signature }
            .mapNotNull { FunctionLookupElementFactory.getTrailingFunctionSignature(it, checkDefaultValues = false) }
            .mapNotNull { FunctionLookupElementFactory.createTrailingFunctionDescriptor(it) }
            .filterNot { it.functionType.hasReceiver }
            .flatMap { createLookupElements(it, candidateChecker, existingParameterNames) }
            .map { elementBuilder ->
                elementBuilder
                    .apply { isTrailingLambdaParameter = true }
                    .applyWeighs(weighingContext)
            }.forEach(sink::addElement)
    }

    context(KaSession)
    private fun createLookupElements(
        trailingFunctionDescriptor: TrailingFunctionDescriptor,
        candidateChecker: KaCompletionExtensionCandidateChecker,
        existingParameterNames: Set<String>,
    ): Sequence<LookupElementBuilder> {
        val kotlinNameSuggester = KotlinNameSuggester()
        val typeNames = mutableMapOf<KaType, MutableSet<String>>()

        return createLookupElements(
            trailingFunctionDescriptor = trailingFunctionDescriptor,
            candidateChecker = candidateChecker,
            fromIndex = existingParameterNames.size,
        ) { parameterType, name ->
            val validator = typeNames.getOrPut(parameterType) {
                existingParameterNames.toMutableSet()
            }::add

            val suggestedNames = sequenceOfNotNull(name) +
                    kotlinNameSuggester.suggestTypeNames(parameterType)

            suggestedNames.map { name ->
                KotlinNameSuggester.suggestNameByName(name, validator)
            }
        }
    }

    context(KaSession)
    private fun createLookupElements(
        trailingFunctionDescriptor: TrailingFunctionDescriptor,
        candidateChecker: KaCompletionExtensionCandidateChecker,
        fromIndex: Int = 0,
        nameSuggester: (parameterType: KaType, name: String?) -> Sequence<String>,
    ): Sequence<LookupElementBuilder> {
        val parameterTypes = trailingFunctionDescriptor.functionType
            .parameterTypes

        fun parameterNameSuggester(
            parameterType: KaType,
            index: Int,
        ): Sequence<String> = nameSuggester(
            parameterType,
            trailingFunctionDescriptor.suggestParameterNameAt(index)?.asString(),
        )

        return when (val parameterType = parameterTypes.singleOrNull()?.lowerBoundIfFlexible()) {
            null -> sequence {
                val suggestedNames = parameterTypes.asSequence()
                    .drop(fromIndex)
                    .mapIndexed { index, parameterType ->
                        val parameterName = parameterNameSuggester(parameterType, index + fromIndex).first()
                        parameterType to parameterName
                    }.toList()

                yieldIfNotNull(createCompoundLookupElement(suggestedNames))
            }

            is KaClassType -> sequence {
                if (fromIndex != 0) return@sequence

                val classSymbol = parameterType.expandedSymbol as? KaNamedClassSymbol
                    ?: return@sequence

                val suggestedNames = getSubstitutedComponents(
                    classSymbol = classSymbol,
                    candidateChecker = candidateChecker,
                    parameterType = parameterType,
                ).mapIndexed { index, (callableSymbol, returnType) ->
                    val name = when (val member = callableSymbol.psi?.navigationElement) {
                        is KtNamedFunction -> {
                            val expression = member.singleReturnExpressionOrNull?.returnedExpression
                                ?: member.bodyExpression

                            (expression as? KtNameReferenceExpression)
                                ?.getReferencedName()
                        }

                        is KtParameter -> member.name
                        else -> null
                    }

                    returnType to nameSuggester(returnType, name).first()
                }

                yieldIfNotNull(createCompoundLookupElement(suggestedNames, isDestructuring = true))

                val lookupObject = classSymbol.psi
                    ?: return@sequence

                val parameterTypeText = parameterType.text
                parameterNameSuggester(parameterType, index = 0).map { lookupString ->
                    LookupElementBuilder.create(lookupObject, lookupString)
                        .withTypeText(parameterTypeText)
                        .withTailTextInsertHandler()
                }.forEach { yield(it) }
            }

            else -> emptySequence()
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun getSubstitutedComponents(
        classSymbol: KaNamedClassSymbol,
        candidateChecker: KaCompletionExtensionCandidateChecker,
        parameterType: KaClassType,
        receiverTypes: List<KaClassType> = listOf(parameterType), // todo all receiver types
    ): List<SubstitutedSymbol> {
        val components = classSymbol.getComponents(receiverTypes)
        if (components.isEmpty()) return emptyList()

        val defaultSubstitutor = classSymbol.typeParameters
            .zip(parameterType.typeArguments.mapNotNull { it.type })
            .toMap()
            .let { createSubstitutor(it) }

        val substituted = components.mapNotNull { callableSymbol ->
            val substitutor = if (callableSymbol.isExtension) {
                val callable = candidateChecker
                    .computeApplicability(callableSymbol) as? KaExtensionApplicabilityResult.ApplicableAsExtensionCallable
                    ?: return@mapNotNull null
                callable.substitutor
            } else {
                defaultSubstitutor
            }

            SubstitutedSymbol(
                callableSymbol = callableSymbol,
                returnType = substitutor.substitute(callableSymbol.returnType),
            )
        }

        return if (substituted.size == components.size) substituted
        else emptyList()
    }

    context(KaSession)
    private fun KaNamedClassSymbol.getComponents(
        receiverTypes: List<KaClassType>,
    ): Collection<KaNamedFunctionSymbol> {
        if (receiverTypes.isEmpty()) return emptyList()

        val listClassSymbol = findClass(StandardClassIds.List)
        if (this == listClassSymbol
            || listClassSymbol != null && isSubClassOf(listClassSymbol)
        ) return emptyList()

        val nameFilter: (Name) -> Boolean = DataClassResolver::isComponentLike
        val candidateSymbols = memberScope.callables(nameFilter)
            .ifEmpty {
                symbolFromIndexProvider.getExtensionCallableSymbolsByNameFilter(nameFilter, receiverTypes) { declaration ->
                    visibilityChecker.canBeVisible(declaration)
                            && declaration.canBeAnalysed()
                }
            }.filterIsInstance<KaNamedFunctionSymbol>()
            .filter { it.isOperator }
            .toList()
        if (candidateSymbols.isEmpty()) return emptyList()

        val symbolsByIndex = candidateSymbols.asSequence()
            .filter { it.visibility == KaSymbolVisibility.PUBLIC } // todo visibilityChecker::isVisible
            .associateByTo(sortedMapOf()) { functionSymbol ->
                DataClassResolver.getComponentIndex(functionSymbol.name.asString())
            }

        if (symbolsByIndex.isEmpty()
            || symbolsByIndex.size != candidateSymbols.size
            || symbolsByIndex.firstKey() != 1
            || symbolsByIndex.lastKey() != candidateSymbols.size
        ) return emptyList()

        return symbolsByIndex.values
    }
}

@KaExperimentalApi
private val NoAnnotationsTypeRenderer: KaTypeRenderer = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
    annotationsRenderer = annotationsRenderer.with {
        annotationFilter = KaRendererAnnotationsFilter.NONE
    }
}

private const val TailText: @NlsSafe String = " -> "

private fun LookupElementBuilder.withTailTextInsertHandler(
    delegate: InsertHandler<LookupElement>? = null,
) = withTailText(TailText, true)
    .withInsertHandler { context, item ->
        delegate?.handleInsert(context, item)
        context.document.insertString(context.tailOffset, TailText)
        context.commitDocument()
        context.editor.caretModel.moveToOffset(context.tailOffset)
    }

context(KaSession)
private fun createCompoundLookupElement(
    suggestedNames: Collection<Pair<KaType, String>>,
    isDestructuring: Boolean = false,
): LookupElementBuilder? {
    if (suggestedNames.isEmpty()) return null

    val (prefix, postfix) = if (suggestedNames.size > 1) "(" to ")"
    else "" to ""

    val lookupStrings = suggestedNames.map { it.second }
    val presentableText = lookupStrings.joinToString(
        prefix = if (isDestructuring) prefix else "",
        postfix = if (isDestructuring) postfix else "",
    )

    val typeText = suggestedNames.joinToString(
        prefix = prefix,
        postfix = postfix,
    ) { it.first.text }

    return LookupElementBuilder.create(lookupStrings.first())
        .withPresentableText(presentableText)
        .withTypeText(typeText)
        .withLookupStrings(lookupStrings)
        .withTailTextInsertHandler { context, item ->
            context.document.replaceString(context.startOffset, context.tailOffset, presentableText)
        }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private val KaType.text: String
    get() = render(
        renderer = NoAnnotationsTypeRenderer,
        position = Variance.INVARIANT,
    )

context(KaCompletionCandidateChecker)
private fun createExtensionCandidateChecker(
    functionLiteral: KtFunctionLiteral,
): KtCompletionExtensionCandidateChecker? {
    val bodyExpression = functionLiteral.bodyExpression
        ?: return null

    val codeFragment = KtPsiFactory(functionLiteral.project)
        .createBlockCodeFragment(
            text = StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.asString(),
            context = bodyExpression,
        )

    val nameExpression = codeFragment.getContentElement()
        .firstStatement as? KtNameReferenceExpression
        ?: return null

    return KtCompletionExtensionCandidateChecker.create(
        originalFile = codeFragment,
        nameExpression = nameExpression,
        explicitReceiver = nameExpression,
    )
}

private data class SubstitutedSymbol(
    val callableSymbol: KaNamedFunctionSymbol,
    val returnType: KaType,
)
