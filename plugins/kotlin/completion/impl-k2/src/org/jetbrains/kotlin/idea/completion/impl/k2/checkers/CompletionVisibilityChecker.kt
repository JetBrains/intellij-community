// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.checkers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.idea.base.utils.fqname.isJavaClassNotToBeUsedInKotlin
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.util.positionContext.KDocNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue

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
            positionContext: KotlinRawPositionContext
        ): CompletionVisibilityChecker = object : CompletionVisibilityChecker {
            context(KtAnalysisSession)
            override fun isVisible(symbol: KtSymbolWithVisibility): Boolean {
                if (positionContext is KDocNameReferencePositionContext) return true

                // Don't offer any deprecated items that could lead to compile errors.
                if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return false

                if (basicContext.parameters.invocationCount > 1) return true

                if (symbol is KtClassLikeSymbol) {
                    val classId = (symbol as? KtClassLikeSymbol)?.classIdIfNonLocal
                    if (classId?.asSingleFqName()?.isJavaClassNotToBeUsedInKotlin() == true) return false
                }

                return isVisible(
                    symbol,
                    basicContext.originalKtFile.getFileSymbol(),
                    (positionContext as? KotlinSimpleNameReferencePositionContext)?.explicitReceiver,
                    positionContext.position
                )
            }
        }
    }
}