// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.InsertionContext
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
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.LookupElementFactory
import org.jetbrains.kotlin.idea.completion.LookupElementSink
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirRawPositionCompletionContext
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.detectImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.priority
import org.jetbrains.kotlin.idea.completion.weighers.CallableWeigher
import org.jetbrains.kotlin.idea.completion.weighers.CallableWeigher.callableWeight
import org.jetbrains.kotlin.idea.completion.weighers.Weighers
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.fir.HLIndexHelper
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.shortenReferencesInRange
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue

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
    protected val lookupElementFactory: KotlinFirLookupElementFactory get() = basicContext.lookupElementFactory
    protected val visibleScope = basicContext.visibleScope


    protected val scopeNameFilter: KtScopeNameFilter =
        { name -> !name.isSpecial && prefixMatcher.prefixMatches(name.identifier) }

    abstract fun KtAnalysisSession.complete(positionContext: C)

    protected fun KtAnalysisSession.addSymbolToCompletion(expectedType: KtType?, symbol: KtSymbol) {
        if (symbol !is KtNamedSymbol) return
        // Don't offer any hidden deprecated items.
        if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return
        with(lookupElementFactory) {
            createLookupElement(symbol)
                .let(sink::addElement)
        }
    }

    protected fun KtAnalysisSession.addClassifierSymbolToCompletion(
        symbol: KtClassifierSymbol,
        context: WeighingContext,
        importingStrategy: ImportStrategy = detectImportStrategy(symbol),
    ) {
        if (symbol !is KtNamedSymbol) return
        // Don't offer any deprecated items that could leads to compile errors.
        if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return
        val lookup = with(lookupElementFactory) {
            when (symbol) {
                is KtClassLikeSymbol -> createLookupElementForClassLikeSymbol(symbol, importingStrategy)
                is KtTypeParameterSymbol -> createLookupElement(symbol)
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
        if (symbol !is KtNamedSymbol) return
        // Don't offer any deprecated items that could leads to compile errors.
        if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return
        val lookup = with(lookupElementFactory) {
            createCallableLookupElement(symbol, options, substitutor)
        }
        priority?.let { lookup.priority = it }
        applyWeighers(context, lookup, symbol, substitutor)
        sink.addElement(lookup.adaptToReceiver(context, explicitReceiverTypeHint?.render()))
    }

    private fun LookupElement.adaptToReceiver(weigherContext: WeighingContext, explicitReceiverTypeHint: String?): LookupElement {
        val explicitReceiverRange = weigherContext.explicitReceiver?.textRange
        val explicitReceiverText = weigherContext.explicitReceiver?.text
        return when (val kind = callableWeight?.kind) {
            // Make the text bold if it's immediate member of the receiver
            CallableWeigher.CallableWeightKind.ThisClassMember, CallableWeigher.CallableWeightKind.ThisTypeExtension ->
                object : LookupElementDecorator<LookupElement>(this) {
                    override fun renderElement(presentation: LookupElementPresentation) {
                        super.renderElement(presentation)
                        presentation.isItemTextBold = true
                    }
                }

            // Make the text gray and insert type cast if the receiver type does not match.
            is CallableWeigher.CallableWeightKind.ReceiverCastRequired -> object : LookupElementDecorator<LookupElement>(this) {
                override fun renderElement(presentation: LookupElementPresentation) {
                    super.renderElement(presentation)
                    presentation.itemTextForeground = LookupElementFactory.CAST_REQUIRED_COLOR
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
                    val castType = explicitReceiverTypeHint ?: kind.fullyQualifiedCastType
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
) {
    with(contextContributor) {
        complete(positionContext)
    }
}
