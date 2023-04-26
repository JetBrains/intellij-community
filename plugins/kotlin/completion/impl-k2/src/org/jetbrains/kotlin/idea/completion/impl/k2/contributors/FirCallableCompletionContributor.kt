// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtExtensionApplicabilityResult
import org.jetbrains.kotlin.analysis.api.components.KtScopeContext
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.fir.codeInsight.HLIndexHelper
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.checkers.ExtensionApplicabilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.*
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.*

internal open class FirCallableCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<FirNameReferencePositionContext>(basicContext, priority) {
    private val typeNamesProvider = TypeNamesProvider(indexHelper)

    protected open fun KtAnalysisSession.getInsertionStrategy(symbol: KtCallableSymbol): CallableInsertionStrategy = when (symbol) {
        is KtFunctionLikeSymbol -> CallableInsertionStrategy.AsCall
        else -> CallableInsertionStrategy.AsIdentifier
    }

    protected open fun KtAnalysisSession.getInsertionStrategyForExtensionFunction(
        symbol: KtCallableSymbol,
        applicabilityResult: KtExtensionApplicabilityResult
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KtExtensionApplicabilityResult.ApplicableAsExtensionCallable -> getInsertionStrategy(symbol)
        is KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> CallableInsertionStrategy.AsCall
        is KtExtensionApplicabilityResult.NonApplicable -> null
    }

    protected fun KtAnalysisSession.getOptions(symbol: KtCallableSymbol): CallableInsertionOptions =
        CallableInsertionOptions(importStrategyDetector.detectImportStrategy(symbol), getInsertionStrategy(symbol))

    private fun KtAnalysisSession.getExtensionOptions(
        symbol: KtCallableSymbol,
        applicability: KtExtensionApplicabilityResult
    ): CallableInsertionOptions? =
        getInsertionStrategyForExtensionFunction(
            symbol,
            applicability
        )?.let { CallableInsertionOptions(importStrategyDetector.detectImportStrategy(symbol), it) }

    protected open fun KtAnalysisSession.filter(symbol: KtCallableSymbol): Boolean = true

    private val shouldCompleteTopLevelCallablesFromIndex: Boolean
        get() = prefixMatcher.prefix.isNotEmpty()

    protected val excludeEnumEntries =
        !basicContext.project.languageVersionSettings.supportsFeature(LanguageFeature.EnumEntries)

    override fun KtAnalysisSession.complete(
        positionContext: FirNameReferencePositionContext,
        weighingContext: WeighingContext
    ): Unit = with(positionContext) {
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val scopesContext = originalKtFile.getScopeContextForPosition(nameExpression)

        val extensionChecker = object : ExtensionApplicabilityChecker {
            context(KtAnalysisSession)
            override fun checkApplicability(symbol: KtCallableSymbol): KtExtensionApplicabilityResult {
                return symbol.checkExtensionIsSuitable(originalKtFile, nameExpression, explicitReceiver)
            }
        }

        val receiver = explicitReceiver
        when {
            receiver != null -> {
                collectDotCompletion(
                    scopesContext,
                    receiver,
                    weighingContext,
                    extensionChecker,
                    visibilityChecker,
                )
            }

            else -> completeWithoutReceiver(scopesContext, weighingContext, extensionChecker, visibilityChecker)
        }
    }

