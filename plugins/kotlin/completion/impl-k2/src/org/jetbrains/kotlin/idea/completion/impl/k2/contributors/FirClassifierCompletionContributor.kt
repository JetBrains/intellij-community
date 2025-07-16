// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.SuspendingLookupElementRenderer
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKind
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiers
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.getAliasNameIfExists
import org.jetbrains.kotlin.idea.completion.contributors.helpers.staticScope
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.shortenCommand
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.utils.yieldIfNotNull

internal open class FirClassifierCompletionContributor(
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinNameReferencePositionContext>(sink, priority),
    ChainCompletionContributor {

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
        when (val explicitReceiver = positionContext.explicitReceiver) {
            null -> completeWithoutReceiver(weighingContext, positionContext, weighingContext)
                .forEach(sink::addElement)

            else -> {
                val reference = explicitReceiver.reference()
                    ?: return

                val symbols = reference.resolveToSymbols()
                if (symbols.isNotEmpty()) {
                    symbols.asSequence()
                        .mapNotNull { it.staticScope }
                        .flatMap { scopeWithKind ->
                            scopeWithKind.completeClassifiers(positionContext)
                                .map { KtSymbolWithOrigin(it, scopeWithKind.kind) }
                        }.flatMap { symbolWithOrigin ->
                            createClassifierLookupElement(
                                classifierSymbol = symbolWithOrigin.symbol,
                                expectedType = weighingContext.expectedType,
                                positionContext = positionContext,
                            ).map { it.applyWeighs(weighingContext, symbolWithOrigin) }
                        }.forEach(sink::addElement)
                } else {
                    sink.registerChainContributor(this@FirClassifierCompletionContributor)
                }
            }
        }
    }

    context(KaSession)
    private fun KaScopeWithKind.completeClassifiers(
        positionContext: KotlinNameReferencePositionContext,
    ): Sequence<KaClassifierSymbol> = scope
        .classifiers(scopeNameFilter)
        .filter { filterClassifiers(it) }
        .filter { visibilityChecker.isVisible(it, positionContext) }

    context(KaSession)
    private fun completeWithoutReceiver(
        weighingContext: WeighingContext,
        positionContext: KotlinNameReferencePositionContext,
        context: WeighingContext,
    ): Sequence<LookupElementBuilder> {
        val availableFromScope = mutableSetOf<KaClassifierSymbol>()
        val scopesToCheck = context.scopeContext
            .scopes
            .toMutableSet()

        fun addContainingScopesToCheck(symbol: KaClassifierSymbol) {
            symbol.staticScope?.let(scopesToCheck::add)
            symbol.containingSymbol?.staticScope?.let(scopesToCheck::add)
        }

        context.expectedType?.symbol?.takeIf { it.modality == KaSymbolModality.SEALED }?.let(::addContainingScopesToCheck)
        context.preferredSubtype?.symbol?.let(::addContainingScopesToCheck)

        val scopeClassifiers = scopesToCheck
            .asSequence()
            .flatMap { scope ->
                scope.getAvailableClassifiers(positionContext, scopeNameFilter, visibilityChecker).map { classifier -> scope to classifier }
            }
            .filter { (_, classifier) -> filterClassifiers(classifier.symbol) }
            .flatMap { (scope, symbolWithOrigin) ->
                val aliasName = scope.getAliasNameIfExists(symbolWithOrigin.symbol)

                val classifierSymbol = symbolWithOrigin.symbol
                if (aliasName == null) {
                    // For import aliases, we should still show the original symbol from the index.
                    availableFromScope += classifierSymbol
                }

                createClassifierLookupElement(
                    classifierSymbol = classifierSymbol,
                    expectedType = weighingContext.expectedType,
                    importingStrategy = getImportingStrategy(classifierSymbol),
                    positionContext = positionContext,
                    aliasName = aliasName,
                ).map {
                    it.applyWeighs(context, symbolWithOrigin)
                }
            }

        val indexClassifiers = if (prefixMatcher.prefix.isNotEmpty()) {
            getAvailableClassifiersFromIndex(
                positionContext = positionContext,
                parameters = parameters,
                symbolProvider = symbolFromIndexProvider,
                scopeNameFilter = getIndexNameFilter(),
                visibilityChecker = visibilityChecker,
            ).filter { it !in availableFromScope && filterClassifiers(it) }
                .flatMap { classifierSymbol ->
                    createClassifierLookupElement(
                        classifierSymbol = classifierSymbol,
                        expectedType = weighingContext.expectedType,
                        importingStrategy = getImportingStrategy(classifierSymbol),
                        positionContext = positionContext,
                    ).map {
                        it.applyWeighs(
                            context = context,
                            symbolWithOrigin = KtSymbolWithOrigin(classifierSymbol),
                        )
                    }
                }
        } else {
            emptySequence()
        }

        return scopeClassifiers +
                indexClassifiers
    }

    context(KaSession)
    override fun createChainedLookupElements(
        positionContext: KotlinNameReferencePositionContext,
        receiverExpression: KtDotQualifiedExpression,
        importingStrategy: ImportStrategy
    ): Sequence<LookupElement> {
        val selectorExpression = receiverExpression.selectorExpression ?: return emptySequence()

        val reference = receiverExpression.reference() ?: return emptySequence()

        val weighingContext = WeighingContext.create(parameters, positionContext)
        return reference.resolveToSymbols()
            .asSequence()
            .mapNotNull { it.staticScope }
            .flatMap { it.completeClassifiers(positionContext) }
            .flatMap {
                createClassifierLookupElement(
                    classifierSymbol = it,
                    expectedType = weighingContext.expectedType,
                    importingStrategy = importingStrategy,
                    positionContext = positionContext,
                )
            }.map { it.withPresentableText(selectorExpression.text + "." + it.lookupString) }
    }


    context(KaSession)
    private fun createClassifierLookupElement(
        classifierSymbol: KaClassifierSymbol,
        positionContext: KotlinRawPositionContext,
        expectedType: KaType? = null,
        aliasName: Name? = null,
        importingStrategy: ImportStrategy = ImportStrategy.DoNothing,
    ): Sequence<LookupElementBuilder> = sequence {
        if (classifierSymbol is KaNamedClassSymbol &&
            classifierSymbol.modality != KaSymbolModality.SEALED &&
            classifierSymbol.modality != KaSymbolModality.ABSTRACT &&
            expectedType != null &&
            classifierSymbol.defaultType.isSubtypeOf(expectedType)
        ) {
            val constructorSymbols = classifierSymbol.memberScope.constructors
                .filter { visibilityChecker.isVisible(it, positionContext) }
                .toList()

            yieldIfNotNull(
                KotlinFirLookupElementFactory.createConstructorCallLookupElement(
                    containingSymbol = classifierSymbol,
                    visibleConstructorSymbols = constructorSymbols,
                    importingStrategy = importingStrategy,
                    aliasName = aliasName
                )
            )
        }

        yieldIfNotNull(KotlinFirLookupElementFactory.createClassifierLookupElement(classifierSymbol, importingStrategy, aliasName))
    }.map { builder ->
        when (importingStrategy) {
            is ImportStrategy.InsertFqNameAndShorten -> {
                val expensiveRenderer = ClassifierLookupElementRenderer(
                    fqName = importingStrategy.fqName,
                    position = parameters.position,
                )
                builder.withExpensiveRenderer(expensiveRenderer)
            }

            else -> builder
        }
    }
}

