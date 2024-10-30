// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKinds
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KDocParameterNamePositionContext

internal open class FirKDocParameterNameContributor(
    visibilityChecker: CompletionVisibilityChecker,
    priority: Int = 0,
) : FirCompletionContributorBase<KDocParameterNamePositionContext>(visibilityChecker, priority) {

    context(KaSession)
    override fun complete(
        positionContext: KDocParameterNamePositionContext,
        weighingContext: WeighingContext,
    ) {
        if (positionContext.explicitReceiver != null) return

        val section = positionContext.nameExpression.getContainingSection()
        val alreadyDocumentedParameters = section.findTagsByName(PARAMETER_TAG_NAME).map { it.getSubjectName() }.toSet()

        val ownerDeclaration = positionContext.nameExpression.getContainingDoc().owner ?: return
        val ownerDeclarationSymbol = ownerDeclaration.symbol

        getParametersForKDoc(ownerDeclarationSymbol)
            .filter { (it.symbol as KaNamedSymbol).name.asString() !in alreadyDocumentedParameters }
            .forEach { addSymbolToCompletion(weighingContext, it) }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun addSymbolToCompletion(weighingContext: WeighingContext, symbolWithOrigin: KtSymbolWithOrigin) {
        val symbol = symbolWithOrigin.symbol
        val origin = symbolWithOrigin.origin
        when (symbol) {
            is KaTypeParameterSymbol -> addClassifierSymbolToCompletion(symbol, weighingContext, origin, ImportStrategy.DoNothing)
            is KaValueParameterSymbol -> addCallableSymbolToCompletion(
                weighingContext,
                symbol.asSignature(),
                CallableInsertionOptions(ImportStrategy.DoNothing, CallableInsertionStrategy.AsIdentifier),
                origin
            )
        }
    }

    context(KaSession)
    private fun getParametersForKDoc(
        ownerDeclarationSymbol: KaDeclarationSymbol
    ): Sequence<KtSymbolWithOrigin> = sequence {
        @OptIn(KaExperimentalApi::class)
        yieldAll(ownerDeclarationSymbol.typeParameters)

        val valueParameters = when (ownerDeclarationSymbol) {
            is KaFunctionSymbol -> ownerDeclarationSymbol.valueParameters

            is KaNamedClassSymbol -> {
                val primaryConstructor = ownerDeclarationSymbol.declaredMemberScope.constructors.firstOrNull { it.isPrimary }
                primaryConstructor?.valueParameters.orEmpty()
            }

            else -> emptyList()
        }
        yieldAll(valueParameters)
    }.map { symbol ->
        val symbolOrigin = CompletionSymbolOrigin.Scope(KaScopeKinds.LocalScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX))
        KtSymbolWithOrigin(symbol, symbolOrigin)
    }

    companion object {
        private const val PARAMETER_TAG_NAME: String = "param"
    }
}