// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.NameUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.completion.*
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSetupScope
import org.jetbrains.kotlin.idea.completion.impl.k2.K2ContributorSectionPriority
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalElementOfSelf
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.TypeLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.VariableOrParameterNameWithTypeWeigher.nameWithTypePriority
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal class K2VariableOrParameterNameWithTypeCompletionContributor : K2SimpleCompletionContributor<KotlinRawPositionContext>(
    positionContextClass = KotlinRawPositionContext::class,
    priority = K2ContributorSectionPriority.HEURISTIC,
) {

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    override fun complete() {
        val variableOrParameter: KtCallableDeclaration = when (val positionContext = context.positionContext) {
            is KotlinValueParameterPositionContext -> positionContext.ktParameter.takeIf { NameWithTypeCompletion.shouldCompleteParameter(it) }
            is KotlinTypeNameReferencePositionContext ->
                positionContext.typeReference?.let { getDeclarationFromReceiverTypeReference(it) } as? KtProperty

            else -> null
        } ?: return

        context.sink.restartCompletionOnPrefixChange(NameWithTypeCompletion.prefixEndsWithUppercaseLetterPattern)

        val lookupNamesAdded = mutableSetOf<String>()
        val scopeContext = context.completionContext.originalFile.scopeContext(variableOrParameter)
        val nameFiltersWithUserPrefixes: List<Pair<NameFilter, String>> = getNameFiltersWithUserPrefixes(context)

        completeFromParametersInFile(
            variableOrParameter = variableOrParameter,
            lookupNamesAdded = lookupNamesAdded,
            scopeContext = scopeContext,
        )

        context.completeLaterInSameSession("Classes From Scope Context", priority = K2ContributorSectionPriority.DEFAULT) {
            completeClassesFromScopeContext(
                variableOrParameter = variableOrParameter,
                lookupNamesAdded = lookupNamesAdded,
                nameFiltersWithUserPrefixes = nameFiltersWithUserPrefixes,
                scopeContext = scopeContext,
            )
        }

        context.completeLaterInSameSession("Classes From Indices", priority = K2ContributorSectionPriority.FROM_INDEX) {
            completeClassesFromIndices(
                variableOrParameter = variableOrParameter,
                nameFiltersWithUserPrefixes = nameFiltersWithUserPrefixes,
                lookupNamesAdded = lookupNamesAdded,
            )
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    private fun completeFromParametersInFile(
        variableOrParameter: KtCallableDeclaration,
        lookupNamesAdded: MutableSet<String>,
        scopeContext: KaScopeContext,
    ) {
        val originalKtFile = context.completionContext.originalFile
        val prefixMatcher = context.prefixMatcher
        val typeParametersScope = scopeContext.compositeScope { it is KaScopeKind.TypeParameterScope }
        val availableTypeParameters = getAvailableTypeParameters(typeParametersScope).toSet()

        val variableOrParameterInOriginal = getOriginalElementOfSelf(variableOrParameter, originalKtFile)

        val parametersInFile = originalKtFile.collectDescendantsOfType<KtParameter>(
            canGoInside = { element ->
                // For performance reasons, don't go inside expressions except declarations (parameters of local functions,
                // which are declared in body block expressions, will be skipped)
                element !is KtExpression || element is KtDeclaration
            },
            predicate = { parameter ->
                parameter.name.let { parameterName ->
                    parameterName != null
                            && variableOrParameterInOriginal != parameter
                            && prefixMatcher.isStartMatch(parameterName)
                }
            }
        )

        val lookupElementsWithNames: List<Pair<LookupElement, String>> = parametersInFile.mapNotNull { parameter ->
            ProgressManager.checkCanceled()

            val name = parameter.name
            if (name == null || variableOrParameterInOriginal == parameter || !prefixMatcher.isStartMatch(name)) return@mapNotNull null

            val type = parameter.returnType
            if (typeIsVisible(type, availableTypeParameters)) {

                val typeLookupElement = KotlinFirLookupElementFactory.createTypeLookupElement(type) ?: return@mapNotNull null
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
            context.addElement(lookupElement)
            lookupNamesAdded.add(name)
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    private fun completeClassesFromScopeContext(
        variableOrParameter: KtCallableDeclaration,
        nameFiltersWithUserPrefixes: List<Pair<NameFilter, String>>,
        lookupNamesAdded: MutableSet<String>,
        scopeContext: KaScopeContext,
    ) {
        for (scopeWithKind in scopeContext.scopes) {
            for ((nameFilter, userPrefix) in nameFiltersWithUserPrefixes) {
                scopeWithKind.scope
                    .classifiers(nameFilter)
                    .filter { context.visibilityChecker.isVisible(it, context.positionContext) }
                    .forEach {
                        addSuggestions(
                            variableOrParameter = variableOrParameter,
                            symbol = it,
                            userPrefix = userPrefix,
                            lookupNamesAdded = lookupNamesAdded,
                            scopeKind = scopeWithKind.kind,
                        )
                    }
            }
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    private fun completeClassesFromIndices(
        variableOrParameter: KtCallableDeclaration,
        nameFiltersWithUserPrefixes: List<Pair<NameFilter, String>>,
        lookupNamesAdded: MutableSet<String>,
    ) {
        for ((nameFilter, userPrefix) in nameFiltersWithUserPrefixes) {
            getAvailableClassifiersFromIndex(
                positionContext = context.positionContext,
                parameters = context.parameters,
                symbolProvider = context.symbolFromIndexProvider,
                scopeNameFilter = nameFilter,
                visibilityChecker = context.visibilityChecker,
            ).forEach {
                addSuggestions(
                    variableOrParameter = variableOrParameter,
                    symbol = it,
                    userPrefix = userPrefix,
                    lookupNamesAdded = lookupNamesAdded,
                )
            }
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    private fun addSuggestions(
        variableOrParameter: KtCallableDeclaration,
        symbol: KaClassifierSymbol,
        userPrefix: String,
        lookupNamesAdded: MutableSet<String>,
        scopeKind: KaScopeKind? = null,
    ) {
        ProgressManager.checkCanceled()

        if (symbol is KaClassSymbol && symbol.classKind.isObject) return

        val shortNameString = when (symbol) {
            is KaTypeParameterSymbol -> symbol.name.asString()
            is KaClassLikeSymbol -> symbol.name?.asString()
        } ?: return

        val typeLookupElement = KotlinFirLookupElementFactory.createTypeLookupElement(symbol) ?: return

        val nameSuggestions = KotlinNameSuggester.getCamelNames(
            shortNameString,
            validator = { true },
            startLowerCase = userPrefix.isEmpty()
        )

        for (nameSuggestion in nameSuggestions) {
            val name = userPrefix + nameSuggestion

            if (!context.prefixMatcher.isStartMatch(name)) continue

            if (!shouldInsertType(variableOrParameter) && !lookupNamesAdded.add(name)) continue

            val lookupElement = createLookupElement(variableOrParameter, name, typeLookupElement)
            lookupElement.nameWithTypePriority = userPrefix.length // suggestions with longer user prefix get lower priority
            lookupElement.applyWeighs(KtSymbolWithOrigin(symbol, scopeKind))

            context.addElement(lookupElement)
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
    private fun getMatchersWithUserPrefixes(context: K2CompletionSectionContext<KotlinRawPositionContext>): List<Pair<PrefixMatcher, String>> {
        val prefix = context.prefixMatcher.prefix
        val prefixWords = if (StringUtil.isCapitalized(prefix)) emptyArray() else NameUtil.splitNameIntoWords(prefix)

        return prefixWords.indices.map { index ->
            val userPrefix = prefixWords.take(index).joinToString("")
            val classNamePrefix = prefixWords.drop(index).joinToString("")
            val classNamePrefixMatcher = CamelHumpMatcher(StringUtil.capitalize(classNamePrefix), false)

            classNamePrefixMatcher to userPrefix
        }
    }

    private fun getNameFiltersWithUserPrefixes(context: K2CompletionSectionContext<KotlinRawPositionContext>): List<Pair<NameFilter, String>> {
        val matchersWithUserPrefixes = getMatchersWithUserPrefixes(context)

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

    context(_: KaSession)
    private fun getAvailableTypeParameters(scopes: KaScope): Sequence<KaTypeParameterSymbol> =
        scopes.classifiers.filterIsInstance<KaTypeParameterSymbol>()

    private fun getDeclarationFromReceiverTypeReference(typeReference: KtTypeReference): KtCallableDeclaration? {
        return (typeReference.parent as? KtCallableDeclaration)?.takeIf { it.receiverTypeReference == typeReference }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    private fun typeIsVisible(
        type: KaType,
        availableTypeParameters: Set<KaTypeParameterSymbol> = emptySet(),
    ): Boolean = when (type) {
        is KaTypeParameterType -> type.symbol in availableTypeParameters

        is KaUsualClassType -> {
            context.visibilityChecker.isVisible(type.symbol, context.positionContext) && type.typeArguments.all { typeArgument ->
                when (typeArgument) {
                    is KaStarTypeProjection -> true
                    is KaTypeArgumentWithVariance -> typeIsVisible(typeArgument.type, availableTypeParameters)
                }
            }
        }

        is KaFunctionType -> {
            val typesInside = listOfNotNull(type.receiverType) + type.returnType + type.parameterTypes

            typesInside.all { typeIsVisible(it, availableTypeParameters) }
        }

        else -> false
    }

    override fun K2CompletionSetupScope<KotlinRawPositionContext>.isAppropriatePosition(): Boolean = when (position) {
        is KotlinTypeNameReferencePositionContext,
        is KotlinSimpleParameterPositionContext,
        is KotlinPrimaryConstructorParameterPositionContext -> true

        else -> false
    }
}