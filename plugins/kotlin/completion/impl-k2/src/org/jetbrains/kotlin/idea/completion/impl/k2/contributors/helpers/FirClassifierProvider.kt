// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.codeInsight.completion.AllClassesGetter
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
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
        parameters: CompletionParameters,
        symbolProvider: KtSymbolFromIndexProvider,
        scopeNameFilter: (Name) -> Boolean,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<KaClassifierSymbol> {
        val kotlinDeclarations = completeKotlinClasses(symbolProvider, scopeNameFilter, visibilityChecker)
        val javaDeclarations = completeJavaClasses(parameters, symbolProvider, scopeNameFilter)
        return (kotlinDeclarations + javaDeclarations)
            .filter { visibilityChecker.isVisible(it) }
    }
}

context(KaSession)
private fun completeKotlinClasses(
    symbolProvider: KtSymbolFromIndexProvider,
    scopeNameFilter: (Name) -> Boolean,
    visibilityChecker: CompletionVisibilityChecker,
): Sequence<KaClassLikeSymbol> = symbolProvider.getKotlinClassesByNameFilter(
    scopeNameFilter,
    psiFilter = { ktClass ->
        if (ktClass is KtEnumEntry) return@getKotlinClassesByNameFilter false
        if (ktClass.getClassId() == null) return@getKotlinClassesByNameFilter true
        !visibilityChecker.isDefinitelyInvisibleByPsi(ktClass)
    }
)

context(KaSession)
private fun completeJavaClasses(
    parameters: CompletionParameters,
    symbolProvider: KtSymbolFromIndexProvider,
    scopeNameFilter: (Name) -> Boolean
): Sequence<KaNamedClassSymbol> = symbolProvider.getJavaClassesByNameFilter(scopeNameFilter) { psiClass ->
    val filterOutPossiblyPossiblyInvisibleClasses = parameters.invocationCount < 2
    if (filterOutPossiblyPossiblyInvisibleClasses) {
        if (PsiReferenceExpressionImpl.seemsScrambled(psiClass) || JavaCompletionProcessor.seemsInternal(psiClass)) {
            return@getJavaClassesByNameFilter false
        }
    }
    AllClassesGetter.isAcceptableInContext(parameters.position, psiClass, filterOutPossiblyPossiblyInvisibleClasses, false)
}