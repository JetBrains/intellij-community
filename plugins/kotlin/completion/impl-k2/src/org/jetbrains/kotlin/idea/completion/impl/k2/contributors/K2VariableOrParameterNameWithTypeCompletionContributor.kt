// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.NameUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeContext
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.NameFilter
import org.jetbrains.kotlin.idea.completion.NameWithTypeCompletion
import org.jetbrains.kotlin.idea.completion.NameWithTypeLookupElementDecorator
import org.jetbrains.kotlin.idea.completion.asNameFilter
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.exclude
import org.jetbrains.kotlin.idea.completion.hideLookupOnColon
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSetupScope
import org.jetbrains.kotlin.idea.completion.impl.k2.K2ContributorSectionPriority
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalElementOfSelf
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.TypeLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.suppressAutoInsertion
import org.jetbrains.kotlin.idea.completion.suppressItemSelectionByCharsOnTyping
import org.jetbrains.kotlin.idea.completion.weighers.VariableOrParameterNameWithTypeWeigher.nameWithTypePriority
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.util.positionContext.KotlinPrimaryConstructorParameterPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleParameterPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinValueParameterPositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtContextReceiver
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal class K2VariableOrParameterNameWithTypeCompletionContributor : K2SimpleCompletionContributor<KotlinRawPositionContext>(
    positionContextClass = KotlinRawPositionContext::class,
    priority = K2ContributorSectionPriority.HEURISTIC,
) {

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    override fun complete() {
        val contextElement: KtElement = when (val positionContext = context.positionContext) {
            is KotlinValueParameterPositionContext -> positionContext.ktParameter.takeIf { NameWithTypeCompletion.shouldCompleteParameter(it) }
            is KotlinTypeNameReferencePositionContext ->
                positionContext.typeReference?.let { getDeclarationFromReceiverTypeReference(it) }

            else -> null
        } ?: return

        if (!shouldOfferParameterNames(contextElement)) return

        context.sink.restartCompletionOnPrefixChange(NameWithTypeCompletion.prefixEndsWithUppercaseLetterPattern)

        val lookupNamesAdded = mutableSetOf<String>()
        val scopeContext = context.completionContext.originalFile.scopeContext(contextElement)
        val nameFiltersWithUserPrefixes: List<Pair<NameFilter, String>> = getNameFiltersWithUserPrefixes(context)

        completeFromParametersInFile(
            contextElement = contextElement,
            lookupNamesAdded = lookupNamesAdded,
            scopeContext = scopeContext,
        )

        context.completeLaterInSameSession("Classes From Scope Context", priority = K2ContributorSectionPriority.DEFAULT) {
            completeClassesFromScopeContext(
                contextElement = contextElement,
                lookupNamesAdded = lookupNamesAdded,
                nameFiltersWithUserPrefixes = nameFiltersWithUserPrefixes,
                scopeContext = scopeContext,
            )
        }

        context.completeLaterInSameSession("Classes From Indices", priority = K2ContributorSectionPriority.FROM_INDEX) {
            completeClassesFromIndices(
                contextElement = contextElement,
                nameFiltersWithUserPrefixes = nameFiltersWithUserPrefixes,
                lookupNamesAdded = lookupNamesAdded,
            )
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    private fun completeFromParametersInFile(
        contextElement: KtElement,
        lookupNamesAdded: MutableSet<String>,
        scopeContext: KaScopeContext,
    ) {
        val originalKtFile = context.completionContext.originalFile
        val prefixMatcher = context.prefixMatcher
        val typeParametersScope = scopeContext.compositeScope { it is KaScopeKind.TypeParameterScope }
        val availableTypeParameters = getAvailableTypeParameters(typeParametersScope).toSet()

        val variableOrParameterInOriginal = getOriginalElementOfSelf(contextElement, originalKtFile)

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
                val lookupElement = createLookupElement(contextElement, name, typeLookupElement)

                lookupElement to name
            } else {
                null
            }
        }


        for ((lookupElementWithName, count) in lookupElementsWithNames.groupingBy { it }.eachCount()) {
            val (lookupElement, name) = lookupElementWithName

            if (!shouldInsertType(contextElement) && lookupNamesAdded.contains(name)) continue

            lookupElement.nameWithTypePriority = -count // suggestions that appear more often than others get higher priority
            addElement(lookupElement)
            lookupNamesAdded.add(name)
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    private fun completeClassesFromScopeContext(
        contextElement: KtElement,
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
                            contextElement = contextElement,
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
        contextElement: KtElement,
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
                    contextElement = contextElement,
                    symbol = it,
                    userPrefix = userPrefix,
                    lookupNamesAdded = lookupNamesAdded,
                )
            }
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    private fun addSuggestions(
        contextElement: KtElement,
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

            if (!shouldInsertType(contextElement) && !lookupNamesAdded.add(name)) continue

            val lookupElement = createLookupElement(contextElement, name, typeLookupElement)
            lookupElement.nameWithTypePriority = userPrefix.length // suggestions with longer user prefix get lower priority
            lookupElement.applyWeighs(KtSymbolWithOrigin(symbol, scopeKind))

            addElement(lookupElement)
        }
    }

    private fun createLookupElement(contextElement: KtElement, name: String, typeLookup: LookupElement): LookupElement {
        val fqRenderedType = (typeLookup.`object` as TypeLookupObject).fqRenderedType
        val lookupElement = NameWithTypeLookupElementDecorator(name, fqRenderedType, typeLookup, shouldInsertType(contextElement))

        val isLateinitVar = (contextElement as? KtProperty)?.hasModifier(KtTokens.LATEINIT_KEYWORD) == true
        if (!isLateinitVar) {
            lookupElement.suppressItemSelectionByCharsOnTyping = true
        }
        lookupElement.hideLookupOnColon = true

        return lookupElement.suppressAutoInsertion()
    }

    private fun shouldInsertType(contextElement: KtElement): Boolean = when (contextElement) {
        is KtProperty -> contextElement.hasModifier(KtTokens.LATEINIT_KEYWORD)
        else -> true
    }

    private fun shouldOfferParameterNames(contextElement: KtElement): Boolean = when (contextElement) {
        is KtParameter, is KtContextReceiver, is KtProperty -> true
        else -> false
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
        val prefixWords = if (StringUtil.isCapitalized(prefix)) emptyList() else NameUtil.splitNameIntoWordList(prefix)

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

    private fun getDeclarationFromReceiverTypeReference(typeReference: KtTypeReference): KtElement? {
        val parent = typeReference.parent

        if (parent is KtContextReceiver && typeReference.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) {
            // Context parameters have an awkward PSI structure to stay somewhat compatible with the abandoned context receivers.
            // They are still KtContextReceiver in the PSI tree, and they do not have a KtParameter until typing the `:`.
            // Due to this, we cannot return the parameter here, so we instead return the context receiver itself.
            return parent
        } else {
            return (parent as? KtCallableDeclaration)?.takeIf { it.receiverTypeReference == typeReference } as? KtProperty
        }
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