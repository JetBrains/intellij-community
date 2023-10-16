// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.parentOfType
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithTypeParameters
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.base.util.letIf
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.InsertionHandlerBase
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.*
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersCurrentScope
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighsToLookupElement
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinWithSubjectEntryPositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render

internal class FirWhenWithSubjectConditionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<KotlinWithSubjectEntryPositionContext>(basicContext, priority) {
    private val prefix: String get() = basicContext.prefixMatcher.prefix
    private val onTypingIsKeyword: Boolean = prefix.isNotEmpty() && KtTokens.IS_KEYWORD.value.startsWith(prefix)

    override val prefixMatcher: PrefixMatcher = if (onTypingIsKeyword) basicContext.prefixMatcher.cloneWithPrefix("") else super.prefixMatcher

    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinWithSubjectEntryPositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        val whenCondition = positionContext.whenCondition
        val whenExpression = whenCondition.parentOfType<KtWhenExpression>() ?: return
        val subject = whenExpression.subjectExpression ?: return
        val allConditionsExceptCurrent = whenExpression.entries.flatMap { entry -> entry.conditions.filter { it != whenCondition } }
        val subjectType = subject.getKtType() ?: return
        val classSymbol = getClassSymbol(subjectType)
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val isSingleCondition = whenCondition.isSingleConditionInEntry()
        when {
            classSymbol?.classKind == KtClassKind.ENUM_CLASS -> {
                completeEnumEntries(weighingContext, classSymbol, allConditionsExceptCurrent, visibilityChecker, isSingleCondition)
            }
            classSymbol?.modality == Modality.SEALED -> {
                completeSubClassesOfSealedClass(
                    weighingContext,
                    classSymbol,
                    allConditionsExceptCurrent,
                    whenCondition,
                    visibilityChecker,
                    isSingleCondition
                )
            }
            else -> {
                completeAllTypes(weighingContext, whenCondition, visibilityChecker, isSingleCondition)
            }
        }
        addNullIfWhenExpressionCanReturnNull(weighingContext, subjectType)
        addElseBranchIfSingleConditionInEntry(weighingContext, whenCondition)
    }

    context(KtAnalysisSession)
    private fun getClassSymbol(subjectType: KtType): KtNamedClassOrObjectSymbol? {
        val classType = subjectType as? KtNonErrorClassType
        return classType?.classSymbol as? KtNamedClassOrObjectSymbol
    }


    context(KtAnalysisSession)
    private fun addNullIfWhenExpressionCanReturnNull(context: WeighingContext, type: KtType?) {
        if (type?.canBeNull == true) {
            val lookupElement = createKeywordElement(keyword = KtTokens.NULL_KEYWORD.value)
            applyWeighsAndAddElementToSink(context, lookupElement, symbolWithOrigin = null)
        }
    }

    context(KtAnalysisSession)
    private fun completeAllTypes(
        context: WeighingContext,
        whenCondition: KtWhenCondition,
        visibilityChecker: CompletionVisibilityChecker,
        isSingleCondition: Boolean,
    ) {
        val availableFromScope = mutableSetOf<KtClassifierSymbol>()
        getAvailableClassifiersCurrentScope(originalKtFile, whenCondition, scopeNameFilter, visibilityChecker)
            .forEach { classifierWithScopeKind ->
                val classifier = classifierWithScopeKind.symbol
                if (classifier !is KtNamedSymbol) return@forEach
                availableFromScope += classifier

                addLookupElement(
                    context,
                    classifier.name.asString(),
                    classifier,
                    CompletionSymbolOrigin.Scope(classifierWithScopeKind.scopeKind),
                    (classifier as? KtNamedClassOrObjectSymbol)?.classIdIfNonLocal?.asSingleFqName(),
                    isSingleCondition,
                )
            }

        if (prefixMatcher.prefix.isNotEmpty()) {
            getAvailableClassifiersFromIndex(symbolFromIndexProvider, scopeNameFilter, visibilityChecker)
                .forEach { classifier ->
                    if (classifier !is KtNamedSymbol || classifier in availableFromScope) return@forEach

                    addLookupElement(
                        context,
                        classifier.name.asString(),
                        classifier,
                        CompletionSymbolOrigin.Index,
                        (classifier as? KtNamedClassOrObjectSymbol)?.classIdIfNonLocal?.asSingleFqName(),
                        isSingleCondition,
                    )
                }
        }
    }

    context(KtAnalysisSession)
    private fun isPrefixNeeded(symbol: KtNamedSymbol): Boolean {
        return when (symbol) {
            is KtAnonymousObjectSymbol -> return false
            is KtNamedClassOrObjectSymbol -> onTypingIsKeyword || !symbol.classKind.isObject
            is KtTypeAliasSymbol -> {
                (symbol.expandedType as? KtNonErrorClassType)?.classSymbol?.let { it is KtNamedSymbol && isPrefixNeeded(it) } == true
            }

            is KtTypeParameterSymbol -> true
            else -> false
        }
    }

    context(KtAnalysisSession)
    private fun completeSubClassesOfSealedClass(
        context: WeighingContext,
        classSymbol: KtNamedClassOrObjectSymbol,
        conditions: List<KtWhenCondition>,
        whenCondition: KtWhenCondition,
        visibilityChecker: CompletionVisibilityChecker,
        isSingleCondition: Boolean,
    ) {
        require(classSymbol.modality == Modality.SEALED)
        val handledCasesClassIds = getHandledClassIds(conditions)
        val allInheritors = getAllSealedInheritors(classSymbol)

        allInheritors
            .asSequence()
            .filter { it.classIdIfNonLocal !in handledCasesClassIds }
            .filter { visibilityChecker.isVisible(it as KtClassifierSymbol) }
            .forEach { inheritor ->
                val classId = inheritor.classIdIfNonLocal ?: return@forEach
                addLookupElement(
                    context,
                    classId.relativeClassName.asString(),
                    inheritor,
                    CompletionSymbolOrigin.Index,
                    classId.asSingleFqName(),
                    isSingleCondition,
                )
            }

        if (allInheritors.any { it.modality == Modality.ABSTRACT }) {
            completeAllTypes(context, whenCondition, visibilityChecker, isSingleCondition)
        }
    }

    context(KtAnalysisSession)
    private fun getHandledClassIds(conditions: List<KtWhenCondition>): Set<ClassId> =
        conditions.mapNotNullTo(hashSetOf()) { condition ->
            val reference = when (condition) {
                is KtWhenConditionWithExpression -> condition.expression?.reference()
                is KtWhenConditionIsPattern -> (condition.typeReference?.typeElement as? KtUserType)?.referenceExpression?.reference()
                else -> null
            }
            val resolvesTo = reference?.resolveToExpandedSymbol() as? KtNamedClassOrObjectSymbol
            resolvesTo?.classIdIfNonLocal
        }

    context(KtAnalysisSession)
    private fun getAllSealedInheritors(classSymbol: KtNamedClassOrObjectSymbol): Collection<KtNamedClassOrObjectSymbol> {

        fun getAllSealedInheritorsTo(
            classSymbol: KtNamedClassOrObjectSymbol,
            destination: MutableSet<KtNamedClassOrObjectSymbol>
        ) {
            classSymbol.getSealedClassInheritors().forEach { inheritor ->
                destination += inheritor
                if (inheritor.modality == Modality.SEALED) {
                    getAllSealedInheritorsTo(inheritor, destination)
                }
            }
        }

        return ObjectOpenCustomHashSet(KtNamedClassOrObjectSymbolTObjectHashingStrategy)
            .apply { getAllSealedInheritorsTo(classSymbol, this) }
    }

    context(KtAnalysisSession)
    private fun addElseBranchIfSingleConditionInEntry(context: WeighingContext, whenCondition: KtWhenCondition) {
        val whenEntry = whenCondition.parent as? KtWhenEntry ?: return
        if (whenEntry.conditions.size > 1) return
        val lookupElement = createKeywordElement(keyword = KtTokens.ELSE_KEYWORD.value, tail = " -> ")

        applyWeighsAndAddElementToSink(context, lookupElement, symbolWithOrigin = null)
    }


    context(KtAnalysisSession)
    private fun completeEnumEntries(
        context: WeighingContext,
        classSymbol: KtNamedClassOrObjectSymbol,
        conditions: List<KtWhenCondition>,
        visibilityChecker: CompletionVisibilityChecker,
        isSingleCondition: Boolean,
    ) {
        require(classSymbol.classKind == KtClassKind.ENUM_CLASS)
        val handledCasesNames = conditions.mapNotNullTo(hashSetOf()) { condition ->
            val conditionWithExpression = condition as? KtWhenConditionWithExpression
            val resolvesTo = conditionWithExpression?.expression?.reference()?.resolveToSymbol() as? KtEnumEntrySymbol
            resolvesTo?.name
        }
        val allEnumEntrySymbols = classSymbol.getEnumEntries()
        allEnumEntrySymbols
            .filter { it.name !in handledCasesNames }
            .filter { visibilityChecker.isVisible(it) }
            .forEach { entry ->
                addLookupElement(
                    context,
                    "${classSymbol.name.asString()}.${entry.name.asString()}",
                    entry,
                    CompletionSymbolOrigin.Index,
                    entry.callableIdIfNonLocal?.asSingleFqName(),
                    isSingleCondition,
                )
            }
    }

    private fun KtWhenCondition.isSingleConditionInEntry(): Boolean {
        val entry = parent as KtWhenEntry
        return entry.conditions.size == 1
    }

    context(KtAnalysisSession)
    private fun addLookupElement(
        context: WeighingContext,
        lookupString: String,
        symbol: KtNamedSymbol,
        origin: CompletionSymbolOrigin,
        fqName: FqName?,
        isSingleCondition: Boolean,
    ) {
        val isPrefixNeeded = isPrefixNeeded(symbol)
        val typeArgumentsCount = (symbol as? KtSymbolWithTypeParameters)?.typeParameters?.size ?: 0
        val lookupObject = WhenConditionLookupObject(symbol.name, fqName, isPrefixNeeded, isSingleCondition, typeArgumentsCount)

        LookupElementBuilder.create(lookupObject, getIsPrefix(isPrefixNeeded) + lookupString)
            .withIcon(getIconFor(symbol))
            .withPsiElement(symbol.psi)
            .withInsertHandler(WhenConditionInsertionHandler)
            .withTailText(createStarTypeArgumentsList(typeArgumentsCount), /*grayed*/true)
            .letIf(isSingleCondition) { it.appendTailText(" -> ",  /*grayed*/true) }
            .let { applyWeighsAndAddElementToSink(context, it, KtSymbolWithOrigin(symbol, origin)) }
    }

    context(KtAnalysisSession)
    private fun applyWeighsAndAddElementToSink(context: WeighingContext, element: LookupElement, symbolWithOrigin: KtSymbolWithOrigin?) {
        applyWeighsToLookupElement(context, element, symbolWithOrigin)
        sink.addElement(element)
    }
}

