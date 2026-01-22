// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.parentOfType
import com.intellij.util.applyIf
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.resolveToExpandedSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinFqNameSerializer
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.idea.base.util.letIf
import org.jetbrains.kotlin.idea.completion.InsertionHandlerBase
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiers
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.addTypeArguments
import org.jetbrains.kotlin.idea.completion.contributors.helpers.createStarTypeArgumentsList
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertString
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2ContributorSectionPriority
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.util.positionContext.KotlinWithSubjectEntryPositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render

internal class K2WhenWithSubjectConditionContributor : K2SimpleCompletionContributor<KotlinWithSubjectEntryPositionContext>(
    KotlinWithSubjectEntryPositionContext::class
) {

    private fun isTypingIsKeyword(context: K2CompletionSectionContext<*>): Boolean {
        return context.prefixMatcher.prefix.let { prefix ->
            prefix.isNotEmpty()
                    && KtTokens.IS_KEYWORD.value.startsWith(prefix)
        }
    }

    private fun getPrefixMatcher(context: K2CompletionSectionContext<*>): PrefixMatcher =
        context.prefixMatcher.applyIf(isTypingIsKeyword(context)) { cloneWithPrefix("") }

    private fun getScopeNameFilter(prefixMatcher: PrefixMatcher): (Name) -> Boolean {
        return { name -> !name.isSpecial && prefixMatcher.prefixMatches(name.identifier) }
    }

    // Prefix matcher that only matches if the completion item starts with the prefix.
    private fun getStartOnlyNameFilter(prefixMatcher: PrefixMatcher): (Name) -> Boolean {
        val startOnlyMatcher = BetterPrefixMatcher(prefixMatcher, Int.MIN_VALUE)
        return { name -> !name.isSpecial && startOnlyMatcher.prefixMatches(name.identifier) }
    }

    context(context: K2CompletionSectionContext<*>)
    internal fun getIndexNameFilter(prefixMatcher: PrefixMatcher): (Name) -> Boolean {
        return if (context.parameters.invocationCount >= 2 || prefixMatcher.prefix.length > 3) {
            getScopeNameFilter(prefixMatcher)
        } else {
            getStartOnlyNameFilter(prefixMatcher)
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinWithSubjectEntryPositionContext>)
    override fun complete() {
        val positionContext = context.positionContext
        val whenCondition = positionContext.whenCondition
        val whenExpression = whenCondition.parentOfType<KtWhenExpression>() ?: return
        val subject = whenExpression.subjectExpression ?: return
        val allConditionsExceptCurrent = whenExpression.entries.flatMap { entry -> entry.conditions.filter { it != whenCondition } }
        val subjectType = subject.expressionType ?: return
        val classSymbol = getClassSymbol(subjectType)
        val isSingleCondition = whenCondition.isSingleConditionInEntry()

        createNullBranchLookupElement(subjectType)
            ?.let { addElement(it) }
        createElseBranchLookupElement(whenCondition)
            ?.let { addElement(it) }

        when {
            classSymbol?.classKind == KaClassKind.ENUM_CLASS -> {
                completeEnumEntries(
                    classSymbol = classSymbol,
                    conditions = allConditionsExceptCurrent,
                    isSingleCondition = isSingleCondition,
                )
            }

            classSymbol?.modality == KaSymbolModality.SEALED -> {
                completeSubClassesOfSealedClass(
                    classSymbol = classSymbol,
                    conditions = allConditionsExceptCurrent,
                    whenCondition = whenCondition,
                    isSingleCondition = isSingleCondition,
                )
            }

            else -> completeAllTypes(
                whenCondition = whenCondition,
                isSingleCondition = isSingleCondition,
            )
        }
    }

    context(_: KaSession)
    private fun getClassSymbol(subjectType: KaType): KaNamedClassSymbol? {
        val classType = subjectType as? KaClassType
        return classType?.symbol as? KaNamedClassSymbol
    }

    context(_: KaSession, _: K2CompletionSectionContext<KotlinWithSubjectEntryPositionContext>)
    private fun createNullBranchLookupElement(
        type: KaType?,
    ): LookupElement? {
        if (type?.isNullable != true) return null

        return createKeywordElement(keyword = KtTokens.NULL_KEYWORD.value)
            .applyWeighs()
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinWithSubjectEntryPositionContext>)
    private fun completeAllTypes(
        whenCondition: KtWhenCondition,
        isSingleCondition: Boolean,
    ) {
        val positionContext = context.positionContext
        val completionContext = context.completionContext
        val availableFromScope = mutableSetOf<KaClassifierSymbol>()
        val prefixMatcher = getPrefixMatcher(context)
        val scopeNameFilter = getScopeNameFilter(prefixMatcher)

        completionContext.originalFile.scopeContext(whenCondition)
            .scopes
            .flatMap { it.getAvailableClassifiers(positionContext, scopeNameFilter, context.visibilityChecker) }
            .mapNotNull { classifierWithScopeKind ->
                val classifier = classifierWithScopeKind.symbol
                if (classifier !is KaNamedSymbol) return@mapNotNull null
                availableFromScope += classifier

                createLookupElement(
                    lookupString = classifier.name.asString(),
                    symbol = classifier,
                    scopeKind = classifierWithScopeKind.scopeKind,
                    fqName = (classifier as? KaNamedClassSymbol)?.classId?.asSingleFqName(),
                    isSingleCondition = isSingleCondition,
                )
            }.forEach { addElement(it) }

        if (prefixMatcher.prefix.isNotEmpty()) {
            context.completeLaterInSameSession("Index", priority = K2ContributorSectionPriority.FROM_INDEX) {
                val innerContext = contextOf<K2CompletionSectionContext<KotlinWithSubjectEntryPositionContext>>()
                getAvailableClassifiersFromIndex(
                    positionContext = positionContext,
                    parameters = innerContext.parameters,
                    symbolProvider = innerContext.symbolFromIndexProvider,
                    scopeNameFilter = getIndexNameFilter(prefixMatcher),
                    visibilityChecker = innerContext.visibilityChecker,
                ).filterNot { it in availableFromScope }
                    .filterIsInstance<KaNamedSymbol>()
                    .map { classifier ->
                        createLookupElement(
                            lookupString = classifier.name.asString(),
                            symbol = classifier,
                            fqName = (classifier as? KaNamedClassSymbol)?.classId?.asSingleFqName(),
                            isSingleCondition = isSingleCondition,
                        )
                    }.forEach {
                        context(innerContext) {
                            addElement(it)
                        }
                    }
            }
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinWithSubjectEntryPositionContext>)
    private fun isPrefixNeeded(symbol: KaNamedSymbol): Boolean {
        return when (symbol) {
            is KaAnonymousObjectSymbol -> return false
            is KaNamedClassSymbol -> isTypingIsKeyword(context) || !symbol.classKind.isObject
            is KaTypeAliasSymbol -> {
                (symbol.expandedType as? KaClassType)?.symbol?.let { it is KaNamedSymbol && isPrefixNeeded(it) } == true
            }

            is KaTypeParameterSymbol -> true
            else -> false
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinWithSubjectEntryPositionContext>)
    private fun completeSubClassesOfSealedClass(
        classSymbol: KaNamedClassSymbol,
        conditions: List<KtWhenCondition>,
        whenCondition: KtWhenCondition,
        isSingleCondition: Boolean,
    ) {
        require(classSymbol.modality == KaSymbolModality.SEALED)
        val positionContext = context.positionContext
        val handledCasesClassIds = getHandledClassIds(conditions)

        getAllSealedInheritors(classSymbol)
            .asSequence()
            .filter { it.classId !in handledCasesClassIds }
            .filter { context.visibilityChecker.isVisible(it as KaClassifierSymbol, positionContext) }
            .mapNotNull { inheritor ->
                val classId = inheritor.classId
                    ?: return@mapNotNull null

                createLookupElement(
                    lookupString = classId.relativeClassName.asString(),
                    symbol = inheritor,
                    fqName = classId.asSingleFqName(),
                    isSingleCondition = isSingleCondition,
                )
            }.forEach { addElement(it) }

        if (getAllSealedInheritors(classSymbol).any { it.modality == KaSymbolModality.ABSTRACT }) {
            completeAllTypes(
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

    context(_: KaSession, _: K2CompletionSectionContext<KotlinWithSubjectEntryPositionContext>)
    private fun createElseBranchLookupElement(
        whenCondition: KtWhenCondition,
    ): LookupElement? {
        val whenEntry = whenCondition.parent as? KtWhenEntry
            ?: return null
        if (whenEntry.conditions.size > 1) return null

        return createKeywordElement(
            keyword = KtTokens.ELSE_KEYWORD.value,
            tail = " -> ",
        ).applyWeighs()
    }


    context(_: KaSession, context: K2CompletionSectionContext<KotlinWithSubjectEntryPositionContext>)
    private fun completeEnumEntries(
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
            .filter { context.visibilityChecker.isVisible(it, context.positionContext) }
            .map { entry ->
                createLookupElement(
                    lookupString = "${classSymbol.name.asString()}.${entry.name.asString()}",
                    symbol = entry,
                    fqName = entry.callableId?.asSingleFqName(),
                    isSingleCondition = isSingleCondition,
                )
            }.forEach { addElement(it) }
    }

    private fun KtWhenCondition.isSingleConditionInEntry(): Boolean {
        val entry = parent as KtWhenEntry
        return entry.conditions.size == 1
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinWithSubjectEntryPositionContext>)
    private fun createLookupElement(
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
            .applyWeighs(KtSymbolWithOrigin(symbol, scopeKind))
    }
}

@Serializable
internal data class WhenConditionLookupObject(
    @Serializable(with = KotlinNameSerializer::class) override val shortName: Name,
    @Serializable(with = KotlinFqNameSerializer::class) val fqName: FqName?,
    val needIsPrefix: Boolean,
    val isSingleCondition: Boolean,
    val typeArgumentsCount: Int,
) : KotlinLookupObject


@Serializable
internal object WhenConditionInsertionHandler : InsertionHandlerBase<WhenConditionLookupObject>(WhenConditionLookupObject::class) {
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
internal object KaNamedClassOrObjectSymbolTObjectHashingStrategy : Hash.Strategy<KaNamedClassSymbol> {
    override fun equals(p0: KaNamedClassSymbol?, p1: KaNamedClassSymbol?): Boolean {
        return p0?.classId == p1?.classId
    }

    override fun hashCode(p0: KaNamedClassSymbol?): Int = p0?.classId?.hashCode() ?: 0
}
