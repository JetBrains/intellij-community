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
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.TrailingLambdaParameterNameWeigher.isTrailingLambdaParameter
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.TrailingFunctionDescriptor
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.isFirstStatement
import org.jetbrains.kotlin.types.Variance

internal class FirTrailingFunctionParameterNameCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinExpressionNameReferencePositionContext>(parameters, sink, priority) {

    context(KaSession)
    override fun complete(
        positionContext: KotlinExpressionNameReferencePositionContext,
        weighingContext: WeighingContext
    ) {
        if (positionContext.explicitReceiver != null) return

        val callExpression = positionContext.nameExpression
            .takeIf { it.isFirstStatement() }
            ?.parentOfType<KtCallExpression>()
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
            .flatMap { createLookupElements(it) }
            .map { it.applyWeighs(weighingContext) }
            .forEach(sink::addElement)
    }
}

@KaExperimentalApi
private val NoAnnotationsTypeRenderer: KaTypeRenderer = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
    annotationsRenderer = annotationsRenderer.with {
        annotationFilter = KaRendererAnnotationsFilter.NONE
    }
}

context(KaSession)
private fun createLookupElements(
    trailingFunctionDescriptor: TrailingFunctionDescriptor,
): Sequence<LookupElementBuilder> {
    val typeNames = mutableMapOf<KaType, MutableSet<String>>()
    val kotlinNameSuggester = KotlinNameSuggester()

    fun suggestNames(
        parameterType: KaType,
        index: Int,
    ): Sequence<String> {
        val suggestedNames = sequenceOfNotNull(trailingFunctionDescriptor.suggestParameterNameAt(index))
            .map { it.asString() } +
                kotlinNameSuggester.suggestTypeNames(parameterType)

        return suggestedNames.map { name ->
            KotlinNameSuggester.suggestNameByName(name) {
                typeNames.getOrPut(parameterType) { mutableSetOf() }
                    .add(it)
            }
        }
    }

    val parameterTypes = trailingFunctionDescriptor.functionType
        .parameterTypes

    return when (val parameterType = parameterTypes.singleOrNull()) {
        null -> {
            val suggestedNames = parameterTypes.mapIndexed { index, parameterType ->
                parameterType to suggestNames(parameterType, index).first()
            }

            sequenceOfNotNull(createCompoundLookupElement(suggestedNames))
        }

        else -> {
            val suggestedNames = suggestNames(parameterType, 0)
            val hardCodedLookupElement = (parameterType as? KaClassType)
                ?.classId
                ?.let { getStandardSuggestions(it) }
                ?.takeUnless { it.isEmpty() }
                ?.let { suggestions ->
                    parameterType.typeArguments
                        .mapNotNull { it.type }
                        .zip(suggestions)
                }?.let { createCompoundLookupElement(it) }

            val parameterTypeText = parameterType.text
            sequenceOfNotNull(hardCodedLookupElement) +
                    suggestedNames.map {
                        LookupElementBuilder.create(it)
                            .withTypeText(parameterTypeText)
                            .withTailTextInsertHandler()
                    }
        }
    }.map { it.apply { isTrailingLambdaParameter = true } }
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
): LookupElementBuilder? {
    if (suggestedNames.isEmpty()) return null

    val lookupStrings = suggestedNames.map { it.second }
    val presentableText = lookupStrings.joinToString()
    return LookupElementBuilder.create(lookupStrings.first())
        .withPresentableText(presentableText)
        .withTypeText(suggestedNames.map { it.first }.joinToString(prefix = "(", postfix = ")") { it.text })
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

private fun baseClassId(name: String) = ClassId(
    packageFqName = StandardClassIds.BASE_KOTLIN_PACKAGE,
    topLevelName = Name.identifier(name),
)

private val PairClassId = baseClassId("Pair")
private val TripleClassId = baseClassId("Triple")

private fun getStandardSuggestions(classId: ClassId) = when (classId) {
    PairClassId -> listOf("first", "second")
    TripleClassId -> listOf("first", "second", "third")

    StandardClassIds.MapEntry,
    StandardClassIds.MutableMapEntry -> listOf("key", "value")

    else -> listOf()
}