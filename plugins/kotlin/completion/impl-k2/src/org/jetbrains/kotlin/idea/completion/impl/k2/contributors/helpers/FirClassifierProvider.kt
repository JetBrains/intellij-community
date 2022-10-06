// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile

internal object FirClassifierProvider {
    fun KtAnalysisSession.getAvailableClassifiersCurrentScope(
        originalKtFile: KtFile,
        position: KtElement,
        scopeNameFilter: KtScopeNameFilter,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<KtClassifierSymbol> =
        originalKtFile.getScopeContextForPosition(position).scopes
            .getClassifierSymbols(scopeNameFilter)
            .filter { visibilityChecker.isVisible(it) }

    fun KtAnalysisSession.getAvailableClassifiersFromIndex(
        symbolProvider: KtSymbolFromIndexProvider,
        scopeNameFilter: KtScopeNameFilter,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<KtClassifierSymbol> {
        val kotlinDeclarations = symbolProvider.getKotlinClassesByNameFilter(
            scopeNameFilter,
            psiFilter = { ktClass -> ktClass !is KtEnumEntry }
        )
        val javaDeclarations = symbolProvider.getJavaClassesByNameFilter(scopeNameFilter)
        return (kotlinDeclarations + javaDeclarations)
            .filter { visibilityChecker.isVisible(it as KtSymbolWithVisibility) }
    }
}