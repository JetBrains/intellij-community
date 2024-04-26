// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KDocParameterNamePositionContext

internal open class FirKDocParameterNameContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<KDocParameterNamePositionContext>(basicContext, priority) {
    context(KtAnalysisSession)
    override fun complete(
        positionContext: KDocParameterNamePositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters
    ) {
        if (positionContext.explicitReceiver != null) return

        val section = positionContext.nameExpression.getContainingSection()
        val alreadyDocumentedParameters = section.findTagsByName(PARAMETER_TAG_NAME).map { it.getSubjectName() }.toSet()

        val ownerDeclaration = positionContext.nameExpression.getContainingDoc().owner ?: return
        val ownerDeclarationSymbol = ownerDeclaration.getSymbol()

        getParametersForKDoc(ownerDeclarationSymbol)
            .filter { (it.symbol as KtNamedSymbol).name.asString() !in alreadyDocumentedParameters }
            .forEach { addSymbolToCompletion(weighingContext, it) }
    }

    context(KtAnalysisSession)
    private fun addSymbolToCompletion(weighingContext: WeighingContext, symbolWithOrigin: KtSymbolWithOrigin) {
        val symbol = symbolWithOrigin.symbol
        val origin = symbolWithOrigin.origin
        when (symbol) {
            is KtTypeParameterSymbol -> addClassifierSymbolToCompletion(symbol, weighingContext, origin, ImportStrategy.DoNothing)
            is KtValueParameterSymbol -> addCallableSymbolToCompletion(
                weighingContext,
                symbol.asSignature(),
                CallableInsertionOptions(ImportStrategy.DoNothing, CallableInsertionStrategy.AsIdentifier),
                origin
            )
        }
    }

    context(KtAnalysisSession)
    private fun getParametersForKDoc(
        ownerDeclarationSymbol: KtDeclarationSymbol
    ): Sequence<KtSymbolWithOrigin> = sequence {
        yieldAll(ownerDeclarationSymbol.typeParameters)

        val valueParameters = when (ownerDeclarationSymbol) {
            is KtFunctionLikeSymbol -> ownerDeclarationSymbol.valueParameters

            is KtNamedClassOrObjectSymbol -> {
                val primaryConstructor = ownerDeclarationSymbol.getDeclaredMemberScope().getConstructors().firstOrNull { it.isPrimary }
                primaryConstructor?.valueParameters.orEmpty()
            }

            else -> emptyList()
        }
        yieldAll(valueParameters)
    }.map { symbol ->
        val symbolOrigin = CompletionSymbolOrigin.Scope(KtScopeKind.LocalScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX))
        KtSymbolWithOrigin(symbol, symbolOrigin)
    }

    companion object {
        private const val PARAMETER_TAG_NAME: String = "param"
    }
}