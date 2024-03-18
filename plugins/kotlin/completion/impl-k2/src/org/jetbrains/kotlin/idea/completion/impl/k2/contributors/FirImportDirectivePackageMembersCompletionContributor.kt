// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.getStaticScopes
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinImportDirectivePositionContext

internal class FirImportDirectivePackageMembersCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCompletionContributorBase<KotlinImportDirectivePositionContext>(basicContext, priority) {
    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinImportDirectivePositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        val reference = positionContext.explicitReceiver?.reference() ?: return
        getStaticScopes(reference).forEach { scopeWithKind ->
            val symbolOrigin = CompletionSymbolOrigin.Scope(scopeWithKind.kind)
            val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)

            scopeWithKind.scope.getClassifierSymbols(scopeNameFilter)
                .filter { visibilityChecker.isVisible(it) }
                .forEach { addClassifierSymbolToCompletion(it, weighingContext, symbolOrigin, ImportStrategy.DoNothing) }

            scopeWithKind.scope.getCallableSymbols(scopeNameFilter)
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