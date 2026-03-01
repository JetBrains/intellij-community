// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.abbreviationOrSelf
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.buildClassTypeWithStarProjections
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.completion.KOTLIN_CAST_REQUIRED_COLOR
import org.jetbrains.kotlin.idea.completion.impl.k2.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.api.serialization.ensureSerializable
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.CallableMetadataProvider
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.AdaptToExplicitReceiverInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.addRequiredTypeArgumentsIfNecessary
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.qualifyContextSensitiveResolutionIfNecessary
import org.jetbrains.kotlin.idea.completion.impl.k2.hasNoExplicitReceiver
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.FunctionCallLookupObject
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.FunctionLookupElementFactory
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.CallableWeigher.callableWeight
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.WeighingContext
import org.jetbrains.kotlin.idea.debugger.evaluate.util.KotlinK2CodeFragmentUtils
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

context(_: KaSession, context: K2CompletionSectionContext<*>)
internal fun createCallableLookupElements(
    signature: KaCallableSignature<*>,
    options: CallableInsertionOptions,
    scopeKind: KaScopeKind? = null,
    presentableText: @NlsSafe String? = null, // TODO decompose
    withTrailingLambda: Boolean = false, // TODO find a better solution
    aliasName: Name? = null,
): Sequence<LookupElementBuilder> {
    val callableSymbol = signature.symbol
    val namedSymbol = when (callableSymbol) {
        is KaNamedSymbol -> callableSymbol
        is KaConstructorSymbol -> callableSymbol.containingDeclaration as? KaNamedClassSymbol
        else -> null
    } ?: return emptySequence()

    val shortName = namedSymbol.name
    val symbolWithOrigin = KtSymbolWithOrigin(callableSymbol, scopeKind)

    return sequence {
        KotlinFirLookupElementFactory.createCallableLookupElement(
            name = shortName,
            signature = signature,
            options = options,
            expectedType = context.weighingContext.expectedType,
            aliasName = aliasName,
        ).let { yield(it) }

        if (withTrailingLambda && signature is KaFunctionSignature<*>) {
            FunctionLookupElementFactory.createLookupWithTrailingLambda(
                shortName = shortName,
                signature = signature,
                options = options,
                aliasName = aliasName,
            )?.let { yield(it) }
        }

        if (namedSymbol is KaNamedFunctionSymbol &&
            signature is KaFunctionSignature<*> &&
            // Only offer bracket operators after dot, not for safe access or implicit receivers
            context.parameters.position.parent?.parent is KtDotQualifiedExpression
        ) {
            createOperatorLookupElement(context.weighingContext, signature, options, namedSymbol)?.let { yield(it) }
        }
    }.map { builder ->
        if (presentableText == null) builder
        else builder.withPresentableText(presentableText)
    }.map { lookup ->
        if (!context.weighingContext.isPositionInsideImportOrPackageDirective) {
            lookup.callableWeight = CallableMetadataProvider.getCallableMetadata(
                signature = signature,
                scopeKind = scopeKind,
                actualReceiverTypes = context.weighingContext.actualReceiverTypes,
                isFunctionalVariableCall = callableSymbol is KaVariableSymbol
                        && lookup.`object` is FunctionCallLookupObject,
            )
        }

        lookup.applyWeighs(symbolWithOrigin)
            .addRequiredTypeArgumentsIfNecessary(context.positionContext)
            .qualifyContextSensitiveResolutionIfNecessary(context.positionContext)
            .applyKindToPresentation()
    }
}

// todo move to the corresponding assignment
private fun LookupElementBuilder.applyKindToPresentation(): LookupElementBuilder = when (callableWeight?.kind) {
    // Make the text bold if it's an immediate member of the receiver
    CallableMetadataProvider.CallableKind.THIS_CLASS_MEMBER,
    CallableMetadataProvider.CallableKind.THIS_TYPE_EXTENSION -> bold()

    // Make the text gray
    CallableMetadataProvider.CallableKind.RECEIVER_CAST_REQUIRED -> {
        val presentation = LookupElementPresentation().apply {
            renderElement(this)
        }

        withTailText(presentation.tailText, true)
            .withItemTextForeground(KOTLIN_CAST_REQUIRED_COLOR)
    }

    else -> this
}

