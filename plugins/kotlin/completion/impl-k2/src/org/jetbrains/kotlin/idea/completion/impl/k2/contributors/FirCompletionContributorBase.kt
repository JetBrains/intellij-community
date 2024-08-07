// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.KOTLIN_CAST_REQUIRED_COLOR
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.priority
import org.jetbrains.kotlin.idea.completion.weighers.CallableWeigher.callableWeight
import org.jetbrains.kotlin.idea.completion.weighers.Weighers
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighsToLookupElement
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.types.Variance

internal abstract class FirCompletionContributorBase<C : KotlinRawPositionContext>(
    protected val basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributor<C> {

    protected open val prefixMatcher: PrefixMatcher get() = basicContext.prefixMatcher

    protected val parameters: CompletionParameters get() = basicContext.parameters
    protected val sink: LookupElementSink = basicContext.sink.withPriority(priority)
    protected val originalKtFile: KtFile get() = basicContext.originalKtFile
    protected val project: Project get() = basicContext.project
    protected val targetPlatform: TargetPlatform get() = basicContext.targetPlatform
    protected val symbolFromIndexProvider: KtSymbolFromIndexProvider get() = basicContext.symbolFromIndexProvider
    protected val importStrategyDetector: ImportStrategyDetector get() = basicContext.importStrategyDetector

    protected val scopeNameFilter: (Name) -> Boolean =
        { name -> !name.isSpecial && prefixMatcher.prefixMatches(name.identifier) }

    context(KaSession)
    protected fun addClassifierSymbolToCompletion(
        symbol: KaClassifierSymbol,
        context: WeighingContext,
        symbolOrigin: CompletionSymbolOrigin,
        importingStrategy: ImportStrategy = importStrategyDetector.detectImportStrategyForClassifierSymbol(symbol),
    ) {
        if (symbol !is KaNamedSymbol) return

        val lookup = with(KotlinFirLookupElementFactory) {
            when (symbol) {
                is KaClassLikeSymbol -> createLookupElementForClassLikeSymbol(symbol, importingStrategy)
                is KaTypeParameterSymbol -> createLookupElement(symbol, importStrategyDetector)
            }
        } ?: return

        applyWeighsToLookupElement(context, lookup, KtSymbolWithOrigin(symbol, symbolOrigin))
        sink.addElement(lookup)
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    protected fun addCallableSymbolToCompletion(
        context: WeighingContext,
        signature: KaCallableSignature<*>,
        options: CallableInsertionOptions,
        symbolOrigin: CompletionSymbolOrigin,
        priority: ItemPriority? = null,
        explicitReceiverTypeHint: KaType? = null,
    ) {
        val namedSymbol = when (val symbol = signature.symbol) {
            is KaNamedSymbol -> symbol
            is KaConstructorSymbol -> symbol.containingDeclaration as? KaNamedClassSymbol
            else -> null
        } ?: return

        val lookup = KotlinFirLookupElementFactory.createCallableLookupElement(
            name = namedSymbol.name,
            signature = signature,
            options = options,
            expectedType = context.expectedType,
        ).apply { this.priority = priority }

        Weighers.applyWeighsToLookupElementForCallable(context, lookup, signature, symbolOrigin)
        sink.addElement(lookup.adaptToReceiver(context, explicitReceiverTypeHint?.render(position = Variance.INVARIANT)))
    }

    private fun LookupElement.adaptToReceiver(weigherContext: WeighingContext, explicitReceiverTypeHint: String?): LookupElement {
        val explicitReceiverRange = weigherContext.explicitReceiver?.textRange
        val explicitReceiverText = weigherContext.explicitReceiver?.text
        return when (callableWeight?.kind) {
            // Make the text bold if it's immediate member of the receiver
            CallableMetadataProvider.CallableKind.THIS_CLASS_MEMBER, CallableMetadataProvider.CallableKind.THIS_TYPE_EXTENSION ->
                object : LookupElementDecorator<LookupElement>(this) {
                    override fun renderElement(presentation: LookupElementPresentation) {
                        super.renderElement(presentation)
                        presentation.isItemTextBold = true
                    }
                }

            // Make the text gray and insert type cast if the receiver type does not match.
            CallableMetadataProvider.CallableKind.RECEIVER_CAST_REQUIRED -> object : LookupElementDecorator<LookupElement>(this) {
                override fun renderElement(presentation: LookupElementPresentation) {
                    super.renderElement(presentation)
                    presentation.itemTextForeground = KOTLIN_CAST_REQUIRED_COLOR
                    // gray all tail fragments too:
                    val fragments = presentation.tailFragments
                    presentation.clearTail()
                    for (fragment in fragments) {
                        presentation.appendTailText(fragment.text, true)
                    }
                }

                override fun handleInsert(context: InsertionContext) {
                    super.handleInsert(context)
                    if (explicitReceiverRange == null || explicitReceiverText == null) return
                    val castType = explicitReceiverTypeHint ?: return
                    val newReceiver = "(${explicitReceiverText} as $castType)"
                    context.document.replaceString(explicitReceiverRange.startOffset, explicitReceiverRange.endOffset, newReceiver)
                    context.commitDocument()
                    shortenReferencesInRange(
                        context.file as KtFile,
                        explicitReceiverRange.grown(newReceiver.length)
                    )
                }
            }

            else -> this
        }
    }
}