private data class WhenConditionLookupObject(
    override val shortName: Name,
    val fqName: FqName?,
    val needIsPrefix: Boolean,
    val isSingleCondition: Boolean,
    val typeArgumentsCount: Int,
) : KotlinLookupObject


private object WhenConditionInsertionHandler : InsertionHandlerBase<WhenConditionLookupObject>(WhenConditionLookupObject::class) {
    override fun handleInsert(context: InsertionContext, item: LookupElement, ktFile: KtFile, lookupObject: WhenConditionLookupObject) {
        context.insertName(lookupObject, ktFile)
        context.addTypeArguments(lookupObject.typeArgumentsCount)
        context.addArrow(lookupObject)
    }

    private fun InsertionContext.addArrow(
        lookupObject: WhenConditionLookupObject
    ) {
        if (lookupObject.isSingleCondition && completionChar != ',') {
            insertString(" -> ")
            commitDocument()
        }
    }

    private fun InsertionContext.insertName(
        lookupObject: WhenConditionLookupObject,
        ktFile: KtFile
    ) {
        if (lookupObject.fqName != null) {
            val fqName = lookupObject.fqName
            document.replaceString(
                startOffset,
                tailOffset,
                getIsPrefix(lookupObject.needIsPrefix) + fqName.render()
            )
            commitDocument()

            shortenReferencesInRange(ktFile, TextRange(startOffset, tailOffset))
        }
    }
}

private fun getIsPrefix(prefixNeeded: Boolean): String {
    return if (prefixNeeded) "is " else ""
}

@Suppress("AnalysisApiMissingLifetimeControlOnCallable")
private object KtNamedClassOrObjectSymbolTObjectHashingStrategy : Hash.Strategy<KtNamedClassOrObjectSymbol> {
    override fun equals(p0: KtNamedClassOrObjectSymbol?, p1: KtNamedClassOrObjectSymbol?): Boolean {
        return p0?.classIdIfNonLocal == p1?.classIdIfNonLocal
    }

    override fun hashCode(p0: KtNamedClassOrObjectSymbol?): Int = p0?.classIdIfNonLocal?.hashCode() ?: 0
}