context(_: KaSession)
internal fun createOperatorLookupElement(
    context: WeighingContext,
    signature: KaFunctionSignature<*>,
    options: CallableInsertionOptions,
    namedSymbol: KaNamedFunctionSymbol,
): LookupElementBuilder? {
    if (!namedSymbol.isOperator) return null

    val operatorString = when (namedSymbol.name) {
        OperatorNameConventions.GET, OperatorNameConventions.SET -> "[]"
        OperatorNameConventions.INVOKE -> "()"
        else -> return null
    }
    return KotlinFirLookupElementFactory.createBracketOperatorLookupElement(
        operatorName = Name.identifier(operatorString),
        signature = signature,
        options = options,
        expectedType = context.expectedType
    )
}

internal fun KotlinTypeNameReferencePositionContext.allowsClassifiersAndPackagesForPossibleExtensionCallables(
    parameters: KotlinFirCompletionParameters,
    prefixMatcher: PrefixMatcher,
): Boolean {
    return !this.hasNoExplicitReceiver()
            || parameters.invocationCount > 0
            || prefixMatcher.prefix.firstOrNull()?.isLowerCase() != true
}

internal fun LookupElementBuilder.adaptToExplicitReceiver(
    receiver: KtElement,
    typeText: String,
): LookupElement = withInsertHandler(
    AdaptToExplicitReceiverInsertionHandler(
        insertHandler = insertHandler?.ensureSerializable(),
        receiverTextRangeStart = receiver.textRange.startOffset,
        receiverTextRangeEnd = receiver.textRange.endOffset,
        receiverText = receiver.text,
        typeText = typeText,
    )
)

@OptIn(KaExperimentalApi::class)
internal fun isRuntimeTypeEvaluatorAvailable(context: K2CompletionSectionContext<*>) =
    (context.parameters.originalFile as? KtCodeFragment)
        ?.getCopyableUserData(KotlinK2CodeFragmentUtils.RUNTIME_TYPE_EVALUATOR_K2) != null

@OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
context(kaSession: KaSession)
internal fun KtExpression.evaluateRuntimeKaType(): KaType? {
    val expr = this
    val containingFile = containingFile as? KtCodeFragment
    val runtimeTypeEvaluator = containingFile?.getCopyableUserData(KotlinK2CodeFragmentUtils.RUNTIME_TYPE_EVALUATOR_K2)
    return runtimeTypeEvaluator?.invoke(expr)?.restore(kaSession)
}

// See KTIJ-35541
@OptIn(KaExperimentalApi::class)
context(_: KaSession)
internal fun KaType.replaceTypeParametersWithStarProjections(): KaType? =
    abbreviationOrSelf.symbol?.let { buildClassTypeWithStarProjections(it) }


/**
 * Represents a [callableId] that has a variable number of generic parameters (variadic).
 * For completion, we want to group these as a single completion item to not clutter up the list
 * with often 25+ nearly identical results.
 *
 * We use the [representativeNumberOfValueArguments] as a performance measure to only show the completion item with
 * this number of arguments. This callable will be shown with the [renderedParameters] instead of its original parameter list.
 * All other callables with the same [callableId] but different number of arguments will be filtered out.
 */
internal class VariadicCallable(
    val callableId: CallableId,
    val renderedParameters: String,
    val representativeNumberOfValueArguments: Int,
)


/**
 * For performance reason, we statically register the variadic callables we want to group
 */
private val variadicCallableIds: Map<CallableId, VariadicCallable> = listOf(
    VariadicCallable(
        callableId = CallableId(StandardKotlinNames.context.parent(), StandardKotlinNames.context.shortName()),
        renderedParameters = "(a: A, ..., block: context(A, ...) () -> R)",
        representativeNumberOfValueArguments = 2,
    )
).associateBy { it.callableId }

/**
 * Returns a [VariadicCallable] in case the signature represents a registered variadic callable
 * found within [variadicCallableIds], otherwise returns `null`.
 */
internal fun KaCallableSignature<*>.toMatchingVariadicCallableOrNull(): VariadicCallable? {
    val callableId = callableId ?: return null
    return variadicCallableIds[callableId]
}

/**
 * Returns true if the [signature] belongs to a [VariadicCallable] that should be shown based on
 * [VariadicCallable.representativeNumberOfValueArguments] (i.e., it does have the representative number of arguments).
 * Returns true for non-variadic callables.
 */
context(_: KaSession)
internal fun isRepresentativeOrNonVariadicCallable(signature: KaCallableSignature<*>): Boolean {
    val variadicCallableId = signature.toMatchingVariadicCallableOrNull() ?: return true
    val functionSymbol = signature.symbol as? KaNamedFunctionSymbol ?: return true

    return variadicCallableId.representativeNumberOfValueArguments == functionSymbol.valueParameters.size
}