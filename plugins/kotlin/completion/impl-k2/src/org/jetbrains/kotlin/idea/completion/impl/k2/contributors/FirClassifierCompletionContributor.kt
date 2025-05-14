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
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiers
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtOutsideTowerScopeKinds
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.staticScope
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.shortenCommand
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.utils.yieldIfNotNull

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
                            ).map { it.applyWeighs(weighingContext, symbolWithOrigin) }
                        }.forEach(sink::addElement)
                } else {
                    runChainCompletion(positionContext, explicitReceiver) { receiverExpression,
                                                                            positionContext,
                                                                            importingStrategy ->
                        val selectorExpression = receiverExpression.selectorExpression
                            ?: return@runChainCompletion emptySequence()

                        val reference = receiverExpression.reference()
                            ?: return@runChainCompletion emptySequence()

                        // TODO val weighingContext = WeighingContext.create(parameters, positionContext)
                        reference.resolveToSymbols()
                            .asSequence()
                            .mapNotNull { it.staticScope }
                            .flatMap { it.completeClassifiers(positionContext) }
                            .flatMap {
                                createClassifierLookupElement(
                                    classifierSymbol = it,
                                    expectedType = weighingContext.expectedType,
                                    importingStrategy = importingStrategy,
                                )
                            }.map { it.withPresentableText(selectorExpression.text + "." + it.lookupString) }
                    }
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
            .toMutableList()

        context.preferredSubtype?.symbol?.let { preferredSubtypeSymbol ->
            preferredSubtypeSymbol.staticScope?.let {
                if (!scopesToCheck.contains(it)) {
                    scopesToCheck.add(it)
                }
            }
            preferredSubtypeSymbol.containingSymbol?.staticScope?.let {
                if (!scopesToCheck.contains(it)) {
                    scopesToCheck.add(it)
                }
            }
        }

        val scopeClassifiers = scopesToCheck
            .asSequence()
            .flatMap { it.getAvailableClassifiers(positionContext, scopeNameFilter, visibilityChecker) }
            .filter { filterClassifiers(it.symbol) }
            .flatMap { symbolWithOrigin ->
                val classifierSymbol = symbolWithOrigin.symbol
                availableFromScope += classifierSymbol

                createClassifierLookupElement(
                    classifierSymbol = classifierSymbol,
                    expectedType = weighingContext.expectedType,
                    importingStrategy = getImportingStrategy(classifierSymbol),
                ).map {
                    it.applyWeighs(context, symbolWithOrigin)
                }
            }

        val indexClassifiers = if (prefixMatcher.prefix.isNotEmpty()) {
            getAvailableClassifiersFromIndex(
                positionContext = positionContext,
                parameters = parameters,
                symbolProvider = symbolFromIndexProvider,
                scopeNameFilter = scopeNameFilter,
                visibilityChecker = visibilityChecker,
            ).filter { it !in availableFromScope && filterClassifiers(it) }
                .flatMap { classifierSymbol ->
                    createClassifierLookupElement(
                        classifierSymbol = classifierSymbol,
                        expectedType = weighingContext.expectedType,
                        importingStrategy = getImportingStrategy(classifierSymbol),
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
    private fun createClassifierLookupElement(
        classifierSymbol: KaClassifierSymbol,
        expectedType: KaType? = null,
        importingStrategy: ImportStrategy = ImportStrategy.DoNothing,
    ): Sequence<LookupElementBuilder> = sequence {
        if (classifierSymbol is KaNamedClassSymbol
            && expectedType?.symbol == classifierSymbol
        ) {
            yieldIfNotNull(KotlinFirLookupElementFactory.createConstructorCallLookupElement(classifierSymbol, importingStrategy))
        }

        yieldIfNotNull(KotlinFirLookupElementFactory.createClassifierLookupElement(classifierSymbol, importingStrategy))
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

        val documentManager = PsiDocumentManager.getInstance(position.project)
        documentManager.commitDocument(document)
        documentManager.doPostponedOperationsAndUnblockDocument(document)

        return analyze(file) {
            collectPossibleReferenceShortenings(file, selection = rangeMarker.textRange)
        }
    }
}