internal class FirAnnotationCompletionContributor(
    sink: LookupElementSink,
    priority: Int = 0,
) : FirClassifierCompletionContributor(sink, priority) {

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
    sink: LookupElementSink,
    priority: Int
) : FirClassifierCompletionContributor(sink, priority) {

    context(KaSession)
    override fun getImportingStrategy(classifierSymbol: KaClassifierSymbol): ImportStrategy = when (classifierSymbol) {
        is KaTypeParameterSymbol -> ImportStrategy.DoNothing
        is KaClassLikeSymbol -> {
            classifierSymbol.classId?.let { ImportStrategy.AddImport(it.asSingleFqName()) } ?: ImportStrategy.DoNothing
        }
    }
}

private class ClassifierLookupElementRenderer(
    private val fqName: FqName,
    position: PsiElement,
) : SuspendingLookupElementRenderer<LookupElement>() {

    private val position = SmartPointerManager
        .getInstance(position.project)
        .createSmartPsiFileRangePointer(
            /* file = */ position.containingFile,
            /* range = */ position.textRange,
        )

    /**
     * @see [com.intellij.codeInsight.lookup.impl.AsyncRendering.scheduleRendering]
     */
    override suspend fun renderElementSuspending(
        element: LookupElement,
        presentation: LookupElementPresentation,
    ) {
        element.renderElement(presentation)

        element.shortenCommand = readAction {
            collectPossibleReferenceShortenings()
        }
    }

    @RequiresReadLock
    private fun collectPossibleReferenceShortenings(): ShortenCommand? {
        val file = position.element
            ?.copy() as? KtFile
            ?: return null

        val document = file.viewProvider
            .document

        val rangeMarker = position.psiRange
            ?.let { document.createRangeMarker(it.startOffset, it.endOffset) }
            ?: return null

        document.replaceString(
            /* startOffset = */ rangeMarker.startOffset,
            /* endOffset = */ rangeMarker.endOffset,
            /* s = */ fqName.render(),
        )

        PsiDocumentManager.getInstance(position.project)
            .commitDocument(document)

        return analyze(file) {
            collectPossibleReferenceShortenings(file, selection = rangeMarker.textRange)
        }
    }
}
