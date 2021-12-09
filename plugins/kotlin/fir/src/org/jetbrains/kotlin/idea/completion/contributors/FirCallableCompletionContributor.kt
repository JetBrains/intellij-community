// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtExtensionApplicabilityResult
import org.jetbrains.kotlin.analysis.api.components.KtScopeContext
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.checkers.ExtensionApplicabilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.collectNonExtensions
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertSymbolAndInvokeCompletion
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.detectImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext.Companion.createWeighingContext
import org.jetbrains.kotlin.idea.fir.HLIndexHelper
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.utils.addIfNotNull

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
        CallableInsertionOptions(detectImportStrategy(symbol), getInsertionStrategy(symbol))

    private fun KtAnalysisSession.getExtensionOptions(
        symbol: KtCallableSymbol,
        applicability: KtExtensionApplicabilityResult
    ): CallableInsertionOptions? =
        getInsertionStrategyForExtensionFunction(symbol, applicability)?.let { CallableInsertionOptions(detectImportStrategy(symbol), it) }

    protected open fun KtAnalysisSession.filter(symbol: KtCallableSymbol): Boolean = true

    private val shouldCompleteTopLevelCallablesFromIndex: Boolean
        get() = prefixMatcher.prefix.isNotEmpty()

    override fun KtAnalysisSession.complete(positionContext: FirNameReferencePositionContext): Unit = with(positionContext) {
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val expectedType = nameExpression.getExpectedType()
        val scopesContext = originalKtFile.getScopeContextForPosition(nameExpression)

        val extensionChecker = ExtensionApplicabilityChecker {
            it.checkExtensionIsSuitable(originalKtFile, nameExpression, explicitReceiver)
        }

        val receiver = explicitReceiver
        val weighingContext = createWeighingContext(receiver, expectedType, scopesContext.implicitReceivers, basicContext.fakeKtFile)

        when {
            receiver != null -> {
                collectDotCompletion(
                    scopesContext.scopes,
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
        implicitScopesContext: KtScopeContext,
        context: WeighingContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ) {
        val (implicitScopes, implicitReceivers) = implicitScopesContext
        val implicitReceiversTypes = implicitReceivers.map { it.type }

        val availableNonExtensions = collectNonExtensions(implicitScopes, visibilityChecker, scopeNameFilter) { filter(it) }
        val extensionsWhichCanBeCalled = collectSuitableExtensions(implicitScopes, extensionChecker, visibilityChecker)

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
            .distinctBy {
                when (it) {
                    is KtVariableLikeSymbol -> it.name
                    else -> it
                }
            }
            .forEach { addCallableSymbolToCompletion(context, it, getOptions(it)) }

        // Here we can't rely on deduplication in LookupElementSink because extension members can have types substituted, which won't be
        // equal to the same symbols from top level without substitution.
        val extensionMembers = mutableSetOf<KtCallableSymbol>()
        extensionsWhichCanBeCalled.forEach { (symbol, applicabilityResult) ->
            getExtensionOptions(symbol, applicabilityResult)?.let {
                extensionMembers += symbol
                addCallableSymbolToCompletion(context, symbol, it, applicabilityResult.substitutor)
            }
        }

        if (shouldCompleteTopLevelCallablesFromIndex) {
            val topLevelCallables = indexHelper.getTopLevelCallables(scopeNameFilter)
            topLevelCallables.asSequence()
                .map { it.getSymbol() as KtCallableSymbol }
                .filter { it !in extensionMembers && with(visibilityChecker) { isVisible(it) } }
                .forEach { addCallableSymbolToCompletion(context, it, getOptions(it)) }
        }

        collectTopLevelExtensionsFromIndices(implicitReceiversTypes, extensionChecker, visibilityChecker)
            .filter { it !in extensionMembers }
            .forEach {
                addCallableSymbolToCompletion(context, it, getOptions(it))
            }
    }

    protected open fun KtAnalysisSession.collectDotCompletion(
        implicitScopes: KtScope,
        explicitReceiver: KtExpression,
        context: WeighingContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ) {
        val symbol = explicitReceiver.reference()?.resolveToSymbol()
        when {
            symbol is KtPackageSymbol -> collectDotCompletionForPackageReceiver(symbol, context, visibilityChecker)
            symbol is KtNamedClassOrObjectSymbol && symbol.classKind == KtClassKind.ENUM_CLASS -> {
                collectNonExtensions(symbol.getStaticMemberScope(), visibilityChecker, scopeNameFilter).forEach { memberSymbol ->
                    addCallableSymbolToCompletion(
                        context,
                        memberSymbol,
                        CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(memberSymbol))
                    )
                }
            }
            symbol is KtNamedClassOrObjectSymbol && !symbol.classKind.isObject && symbol.companionObject == null -> {
                // symbol cannot be used as callable receiver
            }
            else -> {
                collectDotCompletionForCallableReceiver(implicitScopes, explicitReceiver, context, extensionChecker, visibilityChecker)
            }
        }
    }

    private fun KtAnalysisSession.collectDotCompletionForPackageReceiver(
        packageSymbol: KtPackageSymbol,
        context: WeighingContext,
        visibilityChecker: CompletionVisibilityChecker
    ) {
        packageSymbol.getPackageScope()
            .getCallableSymbols(scopeNameFilter)
            .filterNot { it.isExtension }
            .filter { with(visibilityChecker) { isVisible(it) } }
            .filter { filter(it) }
            .forEach { callable ->
                addCallableSymbolToCompletion(context, callable, getOptions(callable))
            }
    }

    protected fun KtAnalysisSession.collectDotCompletionForCallableReceiver(
        implicitScopes: KtScope,
        explicitReceiver: KtExpression,
        context: WeighingContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ) {
        val smartCastInfo = explicitReceiver.getSmartCastInfo()
        if (smartCastInfo?.isStable == false) {
            // Collect members available from unstable smartcast as well.
            collectDotCompletionForCallableReceiver(
                smartCastInfo.smartCastType,
                visibilityChecker,
                implicitScopes,
                extensionChecker,
                context,
                // Only offer the hint if the type is denotable.
                smartCastInfo.smartCastType.takeIf { it.approximateToSuperPublicDenotable() == null }
            )
        }

        val receiverType = explicitReceiver.getKtType() ?: return
        collectDotCompletionForCallableReceiver(receiverType, visibilityChecker, implicitScopes, extensionChecker, context)
    }

    private fun KtAnalysisSession.collectDotCompletionForCallableReceiver(
        typeOfPossibleReceiver: KtType,
        visibilityChecker: CompletionVisibilityChecker,
        implicitScopes: KtScope,
        extensionChecker: ExtensionApplicabilityChecker,
        context: WeighingContext,
        explicitReceiverTypeHint: KtType? = null
    ) {
        val possibleReceiverScope = typeOfPossibleReceiver.getTypeScope() ?: return

        val nonExtensionMembers = collectNonExtensions(possibleReceiverScope, visibilityChecker, scopeNameFilter) { filter(it) }
        val extensionNonMembers = collectSuitableExtensions(implicitScopes, extensionChecker, visibilityChecker)

        val syntheticPropertyOrigins = mutableSetOf<KtFunctionSymbol>()
        nonExtensionMembers.toList()
            .onEach {
                if (it is KtSyntheticJavaPropertySymbol) {
                    syntheticPropertyOrigins.add(it.javaGetterSymbol)
                    syntheticPropertyOrigins.addIfNotNull(it.javaSetterSymbol)
                }
            }
            .forEach {
                if (it !in syntheticPropertyOrigins) {
                    // For basic completion, FE1.0 skips Java functions that are mapped to Kotlin properties.
                    addCallableSymbolToCompletion(context, it, getOptions(it), explicitReceiverTypeHint = explicitReceiverTypeHint)
                }
            }

        // Here we can't rely on deduplication in LookupElementSink because extension members can have types substituted, which won't be
        // equal to the same symbols from top level without substitution.
        val extensionMembers = mutableSetOf<KtCallableSymbol>()
        extensionNonMembers.forEach { (symbol, applicabilityResult) ->
            getExtensionOptions(symbol, applicabilityResult)?.let {
                extensionMembers += symbol
                addCallableSymbolToCompletion(
                    context,
                    symbol,
                    it,
                    applicabilityResult.substitutor,
                    explicitReceiverTypeHint = explicitReceiverTypeHint
                )
            }
        }

        collectTopLevelExtensionsFromIndices(listOf(typeOfPossibleReceiver), extensionChecker, visibilityChecker)
            .filter { it !in extensionMembers && filter(it) }
            .forEach { addCallableSymbolToCompletion(context, it, getOptions(it), explicitReceiverTypeHint = explicitReceiverTypeHint) }
    }

    private fun KtAnalysisSession.collectTopLevelExtensionsFromIndices(
        receiverTypes: List<KtType>,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<KtCallableSymbol> {
        val implicitReceiverNames = findAllNamesOfTypes(receiverTypes)
        val topLevelExtensions = indexHelper.getTopLevelExtensions(scopeNameFilter, implicitReceiverNames)

        return topLevelExtensions.asSequence()
            .map { it.getSymbol() as KtCallableSymbol }
            .filter { filter(it) }
            .filter { with(visibilityChecker) { isVisible(it) } }
            .filter { with(extensionChecker) { isApplicable(it).isApplicable } }
    }

    private fun KtAnalysisSession.collectSuitableExtensions(
        scope: KtScope,
        hasSuitableExtensionReceiver: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<Pair<KtCallableSymbol, KtExtensionApplicabilityResult>> =
        scope.getCallableSymbols(scopeNameFilter)
            .filter { it.isExtension || it is KtVariableLikeSymbol && (it.returnType as? KtFunctionalType)?.hasReceiver == true }
            .filter { with(visibilityChecker) { isVisible(it) } }
            .filter { filter(it) }
            .mapNotNull { callable ->
                val applicabilityResult = with(hasSuitableExtensionReceiver) { isApplicable(callable) }
                if (applicabilityResult.isApplicable) {
                    callable to applicabilityResult
                } else null
            }

    private fun KtAnalysisSession.findAllNamesOfTypes(implicitReceiversTypes: List<KtType>) =
        implicitReceiversTypes.flatMapTo(hashSetOf()) { with(typeNamesProvider) { findAllNames(it) } }
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
        implicitScopes: KtScope,
        explicitReceiver: KtExpression,
        context: WeighingContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker
    ) {
        when (val resolved = explicitReceiver.reference()?.resolveToSymbol()) {
            is KtPackageSymbol -> return
            is KtNamedClassOrObjectSymbol -> {
                resolved.getMemberScope()
                    .getCallableSymbols(scopeNameFilter)
                    .filter { with(visibilityChecker) { isVisible(it) } }
                    .forEach { symbol ->
                        addCallableSymbolToCompletion(context.withoutExpectedType(), symbol, getOptions(symbol))
                    }

            }
            else -> {
                collectDotCompletionForCallableReceiver(implicitScopes, explicitReceiver, context, extensionChecker, visibilityChecker)
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
    fun KtAnalysisSession.findAllNames(type: KtType): Set<String> {
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