    private fun KtAnalysisSession.completeWithoutReceiver(
        scopeContext: KtScopeContext,
        context: WeighingContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ) {
        val implicitReceivers = scopeContext.implicitReceivers
        val implicitReceiversTypes = implicitReceivers.map { it.type }

        val availableNonExtensions = collectNonExtensionsFromScopeContext(
            scopeContext,
            visibilityChecker,
            scopeNameFilter,
            excludeEnumEntries,
        ) { filter(it) }
        val extensionsWhichCanBeCalled = collectSuitableExtensions(scopeContext, extensionChecker, visibilityChecker)

        // TODO: consider relying on tower resolver when populating callable entries. For example
        //  val Int.foo : ...
        //  val Number.foo : ...
        //  val String.foo : ...
        //  fun String.test() {
        //      with(1) {
        //          val foo = ...
        //          fo<caret>
        //      }
        //  }
        //  completion should be able to all of the `foo` by inserting proper qualifiers:
        //   foo -> foo
        //   Int.foo -> this.foo
        //   Number.foo -> (this as Number).foo
        //   String.foo -> this@test.foo
        //
        availableNonExtensions
            // skip shadowed variable or properties
            .distinctBy { (symbol, _) ->
                when (symbol) {
                    is KtVariableLikeSymbol -> symbol.name
                    else -> symbol
                }
            }
            .forEach { (symbol, scopeKind) -> addCallableSymbolToCompletion(context, symbol, getOptions(symbol), scopeKind) }

        // Here we can't rely on deduplication in LookupElementSink because extension members can have types substituted, which won't be
        // equal to the same symbols from top level without substitution.
        val extensionMembers = mutableSetOf<KtCallableSymbol>()
        extensionsWhichCanBeCalled.forEach { (symbolWithScopeKind, applicabilityResult) ->
            val (symbol, scopeKind) = symbolWithScopeKind
            getExtensionOptions(symbol, applicabilityResult)?.let {
                extensionMembers += symbol
                addCallableSymbolToCompletion(context, symbol, it, scopeKind, applicabilityResult.substitutor)
            }
        }

        if (shouldCompleteTopLevelCallablesFromIndex) {
            val topLevelCallables = indexHelper.getTopLevelCallables(scopeNameFilter)
            topLevelCallables.asSequence()
                .filterNot { it.canDefinitelyNotBeSeenFromOtherFile() }
                .filter { it.canBeAnalysed() }
                .map { it.getSymbol() as KtCallableSymbol }
                .filter { it !in extensionMembers && visibilityChecker.isVisible(it) }
                .forEach { addCallableSymbolToCompletion(context, it, getOptions(it), scopeKind = null) }
        }

        collectTopLevelExtensionsFromIndices(implicitReceiversTypes, extensionChecker, visibilityChecker)
            .filter { it !in extensionMembers }
            .forEach {
                addCallableSymbolToCompletion(context, it, getOptions(it), scopeKind = null)
            }
    }

