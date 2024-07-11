// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.checkers

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.base.utils.fqname.isJavaClassNotToBeUsedInKotlin
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.util.positionContext.KDocNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue

internal fun interface CompletionVisibilityChecker {
    context(KaSession)
    fun isVisible(symbol: KaDeclarationSymbol): Boolean

    context(KaSession)
    fun isVisible(symbol: KaCallableSymbol): Boolean {
        return isVisible(symbol as KaDeclarationSymbol)
    }

    context(KaSession)
    fun isVisible(symbol: KaClassifierSymbol): Boolean {
        return isVisible(symbol as KaDeclarationSymbol)
    }

    companion object {
        @OptIn(KaExperimentalApi::class)
        fun create(
            basicContext: FirBasicCompletionContext,
            positionContext: KotlinRawPositionContext
        ): CompletionVisibilityChecker = object : CompletionVisibilityChecker {
            context(KaSession)
            override fun isVisible(symbol: KaDeclarationSymbol): Boolean {
                if (positionContext is KDocNameReferencePositionContext) return true

                // Don't offer any deprecated items that could lead to compile errors.
                if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return false

                if (basicContext.parameters.invocationCount > 1) return true

                if (symbol is KaClassLikeSymbol) {
                    val classId = (symbol as? KaClassLikeSymbol)?.classId
                    if (classId?.asSingleFqName()?.isJavaClassNotToBeUsedInKotlin() == true) return false
                }

                if (basicContext.originalKtFile is KtCodeFragment) return true

                return isVisible(
                    symbol,
                    basicContext.originalKtFile.symbol,
                    (positionContext as? KotlinSimpleNameReferencePositionContext)?.explicitReceiver,
                    positionContext.position
                )
            }
        }
    }
}