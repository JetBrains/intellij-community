// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersCurrentScope
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.staticScope
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext

internal open class FirClassifierCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinNameReferencePositionContext>(parameters, sink, priority) {

    context(KaSession)
    protected open fun filterClassifiers(classifierSymbol: KaClassifierSymbol): Boolean = true

    context(KaSession)
    protected open fun getImportingStrategy(classifierSymbol: KaClassifierSymbol): ImportStrategy =
        importStrategyDetector.detectImportStrategyForClassifierSymbol(classifierSymbol)

    context(KaSession)
    override fun complete(
        positionContext: KotlinNameReferencePositionContext,
        weighingContext: WeighingContext,
    ) {
        when (val receiver = positionContext.explicitReceiver) {
            null -> completeWithoutReceiver(positionContext, weighingContext)

            else -> {
                receiver.reference()?.let {
                    completeWithReceiver(positionContext, weighingContext, it)
                } ?: emptySequence<LookupElement>()
            }
        }.forEach(sink::addElement)
    }

    context(KaSession)
    private fun completeWithReceiver(
        positionContext: KotlinNameReferencePositionContext,
        context: WeighingContext,
        reference: KtReference,
    ): Sequence<LookupElement> = reference
        .resolveToSymbols()
        .asSequence()
        .mapNotNull { it.staticScope }
        .flatMap { scopeWithKind ->
            scopeWithKind.scope
                .classifiers(scopeNameFilter)
                .filter { filterClassifiers(it) }
                .filter { visibilityChecker.isVisible(it, positionContext) }
                .mapNotNull { symbol ->
                    KotlinFirLookupElementFactory.createClassifierLookupElement(symbol)?.applyWeighs(
                        context = context,
                        symbolWithOrigin = KtSymbolWithOrigin(symbol, CompletionSymbolOrigin.Scope(scopeWithKind.kind))
                    )
                }
        }

    context(KaSession)
    private fun completeWithoutReceiver(
        positionContext: KotlinNameReferencePositionContext,
        context: WeighingContext,
    ): Sequence<LookupElement> {
        val availableFromScope = mutableSetOf<KaClassifierSymbol>()
        val scopeClassifiers = getAvailableClassifiersCurrentScope(
            positionContext = positionContext,
            originalKtFile = originalKtFile,
            position = positionContext.nameExpression,
            scopeNameFilter = scopeNameFilter,
            visibilityChecker = visibilityChecker,
        ).filter { filterClassifiers(it.symbol) }
            .mapNotNull { symbolWithScopeKind ->
                val classifierSymbol = symbolWithScopeKind.symbol
                availableFromScope += classifierSymbol

                KotlinFirLookupElementFactory.createClassifierLookupElement(
                    symbol = classifierSymbol,
                    importingStrategy = getImportingStrategy(classifierSymbol),
                )?.applyWeighs(
                    context = context,
                    symbolWithOrigin = KtSymbolWithOrigin(classifierSymbol, CompletionSymbolOrigin.Scope(symbolWithScopeKind.scopeKind)),
                )
            }

        val indexClassifiers = if (prefixMatcher.prefix.isNotEmpty()) {
            getAvailableClassifiersFromIndex(
                positionContext = positionContext,
                parameters = parameters,
                symbolProvider = symbolFromIndexProvider,
                scopeNameFilter = scopeNameFilter,
                visibilityChecker = visibilityChecker,
            ).filter { it !in availableFromScope && filterClassifiers(it) }
                .mapNotNull { classifierSymbol ->
                    KotlinFirLookupElementFactory.createClassifierLookupElement(
                        symbol = classifierSymbol,
                        importingStrategy = getImportingStrategy(classifierSymbol),
                    )?.applyWeighs(
                        context = context,
                        symbolWithOrigin = KtSymbolWithOrigin(classifierSymbol, CompletionSymbolOrigin.Index),
                    )
                }
        } else {
            emptySequence()
        }

        return scopeClassifiers +
                indexClassifiers
    }
}

internal class FirAnnotationCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirClassifierCompletionContributor(parameters, sink, priority) {

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
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int
) : FirClassifierCompletionContributor(parameters, sink, priority) {

    context(KaSession)
    override fun getImportingStrategy(classifierSymbol: KaClassifierSymbol): ImportStrategy = when (classifierSymbol) {
        is KaTypeParameterSymbol -> ImportStrategy.DoNothing
        is KaClassLikeSymbol -> {
            classifierSymbol.classId?.let { ImportStrategy.AddImport(it.asSingleFqName()) } ?: ImportStrategy.DoNothing
        }
    }
}