    protected open fun KtAnalysisSession.collectDotCompletion(
        scopeContext: KtScopeContext,
        explicitReceiver: KtExpression,
        context: WeighingContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ) {
        val symbol = explicitReceiver.reference()?.resolveToSymbol()
        when {
            symbol is KtPackageSymbol -> collectDotCompletionForPackageReceiver(symbol, context, visibilityChecker)

            symbol is KtNamedClassOrObjectSymbol && symbol.hasImportantStaticMemberScope -> {
                if (symbol.classKind == KtClassKind.ENUM_CLASS) {
                    collectDotCompletionForCallableReceiver(scopeContext, explicitReceiver, context, extensionChecker, visibilityChecker)
                }
                val nonExtensions = collectNonExtensionsFromScope(
                    symbol.getStaticMemberScope(),
                    visibilityChecker,
                    scopeNameFilter,
                    excludeEnumEntries,
                )

                nonExtensions.forEach { memberSymbol ->
                    addCallableSymbolToCompletion(
                        context,
                        memberSymbol,
                        CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(memberSymbol)),
                        scopeKind = null
                    )
                }
            }

            symbol is KtNamedClassOrObjectSymbol && !symbol.canBeUsedAsReceiver -> {
                // symbol cannot be used as callable receiver
            }

            else -> {
                collectDotCompletionForCallableReceiver(scopeContext, explicitReceiver, context, extensionChecker, visibilityChecker)
            }
        }
    }

    private val KtNamedClassOrObjectSymbol.hasImportantStaticMemberScope: Boolean
        get() = classKind == KtClassKind.ENUM_CLASS ||
                origin == KtSymbolOrigin.JAVA

    private val KtNamedClassOrObjectSymbol.canBeUsedAsReceiver: Boolean
        get() = classKind.isObject || companionObject != null

    private fun KtAnalysisSession.collectDotCompletionForPackageReceiver(
        packageSymbol: KtPackageSymbol,
        context: WeighingContext,
        visibilityChecker: CompletionVisibilityChecker
    ) {
        packageSymbol.getPackageScope()
            .getCallableSymbols(scopeNameFilter)
            .filterNot { it.isExtension }
            .filter { visibilityChecker.isVisible(it) }
            .filter { filter(it) }
            .forEach { callable ->
                val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(callable))
                addCallableSymbolToCompletion(context, callable, options, scopeKind = null)
            }
    }

    protected fun KtAnalysisSession.collectDotCompletionForCallableReceiver(
        scopeContext: KtScopeContext,
        explicitReceiver: KtExpression,
        context: WeighingContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
        allowSyntheticJavaProperties: Boolean = true,
    ) {
        val smartCastInfo = explicitReceiver.getSmartCastInfo()
        if (smartCastInfo?.isStable == false) {
            // Collect members available from unstable smartcast as well.
            collectDotCompletionForCallableReceiver(
                smartCastInfo.smartCastType,
                visibilityChecker,
                scopeContext,
                extensionChecker,
                context,
                allowSyntheticJavaProperties,
                // Only offer the hint if the type is denotable.
                smartCastInfo.smartCastType.takeIf { it.approximateToSuperPublicDenotable(true) == null }
            )
        }

        val receiverType = explicitReceiver.getKtType().takeUnless { it is KtErrorType } ?: return
        collectDotCompletionForCallableReceiver(
            receiverType,
            visibilityChecker,
            scopeContext,
            extensionChecker,
            context,
            allowSyntheticJavaProperties
        )
    }

    private fun KtAnalysisSession.collectDotCompletionForCallableReceiver(
        typeOfPossibleReceiver: KtType,
        visibilityChecker: CompletionVisibilityChecker,
        scopeContext: KtScopeContext,
        extensionChecker: ExtensionApplicabilityChecker,
        context: WeighingContext,
        allowSyntheticJavaProperties: Boolean = true,
        explicitReceiverTypeHint: KtType? = null
    ) {
        val nonExtensionMembers = collectNonExtensionsForType(
            typeOfPossibleReceiver,
            visibilityChecker,
            scopeNameFilter,
            excludeEnumEntries,
            withSyntheticJavaProperties = allowSyntheticJavaProperties
        ) { filter(it) }
        val extensionNonMembers = collectSuitableExtensions(scopeContext, extensionChecker, visibilityChecker)

        nonExtensionMembers.forEach { (symbol, scopeKind) ->
            addCallableSymbolToCompletion(
                context,
                symbol,
                getOptions(symbol),
                scopeKind,
                explicitReceiverTypeHint = explicitReceiverTypeHint
            )
        }

        // Here we can't rely on deduplication in LookupElementSink because extension members can have types substituted, which won't be
        // equal to the same symbols from top level without substitution.
        val extensionMembers = mutableSetOf<CallableId>()
        extensionNonMembers.forEach { (symbolWithScopeKind, applicabilityResult) ->
            val (symbol, scopeKind) = symbolWithScopeKind
            getExtensionOptions(symbol, applicabilityResult)?.let {
                symbol.callableIdIfNonLocal?.let(extensionMembers::add)
                addCallableSymbolToCompletion(
                    context,
                    symbol,
                    it,
                    scopeKind,
                    applicabilityResult.substitutor,
                    explicitReceiverTypeHint = explicitReceiverTypeHint
                )
            }
        }

        collectTopLevelExtensionsFromIndices(listOf(typeOfPossibleReceiver), extensionChecker, visibilityChecker)
            .filter { it.callableIdIfNonLocal !in extensionMembers && filter(it) }
            .forEach {
                addCallableSymbolToCompletion(
                    context,
                    it,
                    getOptions(it),
                    scopeKind = null,
                    explicitReceiverTypeHint = explicitReceiverTypeHint
                )
            }
    }

    private fun KtAnalysisSession.collectTopLevelExtensionsFromIndices(
        receiverTypes: List<KtType>,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<KtCallableSymbol> {
        if (receiverTypes.isEmpty()) return emptySequence()

        val implicitReceiverNames = findAllNamesOfTypes(receiverTypes)
        val topLevelExtensions = indexHelper.getTopLevelExtensions(scopeNameFilter, implicitReceiverNames)

        return topLevelExtensions.asSequence()
            .filterNot { it.canDefinitelyNotBeSeenFromOtherFile() }
            .filter { it.canBeAnalysed() }
            .map { it.getSymbol() as KtCallableSymbol }
            .filter { filter(it) }
            .filter { visibilityChecker.isVisible(it) }
            .filter { extensionChecker.checkApplicability(it) is KtExtensionApplicabilityResult.Applicable }
    }

    private fun KtAnalysisSession.collectSuitableExtensions(
        scopeContext: KtScopeContext,
        hasSuitableExtensionReceiver: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<Pair<KtSymbolWithContainingScopeKind<KtCallableSymbol>, KtExtensionApplicabilityResult.Applicable>> =
        scopeContext.scopes.asSequence().flatMap { scopeWithKind ->
            val extensions = collectSuitableExtensions(scopeWithKind.scope, hasSuitableExtensionReceiver, visibilityChecker)
            extensions.map { (symbol, applicability) -> KtSymbolWithContainingScopeKind(symbol, scopeWithKind.kind) to applicability }
        }

    private fun KtAnalysisSession.collectSuitableExtensions(
        scope: KtScope,
        hasSuitableExtensionReceiver: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<Pair<KtCallableSymbol, KtExtensionApplicabilityResult.Applicable>> =
        scope.getCallableSymbols(scopeNameFilter)
            .filter { it.isExtension || it is KtVariableLikeSymbol && (it.returnType as? KtFunctionalType)?.hasReceiver == true }
            .filter { visibilityChecker.isVisible(it) }
            .filter { filter(it) }
            .mapNotNull { callable ->
                val applicabilityResult = hasSuitableExtensionReceiver.checkApplicability(callable)
                if (applicabilityResult is KtExtensionApplicabilityResult.Applicable) {
                    callable to applicabilityResult
                } else null
            }

    private fun KtAnalysisSession.findAllNamesOfTypes(implicitReceiversTypes: List<KtType>) =
        implicitReceiversTypes.flatMapTo(hashSetOf()) { typeNamesProvider.findAllNames(it) }
}

internal class FirCallableReferenceCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCallableCompletionContributor(basicContext, priority) {

    override fun KtAnalysisSession.getInsertionStrategy(symbol: KtCallableSymbol): CallableInsertionStrategy =
        CallableInsertionStrategy.AsIdentifier

    override fun KtAnalysisSession.getInsertionStrategyForExtensionFunction(
        symbol: KtCallableSymbol,
        applicabilityResult: KtExtensionApplicabilityResult
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KtExtensionApplicabilityResult.ApplicableAsExtensionCallable -> CallableInsertionStrategy.AsIdentifier
        is KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> null
        is KtExtensionApplicabilityResult.NonApplicable -> null
    }

    override fun KtAnalysisSession.collectDotCompletion(
        scopeContext: KtScopeContext,
        explicitReceiver: KtExpression,
        context: WeighingContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker
    ) {
        val allowSyntheticJavaProperties =
            explicitReceiver.languageVersionSettings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties)

        when (val symbol = explicitReceiver.reference()?.resolveToSymbol()) {
            is KtPackageSymbol -> return
            is KtNamedClassOrObjectSymbol -> {
                val type = symbol.buildSelfClassType()
                val nonExtensionMembers = collectNonExtensionsForType(
                    type,
                    visibilityChecker,
                    scopeNameFilter,
                    excludeEnumEntries,
                    withSyntheticJavaProperties = allowSyntheticJavaProperties
                ) { filter(it) }

                val staticScope = listOfNotNull(symbol.companionObject?.getMemberScope(), symbol.getStaticMemberScope()).asCompositeScope()
                val staticMembers = collectNonExtensionsFromScope(
                    staticScope,
                    visibilityChecker,
                    scopeNameFilter,
                    excludeEnumEntries
                ) { filter(it) }.map { KtSymbolWithContainingScopeKind(it, scopeKind = null) }

                (nonExtensionMembers + staticMembers).forEach { (callableSymbol, scopeKind) ->
                    addCallableSymbolToCompletion(context.withoutExpectedType(), callableSymbol, getOptions(callableSymbol), scopeKind)
                }
            }

            else -> {
                collectDotCompletionForCallableReceiver(
                    scopeContext,
                    explicitReceiver,
                    context,
                    extensionChecker,
                    visibilityChecker,
                    allowSyntheticJavaProperties
                )
            }
        }
    }
}

internal class FirInfixCallableCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCallableCompletionContributor(basicContext, priority) {
    override fun KtAnalysisSession.getInsertionStrategy(symbol: KtCallableSymbol): CallableInsertionStrategy =
        insertionStrategy

    override fun KtAnalysisSession.getInsertionStrategyForExtensionFunction(
        symbol: KtCallableSymbol,
        applicabilityResult: KtExtensionApplicabilityResult
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KtExtensionApplicabilityResult.ApplicableAsExtensionCallable -> getInsertionStrategy(symbol)
        is KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> null
        is KtExtensionApplicabilityResult.NonApplicable -> null
    }

    override fun KtAnalysisSession.filter(symbol: KtCallableSymbol): Boolean {
        return symbol is KtFunctionSymbol && symbol.isInfix
    }

    companion object {
        private val insertionStrategy = CallableInsertionStrategy.AsIdentifierCustom {
            insertSymbolAndInvokeCompletion(" ")
        }
    }
}

private class TypeNamesProvider(private val indexHelper: HLIndexHelper) {
    context(KtAnalysisSession)
    fun findAllNames(type: KtType): Set<String> {
        if (type !is KtNonErrorClassType) return emptySet()

        val typeName = type.classId.shortClassName.let {
            if (it.isSpecial) return emptySet()
            it.identifier
        }

        val result = hashSetOf<String>()
        result += typeName
        result += indexHelper.getPossibleTypeAliasExpansionNames(typeName)

        val superTypes = (type.classSymbol as? KtClassOrObjectSymbol)?.superTypes
        superTypes?.forEach { superType ->
            result += findAllNames(superType)
        }

        return result
    }
}