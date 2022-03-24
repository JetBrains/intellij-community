// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.checkers

import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirNameReferencePositionContext
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility

internal fun interface CompletionVisibilityChecker {
    fun KtAnalysisSession.isVisible(symbol: KtSymbolWithVisibility): Boolean

    fun KtAnalysisSession.isVisible(symbol: KtCallableSymbol): Boolean {
        return symbol !is KtSymbolWithVisibility || isVisible(symbol as KtSymbolWithVisibility)
    }

    fun KtAnalysisSession.isVisible(symbol: KtClassifierSymbol): Boolean {
        return symbol !is KtSymbolWithVisibility || isVisible(symbol as KtSymbolWithVisibility)
    }

    companion object {
        fun create(
            basicContext: FirBasicCompletionContext,
            positionContext: FirNameReferencePositionContext
        ): CompletionVisibilityChecker = CompletionVisibilityChecker {
            basicContext.parameters.invocationCount > 1 || isVisible(
                it,
                basicContext.originalKtFile.getFileSymbol(),
                positionContext.explicitReceiver,
                positionContext.position
            )
        }
    }
}