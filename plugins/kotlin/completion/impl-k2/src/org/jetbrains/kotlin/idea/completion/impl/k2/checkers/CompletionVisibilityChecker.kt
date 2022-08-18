// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.checkers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirNameReferencePositionContext

internal fun interface CompletionVisibilityChecker {
    context(KtAnalysisSession)
    fun isVisible(symbol: KtSymbolWithVisibility): Boolean

    context(KtAnalysisSession)
    fun isVisible(symbol: KtCallableSymbol): Boolean {
        return symbol !is KtSymbolWithVisibility || isVisible(symbol as KtSymbolWithVisibility)
    }

    context(KtAnalysisSession)
    fun isVisible(symbol: KtClassifierSymbol): Boolean {
        return symbol !is KtSymbolWithVisibility || isVisible(symbol as KtSymbolWithVisibility)
    }

    companion object {
        fun create(
            basicContext: FirBasicCompletionContext,
            positionContext: FirNameReferencePositionContext
        ): CompletionVisibilityChecker = object : CompletionVisibilityChecker {
            context(KtAnalysisSession)
            override fun isVisible(symbol: KtSymbolWithVisibility): Boolean {
                return basicContext.parameters.invocationCount > 1 || isVisible(
                    symbol,
                    basicContext.originalKtFile.getFileSymbol(),
                    positionContext.explicitReceiver,
                    positionContext.position
                )
            }
        }
    }
}