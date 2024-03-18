// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.NameUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.components.KtScopeContext
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.completion.*
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.getOriginalElementOfSelf
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.TypeLookupObject
import org.jetbrains.kotlin.idea.completion.weighers.VariableOrParameterNameWithTypeWeigher.nameWithTypePriority
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighsToLookupElement
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinValueParameterPositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal class FirVariableOrParameterNameWithTypeCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCompletionContributorBase<KotlinRawPositionContext>(basicContext, priority) {

    private val nameFiltersWithUserPrefixes: List<Pair<NameFilter, String>> = getNameFiltersWithUserPrefixes()

    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinRawPositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        val variableOrParameter: KtCallableDeclaration = when (positionContext) {
            is KotlinValueParameterPositionContext -> positionContext.ktParameter.takeIf { NameWithTypeCompletion.shouldCompleteParameter(it) }
            is KotlinTypeNameReferencePositionContext ->
                positionContext.typeReference?.let { getDeclarationFromReceiverTypeReference(it) } as? KtProperty

            else -> null
        } ?: return

        sink.restartCompletionOnPrefixChange(NameWithTypeCompletion.prefixEndsWithUppercaseLetterPattern)

        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val lookupNamesAdded = mutableSetOf<String>()
        val scopeContext = originalKtFile.getScopeContextForPosition(variableOrParameter)

        completeFromParametersInFile(variableOrParameter, visibilityChecker, lookupNamesAdded, scopeContext)
        completeClassesFromScopeContext(variableOrParameter, visibilityChecker, lookupNamesAdded, scopeContext, weighingContext)
        completeClassesFromIndices(variableOrParameter, visibilityChecker, lookupNamesAdded, weighingContext)
    }

    context(KtAnalysisSession)
    private fun completeFromParametersInFile(
        variableOrParameter: KtCallableDeclaration,
        visibilityChecker: CompletionVisibilityChecker,
        lookupNamesAdded: MutableSet<String>,
        scopeContext: KtScopeContext
    ) {
        val typeParametersScope = scopeContext.getCompositeScope { it is KtScopeKind.TypeParameterScope }
        val availableTypeParameters = getAvailableTypeParameters(typeParametersScope).toSet()

        val variableOrParameterInOriginal = getOriginalElementOfSelf(variableOrParameter, basicContext.originalKtFile)

        val parametersInFile = originalKtFile.collectDescendantsOfType<KtParameter>(
            canGoInside = { element ->
                // For performance reasons, don't go inside expressions except declarations (parameters of local functions,
                // which are declared in body block expressions, will be skipped)
                element !is KtExpression || element is KtDeclaration
            },
            predicate = { parameter ->
                parameter.name != null
                        && variableOrParameterInOriginal != parameter
                        && prefixMatcher.isStartMatch(parameter.name)
            }
        )

        val lookupElementsWithNames: List<Pair<LookupElement, String>> = parametersInFile.mapNotNull { parameter ->
            ProgressManager.checkCanceled()

            val name = parameter.name
            if (name == null || variableOrParameterInOriginal == parameter || !prefixMatcher.isStartMatch(name)) return@mapNotNull null

            val type = parameter.getReturnKtType()
            if (typeIsVisible(type, visibilityChecker, availableTypeParameters)) {

                val typeLookupElement = lookupElementFactory.createTypeLookupElement(type) ?: return@mapNotNull null
                val lookupElement = createLookupElement(variableOrParameter, name, typeLookupElement)

                lookupElement to name
            } else {
                null
            }
        }


        for ((lookupElementWithName, count) in lookupElementsWithNames.groupingBy { it }.eachCount()) {
            val (lookupElement, name) = lookupElementWithName

            if (!shouldInsertType(variableOrParameter) && lookupNamesAdded.contains(name)) continue

            lookupElement.nameWithTypePriority = -count // suggestions that appear more often than others get higher priority
            sink.addElement(lookupElement)
            lookupNamesAdded.add(name)
        }
    }

    context(KtAnalysisSession)
    private fun completeClassesFromScopeContext(
        variableOrParameter: KtCallableDeclaration,
        visibilityChecker: CompletionVisibilityChecker,
        lookupNamesAdded: MutableSet<String>,
        scopeContext: KtScopeContext,
        weighingContext: WeighingContext
    ) {
        for (scopeWithKind in scopeContext.scopes) {
            for ((nameFilter, userPrefix) in nameFiltersWithUserPrefixes) {
                scopeWithKind.scope.getClassifierSymbols(nameFilter).filter { visibilityChecker.isVisible(it) }.forEach { classifier ->
                    val symbolOrigin = CompletionSymbolOrigin.Scope(scopeWithKind.kind)
                    addSuggestions(variableOrParameter, classifier, userPrefix, lookupNamesAdded, weighingContext, symbolOrigin)
                }
            }
        }
    }

    context(KtAnalysisSession)
    private fun completeClassesFromIndices(
        variableOrParameter: KtCallableDeclaration,
        visibilityChecker: CompletionVisibilityChecker,
        lookupNamesAdded: MutableSet<String>,
        weighingContext: WeighingContext
    ) {
        for ((nameFilter, userPrefix) in nameFiltersWithUserPrefixes) {
            getAvailableClassifiersFromIndex(symbolFromIndexProvider, nameFilter, visibilityChecker).forEach { classifier ->
                val symbolOrigin = CompletionSymbolOrigin.Index
                addSuggestions(variableOrParameter, classifier, userPrefix, lookupNamesAdded, weighingContext, symbolOrigin)
            }
        }
    }

    context(KtAnalysisSession)
    private fun addSuggestions(
        variableOrParameter: KtCallableDeclaration,
        symbol: KtClassifierSymbol,
        userPrefix: String,
        lookupNamesAdded: MutableSet<String>,
        weighingContext: WeighingContext,
        symbolOrigin: CompletionSymbolOrigin,
    ) {
        ProgressManager.checkCanceled()

        if (symbol is KtClassOrObjectSymbol && symbol.classKind.isObject) return

        val shortNameString = when (symbol) {
            is KtTypeParameterSymbol -> symbol.name.asString()
            is KtClassLikeSymbol -> symbol.name?.asString()
        } ?: return

        val typeLookupElement = lookupElementFactory.createTypeLookupElement(symbol) ?: return

        val nameSuggestions = KotlinNameSuggester.getCamelNames(
            shortNameString,
            validator = { true },
            startLowerCase = userPrefix.isEmpty()
        )

        for (nameSuggestion in nameSuggestions) {
            val name = userPrefix + nameSuggestion

            if (!prefixMatcher.isStartMatch(name)) continue

            if (!shouldInsertType(variableOrParameter) && lookupNamesAdded.contains(name)) continue

            val lookupElement = createLookupElement(variableOrParameter, name, typeLookupElement)
            lookupElement.nameWithTypePriority = userPrefix.length // suggestions with longer user prefix get lower priority
            applyWeighsToLookupElement(weighingContext, lookupElement, KtSymbolWithOrigin(symbol, symbolOrigin))

            sink.addElement(lookupElement)
            lookupNamesAdded.add(name)
        }
    }

    private fun createLookupElement(variableOrParameter: KtCallableDeclaration, name: String, typeLookup: LookupElement): LookupElement {
        val fqRenderedType = (typeLookup.`object` as TypeLookupObject).fqRenderedType
        val lookupElement = NameWithTypeLookupElementDecorator(name, fqRenderedType, typeLookup, shouldInsertType(variableOrParameter))

        val isLateinitVar = (variableOrParameter as? KtProperty)?.hasModifier(KtTokens.LATEINIT_KEYWORD) == true
        if (!isLateinitVar) {
            lookupElement.suppressItemSelectionByCharsOnTyping = true
        }
        lookupElement.hideLookupOnColon = true

        return lookupElement.suppressAutoInsertion()
    }

    private fun shouldInsertType(variableOrParameter: KtCallableDeclaration): Boolean = when (variableOrParameter) {
        is KtParameter -> true
        is KtProperty -> variableOrParameter.hasModifier(KtTokens.LATEINIT_KEYWORD)
        else -> error("Declaration must be KtParameter or KtProperty.")
    }

    /**
     * Generates all possible partitions of original prefix into user prefix and prefix for matcher, for example,
     * prefix `fooBarBaz` produces three pairs:
     * - ("", "FooBarBaz")
     * - ("foo", "BarBaz")
     * - ("fooBar", "Baz")
     *
     * where the first component is a user prefix and the second one is a prefix for matcher.
     * The second prefix is then used to create a matcher.
     */
    private fun getMatchersWithUserPrefixes(): List<Pair<PrefixMatcher, String>> {
        val prefix = prefixMatcher.prefix
        val prefixWords = if (StringUtil.isCapitalized(prefix)) emptyArray() else NameUtil.splitNameIntoWords(prefix)

        return prefixWords.indices.map { index ->
            val userPrefix = prefixWords.take(index).joinToString("")
            val classNamePrefix = prefixWords.drop(index).joinToString("")
            val classNamePrefixMatcher = CamelHumpMatcher(StringUtil.capitalize(classNamePrefix), false)

            classNamePrefixMatcher to userPrefix
        }
    }

    private fun getNameFiltersWithUserPrefixes(): List<Pair<NameFilter, String>> {
        val matchersWithUserPrefixes = getMatchersWithUserPrefixes()

        return matchersWithUserPrefixes.mapIndexed { index, matcherWithUserPrefix ->
            val (matcher, userPrefix) = matcherWithUserPrefix
            val nameFilter = if (index == 0) {
                matcher.asNameFilter()
            } else {
                // don't add suggestions for the same type with longer user prefix
                val (prevMatcher, _) = matchersWithUserPrefixes[index - 1]
                matcher.asNameFilter() exclude prevMatcher.asNameFilter()
            }

            nameFilter to userPrefix
        }
    }

    context(KtAnalysisSession)
    private fun getAvailableTypeParameters(scopes: KtScope): Sequence<KtTypeParameterSymbol> =
        scopes.getClassifierSymbols().filterIsInstance<KtTypeParameterSymbol>()

    private fun getDeclarationFromReceiverTypeReference(typeReference: KtTypeReference): KtCallableDeclaration? {
        return (typeReference.parent as? KtCallableDeclaration)?.takeIf { it.receiverTypeReference == typeReference }
    }

    context(KtAnalysisSession)
    private fun typeIsVisible(
        type: KtType,
        visibilityChecker: CompletionVisibilityChecker,
        availableTypeParameters: Set<KtTypeParameterSymbol> = emptySet()
    ): Boolean = when (type) {
        is KtTypeParameterType -> type.symbol in availableTypeParameters

        is KtUsualClassType -> {
            visibilityChecker.isVisible(type.classSymbol) && type.ownTypeArguments.all { typeArgument ->
                when (typeArgument) {
                    is KtStarTypeProjection -> true
                    is KtTypeArgumentWithVariance -> typeIsVisible(typeArgument.type, visibilityChecker, availableTypeParameters)
                }
            }
        }

        is KtFunctionalType -> {
            val typesInside = listOfNotNull(type.receiverType) + type.returnType + type.parameterTypes

            typesInside.all { typeIsVisible(it, visibilityChecker, availableTypeParameters) }
        }

        else -> false
    }
}