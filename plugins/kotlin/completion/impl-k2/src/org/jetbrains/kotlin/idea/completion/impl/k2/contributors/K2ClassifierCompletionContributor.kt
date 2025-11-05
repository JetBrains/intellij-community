// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.SuspendingLookupElementRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKind
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.memberScope
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbols
import org.jetbrains.kotlin.analysis.api.components.staticDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiers
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.getAliasNameIfExists
import org.jetbrains.kotlin.idea.completion.contributors.helpers.staticScope
import org.jetbrains.kotlin.idea.completion.impl.k2.*
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.shortenCommand
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.utils.yieldIfNotNull

internal open class K2ClassifierCompletionContributor : K2CompletionContributor<KotlinNameReferencePositionContext>(
    KotlinNameReferencePositionContext::class
), K2ChainCompletionContributor {

    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun filterClassifiers(
        classifierSymbol: KaClassifierSymbol
    ): Boolean {
        if (context.positionContext !is KotlinAnnotationTypeNameReferencePositionContext) return true
        return when (classifierSymbol) {
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

    context(_: KaSession)
    private fun getImportingStrategy(
        context: K2CompletionContext<KotlinNameReferencePositionContext>,
        importStrategyDetector: ImportStrategyDetector,
        classifierSymbol: KaClassifierSymbol,
        aliasName: Name?,
    ): ImportStrategy {
        if (aliasName != null) {
            // If we are using the alias, then we do not need to import anything
            return ImportStrategy.DoNothing
        }
        return if (context.positionContext is KotlinCallableReferencePositionContext) {
            when (classifierSymbol) {
                is KaTypeParameterSymbol -> ImportStrategy.DoNothing
                is KaClassLikeSymbol -> {
                    classifierSymbol.classId?.let { ImportStrategy.AddImport(it.asSingleFqName()) } ?: ImportStrategy.DoNothing
                }
            }
        } else {
            importStrategyDetector.detectImportStrategyForClassifierSymbol(classifierSymbol)
        }
    }

    override fun K2CompletionSetupScope<KotlinNameReferencePositionContext>.registerCompletions() {
        val explicitReceiver = position.explicitReceiver
        if (explicitReceiver != null) {
            completion("With Receiver") {
                completeWithReceiver(explicitReceiver)
            }
        } else {
            completion("Without Receiver") {
                completeWithoutReceiverFromScopes()
            }

            completion("Without Receiver From index", priority = K2ContributorSectionPriority.FROM_INDEX) {
                completeWithoutReceiverFromIndex()
            }
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    override fun shouldExecute(): Boolean {
        return !context.positionContext.isAfterRangeOperator() && !context.positionContext.allowsOnlyNamedArguments()
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun KaScopeWithKind.completeClassifiers(
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<KaClassifierSymbol> = scope
        .classifiers(context.completionContext.scopeNameFilter)
        .filter { filterClassifiers(it) }
        .filter { visibilityChecker.isVisible(it, context.positionContext) }

    context(_: KaSession, sectionContext: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun completeWithoutReceiverFromScopes() {
        val context = sectionContext.weighingContext
        val positionContext = sectionContext.positionContext
        // TODO: Find solution for potentially deduplicating classifiers in index completion
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
                scope.getAvailableClassifiers(
                    positionContext = positionContext,
                    scopeNameFilter = sectionContext.completionContext.scopeNameFilter,
                    visibilityChecker = sectionContext.visibilityChecker
                )
            }
            .filter { classifier -> filterClassifiers(classifier.symbol) }
            .flatMap { symbolWithOrigin ->
                val aliasName = sectionContext.parameters.completionFile.getAliasNameIfExists(symbolWithOrigin.symbol)

                val classifierSymbol = symbolWithOrigin.symbol
                if (aliasName == null) {
                    // For import aliases, we should still show the original symbol from the index.
                    availableFromScope += classifierSymbol
                }

                createClassifierLookupElement(
                    classifierSymbol = classifierSymbol,
                    expectedType = context.expectedType,
                    importingStrategy = getImportingStrategy(
                        context = sectionContext.completionContext,
                        importStrategyDetector = sectionContext.importStrategyDetector,
                        classifierSymbol = classifierSymbol,
                        aliasName = aliasName,
                    ),
                    aliasName = aliasName,
                    positionContext = positionContext,
                    visibilityChecker = sectionContext.visibilityChecker,
                ).map {
                    it.applyWeighs(symbolWithOrigin)
                }
            }

        scopeClassifiers.forEach { sectionContext.addElement(it) }
    }

    context(_: KaSession, sectionContext: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun completeWithoutReceiverFromIndex() {
        val weighingContext = sectionContext.weighingContext
        val indexClassifiers = if (sectionContext.prefixMatcher.prefix.isNotEmpty()) {
            getAvailableClassifiersFromIndex(
                positionContext = sectionContext.positionContext,
                parameters = sectionContext.parameters,
                symbolProvider = sectionContext.symbolFromIndexProvider,
                scopeNameFilter = sectionContext.completionContext.getIndexNameFilter(),
                visibilityChecker = sectionContext.visibilityChecker,
            ).filter { filterClassifiers(it) }
                // TODO: We need to find a solution to maybe block index elements we already have available from scope if duplicates arise
                .flatMap { classifierSymbol ->
                    createClassifierLookupElement(
                        classifierSymbol = classifierSymbol,
                        expectedType = weighingContext.expectedType,
                        importingStrategy = getImportingStrategy(
                            context = sectionContext.completionContext,
                            importStrategyDetector = sectionContext.importStrategyDetector,
                            classifierSymbol = classifierSymbol,
                            aliasName = null,
                        ),
                        positionContext = sectionContext.positionContext,
                        visibilityChecker = sectionContext.visibilityChecker,
                    ).map {
                        it.applyWeighs(
                            symbolWithOrigin = KtSymbolWithOrigin(classifierSymbol),
                        )
                    }
                }
        } else {
            // We do not show items from index when there is no prefix because there would be too many.
            // That means if the prefix changes at all (the only option is for it to grow in size), we must restart completion.
            sectionContext.sink.restartCompletionOnAnyPrefixChange()
            emptySequence()
        }

        indexClassifiers.forEach { sectionContext.addElement(it) }
    }

    context(_: KaSession, sectionContext: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun completeWithReceiver(explicitReceiver: KtElement) {
        val reference = explicitReceiver.reference()
            ?: return

        val symbols = reference.resolveToSymbols()
        if (symbols.isNotEmpty()) {
            symbols.asSequence()
                .mapNotNull { it.staticScope }
                .flatMap { scopeWithKind ->
                    scopeWithKind.completeClassifiers(sectionContext.visibilityChecker)
                        .map { KtSymbolWithOrigin(it, scopeWithKind.kind) }
                }.flatMap { symbolWithOrigin ->
                    createClassifierLookupElement(
                        classifierSymbol = symbolWithOrigin.symbol,
                        expectedType = sectionContext.weighingContext.expectedType,
                        positionContext = sectionContext.positionContext,
                        visibilityChecker = sectionContext.visibilityChecker,
                    ).map { it.applyWeighs(symbolWithOrigin) }
                }.forEach { sectionContext.addElement(it) }
        } else {
            sectionContext.sink.registerChainContributor(this)
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinExpressionNameReferencePositionContext>)
    override fun createChainedLookupElements(
        receiverExpression: KtDotQualifiedExpression,
        nameToImport: FqName,
    ): Sequence<LookupElement> {
        val selectorExpression = receiverExpression.selectorExpression ?: return emptySequence()

        val reference = receiverExpression.reference() ?: return emptySequence()

        return reference.resolveToSymbols()
            .asSequence()
            .mapNotNull { it.staticScope }
            .flatMap { it.completeClassifiers(context.visibilityChecker) }
            .flatMap {
                createClassifierLookupElement(
                    classifierSymbol = it,
                    expectedType = context.weighingContext.expectedType,
                    importingStrategy = ImportStrategy.AddImport(nameToImport),
                    positionContext = context.positionContext,
                    visibilityChecker = context.visibilityChecker
                )
            }.map { it.withPresentableText(selectorExpression.text + "." + it.lookupString) }
    }

    context(_: KaSession)
    private fun createClassifierLookupElement(
        classifierSymbol: KaClassifierSymbol,
        positionContext: KotlinNameReferencePositionContext,
        visibilityChecker: CompletionVisibilityChecker,
        expectedType: KaType? = null,
        aliasName: Name? = null,
        importingStrategy: ImportStrategy = ImportStrategy.DoNothing,
    ): Sequence<LookupElementBuilder> = sequence {
        if (classifierSymbol is KaNamedClassSymbol &&
            classifierSymbol.classKind != KaClassKind.OBJECT &&
            classifierSymbol.classKind != KaClassKind.ENUM_CLASS &&
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
                val expensiveRenderer = K2ClassifierLookupElementRenderer(
                    fqName = importingStrategy.fqName,
                    position = positionContext.position,
                )
                builder.withExpensiveRenderer(expensiveRenderer)
            }

            else -> builder
        }
    }

    override fun K2CompletionSectionContext<KotlinNameReferencePositionContext>.getGroupPriority(): Int = when (positionContext) {
        is KotlinWithSubjectEntryPositionContext, is KotlinCallableReferencePositionContext -> 1
        else -> 0
    }

    override fun K2CompletionSetupScope<KotlinNameReferencePositionContext>.isAppropriatePosition(): Boolean {
        if (position is KotlinPackageDirectivePositionContext ||
            position is KotlinImportDirectivePositionContext ||
            position is KotlinSuperTypeCallNameReferencePositionContext
        ) return false
        if (position !is KotlinTypeNameReferencePositionContext) return true
        return position.allowsClassifiersAndPackagesForPossibleExtensionCallables(
            parameters = completionContext.parameters,
            prefixMatcher = completionContext.prefixMatcher,
        )
    }
}

private class K2ClassifierLookupElementRenderer(
    private val fqName: FqName,
    position: PsiElement,
) : SuspendingLookupElementRenderer<LookupElement>() {

    private val position = SmartPointerManager
        .getInstance(position.project)
        .createSmartPsiFileRangePointer(
            /* psiFile = */ position.containingFile,
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