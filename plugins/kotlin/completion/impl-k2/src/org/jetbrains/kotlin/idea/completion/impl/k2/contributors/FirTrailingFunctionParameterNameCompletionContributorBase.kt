// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.sequenceOfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeUsedAsExtension
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.TrailingLambdaParameterNameWeigher.isTrailingLambdaParameter
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.TrailingFunctionDescriptor
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleParameterPositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.psiUtil.isFirstStatement
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.yieldIfNotNull
import java.util.regex.Matcher
import java.util.regex.Pattern

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

        callExpression.resolveToCallCandidates()
            .asSequence()
            .map { it.candidate }
            .filterIsInstance<KaSimpleFunctionCall>()
            .map { it.partiallyAppliedSymbol }
            .map { it.signature }
            .mapNotNull { FunctionLookupElementFactory.getTrailingFunctionSignature(it, checkDefaultValues = false) }
            .mapNotNull { FunctionLookupElementFactory.createTrailingFunctionDescriptor(it) }
            .filterNot { it.functionType.hasReceiver }
            .flatMap { createLookupElements(it, existingParameterNames) }
            .map { elementBuilder ->
                elementBuilder
                    .apply { isTrailingLambdaParameter = true }
                    .applyWeighs(weighingContext)
            }.forEach(sink::addElement)
    }

    context(KaSession)
    private fun createLookupElements(
        trailingFunctionDescriptor: TrailingFunctionDescriptor,
        existingParameterNames: Set<String>,
    ): Sequence<LookupElementBuilder> {
        val typeNames = mutableMapOf<KaType, MutableSet<String>>()
        return createLookupElements(
            trailingFunctionDescriptor = trailingFunctionDescriptor,
            fromIndex = existingParameterNames.size,
        ) { parameterType, name ->
            typeNames.getOrPut(parameterType) { existingParameterNames.toMutableSet() }
                .add(name)
        }
    }

    context(KaSession)
    private fun createLookupElements(
        trailingFunctionDescriptor: TrailingFunctionDescriptor,
        fromIndex: Int = 0,
        nameValidator: (parameterType: KaType, name: String) -> Boolean = { _, _ -> true },
    ): Sequence<LookupElementBuilder> {
        val kotlinNameSuggester = KotlinNameSuggester()

        fun suggestNames(
            parameterType: KaType,
            index: Int = 0,
        ): Sequence<String> {
            val suggestedNames = sequenceOfNotNull(trailingFunctionDescriptor.suggestParameterNameAt(index))
                .map { it.asString() } +
                    kotlinNameSuggester.suggestTypeNames(parameterType)

            return suggestedNames.map { name ->
                KotlinNameSuggester.suggestNameByName(name) {
                    nameValidator(parameterType, it)
                }
            }
        }

        val parameterTypes = trailingFunctionDescriptor.functionType
            .parameterTypes

        return when (val parameterType = parameterTypes.singleOrNull()?.lowerBoundIfFlexible()) {
            null -> sequence {
                val suggestedNames = parameterTypes.mapIndexedNotNull { index, parameterType ->
                    if (index < fromIndex) return@mapIndexedNotNull null
                    parameterType to suggestNames(parameterType, index).first()
                }

                yieldIfNotNull(createCompoundLookupElement(suggestedNames))
            }

            is KaClassType -> @OptIn(KaExperimentalApi::class) sequence {
                if (fromIndex != 0) return@sequence

                val classSymbol = parameterType.expandedSymbol as? KaNamedClassSymbol
                    ?: return@sequence

                val substitutor = classSymbol.typeParameters
                    .zip(parameterType.typeArguments.mapNotNull { it.type })
                    .toMap()
                    .let { createSubstitutor(it) }

                val suggestedNames = destructurize(classSymbol, parameterType).mapIndexed { index, (_, returnType, name) ->
                    val type = substitutor.substitute(returnType)
                    type to
                            (name?.asString() ?: suggestNames(type, index).first())
                }

                yieldIfNotNull(createCompoundLookupElement(suggestedNames, isDestructuring = true))

                val lookupObject = classSymbol.psi
                    ?: return@sequence

                val parameterTypeText = parameterType.text
                suggestNames(parameterType).map { lookupString ->
                    LookupElementBuilder.create(lookupObject, lookupString)
                        .withTypeText(parameterTypeText)
                        .withTailTextInsertHandler()
                }.forEach { yield(it) }
            }

            else -> emptySequence()
        }
    }

    context(KaSession)
    private fun destructurize(
        classSymbol: KaNamedClassSymbol,
        parameterType: KaClassType,
    ): List<Destructurized> {
        val targetScope = classSymbol.memberScope

        val nameFilter = Name::hasComponentName
        val components = targetScope.callables(nameFilter)
            .ifEmpty {
                symbolFromIndexProvider.getExtensionCallableSymbolsByNameFilter(
                    nameFilter = nameFilter,
                    receiverTypes = listOf(parameterType),
                ) { declaration ->
                    visibilityChecker.canBeVisible(declaration)
                            && declaration.canBeAnalysed()
                }
            }.toList()

        val indices = components.asSequence()
            .filterIsInstance<KaNamedFunctionSymbol>()
            .filter { it.isOperator }
            .filter { it.visibility == KaSymbolVisibility.PUBLIC } // todo visibilityChecker::isVisible
            .map { it.name }
            .mapNotNull { name ->
                val matcher = ComponentFunctionNameRegex.matcher(name)
                if (matcher.find()) matcher.group(1) else null
            }.mapNotNull { it.toIntOrNull() }
            .toSortedSet()

        val count = components.size
        if (count == 0
            || indices.size != count
            || indices.first() != 1
            || indices.last() != count
        ) return emptyList()

        return if (classSymbol.isData) {
            targetScope.constructors
                .first { it.isPrimary }
                .valueParameters
                .map { Destructurized.ByName(it) }
        } else {
            components.map { Destructurized.ByType(it as KaNamedFunctionSymbol) }
        }
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

private val ComponentFunctionNameRegex: Pattern = Pattern.compile("^component(\\d+)$")

private sealed interface Destructurized {

    val callableSymbol: KaCallableSymbol

    operator fun component1(): KaCallableSymbol = callableSymbol

    val returnType: KaType get() = callableSymbol.returnType

    operator fun component2(): KaType = returnType

    val name: Name? get() = callableSymbol.name

    operator fun component3(): Name? = name

    data class ByName(
        override val callableSymbol: KaCallableSymbol,
    ) : Destructurized

    data class ByType(
        override val callableSymbol: KaFunctionSymbol,
    ) : Destructurized {

        override val name: Name? get() = null
    }
}

private fun Pattern.matcher(name: Name): Matcher =
    matcher(name.asString())

private val Name.hasComponentName: Boolean
    get() = ComponentFunctionNameRegex.matcher(this).matches()