// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.util.NlsSafe
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
import org.jetbrains.kotlin.idea.completion.KOTLIN_CAST_REQUIRED_COLOR
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.api.serialization.ensureSerializable
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.FirCompletionContributorBase.AdaptToExplicitReceiverInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.hasNoExplicitReceiver
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionCallLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.CallableWeigher.callableWeight
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.util.OperatorNameConventions

context(_: KaSession)
internal fun createCallableLookupElements(
    context: WeighingContext,
    parameters: KotlinFirCompletionParameters,
    signature: KaCallableSignature<*>,
    options: CallableInsertionOptions,
    scopeKind: KaScopeKind? = null,
    presentableText: @NlsSafe String? = null, // TODO decompose
    withTrailingLambda: Boolean = false, // TODO find a better solution
    aliasName: Name? = null
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
            expectedType = context.expectedType,
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
            parameters.position.parent?.parent is KtDotQualifiedExpression
        ) {
            createOperatorLookupElement(context, signature, options, namedSymbol)?.let { yield(it) }
        }
    }.map { builder ->
        if (presentableText == null) builder
        else builder.withPresentableText(presentableText)
    }.map { lookup ->
        if (!context.isPositionInsideImportOrPackageDirective) {
            lookup.callableWeight = CallableMetadataProvider.getCallableMetadata(
                signature = signature,
                scopeKind = scopeKind,
                actualReceiverTypes = context.actualReceiverTypes,
                isFunctionalVariableCall = callableSymbol is KaVariableSymbol
                        && lookup.`object` is FunctionCallLookupObject,
            )
        }

        lookup.applyWeighs(context, symbolWithOrigin)
        lookup.applyKindToPresentation()
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