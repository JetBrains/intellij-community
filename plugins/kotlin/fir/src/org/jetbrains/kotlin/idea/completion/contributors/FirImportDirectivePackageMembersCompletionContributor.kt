// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.idea.completion.CallableImportStrategy
import org.jetbrains.kotlin.idea.completion.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirImportDirectivePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.getStaticScope
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession

internal class FirImportDirectivePackageMembersCompletionContributor(
    basicContext: FirBasicCompletionContext
) : FirCompletionContributorBase<FirImportDirectivePositionContext>(basicContext) {
    override fun KtAnalysisSession.complete(positionContext: FirImportDirectivePositionContext) {
        val reference = positionContext.explicitReceiver?.reference() ?: return
        val scope = getStaticScope(reference) ?: return
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)

        scope.getClassifierSymbols(scopeNameFilter)
            .filter { with(visibilityChecker) { isVisible(it) } }
            .forEach { addClassifierSymbolToCompletion(it, insertFqName = false) }

        scope.getCallableSymbols(scopeNameFilter)
            .filter { with(visibilityChecker) { isVisible(it) } }
            .forEach {
                addCallableSymbolToCompletion(
                    it,
                    importingStrategy = CallableImportStrategy.DoNothing,
                    insertionStrategy = CallableInsertionStrategy.AS_IDENTIFIER
                )
            }
    }
}