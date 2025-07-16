// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.completion.KOTLIN_CAST_REQUIRED_COLOR
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.api.serialization.ensureSerializable
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.doPostponedOperationsAndUnblockDocument
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionCallLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.CallableWeigher.callableWeight
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.util.OperatorNameConventions

internal abstract class FirCompletionContributorBase<C : KotlinRawPositionContext>(
    sink: LookupElementSink,
    priority: Int,
) : FirCompletionContributor<C> {

    protected val sink: LookupElementSink = sink
        .withPriority(priority)
        .withContributorClass(this@FirCompletionContributorBase.javaClass)

    protected val parameters: KotlinFirCompletionParameters
        get() = sink.parameters

    protected open val prefixMatcher: PrefixMatcher
        get() = sink.prefixMatcher

    protected val visibilityChecker = CompletionVisibilityChecker(parameters)

    protected val originalKtFile: KtFile // todo inline
        get() = parameters.originalFile

    protected val project: Project // todo remove entirely
        get() = originalKtFile.project

    protected val targetPlatform = originalKtFile.platform
    protected val symbolFromIndexProvider = KtSymbolFromIndexProvider(parameters.completionFile)
    protected val importStrategyDetector = ImportStrategyDetector(originalKtFile, project)

    protected val scopeNameFilter: (Name) -> Boolean =
        { name -> !name.isSpecial && prefixMatcher.prefixMatches(name.identifier) }

    // Prefix matcher that only matches if the completion item starts with the prefix.
    private val startOnlyMatcher by lazy { BetterPrefixMatcher(prefixMatcher, Int.MIN_VALUE) }
    private val startOnlyNameFilter: (Name) -> Boolean =
        { name -> !name.isSpecial && startOnlyMatcher.prefixMatches(name.identifier) }

    /**
     * Returns the name filter that should be used for index lookups.
     * If the prefix is less than 4 characters, we do not use the regular [scopeNameFilter] as it will
     * match occurrences anywhere in the name, which might yield too many results.
     * For other cases (unless the user invokes completion multiple times), this function will return
     * the [startOnlyNameFilter] that requires a match at the start of the lookup item's lookup strings.
     */
    internal fun getIndexNameFilter(): (Name) -> Boolean {
        return if (parameters.invocationCount >= 2 || sink.prefixMatcher.prefix.length > 3) {
            scopeNameFilter
        } else {
            startOnlyNameFilter
        }
    }

    context(KaSession)
    protected fun createOperatorLookupElement(
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

    context(KaSession)
    protected fun createCallableLookupElements(
        context: WeighingContext,
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

    // todo move out
    // todo move to the corresponding assignment
    protected fun LookupElementBuilder.adaptToExplicitReceiver(
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

    @Serializable
    internal data class AdaptToExplicitReceiverInsertionHandler(
        val insertHandler: SerializableInsertHandler?,
        val receiverTextRangeStart: Int,
        val receiverTextRangeEnd: Int,
        val receiverText: String,
        val typeText: String,
    ): SerializableInsertHandler {
        override fun handleInsert(
            context: InsertionContext,
            item: LookupElement
        ) {
            // Insert type cast if the receiver type does not match.

            val explicitReceiverRange = context.document
                .createRangeMarker(receiverTextRangeStart, receiverTextRangeEnd)
            insertHandler?.handleInsert(context, item)

            val newReceiver = "(${receiverText} as $typeText)"
            context.document.replaceString(explicitReceiverRange.startOffset, explicitReceiverRange.endOffset, newReceiver)
            context.commitDocument()

            shortenReferencesInRange(
                file = context.file as KtFile,
                selection = explicitReceiverRange.textRange.grown(newReceiver.length),
            )
            context.doPostponedOperationsAndUnblockDocument()
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
}

@ApiStatus.Experimental // todo reconsider
internal interface ChainCompletionContributor : FirCompletionContributor<KotlinNameReferencePositionContext> {

    context(KaSession)
    fun createChainedLookupElements(
        positionContext: KotlinNameReferencePositionContext,
        receiverExpression: KtDotQualifiedExpression,
        importingStrategy: ImportStrategy,
    ): Sequence<LookupElement>
}