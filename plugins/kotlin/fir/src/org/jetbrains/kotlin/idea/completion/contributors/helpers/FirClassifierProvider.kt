// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.fir.HLIndexHelper
import org.jetbrains.kotlin.idea.util.classIdIfNonLocal
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile

internal object FirClassifierProvider {
    fun KtAnalysisSession.getAvailableClassifiersCurrentScope(
        originalKtFile: KtFile,
        position: KtElement,
        scopeNameFilter: KtScopeNameFilter,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<KtClassifierSymbol> = sequence {
        yieldAll(
            originalKtFile.getScopeContextForPosition(position).scopes
                .getClassifierSymbols(scopeNameFilter)
                .filter { with(visibilityChecker) { isVisible(it) } }
        )
    }

    fun KtAnalysisSession.getAvailableClassifiersFromIndex(
        indexHelper: HLIndexHelper,
        scopeNameFilter: KtScopeNameFilter,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<KtClassifierSymbol> = sequence {
        yieldAll(
            indexHelper.getKotlinClasses(scopeNameFilter, psiFilter = { it !is KtEnumEntry }).asSequence()
                .map { it.getSymbol() as KtClassifierSymbol }
                .filter { with(visibilityChecker) { isVisible(it) } }
        )

        yieldAll(
            indexHelper.getJavaClasses(scopeNameFilter).asSequence()
                .mapNotNull { it.classIdIfNonLocal()?.getCorrespondingToplevelClassOrObjectSymbol() }
                .filter { with(visibilityChecker) { isVisible(it) } }
        )
    }
}