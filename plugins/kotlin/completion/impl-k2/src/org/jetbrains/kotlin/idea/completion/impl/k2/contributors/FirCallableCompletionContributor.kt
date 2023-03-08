// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtExtensionApplicabilityResult
import org.jetbrains.kotlin.analysis.api.components.KtScopeContext
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
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
import org.jetbrains.kotlin.psi.psiUtil.nextSiblingOfSameType

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

    protected data class CallableWithMetadataForCompletion(
        private val _symbol: KtCallableSymbol,
        private val _substitutor: KtSubstitutor,
        private val _explicitReceiverTypeHint: KtType?,
        val options: CallableInsertionOptions,
        val scopeKind: KtScopeKind?,
        val withExpectedType: Boolean,
    ) : KtLifetimeOwner {
        override val token: KtLifetimeToken
            get() = _symbol.token
        val symbol: KtCallableSymbol get() = withValidityAssertion { _symbol }
        val substitutor: KtSubstitutor get() = withValidityAssertion { _substitutor }
        val explicitReceiverTypeHint: KtType? get() = withValidityAssertion { _explicitReceiverTypeHint }
    }

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
        val weighingContextWithoutExpectedType = weighingContext.withoutExpectedType()

        val callablesWithMetadata: Sequence<CallableWithMetadataForCompletion> = when {
            receiver != null -> collectDotCompletion(scopesContext, receiver, extensionChecker, visibilityChecker)
            else -> completeWithoutReceiver(scopesContext, extensionChecker, visibilityChecker)
        }.filterOutUninitializedCallables(positionContext.position)

        for (callableWithMetadata in callablesWithMetadata) {
            val context = if (callableWithMetadata.withExpectedType) weighingContext else weighingContextWithoutExpectedType
            addCallableSymbolToCompletion(
                context,
                callableWithMetadata.symbol,
                callableWithMetadata.options,
                callableWithMetadata.scopeKind,
                callableWithMetadata.substitutor,
                priority = null,
                callableWithMetadata.explicitReceiverTypeHint)
        }
    }

    private fun KtAnalysisSession.completeWithoutReceiver(
        scopeContext: KtScopeContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
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
            .forEach { (symbol, scopeKind) -> yield(createCallableWithMetadata(symbol, scopeKind)) }

        // Here we can't rely on deduplication in LookupElementSink because extension members can have types substituted, which won't be
        // equal to the same symbols from top level without substitution.
        val extensionMembers = mutableSetOf<KtCallableSymbol>()
        extensionsWhichCanBeCalled.forEach { (symbolWithScopeKind, applicabilityResult) ->
            val (symbol, scopeKind) = symbolWithScopeKind
            getExtensionOptions(symbol, applicabilityResult)?.let {
                extensionMembers += symbol
                yield(createCallableWithMetadata(symbol, scopeKind, it, applicabilityResult.substitutor))
            }
        }

        if (shouldCompleteTopLevelCallablesFromIndex) {
            val topLevelCallables = indexHelper.getTopLevelCallables(scopeNameFilter)
            val callablesFromIndex = topLevelCallables.asSequence()
                .filterNot { it.canDefinitelyNotBeSeenFromOtherFile() }
                .filter { it.canBeAnalysed() }
                .map { it.getSymbol() as KtCallableSymbol }

            val callablesFromExtensions = getResolveExtensionScopeWithTopLevelDeclarations().getCallableSymbols(scopeNameFilter)

            (callablesFromIndex + callablesFromExtensions)
                .filter { it !in extensionMembers && visibilityChecker.isVisible(it) }
                .forEach { yield(createCallableWithMetadata(it)) }
        }

        collectTopLevelExtensionsFromIndices(implicitReceiversTypes, extensionChecker, visibilityChecker)
            .filter { it !in extensionMembers }
            .forEach { yield(createCallableWithMetadata(it)) }
    }

    protected open fun KtAnalysisSession.collectDotCompletion(
        scopeContext: KtScopeContext,
        explicitReceiver: KtExpression,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ): Sequence<CallableWithMetadataForCompletion> {
        val symbol = explicitReceiver.reference()?.resolveToSymbol()
        return when {
            symbol is KtPackageSymbol -> collectDotCompletionForPackageReceiver(symbol, visibilityChecker)

            symbol is KtNamedClassOrObjectSymbol && symbol.hasImportantStaticMemberScope -> sequence {
                if (symbol.classKind == KtClassKind.ENUM_CLASS) {
                    yieldAll(collectDotCompletionForCallableReceiver(scopeContext, explicitReceiver, extensionChecker, visibilityChecker))
                }
                val nonExtensions = collectNonExtensionsFromScope(
                    symbol.getStaticMemberScope(),
                    visibilityChecker,
                    scopeNameFilter,
                    excludeEnumEntries,
                )

                nonExtensions.forEach { memberSymbol ->
                    val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(memberSymbol))
                    yield(createCallableWithMetadata(memberSymbol, scopeKind = null, options))
                }
            }

            symbol is KtNamedClassOrObjectSymbol && !symbol.canBeUsedAsReceiver -> {
                // symbol cannot be used as callable receiver
                emptySequence()
            }

            else -> collectDotCompletionForCallableReceiver(scopeContext, explicitReceiver, extensionChecker, visibilityChecker)
        }
    }

    private val KtNamedClassOrObjectSymbol.hasImportantStaticMemberScope: Boolean
        get() = classKind == KtClassKind.ENUM_CLASS ||
                origin == KtSymbolOrigin.JAVA

    private val KtNamedClassOrObjectSymbol.canBeUsedAsReceiver: Boolean
        get() = classKind.isObject || companionObject != null

    private fun KtAnalysisSession.collectDotCompletionForPackageReceiver(
        packageSymbol: KtPackageSymbol,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<CallableWithMetadataForCompletion> =
        packageSymbol.getPackageScope()
            .getCallableSymbols(scopeNameFilter)
            .filterNot { it.isExtension }
            .filter { visibilityChecker.isVisible(it) }
            .filter { filter(it) }
            .map { callable ->
                val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(callable))
                createCallableWithMetadata(callable, options = options)
            }

    protected fun KtAnalysisSession.collectDotCompletionForCallableReceiver(
        scopeContext: KtScopeContext,
        explicitReceiver: KtExpression,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
        allowSyntheticJavaProperties: Boolean = true,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val smartCastInfo = explicitReceiver.getSmartCastInfo()
        if (smartCastInfo?.isStable == false) {
            // Collect members available from unstable smartcast as well.
            val callablesWithMetadataFromUnstableSmartCast = collectDotCompletionForCallableReceiver(
                smartCastInfo.smartCastType,
                visibilityChecker,
                scopeContext,
                extensionChecker,
                allowSyntheticJavaProperties,
                // Only offer the hint if the type is denotable.
                smartCastInfo.smartCastType.takeIf { it.approximateToSuperPublicDenotable(true) == null }
            )
            yieldAll(callablesWithMetadataFromUnstableSmartCast)
        }

        val receiverType = explicitReceiver.getKtType().takeUnless { it is KtErrorType } ?: return@sequence
        val callablesWithMetadata = collectDotCompletionForCallableReceiver(
            receiverType,
            visibilityChecker,
            scopeContext,
            extensionChecker,
            allowSyntheticJavaProperties
        )
        yieldAll(callablesWithMetadata)
    }

    private fun KtAnalysisSession.collectDotCompletionForCallableReceiver(
        typeOfPossibleReceiver: KtType,
        visibilityChecker: CompletionVisibilityChecker,
        scopeContext: KtScopeContext,
        extensionChecker: ExtensionApplicabilityChecker,
        allowSyntheticJavaProperties: Boolean = true,
        explicitReceiverTypeHint: KtType? = null
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val nonExtensionMembers = collectNonExtensionsForType(
            typeOfPossibleReceiver,
            visibilityChecker,
            scopeNameFilter,
            excludeEnumEntries,
            withSyntheticJavaProperties = allowSyntheticJavaProperties
        ) { filter(it) }
        val extensionNonMembers = collectSuitableExtensions(scopeContext, extensionChecker, visibilityChecker)

        nonExtensionMembers.forEach { (symbol, scopeKind) ->
            yield(createCallableWithMetadata(symbol, scopeKind, explicitReceiverTypeHint = explicitReceiverTypeHint))
        }

        // Here we can't rely on deduplication in LookupElementSink because extension members can have types substituted, which won't be
        // equal to the same symbols from top level without substitution.
        val extensionMembers = mutableSetOf<CallableId>()
        extensionNonMembers.forEach { (symbolWithScopeKind, applicabilityResult) ->
            val (symbol, scopeKind) = symbolWithScopeKind
            getExtensionOptions(symbol, applicabilityResult)?.let {
                symbol.callableIdIfNonLocal?.let(extensionMembers::add)
                yield(createCallableWithMetadata(symbol, scopeKind, it, applicabilityResult.substitutor, explicitReceiverTypeHint))
            }
        }

        collectTopLevelExtensionsFromIndices(listOf(typeOfPossibleReceiver), extensionChecker, visibilityChecker)
            .filter { it.callableIdIfNonLocal !in extensionMembers && filter(it) }
            .forEach { yield(createCallableWithMetadata(it, explicitReceiverTypeHint = explicitReceiverTypeHint)) }
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

    protected fun KtAnalysisSession.createCallableWithMetadata(
        symbol: KtCallableSymbol,
        scopeKind: KtScopeKind? = null,
        options: CallableInsertionOptions = getOptions(symbol),
        substitutor: KtSubstitutor = KtSubstitutor.Empty(token),
        explicitReceiverTypeHint: KtType? = null,
        withExpectedType: Boolean = true,
    ) = CallableWithMetadataForCompletion(symbol, substitutor, explicitReceiverTypeHint, options, scopeKind, withExpectedType)

    context(KtAnalysisSession)
    private fun Sequence<CallableWithMetadataForCompletion>.filterOutUninitializedCallables(
        position: PsiElement
    ): Sequence<CallableWithMetadataForCompletion> {
        val uninitializedCallablesForPosition = collectUninitializedCallablesForPosition(position)
        return filterNot { it.symbol.psi in uninitializedCallablesForPosition }
    }

    context(KtAnalysisSession)
    private fun collectUninitializedCallablesForPosition(position: PsiElement): Set<KtCallableDeclaration> = buildSet {
        for (parent in position.parents(withSelf = false)) {
            val grandParent = parent.parent
            when (grandParent) {
                is KtParameter -> {
                    if (grandParent.defaultValue == parent) {
                        // Filter out current parameter and all parameters initialized after current parameter. In the following example:
                        // ```
                        // fun test(a, b: Int = <caret>, c: Int) {}
                        // ```
                        // `a` and `b` should not show up in completion.
                        val originalOrSelf = getOriginalDeclarationOrSelf(grandParent)
                        originalOrSelf.getNextParametersWithSelf().forEach { add(it) }
                    }
                }

                is KtProperty -> if (grandParent.initializer == parent) add(getOriginalDeclarationOrSelf(grandParent))
            }

            if (parent is KtDeclaration) break // we can use variable inside lambda or anonymous object located in its initializer
        }
    }

    private inline fun <reified T : KtDeclaration> KtAnalysisSession.getOriginalDeclarationOrSelf(declaration: T): T =
        declaration.getOriginalDeclaration() as? T ?: declaration

    private fun KtParameter.getNextParametersWithSelf(): Sequence<KtParameter> = generateSequence({ this }, { it.nextSiblingOfSameType() })
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
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<CallableWithMetadataForCompletion> {
        val allowSyntheticJavaProperties =
            explicitReceiver.languageVersionSettings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties)

        return when (val symbol = explicitReceiver.reference()?.resolveToSymbol()) {
            is KtPackageSymbol -> emptySequence()
            is KtNamedClassOrObjectSymbol -> sequence {
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
                    yield(createCallableWithMetadata(callableSymbol, scopeKind, withExpectedType = false))
                }
            }

            else -> {
                collectDotCompletionForCallableReceiver(
                    scopeContext,
                    explicitReceiver,
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