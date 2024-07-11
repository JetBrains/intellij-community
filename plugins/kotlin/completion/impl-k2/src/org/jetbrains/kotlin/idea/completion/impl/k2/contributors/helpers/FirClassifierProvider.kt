// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithVisibility
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile

internal object FirClassifierProvider {
    context(KaSession)
    fun getAvailableClassifiersCurrentScope(
        originalKtFile: KtFile,
        position: KtElement,
        scopeNameFilter: (Name) -> Boolean,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<KaClassifierSymbolWithContainingScopeKind> =
        originalKtFile.scopeContext(position).scopes.asSequence().flatMap { scopeWithKind ->
            val classifiers = scopeWithKind.scope.classifiers(scopeNameFilter)
                .filter { visibilityChecker.isVisible(it) }
                .map { KaClassifierSymbolWithContainingScopeKind(it, scopeWithKind.kind) }
            classifiers
        }

    context(KaSession)
    fun getAvailableClassifiersFromIndex(
        symbolProvider: KtSymbolFromIndexProvider,
        scopeNameFilter: (Name) -> Boolean,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<KaClassifierSymbol> {
        val kotlinDeclarations = symbolProvider.getKotlinClassesByNameFilter(
            scopeNameFilter,
            psiFilter = { ktClass -> ktClass !is KtEnumEntry }
        )
        val javaDeclarations = symbolProvider.getJavaClassesByNameFilter(scopeNameFilter)
        return (kotlinDeclarations + javaDeclarations)
            .filter { visibilityChecker.isVisible(it as KaSymbolWithVisibility) }
    }
}