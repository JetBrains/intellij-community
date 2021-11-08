// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirImportDirectivePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.getStaticScope
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext.Companion.createEmptyWeighingContext

internal class FirImportDirectivePackageMembersCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCompletionContributorBase<FirImportDirectivePositionContext>(basicContext, priority) {
    override fun KtAnalysisSession.complete(positionContext: FirImportDirectivePositionContext) {
        val reference = positionContext.explicitReceiver?.reference() ?: return
        val scope = getStaticScope(reference) ?: return
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val weighingContext = createEmptyWeighingContext(basicContext.fakeKtFile)

        scope.getClassifierSymbols(scopeNameFilter)
            .filter { with(visibilityChecker) { isVisible(it) } }
            .forEach { addClassifierSymbolToCompletion(it, weighingContext, ImportStrategy.DoNothing) }

        scope.getCallableSymbols(scopeNameFilter)
            .filter { with(visibilityChecker) { isVisible(it) } }
            .forEach {
                addCallableSymbolToCompletion(
                    createEmptyWeighingContext(basicContext.fakeKtFile),
                    it,
                    CallableInsertionOptions(ImportStrategy.DoNothing, CallableInsertionStrategy.AsIdentifier)
                )
            }
    }
}