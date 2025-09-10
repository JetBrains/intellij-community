// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.fir

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.parentOfType
import com.intellij.util.applyIf
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isNullable
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.components.sealedClassInheritors
import org.jetbrains.kotlin.analysis.api.components.staticDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.resolveToExpandedSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.base.util.letIf
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiers
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.createStarTypeArgumentsList
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.FirCompletionContributorBase
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.KaNamedClassOrObjectSymbolTObjectHashingStrategy
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.WhenConditionInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.WhenConditionLookupObject
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinWithSubjectEntryPositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

internal class FirWhenWithSubjectConditionContributor(
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinWithSubjectEntryPositionContext>(sink, priority) {

    private val onTypingIsKeyword: Boolean =
        super.prefixMatcher.prefix.let { prefix ->
            prefix.isNotEmpty()
                    && KtTokens.IS_KEYWORD.value.startsWith(prefix)
        }

    override val prefixMatcher: PrefixMatcher =
        super.prefixMatcher
            .applyIf(onTypingIsKeyword) { cloneWithPrefix("") }

    context(_: KaSession)
    override fun complete(
        positionContext: KotlinWithSubjectEntryPositionContext,
        weighingContext: WeighingContext,
    ) {
        val whenCondition = positionContext.whenCondition
        val whenExpression = whenCondition.parentOfType<KtWhenExpression>() ?: return
        val subject = whenExpression.subjectExpression ?: return
        val allConditionsExceptCurrent = whenExpression.entries.flatMap { entry -> entry.conditions.filter { it != whenCondition } }
        val subjectType = subject.expressionType ?: return
        val classSymbol = getClassSymbol(subjectType)
        val isSingleCondition = whenCondition.isSingleConditionInEntry()
        when {
            classSymbol?.classKind == KaClassKind.ENUM_CLASS -> {
                completeEnumEntries(
                    positionContext = positionContext,
                    context = weighingContext,
                    classSymbol = classSymbol,
                    conditions = allConditionsExceptCurrent,
                    isSingleCondition = isSingleCondition,
                )
            }

            classSymbol?.modality == KaSymbolModality.SEALED -> {
                completeSubClassesOfSealedClass(
                    positionContext = positionContext,
                    context = weighingContext,
                    classSymbol = classSymbol,
                    conditions = allConditionsExceptCurrent,
                    whenCondition = whenCondition,
                    isSingleCondition = isSingleCondition,
                )
            }

            else -> completeAllTypes(
                positionContext = positionContext,
                context = weighingContext,
                whenCondition = whenCondition,
                isSingleCondition = isSingleCondition,
            )
        }
        createNullBranchLookupElement(weighingContext, subjectType)
            ?.let(sink::addElement)
        createElseBranchLookupElement(weighingContext, whenCondition)
            ?.let(sink::addElement)
    }

    context(_: KaSession)
    private fun getClassSymbol(subjectType: KaType): KaNamedClassSymbol? {
        val classType = subjectType as? KaClassType
        return classType?.symbol as? KaNamedClassSymbol
    }

    context(_: KaSession)
    private fun createNullBranchLookupElement(
        context: WeighingContext,
        type: KaType?,
    ): LookupElement? {
        if (type?.isNullable != true) return null

        return createKeywordElement(keyword = KtTokens.NULL_KEYWORD.value)
            .applyWeighs(context)
    }

    context(_: KaSession)
    private fun completeAllTypes(
        positionContext: KotlinWithSubjectEntryPositionContext,
        context: WeighingContext,
        whenCondition: KtWhenCondition,
        isSingleCondition: Boolean,
    ) {
        val availableFromScope = mutableSetOf<KaClassifierSymbol>()
        originalKtFile.scopeContext(whenCondition)
            .scopes
            .flatMap { it.getAvailableClassifiers(positionContext, scopeNameFilter, visibilityChecker) }
            .mapNotNull { classifierWithScopeKind ->
                val classifier = classifierWithScopeKind.symbol
                if (classifier !is KaNamedSymbol) return@mapNotNull null
                availableFromScope += classifier

                createLookupElement(
                    context = context,
                    lookupString = classifier.name.asString(),
                    symbol = classifier,
                    scopeKind = classifierWithScopeKind.scopeKind,
                    fqName = (classifier as? KaNamedClassSymbol)?.classId?.asSingleFqName(),
                    isSingleCondition = isSingleCondition,
                )
            }.forEach(sink::addElement)

        if (prefixMatcher.prefix.isNotEmpty()) {
            getAvailableClassifiersFromIndex(
                positionContext = positionContext,
                parameters = parameters,
                symbolProvider = symbolFromIndexProvider,
                scopeNameFilter = getIndexNameFilter(),
                visibilityChecker = visibilityChecker,
            ).filterNot { it in availableFromScope }
                .filterIsInstance<KaNamedSymbol>()
                .map { classifier ->
                    createLookupElement(
                        context = context,
                        lookupString = classifier.name.asString(),
                        symbol = classifier,
                        fqName = (classifier as? KaNamedClassSymbol)?.classId?.asSingleFqName(),
                        isSingleCondition = isSingleCondition,
                    )
                }.forEach(sink::addElement)
        }
    }

    context(_: KaSession)
    private fun isPrefixNeeded(symbol: KaNamedSymbol): Boolean {
        return when (symbol) {
            is KaAnonymousObjectSymbol -> return false
            is KaNamedClassSymbol -> onTypingIsKeyword || !symbol.classKind.isObject
            is KaTypeAliasSymbol -> {
                (symbol.expandedType as? KaClassType)?.symbol?.let { it is KaNamedSymbol && isPrefixNeeded(it) } == true
            }

            is KaTypeParameterSymbol -> true
            else -> false
        }
    }

    context(_: KaSession)
    private fun completeSubClassesOfSealedClass(
        positionContext: KotlinWithSubjectEntryPositionContext,
        context: WeighingContext,
        classSymbol: KaNamedClassSymbol,
        conditions: List<KtWhenCondition>,
        whenCondition: KtWhenCondition,
        isSingleCondition: Boolean,
    ) {
        require(classSymbol.modality == KaSymbolModality.SEALED)
        val handledCasesClassIds = getHandledClassIds(conditions)

        getAllSealedInheritors(classSymbol)
            .asSequence()
            .filter { it.classId !in handledCasesClassIds }
            .filter { visibilityChecker.isVisible(it as KaClassifierSymbol, positionContext) }
            .mapNotNull { inheritor ->
                val classId = inheritor.classId
                    ?: return@mapNotNull null

                createLookupElement(
                    context = context,
                    lookupString = classId.relativeClassName.asString(),
                    symbol = inheritor,
                    fqName = classId.asSingleFqName(),
                    isSingleCondition = isSingleCondition,
                )
            }.forEach(sink::addElement)

        if (getAllSealedInheritors(classSymbol).any { it.modality == KaSymbolModality.ABSTRACT }) {
            completeAllTypes(
                positionContext = positionContext,
                context = context,
                whenCondition = whenCondition,
                isSingleCondition = isSingleCondition,
            )
        }
    }

    context(_: KaSession)
    private fun getHandledClassIds(conditions: List<KtWhenCondition>): Set<ClassId> =
        conditions.mapNotNullTo(hashSetOf()) { condition ->
            val reference = when (condition) {
                is KtWhenConditionWithExpression -> condition.expression?.reference()
                is KtWhenConditionIsPattern -> (condition.typeReference?.typeElement as? KtUserType)?.referenceExpression?.reference()
                else -> null
            }
            val resolvesTo = reference?.resolveToExpandedSymbol() as? KaNamedClassSymbol
            resolvesTo?.classId
        }

    context(_: KaSession)
    private fun getAllSealedInheritors(classSymbol: KaNamedClassSymbol): Collection<KaNamedClassSymbol> {

        fun getAllSealedInheritorsTo(
            classSymbol: KaNamedClassSymbol,
            destination: MutableSet<KaNamedClassSymbol>
        ) {
            classSymbol.sealedClassInheritors.forEach { inheritor ->
                destination += inheritor
                if (inheritor.modality == KaSymbolModality.SEALED) {
                    getAllSealedInheritorsTo(inheritor, destination)
                }
            }
        }

        return ObjectOpenCustomHashSet(KaNamedClassOrObjectSymbolTObjectHashingStrategy)
            .apply { getAllSealedInheritorsTo(classSymbol, this) }
    }

    context(_: KaSession)
    private fun createElseBranchLookupElement(
        context: WeighingContext,
        whenCondition: KtWhenCondition,
    ): LookupElement? {
        val whenEntry = whenCondition.parent as? KtWhenEntry
            ?: return null
        if (whenEntry.conditions.size > 1) return null

        return createKeywordElement(
            keyword = KtTokens.ELSE_KEYWORD.value,
            tail = " -> ",
        ).applyWeighs(context)
    }


    context(_: KaSession)
    private fun completeEnumEntries(
        positionContext: KotlinWithSubjectEntryPositionContext,
        context: WeighingContext,
        classSymbol: KaNamedClassSymbol,
        conditions: List<KtWhenCondition>,
        isSingleCondition: Boolean,
    ) {
        require(classSymbol.classKind == KaClassKind.ENUM_CLASS)
        val handledCasesNames = conditions.mapNotNullTo(hashSetOf()) { condition ->
            val conditionWithExpression = condition as? KtWhenConditionWithExpression
            val resolvesTo = conditionWithExpression?.expression?.reference()?.resolveToSymbol() as? KaEnumEntrySymbol
            resolvesTo?.name
        }
        classSymbol.staticDeclaredMemberScope
            .callables
            .filterIsInstance<KaEnumEntrySymbol>()
            .filter { it.name !in handledCasesNames }
            .filter { visibilityChecker.isVisible(it, positionContext) }
            .map { entry ->
                createLookupElement(
                    context = context,
                    lookupString = "${classSymbol.name.asString()}.${entry.name.asString()}",
                    symbol = entry,
                    fqName = entry.callableId?.asSingleFqName(),
                    isSingleCondition = isSingleCondition,
                )
            }.forEach(sink::addElement)
    }

    private fun KtWhenCondition.isSingleConditionInEntry(): Boolean {
        val entry = parent as KtWhenEntry
        return entry.conditions.size == 1
    }

    context(_: KaSession)
    private fun createLookupElement(
        context: WeighingContext,
        lookupString: String,
        symbol: KaNamedSymbol,
        fqName: FqName?,
        isSingleCondition: Boolean,
        scopeKind: KaScopeKind? = null,
    ): LookupElement {
        val isPrefixNeeded = isPrefixNeeded(symbol)

        @OptIn(KaExperimentalApi::class)
        val typeArgumentsCount = (symbol as? KaDeclarationSymbol)?.typeParameters?.size ?: 0
        val lookupObject = WhenConditionLookupObject(symbol.name, fqName, isPrefixNeeded, isSingleCondition, typeArgumentsCount)

        return LookupElementBuilder.create(lookupObject, getIsPrefix(isPrefixNeeded) + lookupString)
            .withIcon(getIconFor(symbol))
            .withPsiElement(symbol.psi)
            .withInsertHandler(WhenConditionInsertionHandler)
            .withTailText(createStarTypeArgumentsList(typeArgumentsCount), /*grayed*/true)
            .letIf(isSingleCondition) { it.appendTailText(" -> ",  /*grayed*/true) }
            .applyWeighs(context, KtSymbolWithOrigin(symbol, scopeKind))
    }
}

private fun getIsPrefix(prefixNeeded: Boolean): String {
    return if (prefixNeeded) "is " else ""
}