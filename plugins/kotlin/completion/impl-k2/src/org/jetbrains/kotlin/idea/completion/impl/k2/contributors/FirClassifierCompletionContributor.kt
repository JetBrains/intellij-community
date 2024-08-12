// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersCurrentScope
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.staticScope
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtElement

internal open class FirClassifierCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinNameReferencePositionContext>(basicContext, priority) {

    context(KaSession)
    protected open fun filterClassifiers(classifierSymbol: KaClassifierSymbol): Boolean = true

    context(KaSession)
    protected open fun getImportingStrategy(classifierSymbol: KaClassifierSymbol): ImportStrategy =
        importStrategyDetector.detectImportStrategyForClassifierSymbol(classifierSymbol)

    context(KaSession)
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

    context(KaSession)
    private fun completeWithReceiver(
        receiver: KtElement,
        visibilityChecker: CompletionVisibilityChecker,
        context: WeighingContext
    ) {
        val symbols = receiver.reference()
            ?.resolveToSymbols()
            ?: return

        symbols.asSequence()
            .mapNotNull { it.staticScope }
            .forEach { scopeWithKind ->
            scopeWithKind.scope
                .classifiers(scopeNameFilter)
                .filter { filterClassifiers(it) }
                .filter { visibilityChecker.isVisible(it) }
                .forEach {
                    val symbolOrigin = CompletionSymbolOrigin.Scope(scopeWithKind.kind)
                    addClassifierSymbolToCompletion(it, context, symbolOrigin, ImportStrategy.DoNothing)
                }
        }
    }

    context(KaSession)
    private fun completeWithoutReceiver(
        positionContext: KotlinNameReferencePositionContext,
        visibilityChecker: CompletionVisibilityChecker,
        context: WeighingContext
    ) {
        val availableFromScope = mutableSetOf<KaClassifierSymbol>()
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
    priority: Int = 0,
) : FirClassifierCompletionContributor(basicContext, priority) {

    context(KaSession)
    override fun filterClassifiers(classifierSymbol: KaClassifierSymbol): Boolean = when (classifierSymbol) {
        is KaAnonymousObjectSymbol -> false
        is KaTypeParameterSymbol -> false
        is KaNamedClassSymbol -> when (classifierSymbol.classKind) {
            KaClassKind.ANNOTATION_CLASS -> true
            KaClassKind.ENUM_CLASS -> false
            KaClassKind.ANONYMOUS_OBJECT -> false
            KaClassKind.CLASS, KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT, KaClassKind.INTERFACE -> {
                classifierSymbol.staticDeclaredMemberScope.classifiers.any { filterClassifiers(it) }
            }
        }

        is KaTypeAliasSymbol -> {
            val expendedClass = (classifierSymbol.expandedType as? KaClassType)?.symbol
            expendedClass?.let { filterClassifiers(it) } == true
        }
    }
}

internal class FirClassifierReferenceCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirClassifierCompletionContributor(basicContext, priority) {

    context(KaSession)
    override fun getImportingStrategy(classifierSymbol: KaClassifierSymbol): ImportStrategy = when (classifierSymbol) {
        is KaTypeParameterSymbol -> ImportStrategy.DoNothing
        is KaClassLikeSymbol -> {
            classifierSymbol.classId?.let { ImportStrategy.AddImport(it.asSingleFqName()) } ?: ImportStrategy.DoNothing
        }
    }
}
