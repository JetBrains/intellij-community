// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.resolveToSymbols
import org.jetbrains.kotlin.idea.completion.contributors.helpers.staticScope
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinImportDirectivePositionContext

internal class FirImportDirectivePackageMembersCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinImportDirectivePositionContext>(basicContext, priority) {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun complete(
        positionContext: KotlinImportDirectivePositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        positionContext.resolveToSymbols()
            .mapNotNull { it.staticScope }
            .forEach { scopeWithKind ->
                val symbolOrigin = CompletionSymbolOrigin.Scope(scopeWithKind.kind)
                val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)

                val classifiers = scopeWithKind.scope
                    .classifiers(scopeNameFilter)
                    .toList()
                classifiers
                    .filter { visibilityChecker.isVisible(it) }
                    .forEach { addClassifierSymbolToCompletion(it, weighingContext, symbolOrigin, ImportStrategy.DoNothing) }

                val callables = scopeWithKind.scope
                    .callables(scopeNameFilter)
                    .toList()
                callables
                    .filter { visibilityChecker.isVisible(it) }
                    .forEach {
                        addCallableSymbolToCompletion(
                            weighingContext,
                            it.asSignature(),
                            CallableInsertionOptions(ImportStrategy.DoNothing, CallableInsertionStrategy.AsIdentifier),
                            symbolOrigin,
                        )
                    }
            }
    }
}