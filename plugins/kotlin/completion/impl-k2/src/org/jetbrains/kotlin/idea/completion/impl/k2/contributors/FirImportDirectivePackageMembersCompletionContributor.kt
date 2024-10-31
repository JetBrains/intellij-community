// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.resolveReceiverToSymbols
import org.jetbrains.kotlin.idea.completion.contributors.helpers.staticScope
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinImportDirectivePositionContext

internal class FirImportDirectivePackageMembersCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinImportDirectivePositionContext>(parameters, sink, priority) {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun complete(
        positionContext: KotlinImportDirectivePositionContext,
        weighingContext: WeighingContext,
    ) {
        positionContext.resolveReceiverToSymbols()
            .mapNotNull { it.staticScope }
            .forEach { scopeWithKind ->
                val symbolOrigin = CompletionSymbolOrigin.Scope(scopeWithKind.kind)

                scopeWithKind.scope
                    .classifiers(scopeNameFilter)
                    .filter { visibilityChecker.isVisible(it, positionContext) }
                    .mapNotNull { symbol ->
                        KotlinFirLookupElementFactory.createClassifierLookupElement(symbol)?.applyWeighs(
                            context = weighingContext,
                            symbolWithOrigin = KtSymbolWithOrigin(symbol, symbolOrigin)
                        )
                    }.forEach(sink::addElement)

                scopeWithKind.scope
                    .callables(scopeNameFilter)
                    .filter { visibilityChecker.isVisible(it, positionContext) }
                    .map { it.asSignature() }
                    .forEach {
                        addCallableSymbolToCompletion(
                            context = weighingContext,
                            signature = it,
                            options = CallableInsertionOptions(ImportStrategy.DoNothing, CallableInsertionStrategy.AsIdentifier),
                            symbolOrigin = symbolOrigin,
                        )
                    }
            }
    }
}