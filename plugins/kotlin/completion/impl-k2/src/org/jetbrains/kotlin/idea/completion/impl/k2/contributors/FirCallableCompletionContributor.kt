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
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.fir.codeInsight.HLIndexHelper
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.checkers.ApplicableExtension
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.checkers.ExtensionApplicabilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.*
import org.jetbrains.kotlin.idea.completion.contributors.helpers.ShadowedCallablesFilter
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.nextSiblingOfSameType

internal open class FirCallableCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<FirNameReferencePositionContext>(basicContext, priority) {
    private val typeNamesProvider = TypeNamesProvider(indexHelper)

    protected open fun KtAnalysisSession.getInsertionStrategy(signature: KtCallableSignature<*>): CallableInsertionStrategy =
        when (signature) {
            is KtFunctionLikeSignature<*> -> CallableInsertionStrategy.AsCall
            else -> CallableInsertionStrategy.AsIdentifier
        }

    protected open fun KtAnalysisSession.getInsertionStrategyForExtensionFunction(
        signature: KtCallableSignature<*>,
        applicabilityResult: KtExtensionApplicabilityResult
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KtExtensionApplicabilityResult.ApplicableAsExtensionCallable -> getInsertionStrategy(signature)
        is KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> CallableInsertionStrategy.AsCall
        is KtExtensionApplicabilityResult.NonApplicable -> null
    }

    protected fun KtAnalysisSession.getOptions(
        signature: KtCallableSignature<*>,
        noImportRequired: Boolean = false
    ): CallableInsertionOptions {
        val importStrategy = when {
            noImportRequired -> ImportStrategy.DoNothing
            else -> importStrategyDetector.detectImportStrategyForCallableSymbol(signature.symbol)
        }
        return CallableInsertionOptions(importStrategy, getInsertionStrategy(signature))
    }

    private fun KtAnalysisSession.getExtensionOptions(
        signature: KtCallableSignature<*>,
        applicability: KtExtensionApplicabilityResult
    ): CallableInsertionOptions? {
        val insertionStrategy = getInsertionStrategyForExtensionFunction(signature, applicability) ?: return null
        val isFunctionalVariableCall = applicability is KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall
        val importStrategy = importStrategyDetector.detectImportStrategyForCallableSymbol(signature.symbol, isFunctionalVariableCall)
        return CallableInsertionOptions(importStrategy, insertionStrategy)
    }

    protected open fun KtAnalysisSession.filter(symbol: KtCallableSymbol): Boolean = true

    private val shouldCompleteTopLevelCallablesFromIndex: Boolean
        get() = prefixMatcher.prefix.isNotEmpty()

    protected val excludeEnumEntries =
        !basicContext.project.languageVersionSettings.supportsFeature(LanguageFeature.EnumEntries)

    protected data class CallableWithMetadataForCompletion(
        private val _signature: KtCallableSignature<*>,
        private val _explicitReceiverTypeHint: KtType?,
        val options: CallableInsertionOptions,
        val symbolOrigin: CompletionSymbolOrigin,
        val withExpectedType: Boolean,
    ) : KtLifetimeOwner {
        override val token: KtLifetimeToken
            get() = _signature.token
        val signature: KtCallableSignature<*> get() = withValidityAssertion { _signature }
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
        }
            .filterOutShadowedCallables()
            .filterOutUninitializedCallables(positionContext.position)

