// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersCurrentScope
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.getStaticScopes
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtElement

internal open class FirClassifierCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<KotlinNameReferencePositionContext>(basicContext, priority) {

    context(KtAnalysisSession)
    protected open fun filterClassifiers(classifierSymbol: KtClassifierSymbol): Boolean = true

    context(KtAnalysisSession)
    protected open fun getImportingStrategy(classifierSymbol: KtClassifierSymbol): ImportStrategy =
        importStrategyDetector.detectImportStrategyForClassifierSymbol(classifierSymbol)

    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinNameReferencePositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)

        when (val receiver = positionContext.explicitReceiver) {
            null -> {
                completeWithoutReceiver(positionContext, visibilityChecker, weighingContext)
            }

            else -> {
                completeWithReceiver(receiver, visibilityChecker, weighingContext)
            }
        }
    }

    context(KtAnalysisSession)
    private fun completeWithReceiver(
        receiver: KtElement,
        visibilityChecker: CompletionVisibilityChecker,
        context: WeighingContext
    ) {
        val reference = receiver.reference() ?: return
        getStaticScopes(reference).forEach { scopeWithKind ->
            scopeWithKind.scope
                .getClassifierSymbols(scopeNameFilter)
                .filter { filterClassifiers(it) }
                .filter { visibilityChecker.isVisible(it) }
                .forEach {
                    val symbolOrigin = CompletionSymbolOrigin.Scope(scopeWithKind.kind)
                    addClassifierSymbolToCompletion(it, context, symbolOrigin, ImportStrategy.DoNothing)
                }
        }
    }

    context(KtAnalysisSession)
    private fun completeWithoutReceiver(
        positionContext: KotlinNameReferencePositionContext,
        visibilityChecker: CompletionVisibilityChecker,
        context: WeighingContext
    ) {
        val availableFromScope = mutableSetOf<KtClassifierSymbol>()
        getAvailableClassifiersCurrentScope(
            originalKtFile,
            positionContext.nameExpression,
            scopeNameFilter,
            visibilityChecker
        )
            .filter { filterClassifiers(it.symbol) }
            .forEach { symbolWithScopeKind ->
                val classifierSymbol = symbolWithScopeKind.symbol
                val symbolOrigin = CompletionSymbolOrigin.Scope(symbolWithScopeKind.scopeKind)
                availableFromScope += classifierSymbol
                addClassifierSymbolToCompletion(classifierSymbol, context, symbolOrigin, getImportingStrategy(classifierSymbol))
            }

        if (prefixMatcher.prefix.isNotEmpty()) {
            getAvailableClassifiersFromIndex(
                symbolFromIndexProvider,
                scopeNameFilter,
                visibilityChecker
            )
                .filter { it !in availableFromScope && filterClassifiers(it) }
                .forEach { classifierSymbol ->
                    val symbolOrigin = CompletionSymbolOrigin.Index
                    addClassifierSymbolToCompletion(classifierSymbol, context, symbolOrigin, getImportingStrategy(classifierSymbol))
                }
        }
    }
}

internal class FirAnnotationCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirClassifierCompletionContributor(basicContext, priority) {

    context(KtAnalysisSession)
    override fun filterClassifiers(classifierSymbol: KtClassifierSymbol): Boolean = when (classifierSymbol) {
        is KtAnonymousObjectSymbol -> false
        is KtTypeParameterSymbol -> false
        is KtNamedClassOrObjectSymbol -> when (classifierSymbol.classKind) {
            KtClassKind.ANNOTATION_CLASS -> true
            KtClassKind.ENUM_CLASS -> false
            KtClassKind.ANONYMOUS_OBJECT -> false
            KtClassKind.CLASS, KtClassKind.OBJECT, KtClassKind.COMPANION_OBJECT, KtClassKind.INTERFACE -> {
                // TODO show class if nested classifier is annotation class
                // classifierSymbol.getDeclaredMemberScope().getClassifierSymbols().any { filterClassifiers(it) }
                false
            }
        }

        is KtTypeAliasSymbol -> {
            val expendedClass = (classifierSymbol.expandedType as? KtNonErrorClassType)?.classSymbol
            expendedClass?.let { filterClassifiers(it) } == true
        }
    }
}

internal class FirClassifierReferenceCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirClassifierCompletionContributor(basicContext, priority) {

    context(KtAnalysisSession)
    override fun getImportingStrategy(classifierSymbol: KtClassifierSymbol): ImportStrategy = when (classifierSymbol) {
        is KtTypeParameterSymbol -> ImportStrategy.DoNothing
        is KtClassLikeSymbol -> {
            classifierSymbol.classIdIfNonLocal?.let { ImportStrategy.AddImport(it.asSingleFqName()) } ?: ImportStrategy.DoNothing
        }
    }
}
