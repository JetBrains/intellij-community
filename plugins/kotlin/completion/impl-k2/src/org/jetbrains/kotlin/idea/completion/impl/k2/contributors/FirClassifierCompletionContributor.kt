// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersCurrentScope
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.getStaticScope
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.psi.KtExpression

internal open class FirClassifierCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<FirNameReferencePositionContext>(basicContext, priority) {

    protected open fun KtAnalysisSession.filterClassifiers(classifierSymbol: KtClassifierSymbol): Boolean = true

    protected open fun KtAnalysisSession.getImportingStrategy(classifierSymbol: KtClassifierSymbol): ImportStrategy =
        importStrategyDetector.detectImportStrategy(classifierSymbol)

    override fun KtAnalysisSession.complete(positionContext: FirNameReferencePositionContext, weighingContext: WeighingContext) {
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

    private fun KtAnalysisSession.completeWithReceiver(
        receiver: KtExpression,
        visibilityChecker: CompletionVisibilityChecker,
        context: WeighingContext
    ) {
        val reference = receiver.reference() ?: return
        val scopeWithKind = getStaticScope(reference) ?: return
        scopeWithKind.scope
            .getClassifierSymbols(scopeNameFilter)
            .filter { filterClassifiers(it) }
            .filter { visibilityChecker.isVisible(it) }
            .forEach {
                val symbolOrigin = CompletionSymbolOrigin.Scope(scopeWithKind.kind)
                addClassifierSymbolToCompletion(it, context, symbolOrigin, ImportStrategy.DoNothing)
            }
    }

    private fun KtAnalysisSession.completeWithoutReceiver(
        positionContext: FirNameReferencePositionContext,
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

    override fun KtAnalysisSession.filterClassifiers(classifierSymbol: KtClassifierSymbol): Boolean = when (classifierSymbol) {
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

    override fun KtAnalysisSession.getImportingStrategy(classifierSymbol: KtClassifierSymbol): ImportStrategy =
        ImportStrategy.DoNothing
}
