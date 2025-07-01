// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import com.intellij.util.containers.sequenceOfNotNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaCompletionCandidateChecker
import org.jetbrains.kotlin.analysis.api.components.KaCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.analysis.api.components.KaExtensionApplicabilityResult
import org.jetbrains.kotlin.analysis.api.impl.base.components.KaBaseIllegalPsiException
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinFqNameSerializer
import org.jetbrains.kotlin.idea.codeinsight.utils.singleReturnExpressionOrNull
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.api.serialization.ensureSerializable
import org.jetbrains.kotlin.idea.completion.doPostponedOperationsAndUnblockDocument
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.checkers.KtCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.TrailingLambdaParameterNameWeigher.isTrailingLambdaParameter
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.addImportIfRequired
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.TrailingFunctionDescriptor
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleParameterPositionContext
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.yieldIfNotNull

internal sealed class FirTrailingFunctionParameterNameCompletionContributorBase<C : KotlinRawPositionContext>(
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<C>(sink, priority) {

    class All(
        sink: LookupElementSink,
        priority: Int = 0,
    ) : FirTrailingFunctionParameterNameCompletionContributorBase<KotlinExpressionNameReferencePositionContext>(sink, priority) {

        context(KaSession)
        override fun complete(
            positionContext: KotlinExpressionNameReferencePositionContext,
            weighingContext: WeighingContext,
        ) {
            if (positionContext.explicitReceiver != null) return

            val nameExpression = positionContext.nameExpression
            val functionLiteral = nameExpression.parentOfType<KtFunctionLiteral>()
                ?: return

            if (functionLiteral.bodyExpression?.firstStatement != nameExpression) return

            super.complete(
                position = nameExpression,
                existingParameterNames = emptySet(),
                weighingContext = weighingContext,
            )
        }
    }

    class Missing(
        sink: LookupElementSink,
        priority: Int = 0,
    ) : FirTrailingFunctionParameterNameCompletionContributorBase<KotlinSimpleParameterPositionContext>(sink, priority) {

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
        val functionLiteral = originalKtFile.findElementAt(position.startOffset)
            ?.parentOfType<KtFunctionLiteral>()
            ?: return

        if (functionLiteral.hasParameterSpecification()) return

        val bodyExpression = functionLiteral.bodyExpression
            ?: return

        val callExpression = functionLiteral.parentOfType<KtCallExpression>()
            ?: return

        val candidateChecker = createExtensionCandidateChecker(bodyExpression)
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

                val signatures = classSymbol.getSignatures(candidateChecker, parameterType)
                val suggestedNames = signatures.map { functionSignature ->
                    val name = when (val member = functionSignature.symbol.psi?.navigationElement) {
                        is KtNamedFunction -> {
                            val expression = member.singleReturnExpressionOrNull?.returnedExpression
                                ?: member.bodyExpression

                            (expression as? KtNameReferenceExpression)
                                ?.getReferencedName()
                        }

                        is KtParameter -> member.name
                        else -> null
                    }

                    val returnType = functionSignature.returnType
                        .lowerBoundIfFlexible()
                    returnType to nameSuggester(returnType, name).first()
                }

                val fqNames = signatures.mapNotNull { signature ->
                    val addImport =
                        importStrategyDetector.detectImportStrategyForCallableSymbol(signature.symbol) as? ImportStrategy.AddImport
                            ?: return@mapNotNull null
                    addImport.nameToImport
                }

                createCompoundLookupElement(suggestedNames, isDestructuring = true)?.withChainedInsertHandler(
                    WithImportInsertionHandler(fqNames)
                )?.let { yield(it) }

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

    @Serializable
    internal data class WithImportInsertionHandler(
        @Serializable(with = FqNameListSerializer::class) val namesToImport: List<FqName>,
    ): SerializableInsertHandler {
        override fun handleInsert(
            context: InsertionContext,
            item: LookupElement
        ) {
            val targetFile = context.file
            if (targetFile !is KtFile) throw IllegalStateException("Target file '${targetFile.name}' is not a Kotlin file")

            for (nameToImport in namesToImport) {
                addImportIfRequired(context, nameToImport)
            }
            context.commitDocument()
            context.doPostponedOperationsAndUnblockDocument()
        }

        object FqNameListSerializer : KSerializer<List<FqName>> by ListSerializer(KotlinFqNameSerializer)
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KaNamedClassSymbol.getSignatures(
        candidateChecker: KaCompletionExtensionCandidateChecker,
        parameterType: KaClassType,
        receiverTypes: List<KaClassType> = listOf(parameterType), // todo all receiver types
    ): List<KaFunctionSignature<KaNamedFunctionSymbol>> {
        val components = getComponents(receiverTypes)
        if (components.isEmpty()) return emptyList()

        val defaultSubstitutor = typeParameters
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

            callableSymbol.substitute(substitutor)
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

private fun LookupElementBuilder.withTailTextInsertHandler() = this
    .withTailText(TailText, true)
    .withInsertHandler(TailTextInsertHandler)

@Serializable
internal object TailTextInsertHandler : SerializableInsertHandler {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        context.document.insertString(context.tailOffset, TailText)
        context.commitDocument()
        context.editor.caretModel.moveToOffset(context.tailOffset)
    }
}

private fun LookupElementBuilder.withChainedInsertHandler(
    delegate: SerializableInsertHandler,
): LookupElementBuilder {
    return when (val insertHandler = insertHandler?.ensureSerializable()) {
        null -> withInsertHandler(delegate)
        else -> withInsertHandler(ChainedInsertHandler(delegate, insertHandler))
    }
}

@Serializable
data class ChainedInsertHandler(
    val first: SerializableInsertHandler,
    val second: SerializableInsertHandler,
) : SerializableInsertHandler {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        first.handleInsert(context, item)
        second.handleInsert(context, item)
    }
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
        .withTailTextInsertHandler()
        .withChainedInsertHandler(CompoundInsertionHandler(presentableText))
}

@Serializable
internal data class CompoundInsertionHandler(
    val presentableText: String,
): SerializableInsertHandler {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        context.document.replaceString(context.startOffset, context.tailOffset, presentableText)
        context.commitDocument()
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
    bodyExpression: KtBlockExpression,
): KtCompletionExtensionCandidateChecker? {
    val codeFragment = KtPsiFactory(bodyExpression.project)
        .createBlockCodeFragment(
            text = StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.asString(),
            context = bodyExpression,
        )

    val nameExpression = codeFragment.getContentElement()
        .firstStatement as? KtNameReferenceExpression
        ?: return null

    // FIXME: KTIJ-34273
    @OptIn(KaImplementationDetail::class)
    return KaBaseIllegalPsiException.allowIllegalPsiAccess {
        KtCompletionExtensionCandidateChecker.create(
            originalFile = codeFragment,
            nameExpression = nameExpression,
            explicitReceiver = nameExpression,
        )
    }
}