        for (callableWithMetadata in callablesWithMetadata) {
            val context = if (callableWithMetadata.withExpectedType) weighingContext else weighingContextWithoutExpectedType
            addCallableSymbolToCompletion(
                context,
                callableWithMetadata.signature,
                callableWithMetadata.options,
                callableWithMetadata.symbolOrigin,
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
        availableNonExtensions.forEach { yield(createCallableWithMetadata(it.signature, it.scopeKind)) }

        extensionsWhichCanBeCalled.forEach { (signatureWithScopeKind, applicabilityResult) ->
            val signature = signatureWithScopeKind.signature
            getExtensionOptions(signature, applicabilityResult)?.let {
                yield(createCallableWithMetadata(signature, signatureWithScopeKind.scopeKind, options = it))
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
                .filterNot { it.isExtension } // extensions should be collected and checked with `collectTopLevelExtensionsFromIndices`
                .forEach { yield(createCallableWithMetadata(it.asSignature(), CompletionSymbolOrigin.Index)) }
        }

        collectTopLevelExtensionsFromIndexAndResolveExtensionScope(implicitReceiversTypes, extensionChecker, visibilityChecker)
            .forEach { (signature, applicabilityResult) ->
                val extensionOptions = getExtensionOptions(signature, applicabilityResult) ?: return@forEach
                yield(createCallableWithMetadata(signature, CompletionSymbolOrigin.Index, extensionOptions))
            }
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

                val staticScope = symbol.getStaticMemberScope()
                val staticScopeKind = KtScopeKind.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

                val nonExtensions = collectNonExtensionsFromScope(
                    staticScope,
                    visibilityChecker,
                    scopeNameFilter,
                    excludeEnumEntries,
                )

                nonExtensions.forEach { member ->
                    val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(member))
                    yield(createCallableWithMetadata(member, staticScopeKind, options = options))
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
    ): Sequence<CallableWithMetadataForCompletion> {
        val packageScope = packageSymbol.getPackageScope()
        val packageScopeKind = KtScopeKind.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

        return packageScope
            .getCallableSymbols(scopeNameFilter)
            .filterNot { it.isExtension }
            .filter { visibilityChecker.isVisible(it) }
            .filter { filter(it) }
            .map { callable ->
                val callableSignature = callable.asSignature()
                val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(callableSignature))
                createCallableWithMetadata(callableSignature, packageScopeKind, options = options)
            }
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
        val extensionNonMembers = collectSuitableExtensions(scopeContext, extensionChecker, visibilityChecker, typeOfPossibleReceiver)

        nonExtensionMembers.forEach { signatureWithScopeKind ->
            val callableWithMetadata = createCallableWithMetadata(
                signatureWithScopeKind.signature,
                signatureWithScopeKind.scopeKind,
                noImportRequired = true,
                explicitReceiverTypeHint = explicitReceiverTypeHint
            )
            yield(callableWithMetadata)
        }

        extensionNonMembers.forEach { (signatureWithScopeKind, applicabilityResult) ->
            val signature = signatureWithScopeKind.signature
            val scopeKind = signatureWithScopeKind.scopeKind
            getExtensionOptions(signature, applicabilityResult)?.let {
                yield(createCallableWithMetadata(signature, scopeKind, noImportRequired = false, options = it, explicitReceiverTypeHint))
            }
        }

        collectTopLevelExtensionsFromIndexAndResolveExtensionScope(listOf(typeOfPossibleReceiver), extensionChecker, visibilityChecker)
            .filter { (signature, _) -> filter(signature.symbol) }
            .forEach { (signature, applicabilityResult) ->
                val extensionOptions = getExtensionOptions(signature, applicabilityResult) ?: return@forEach
                val callableWithMetadata = createCallableWithMetadata(
                    signature,
                    CompletionSymbolOrigin.Index,
                    extensionOptions,
                    explicitReceiverTypeHint = explicitReceiverTypeHint
                )
                yield(callableWithMetadata)
            }
    }

    private fun KtAnalysisSession.collectTopLevelExtensionsFromIndexAndResolveExtensionScope(
        receiverTypes: List<KtType>,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ): Collection<ApplicableExtension> {
        if (receiverTypes.isEmpty()) return emptyList()

        val implicitReceiverNames = findAllNamesOfTypes(receiverTypes)
        val topLevelExtensionsFromIndex = indexHelper.getTopLevelExtensions(scopeNameFilter, implicitReceiverNames)
            .filterNot { it.canDefinitelyNotBeSeenFromOtherFile() }
            .filter { it.canBeAnalysed() }
            .map { it.getSymbol() as KtCallableSymbol }

        val resolveExtensionScope = getResolveExtensionScopeWithTopLevelDeclarations()
        val topLevelExtensionsFromResolveExtension = resolveExtensionScope.getCallableSymbols(scopeNameFilter).filter { it.isExtension }

        return (topLevelExtensionsFromIndex + topLevelExtensionsFromResolveExtension)
            .filter { filter(it) }
            .filter { visibilityChecker.isVisible(it) }
            .mapNotNull { checkApplicabilityAndSubstitute(it, extensionChecker) }
            .let { ShadowedCallablesFilter.sortExtensions(it, receiverTypes) }
    }

    private fun KtAnalysisSession.collectSuitableExtensions(
        scopeContext: KtScopeContext,
        extensionChecker: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
        explicitReceiverType: KtType? = null,
    ): Sequence<Pair<KtCallableSignatureWithContainingScopeKind, KtExtensionApplicabilityResult.Applicable>> {
        val receiversTypes = explicitReceiverType?.let { listOf(it) } ?: scopeContext.implicitReceivers.map { it.type }

        return scopeContext.scopes.asSequence().flatMap { scopeWithKind ->
            val extensions = collectSuitableExtensions(scopeWithKind.scope, receiversTypes, extensionChecker, visibilityChecker)
            extensions.map { (signature, applicability) ->
                KtCallableSignatureWithContainingScopeKind(signature, scopeWithKind.kind) to applicability
            }
        }
    }

    private fun KtAnalysisSession.collectSuitableExtensions(
        scope: KtScope,
        receiverTypes: List<KtType>,
        hasSuitableExtensionReceiver: ExtensionApplicabilityChecker,
        visibilityChecker: CompletionVisibilityChecker,
    ): Collection<ApplicableExtension> =
        scope.getCallableSymbols(scopeNameFilter)
            .filter { it.isExtension || it is KtVariableLikeSymbol && (it.returnType as? KtFunctionalType)?.hasReceiver == true }
            .filter { visibilityChecker.isVisible(it) }
            .filter { filter(it) }
            .mapNotNull { callable -> checkApplicabilityAndSubstitute(callable, hasSuitableExtensionReceiver) }
            .let { ShadowedCallablesFilter.sortExtensions(it.toList(), receiverTypes) }

    /**
     * If [callableSymbol] is applicable returns substituted signature and applicability result, otherwise, null.
     */
    context(KtAnalysisSession)
    private fun checkApplicabilityAndSubstitute(
        callableSymbol: KtCallableSymbol,
        extensionChecker: ExtensionApplicabilityChecker
    ): ApplicableExtension? {
        val applicabilityResult = extensionChecker.checkApplicability(callableSymbol)
        return if (applicabilityResult is KtExtensionApplicabilityResult.Applicable) {
            ApplicableExtension(callableSymbol.substitute(applicabilityResult.substitutor), applicabilityResult)
        } else null
    }

    private fun KtAnalysisSession.findAllNamesOfTypes(implicitReceiversTypes: List<KtType>) =
        implicitReceiversTypes.flatMapTo(hashSetOf()) { typeNamesProvider.findAllNames(it) }

    context(KtAnalysisSession)
    protected fun createCallableWithMetadata(
        signature: KtCallableSignature<*>,
        scopeKind: KtScopeKind,
        noImportRequired: Boolean = false,
        options: CallableInsertionOptions = getOptions(signature, noImportRequired),
        explicitReceiverTypeHint: KtType? = null,
        withExpectedType: Boolean = true,
    ): CallableWithMetadataForCompletion {
        val symbolOrigin = CompletionSymbolOrigin.Scope(scopeKind)
        return CallableWithMetadataForCompletion(signature, explicitReceiverTypeHint, options, symbolOrigin, withExpectedType)
    }

    context(KtAnalysisSession)
    protected fun createCallableWithMetadata(
        signature: KtCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
        options: CallableInsertionOptions = getOptions(signature),
        explicitReceiverTypeHint: KtType? = null,
        withExpectedType: Boolean = true,
    ) = CallableWithMetadataForCompletion(signature, explicitReceiverTypeHint, options, symbolOrigin, withExpectedType)

    context(KtAnalysisSession)
    private fun Sequence<CallableWithMetadataForCompletion>.filterOutUninitializedCallables(
        position: PsiElement
    ): Sequence<CallableWithMetadataForCompletion> {
        val uninitializedCallablesForPosition = collectUninitializedCallablesForPosition(position)
        return filterNot { it.signature.symbol.psi in uninitializedCallablesForPosition }
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

    context(KtAnalysisSession)
    private fun Sequence<CallableWithMetadataForCompletion>.filterOutShadowedCallables(): Sequence<CallableWithMetadataForCompletion> =
        sequence {
            val shadowedCallablesFilter = ShadowedCallablesFilter()

            for (callableWithMetadata in this@filterOutShadowedCallables) {
                val excludeFromCompletion = shadowedCallablesFilter.excludeFromCompletion(
                    callableWithMetadata.signature,
                    callableWithMetadata.options,
                    callableWithMetadata.symbolOrigin,
                )
                if (!excludeFromCompletion) yield(callableWithMetadata)
            }
        }
}

internal class FirCallableReferenceCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCallableCompletionContributor(basicContext, priority) {

    override fun KtAnalysisSession.getInsertionStrategy(signature: KtCallableSignature<*>): CallableInsertionStrategy =
        CallableInsertionStrategy.AsIdentifier

    override fun KtAnalysisSession.getInsertionStrategyForExtensionFunction(
        signature: KtCallableSignature<*>,
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
                val staticScopeKind = KtScopeKind.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)
                val staticMembers = collectNonExtensionsFromScope(
                    staticScope,
                    visibilityChecker,
                    scopeNameFilter,
                    excludeEnumEntries
                ) { filter(it) }.map { KtCallableSignatureWithContainingScopeKind(it, staticScopeKind) }

                (nonExtensionMembers + staticMembers).forEach {
                    yield(createCallableWithMetadata(it.signature, it.scopeKind, noImportRequired = true, withExpectedType = false))
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
    override fun KtAnalysisSession.getInsertionStrategy(signature: KtCallableSignature<*>): CallableInsertionStrategy =
        insertionStrategy

    override fun KtAnalysisSession.getInsertionStrategyForExtensionFunction(
        signature: KtCallableSignature<*>,
        applicabilityResult: KtExtensionApplicabilityResult
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KtExtensionApplicabilityResult.ApplicableAsExtensionCallable -> getInsertionStrategy(signature)
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