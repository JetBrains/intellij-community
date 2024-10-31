// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.completion.KOTLIN_CAST_REQUIRED_COLOR
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.CallableWeigher.addCallableWeight
import org.jetbrains.kotlin.idea.completion.weighers.CallableWeigher.callableWeight
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal abstract class FirCompletionContributorBase<C : KotlinRawPositionContext>(
    protected val parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int,
) : FirCompletionContributor<C> {

    protected open val prefixMatcher: PrefixMatcher
        get() = sink.prefixMatcher

    protected val visibilityChecker = CompletionVisibilityChecker(parameters)

    protected val sink: LookupElementSink = sink
        .withPriority(priority)
        .withContributorClass(this@FirCompletionContributorBase.javaClass)

    protected val originalKtFile: KtFile // todo inline
        get() = parameters.originalFile

    protected val project: Project // todo remove entirely
        get() = originalKtFile.project

    protected val targetPlatform = originalKtFile.platform
    protected val symbolFromIndexProvider = KtSymbolFromIndexProvider(parameters.completionFile)
    protected val importStrategyDetector = ImportStrategyDetector(originalKtFile, project)

    protected val scopeNameFilter: (Name) -> Boolean =
        { name -> !name.isSpecial && prefixMatcher.prefixMatches(name.identifier) }

    context(KaSession)
    protected fun createCallableLookupElements(
        context: WeighingContext,
        signature: KaCallableSignature<*>,
        options: CallableInsertionOptions,
        symbolOrigin: CompletionSymbolOrigin,
        withTrailingLambda: Boolean = false, // TODO find a better solution
    ): Sequence<LookupElementBuilder> {
        val namedSymbol = when (val symbol = signature.symbol) {
            is KaNamedSymbol -> symbol
            is KaConstructorSymbol -> symbol.containingDeclaration as? KaNamedClassSymbol
            else -> null
        } ?: return emptySequence()

        val shortName = namedSymbol.name

        return sequence {
            KotlinFirLookupElementFactory.createCallableLookupElement(
                name = shortName,
                signature = signature,
                options = options,
                expectedType = context.expectedType,
            ).let { yield(it) }

            if (withTrailingLambda) {
                KotlinFirLookupElementFactory.createCallableLookupElementWithTrailingLambda(
                    name = shortName,
                    signature = signature,
                    options = options,
                )?.let { yield(it) }
            }
        }.map { lookup ->
            lookup.addCallableWeight(context, signature, symbolOrigin)
            lookup.applyWeighs(context, KtSymbolWithOrigin(signature.symbol, symbolOrigin))
            lookup.applyKindToPresentation()
        }
    }

    // todo move out
    // todo move to the corresponding assignment
    protected fun LookupElementBuilder.adaptToExplicitReceiver(
        receiver: KtElement,
        typeText: String,
    ): LookupElement = withInsertHandler { context, item ->
        // Insert type cast if the receiver type does not match.
        insertHandler?.handleInsert(context, item)

        val explicitReceiverRange = receiver.textRange

        val newReceiver = "(${receiver.text} as $typeText)"
        context.document.replaceString(explicitReceiverRange.startOffset, explicitReceiverRange.endOffset, newReceiver)
        context.commitDocument()

        shortenReferencesInRange(
            file = context.file as KtFile,
            range = explicitReceiverRange.grown(newReceiver.length),
        )
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
