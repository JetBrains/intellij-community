// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.idea.completion.*
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirRawPositionCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.context.FirValueParameterPositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.TypeLookupObject
import org.jetbrains.kotlin.idea.completion.weighers.VariableOrParameterNameWithTypeWeigher.nameWithTypePriority
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighsToLookupElement
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.core.FirKotlinNameSuggester
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal class FirVariableOrParameterNameWithTypeCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCompletionContributorBase<FirRawPositionCompletionContext>(basicContext, priority) {

    override fun KtAnalysisSession.complete(positionContext: FirRawPositionCompletionContext, weighingContext: WeighingContext) {
        val variableOrParameter: KtCallableDeclaration = when (positionContext) {
            is FirValueParameterPositionContext -> positionContext.ktParameter.takeIf { NameWithTypeCompletion.shouldCompleteParameter(it) }
            is FirTypeNameReferencePositionContext ->
                positionContext.typeReference?.let { getDeclarationFromReceiverTypeReference(it) } as? KtProperty

            else -> null
        } ?: return

        sink.restartCompletionOnPrefixChange(NameWithTypeCompletion.prefixEndsWithUppercaseLetterPattern)

        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val lookupNamesAdded = mutableSetOf<String>()
        val scopes = originalKtFile.getScopeContextForPosition(variableOrParameter).scopes

        completeFromParametersInFile(variableOrParameter, visibilityChecker, lookupNamesAdded, scopes)
        completeClassesFromIndexes(variableOrParameter, visibilityChecker, lookupNamesAdded, scopes, weighingContext)
    }

    private fun KtAnalysisSession.completeFromParametersInFile(
        variableOrParameter: KtCallableDeclaration,
        visibilityChecker: CompletionVisibilityChecker,
        lookupNamesAdded: MutableSet<String>,
        scopes: KtScope
    ) {
        val availableTypeParameters = getAvailableTypeParameters(scopes).toSet()

        val parametersInFile = variableOrParameter.containingFile.collectDescendantsOfType<KtParameter>(
            // for performance reasons don't go inside expressions except declarations (parameters of local functions,
            // which are declared in body block expressions, will be skipped)
            canGoInside = { it !is KtExpression || it is KtDeclaration }
        ) { parameter -> parameter.name != null && variableOrParameter != parameter && prefixMatcher.isStartMatch(parameter.name) }

        val lookupElementsWithNames: List<Pair<LookupElement, String>> = parametersInFile.mapNotNull { parameter ->
            ProgressManager.checkCanceled()

            val name = parameter.name
            if (name == null || variableOrParameter == parameter || !prefixMatcher.isStartMatch(name)) return@mapNotNull null

            val type = parameter.getReturnKtType()
            if (typeIsVisible(type, visibilityChecker, availableTypeParameters)) {

                val typeLookupElement = with(lookupElementFactory) { createTypeLookupElement(type) } ?: return@mapNotNull null
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

    private fun KtAnalysisSession.completeClassesFromIndexes(
        variableOrParameter: KtCallableDeclaration,
        visibilityChecker: CompletionVisibilityChecker,
        lookupNamesAdded: MutableSet<String>,
        scopes: KtScope,
        weighingContext: WeighingContext
    ) {
        val matchersWithUserPrefixes = getMatchersWithUserPrefixes()

        for ((index, matcherWithUserPrefix) in matchersWithUserPrefixes.withIndex()) {
            val (matcher, userPrefix) = matcherWithUserPrefix

            val nameFilter = if (index == 0) {
                matcher.asNameFilter()
            } else {
                // don't add suggestions for the same type with longer user prefix
                val (prevMatcher, _) = matchersWithUserPrefixes[index - 1]
                matcher.asNameFilter() exclude prevMatcher.asNameFilter()
            }

            // get available classifiers from current scope
            scopes.getClassifierSymbols(nameFilter).filter { visibilityChecker.isVisible(it) }.forEach { classifier ->
                addSuggestions(variableOrParameter, classifier, userPrefix, lookupNamesAdded, weighingContext)
            }

            getAvailableClassifiersFromIndex(symbolFromIndexProvider, nameFilter, visibilityChecker).forEach { classifier ->
                addSuggestions(variableOrParameter, classifier, userPrefix, lookupNamesAdded, weighingContext)
            }
        }
    }

    private fun KtAnalysisSession.addSuggestions(
        variableOrParameter: KtCallableDeclaration,
        symbol: KtClassifierSymbol,
        userPrefix: String,
        lookupNamesAdded: MutableSet<String>,
        weighingContext: WeighingContext
    ) {
        ProgressManager.checkCanceled()

        if (symbol is KtClassOrObjectSymbol && symbol.classKind.isObject) return

        val shortNameString = when (symbol) {
            is KtTypeParameterSymbol -> symbol.name.asString()
            is KtClassLikeSymbol -> symbol.name?.asString()
        } ?: return

        val typeLookupElement = with(lookupElementFactory) { createTypeLookupElement(symbol) } ?: return

        val nameSuggestions = FirKotlinNameSuggester.getCamelNames(
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
            applyWeighsToLookupElement(weighingContext, lookupElement, symbol)

            sink.addElement(lookupElement)
            lookupNamesAdded.add(name)
        }
    }

    private fun createLookupElement(variableOrParameter: KtCallableDeclaration, name: String, typeLookup: LookupElement): LookupElement {
        val fqRenderedType = (typeLookup.`object` as TypeLookupObject).fqRenderedType
        val lookupElement = NameWithTypeLookupElementDecorator(name, fqRenderedType, typeLookup, shouldInsertType(variableOrParameter))

        val isLateinitVar = (variableOrParameter as? KtProperty)?.hasModifier(KtTokens.LATEINIT_KEYWORD) == true
        if (!isLateinitVar) {
            lookupElement.putUserData(KotlinCompletionCharFilter.SUPPRESS_ITEM_SELECTION_BY_CHARS_ON_TYPING, Unit)
        }
        lookupElement.putUserData(KotlinCompletionCharFilter.HIDE_LOOKUP_ON_COLON, Unit)

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

    private fun KtAnalysisSession.getAvailableTypeParameters(scopes: KtScope): Sequence<KtTypeParameterSymbol> =
        scopes.getClassifierSymbols().filterIsInstance<KtTypeParameterSymbol>()

    private fun getDeclarationFromReceiverTypeReference(typeReference: KtTypeReference): KtCallableDeclaration? {
        return (typeReference.parent as? KtCallableDeclaration)?.takeIf { it.receiverTypeReference == typeReference }
    }

    private fun KtAnalysisSession.typeIsVisible(
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