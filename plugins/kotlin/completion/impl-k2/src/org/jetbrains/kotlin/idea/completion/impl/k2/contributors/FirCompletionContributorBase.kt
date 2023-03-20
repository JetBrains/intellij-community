// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.fir.codeInsight.HLIndexHelper
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.LookupElementSink
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirRawPositionCompletionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider.getCallableMetadata
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.priority
import org.jetbrains.kotlin.idea.completion.weighers.Weighers
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue
import org.jetbrains.kotlin.types.Variance

internal class FirCompletionContributorOptions(
    val priority: Int = 0
) {
    companion object {
        val DEFAULT = FirCompletionContributorOptions()
    }
}

internal abstract class FirCompletionContributorBase<C : FirRawPositionCompletionContext>(
    protected val basicContext: FirBasicCompletionContext,
    options: FirCompletionContributorOptions,
) {

    constructor(basicContext: FirBasicCompletionContext, priority: Int) :
            this(basicContext, FirCompletionContributorOptions(priority))

    protected val prefixMatcher: PrefixMatcher get() = basicContext.prefixMatcher
    protected val parameters: CompletionParameters get() = basicContext.parameters
    protected val sink: LookupElementSink = basicContext.sink.withPriority(options.priority)
    protected val originalKtFile: KtFile get() = basicContext.originalKtFile
    protected val fakeKtFile: KtFile get() = basicContext.fakeKtFile
    protected val project: Project get() = basicContext.project
    protected val targetPlatform: TargetPlatform get() = basicContext.targetPlatform
    protected val indexHelper: HLIndexHelper get() = basicContext.indexHelper
    protected val symbolFromIndexProvider: KtSymbolFromIndexProvider get() = basicContext.symbolFromIndexProvider
    protected val lookupElementFactory: KotlinFirLookupElementFactory get() = basicContext.lookupElementFactory
    protected val importStrategyDetector: ImportStrategyDetector get() = basicContext.importStrategyDetector
    protected val visibleScope = basicContext.visibleScope


    protected val scopeNameFilter: KtScopeNameFilter =
        { name -> !name.isSpecial && prefixMatcher.prefixMatches(name.identifier) }

    abstract fun KtAnalysisSession.complete(positionContext: C, weighingContext: WeighingContext)

    protected fun KtAnalysisSession.addSymbolToCompletion(expectedType: KtType?, symbol: KtSymbol) {
        if (symbol !is KtNamedSymbol) return
        // Don't offer any hidden deprecated items.
        if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return
        with(lookupElementFactory) {
            createLookupElement(symbol, importStrategyDetector, expectedType = expectedType)
                .let(sink::addElement)
        }
    }

    protected fun KtAnalysisSession.addClassifierSymbolToCompletion(
        symbol: KtClassifierSymbol,
        context: WeighingContext,
        importingStrategy: ImportStrategy = importStrategyDetector.detectImportStrategy(symbol),
    ) {
        if (symbol !is KtNamedSymbol) return
        // Don't offer any deprecated items that could leads to compile errors.
        if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return
        val lookup = with(lookupElementFactory) {
            when (symbol) {
                is KtClassLikeSymbol -> createLookupElementForClassLikeSymbol(symbol, importingStrategy)
                is KtTypeParameterSymbol -> createLookupElement(symbol, importStrategyDetector)
            }
        } ?: return
        lookup.availableWithoutImport = importingStrategy == ImportStrategy.DoNothing
        applyWeighers(context, lookup, symbol, KtSubstitutor.Empty(token))
        sink.addElement(lookup)
    }

    protected fun KtAnalysisSession.addCallableSymbolToCompletion(
        context: WeighingContext,
        symbol: KtCallableSymbol,
        options: CallableInsertionOptions,
        substitutor: KtSubstitutor = KtSubstitutor.Empty(token),
        priority: ItemPriority? = null,
        explicitReceiverTypeHint: KtType? = null,
    ) {
        val name = when (symbol) {
            is KtNamedSymbol -> symbol.name
            is KtConstructorSymbol -> (symbol.getContainingSymbol() as? KtNamedClassOrObjectSymbol)?.name
            else -> null
        } ?: return

        // Don't offer any deprecated items that could leads to compile errors.
        if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return
        val lookup = with(lookupElementFactory) {
            createCallableLookupElement(name, symbol, options, substitutor, context.expectedType)
        }
        priority?.let { lookup.priority = it }
        lookup.callableWeight = getCallableMetadata(context, symbol, substitutor)
        applyWeighers(context, lookup, symbol, substitutor)
        sink.addElement(lookup.adaptToReceiver(context, explicitReceiverTypeHint?.render(position = Variance.INVARIANT)))
    }

    private fun LookupElement.adaptToReceiver(weigherContext: WeighingContext, explicitReceiverTypeHint: String?): LookupElement {
        val explicitReceiverRange = weigherContext.explicitReceiver?.textRange
        val explicitReceiverText = weigherContext.explicitReceiver?.text
        return when (callableWeight?.kind) {
            // Make the text bold if it's immediate member of the receiver
            CallableMetadataProvider.CallableKind.ThisClassMember, CallableMetadataProvider.CallableKind.ThisTypeExtension ->
                object : LookupElementDecorator<LookupElement>(this) {
                    override fun renderElement(presentation: LookupElementPresentation) {
                        super.renderElement(presentation)
                        presentation.isItemTextBold = true
                    }
                }

            // TODO this code should be uncommented when KTIJ-20913 is fixed
            //// Make the text gray and insert type cast if the receiver type does not match.
            //is CallableMetadataProvider.CallableKind.ReceiverCastRequired -> object : LookupElementDecorator<LookupElement>(this) {
            //    override fun renderElement(presentation: LookupElementPresentation) {
            //        super.renderElement(presentation)
            //        presentation.itemTextForeground = KOTLIN_CAST_REQUIRED_COLOR
            //        // gray all tail fragments too:
            //        val fragments = presentation.tailFragments
            //        presentation.clearTail()
            //        for (fragment in fragments) {
            //            presentation.appendTailText(fragment.text, true)
            //        }
            //    }
            //
            //    override fun handleInsert(context: InsertionContext) {
            //        super.handleInsert(context)
            //        if (explicitReceiverRange == null || explicitReceiverText == null) return
            //        val castType = explicitReceiverTypeHint ?: return
            //        val newReceiver = "(${explicitReceiverText} as $castType)"
            //        context.document.replaceString(explicitReceiverRange.startOffset, explicitReceiverRange.endOffset, newReceiver)
            //        context.commitDocument()
            //        shortenReferencesInRange(
            //            context.file as KtFile,
            //            explicitReceiverRange.grown(newReceiver.length)
            //        )
            //    }
            //}

            else -> this
        }
    }

    protected fun KtExpression.reference() = when (this) {
        is KtDotQualifiedExpression -> selectorExpression?.mainReference
        else -> mainReference
    }

    private fun KtAnalysisSession.applyWeighers(
        context: WeighingContext,
        lookupElement: LookupElement,
        symbol: KtSymbol,
        substitutor: KtSubstitutor,
    ): LookupElement = lookupElement.apply {
        with(Weighers) { applyWeighsToLookupElement(context, lookupElement, symbol, substitutor) }
    }
}

internal var LookupElement.availableWithoutImport: Boolean by NotNullableUserDataProperty(Key("KOTLIN_AVAILABLE_FROM_CURRENT_SCOPE"), true)

internal fun <C : FirRawPositionCompletionContext> KtAnalysisSession.complete(
    contextContributor: FirCompletionContributorBase<C>,
    positionContext: C,
    weighingContext: WeighingContext
) {
    with(contextContributor) {
        complete(positionContext, weighingContext)
    }
}

internal var LookupElement.callableWeight by UserDataProperty(Key<CallableMetadataProvider.CallableMetadata>("KOTLIN_CALLABlE_WEIGHT"))
    private set

