// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKinds
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.TypeParameterLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KDocParameterNamePositionContext

internal open class FirKDocParameterNameContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KDocParameterNamePositionContext>(parameters, sink, priority) {

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
            .flatMap { createLookupElements(weighingContext, it) }
            .forEach(sink::addElement)
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createLookupElements(
        weighingContext: WeighingContext,
        symbolWithOrigin: KtSymbolWithOrigin,
    ): Sequence<LookupElement> {
        val origin = symbolWithOrigin.origin
        return when (val symbol = symbolWithOrigin.symbol) {
            is KaTypeParameterSymbol ->
                TypeParameterLookupElementFactory.createLookup(symbol)
                    .applyWeighs(weighingContext, KtSymbolWithOrigin(symbol, origin))
                    .let { sequenceOf(it) }

            is KaValueParameterSymbol -> createCallableLookupElements(
                context = weighingContext,
                signature = symbol.asSignature(),
                options = CallableInsertionOptions(ImportStrategy.DoNothing, CallableInsertionStrategy.AsIdentifier),
                symbolOrigin = origin,
            )

            else -> emptySequence()
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