// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.util.parents
import com.intellij.util.applyIf
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.components.KtExtensionApplicabilityResult
import org.jetbrains.kotlin.analysis.api.components.KtScopeContext
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.components.KtScopeWithKind
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtPossibleMultiplatformSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.checkers.ApplicableExtension
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.checkers.ExtensionApplicabilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.*
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionInsertionHelper
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.nextSiblingOfSameType
import org.jetbrains.kotlin.resolve.ArrayFqNames

internal open class FirCallableCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<KotlinNameReferencePositionContext>(basicContext, priority) {
    context(KtAnalysisSession)
    protected open fun getImportStrategy(signature: KtCallableSignature<*>, isImportDefinitelyNotRequired: Boolean): ImportStrategy =
        if (isImportDefinitelyNotRequired) {
            ImportStrategy.DoNothing
        } else {
            importStrategyDetector.detectImportStrategyForCallableSymbol(signature.symbol)
        }

    context(KtAnalysisSession)
    protected open fun getInsertionStrategy(signature: KtCallableSignature<*>): CallableInsertionStrategy =
        when (signature) {
            is KtFunctionLikeSignature<*> -> CallableInsertionStrategy.AsCall
            else -> CallableInsertionStrategy.AsIdentifier
        }

    context(KtAnalysisSession)
    protected open fun getInsertionStrategyForExtensionFunction(
        signature: KtCallableSignature<*>,
        applicabilityResult: KtExtensionApplicabilityResult?
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KtExtensionApplicabilityResult.ApplicableAsExtensionCallable -> getInsertionStrategy(signature)
        is KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> CallableInsertionStrategy.AsCall
        else -> null
    }

    context(KtAnalysisSession)
    private fun getOptions(
        signature: KtCallableSignature<*>,
        isImportDefinitelyNotRequired: Boolean = false
    ): CallableInsertionOptions = CallableInsertionOptions(
        getImportStrategy(signature, isImportDefinitelyNotRequired),
        getInsertionStrategy(signature)
    )

    context(KtAnalysisSession)
    private fun getExtensionOptions(
        signature: KtCallableSignature<*>,
        applicability: KtExtensionApplicabilityResult?
    ): CallableInsertionOptions? {
        val insertionStrategy = getInsertionStrategyForExtensionFunction(signature, applicability) ?: return null
        val isFunctionalVariableCall = applicability is KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall
        val importStrategy = importStrategyDetector.detectImportStrategyForCallableSymbol(signature.symbol, isFunctionalVariableCall)
        return CallableInsertionOptions(importStrategy, insertionStrategy)
    }

    context(KtAnalysisSession)
    protected open fun filter(symbol: KtCallableSymbol, sessionParameters: FirCompletionSessionParameters): Boolean =
        sessionParameters.allowExpectedDeclarations || !(symbol is KtPossibleMultiplatformSymbol && symbol.isExpect)

    private val shouldCompleteTopLevelCallablesFromIndex: Boolean
        get() = prefixMatcher.prefix.isNotEmpty()

    protected data class CallableWithMetadataForCompletion(
        private val _signature: KtCallableSignature<*>,
        private val _explicitReceiverTypeHint: KtType?,
        val options: CallableInsertionOptions,
        val symbolOrigin: CompletionSymbolOrigin,
    ) : KtLifetimeOwner {
        override val token: KtLifetimeToken
            get() = _signature.token
        val signature: KtCallableSignature<*> get() = withValidityAssertion { _signature }
        val explicitReceiverTypeHint: KtType? get() = withValidityAssertion { _explicitReceiverTypeHint }
    }

    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinNameReferencePositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ): Unit = with(positionContext) {
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val scopesContext = originalKtFile.getScopeContextForPosition(nameExpression)

        val extensionChecker = if (positionContext is KotlinSimpleNameReferencePositionContext) {
            object : ExtensionApplicabilityChecker {
                /**
                 * Cached applicability results for callable extension symbols.
                 * The cache lifetime doesn't exceed the lifetime of a single completion session.
                 *
                 * If an extension is applicable but some of its type parameters are substituted to error types, then multiple calls to
                 * [KtAnalysisSession.checkExtensionIsSuitable] produce unequal substitutors, and subsequently unequal signatures, because
                 * error types are considered equal only if their underlying types are referentially equal, so we need to use [cache] in order
                 * to avoid unexpected unequal signatures.
                 *
                 * The cache also helps to avoid recalculation of applicability for extensions which are suggested twice:
                 * the first time while processing the scope context and the second time while processing callables from indexes.
                 */
                private val cache: MutableMap<KtCallableSymbol, KtExtensionApplicabilityResult> = mutableMapOf()

                context(KtAnalysisSession)
                override fun checkApplicability(symbol: KtCallableSymbol): KtExtensionApplicabilityResult = cache.getOrPut(symbol) {
                    symbol.checkExtensionIsSuitable(originalKtFile, positionContext.nameExpression, positionContext.explicitReceiver)
                }
            }
        } else null

        val receiver = explicitReceiver

        val callablesWithMetadata: Sequence<CallableWithMetadataForCompletion> = when {
            receiver != null -> collectDotCompletion(scopesContext, receiver, extensionChecker, visibilityChecker, sessionParameters)
            else -> completeWithoutReceiver(scopesContext, extensionChecker, visibilityChecker, sessionParameters)
        }
            .filterIfInsideAnnotationEntryArgument(positionContext.position, weighingContext.expectedType)
            .filterOutShadowedCallables(weighingContext.expectedType)
            .filterOutUninitializedCallables(positionContext.position)

        for (callableWithMetadata in callablesWithMetadata) {
            addCallableSymbolToCompletion(
                weighingContext,
                callableWithMetadata.signature,
                callableWithMetadata.options,
                callableWithMetadata.symbolOrigin,
                priority = null,
                callableWithMetadata.explicitReceiverTypeHint
            )
        }
    }

    context(KtAnalysisSession)
    private fun completeWithoutReceiver(
        scopeContext: KtScopeContext,
        extensionChecker: ExtensionApplicabilityChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val implicitReceivers = scopeContext.implicitReceivers
        val implicitReceiversTypes = implicitReceivers.map { it.type }

        val availableLocalAndMemberNonExtensions = collectLocalAndMemberNonExtensionsFromScopeContext(
            scopeContext,
            visibilityChecker,
            scopeNameFilter,
            sessionParameters,
        ) { filter(it, sessionParameters) }
        val extensionsWhichCanBeCalled = collectSuitableExtensions(scopeContext, extensionChecker, visibilityChecker, sessionParameters)
        val availableStaticAndTopLevelNonExtensions = collectStaticAndTopLevelNonExtensionsFromScopeContext(
            scopeContext,
            visibilityChecker,
            scopeNameFilter,
            sessionParameters,
        ) { filter(it, sessionParameters) }

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
        availableLocalAndMemberNonExtensions.forEach { yield(createCallableWithMetadata(it.signature, it.scopeKind)) }

        extensionsWhichCanBeCalled.forEach { (signatureWithScopeKind, insertionOptions) ->
            val signature = signatureWithScopeKind.signature
            yield(createCallableWithMetadata(signature, signatureWithScopeKind.scopeKind, options = insertionOptions))
        }
        availableStaticAndTopLevelNonExtensions.forEach { yield(createCallableWithMetadata(it.signature, it.scopeKind)) }


        if (shouldCompleteTopLevelCallablesFromIndex) {
            val topLevelCallablesFromIndex = symbolFromIndexProvider.getTopLevelCallableSymbolsByNameFilter(scopeNameFilter) {
                !it.canDefinitelyNotBeSeenFromOtherFile() && it.canBeAnalysed()
            }

            topLevelCallablesFromIndex
                .filter { filter(it, sessionParameters) }
                .filter { visibilityChecker.isVisible(it) }
                .forEach { yield(createCallableWithMetadata(it.asSignature(), CompletionSymbolOrigin.Index)) }
        }

        collectTopLevelExtensionsFromIndexAndResolveExtensionScope(
            implicitReceiversTypes,
            extensionChecker,
            visibilityChecker,
            sessionParameters,
        )
            .forEach { applicableExtension ->
                val signature = applicableExtension.signature
                yield(createCallableWithMetadata(signature, CompletionSymbolOrigin.Index, applicableExtension.insertionOptions))
            }
    }

    context(KtAnalysisSession)
    protected open fun collectDotCompletion(
        scopeContext: KtScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: ExtensionApplicabilityChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()
        return when {
            symbol is KtPackageSymbol -> collectDotCompletionForPackageReceiver(symbol, visibilityChecker, sessionParameters)

            else -> sequence {
                if (symbol is KtNamedClassOrObjectSymbol && symbol.hasImportantStaticMemberScope) {
                    yieldAll(collectDotCompletionFromStaticScope(symbol, withCompanionScope = false, visibilityChecker, sessionParameters))
                }

                if (symbol !is KtNamedClassOrObjectSymbol || symbol.canBeUsedAsReceiver) {
                    yieldAll(
                        collectDotCompletionForCallableReceiver(
                            scopeContext,
                            explicitReceiver,
                            extensionChecker,
                            visibilityChecker,
                            sessionParameters,
                        )
                    )
                }
            }
        }
    }

    private val KtNamedClassOrObjectSymbol.hasImportantStaticMemberScope: Boolean
        get() = classKind == KtClassKind.ENUM_CLASS ||
                origin == KtSymbolOrigin.JAVA

    private val KtNamedClassOrObjectSymbol.canBeUsedAsReceiver: Boolean
        get() = classKind.isObject || companionObject != null

    context(KtAnalysisSession)
    private fun collectDotCompletionForPackageReceiver(
        packageSymbol: KtPackageSymbol,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> {
        val packageScope = packageSymbol.getPackageScope()
        val packageScopeKind = KtScopeKind.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

        return packageScope
            .getCallableSymbols(scopeNameFilter)
            .filterNot { it.isExtension }
            .filter { visibilityChecker.isVisible(it) }
            .filter { filter(it, sessionParameters) }
            .map { callable ->
                val callableSignature = callable.asSignature()
                val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(callableSignature))
                createCallableWithMetadata(callableSignature, packageScopeKind, options = options)
            }
    }

    context(KtAnalysisSession)
    protected fun collectDotCompletionForCallableReceiver(
        scopeContext: KtScopeContext,
        explicitReceiver: KtExpression,
        extensionChecker: ExtensionApplicabilityChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val receiverType = explicitReceiver.getKtType().takeUnless { it is KtErrorType } ?: return@sequence
        val callablesWithMetadata = collectDotCompletionForCallableReceiver(
            receiverType,
            visibilityChecker,
            scopeContext,
            extensionChecker,
            sessionParameters,
        )
        yieldAll(callablesWithMetadata)

        val smartCastInfo = explicitReceiver.getSmartCastInfo()
        if (smartCastInfo?.isStable == false) {
            // Collect members available from unstable smartcast as well.
            val callablesWithMetadataFromUnstableSmartCast = collectDotCompletionForCallableReceiver(
                smartCastInfo.smartCastType,
                visibilityChecker,
                scopeContext,
                extensionChecker,
                sessionParameters,
                // Only offer the hint if the type is denotable.
                smartCastInfo.smartCastType.takeIf { it.approximateToSuperPublicDenotable(true) == null }
            )
            yieldAll(callablesWithMetadataFromUnstableSmartCast)
        }
    }

    context(KtAnalysisSession)
    private fun collectDotCompletionForCallableReceiver(
        typeOfPossibleReceiver: KtType,
        visibilityChecker: CompletionVisibilityChecker,
        scopeContext: KtScopeContext,
        extensionChecker: ExtensionApplicabilityChecker?,
        sessionParameters: FirCompletionSessionParameters,
        explicitReceiverTypeHint: KtType? = null
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val nonExtensionMembers = collectNonExtensionsForType(
            typeOfPossibleReceiver,
            visibilityChecker,
            scopeNameFilter,
            sessionParameters,
        ) { filter(it, sessionParameters) }
        val extensionNonMembers = collectSuitableExtensions(
            scopeContext,
            extensionChecker,
            visibilityChecker,
            sessionParameters,
            typeOfPossibleReceiver
        )

        nonExtensionMembers.forEach { signatureWithScopeKind ->
            val callableWithMetadata = createCallableWithMetadata(
                signatureWithScopeKind.signature,
                signatureWithScopeKind.scopeKind,
                isImportDefinitelyNotRequired = true,
                explicitReceiverTypeHint = explicitReceiverTypeHint
            )
            yield(callableWithMetadata)
        }

        extensionNonMembers.forEach { (signatureWithScopeKind, insertionOptions) ->
            val signature = signatureWithScopeKind.signature
            val scopeKind = signatureWithScopeKind.scopeKind
            yield(
                createCallableWithMetadata(
                    signature,
                    scopeKind,
                    isImportDefinitelyNotRequired = false,
                    insertionOptions,
                    explicitReceiverTypeHint
                )
            )
        }

        collectTopLevelExtensionsFromIndexAndResolveExtensionScope(
            listOf(typeOfPossibleReceiver),
            extensionChecker,
            visibilityChecker,
            sessionParameters
        )
            .filter { filter(it.signature.symbol, sessionParameters) }
            .forEach { applicableExtension ->
                val callableWithMetadata = createCallableWithMetadata(
                    applicableExtension.signature,
                    CompletionSymbolOrigin.Index,
                    applicableExtension.insertionOptions,
                    explicitReceiverTypeHint = explicitReceiverTypeHint
                )
                yield(callableWithMetadata)
            }
    }

    context(KtAnalysisSession)
    private fun collectDotCompletionFromStaticScope(
        symbol: KtNamedClassOrObjectSymbol,
        withCompanionScope: Boolean,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> {
        val staticScope = symbol.staticScope(withCompanionScope)
        val staticScopeKind = KtScopeKind.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

        val nonExtensions = collectNonExtensionsFromScope(
            staticScope,
            visibilityChecker,
            scopeNameFilter,
            sessionParameters,
        )

        return nonExtensions.map { member ->
            val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(member))
            createCallableWithMetadata(member, staticScopeKind, options = options)
        }
    }

    context(KtAnalysisSession)
    private fun collectTopLevelExtensionsFromIndexAndResolveExtensionScope(
        receiverTypes: List<KtType>,
        extensionChecker: ExtensionApplicabilityChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Collection<ApplicableExtension> {
        if (receiverTypes.isEmpty()) return emptyList()

        val topLevelExtensionsFromIndex = symbolFromIndexProvider.getTopLevelExtensionCallableSymbolsByNameFilter(
            scopeNameFilter,
            receiverTypes,
        ) { !it.canDefinitelyNotBeSeenFromOtherFile() && it.canBeAnalysed() }

        return topLevelExtensionsFromIndex
            .filter { filter(it, sessionParameters) }
            .filter { visibilityChecker.isVisible(it) }
            .mapNotNull { checkApplicabilityAndSubstitute(it, extensionChecker) }
            .let { ShadowedCallablesFilter.sortExtensions(it.toList(), receiverTypes) }
    }

    context(KtAnalysisSession)
    private fun collectSuitableExtensions(
        scopeContext: KtScopeContext,
        extensionChecker: ExtensionApplicabilityChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
        explicitReceiverType: KtType? = null,
    ): Sequence<Pair<KtCallableSignatureWithContainingScopeKind, CallableInsertionOptions>> {
        val receiversTypes = explicitReceiverType?.let { listOf(it) } ?: scopeContext.implicitReceivers.map { it.type }

        return scopeContext.scopes.asSequence().flatMap { scopeWithKind ->
            collectSuitableExtensions(scopeWithKind.scope, receiversTypes, extensionChecker, visibilityChecker, sessionParameters)
                .map { KtCallableSignatureWithContainingScopeKind(it.signature, scopeWithKind.kind) to it.insertionOptions }
        }
    }

    context(KtAnalysisSession)
    private fun collectSuitableExtensions(
        scope: KtScope,
        receiverTypes: List<KtType>,
        hasSuitableExtensionReceiver: ExtensionApplicabilityChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Collection<ApplicableExtension> =
        scope.getCallableSymbols(scopeNameFilter)
            .filter { it.isExtension || it is KtVariableLikeSymbol && (it.returnType as? KtFunctionalType)?.hasReceiver == true }
            .filter { visibilityChecker.isVisible(it) }
            .filter { filter(it, sessionParameters) }
            .mapNotNull { callable -> checkApplicabilityAndSubstitute(callable, hasSuitableExtensionReceiver) }
            .let { ShadowedCallablesFilter.sortExtensions(it.toList(), receiverTypes) }

    /**
     * If [callableSymbol] is applicable returns substituted signature and insertion options, otherwise, null.
     * When [extensionChecker] is null, no check is carried and applicability result is null.
     */
    context(KtAnalysisSession)
    private fun checkApplicabilityAndSubstitute(
        callableSymbol: KtCallableSymbol,
        extensionChecker: ExtensionApplicabilityChecker?
    ): ApplicableExtension? {
        val (signature, applicabilityResult) = if (extensionChecker != null) {
            val result = extensionChecker.checkApplicability(callableSymbol) as? KtExtensionApplicabilityResult.Applicable ?: return null
            val signature = callableSymbol.substitute(result.substitutor)

            signature to result
        } else {
            callableSymbol.asSignature() to null
        }

        val insertionOptions = getExtensionOptions(signature, applicabilityResult) ?: return null
        return ApplicableExtension(signature, insertionOptions)
    }

    /**
     * Note, that [isImportDefinitelyNotRequired] should be set to true only if the callable is available without import, and it doesn't
     * require import or fully-qualified name to be resolved unambiguously.
     */
    context(KtAnalysisSession)
    protected fun createCallableWithMetadata(
        signature: KtCallableSignature<*>,
        scopeKind: KtScopeKind,
        isImportDefinitelyNotRequired: Boolean = false,
        options: CallableInsertionOptions = getOptions(signature, isImportDefinitelyNotRequired),
        explicitReceiverTypeHint: KtType? = null,
    ): CallableWithMetadataForCompletion {
        val symbolOrigin = CompletionSymbolOrigin.Scope(scopeKind)
        return CallableWithMetadataForCompletion(signature, explicitReceiverTypeHint, options, symbolOrigin)
    }

    context(KtAnalysisSession)
    private fun createCallableWithMetadata(
        signature: KtCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
        options: CallableInsertionOptions = getOptions(signature),
        explicitReceiverTypeHint: KtType? = null,
    ) = CallableWithMetadataForCompletion(signature, explicitReceiverTypeHint, options, symbolOrigin)

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
            when (val grandParent = parent.parent) {
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
    private fun Sequence<CallableWithMetadataForCompletion>.filterOutShadowedCallables(
        expectedType: KtType?,
    ): Sequence<CallableWithMetadataForCompletion> =
        sequence {
            val shadowedCallablesFilter = ShadowedCallablesFilter()

            for (callableWithMetadata in this@filterOutShadowedCallables) {
                val callableFqName = callableWithMetadata.signature.callableIdIfNonLocal?.asSingleFqName()
                val isAlreadyImported = with(importStrategyDetector) { callableFqName?.isAlreadyImported() == true }
                val typeArgumentsAreRequired = (callableWithMetadata.signature.symbol as? KtFunctionLikeSymbol)?.let {
                    FunctionInsertionHelper.functionCanBeCalledWithoutExplicitTypeArguments(it, expectedType)
                } == false

                val (excludeFromCompletion, updatedOptions) = shadowedCallablesFilter.excludeFromCompletion(
                    callableWithMetadata.signature,
                    callableWithMetadata.options,
                    callableWithMetadata.symbolOrigin,
                    isAlreadyImported,
                    typeArgumentsAreRequired,
                )
                if (excludeFromCompletion) continue

                yield(callableWithMetadata.applyIf(updatedOptions != callableWithMetadata.options) { copy(options = updatedOptions) })
            }
        }

    context(KtAnalysisSession)
    private fun Sequence<CallableWithMetadataForCompletion>.filterIfInsideAnnotationEntryArgument(
        position: PsiElement,
        expectedType: KtType?,
    ): Sequence<CallableWithMetadataForCompletion> {
        if (!position.isInsideAnnotationEntryArgumentList()) return this

        return filter { callableWithMetadata ->
            val symbol = callableWithMetadata.signature.symbol

            if (symbol.hasConstEvaluationAnnotation()) return@filter true

            when (symbol) {
                is KtJavaFieldSymbol -> symbol.isStatic && symbol.isVal && symbol.hasPrimitiveOrStringReturnType()
                is KtKotlinPropertySymbol -> symbol.isConst
                is KtEnumEntrySymbol -> true
                is KtFunctionSymbol -> {
                    val isArrayOfCall = symbol.callableIdIfNonLocal?.asSingleFqName() in ArrayFqNames.ARRAY_CALL_FQ_NAMES

                    isArrayOfCall && expectedType?.let { symbol.returnType.isPossiblySubTypeOf(it) } != false
                }

                else -> false
            }
        }
    }

    context(KtAnalysisSession)
    private fun KtJavaFieldSymbol.hasPrimitiveOrStringReturnType(): Boolean =
        (psi as? PsiField)?.type is PsiPrimitiveType || returnType.isString

    context(KtAnalysisSession)
    private fun KtCallableSymbol.hasConstEvaluationAnnotation(): Boolean =
        annotations.any { it.classId == StandardClassIds.Annotations.IntrinsicConstEvaluation }

    context(KtAnalysisSession)
    protected fun KtNamedClassOrObjectSymbol.staticScope(withCompanionScope: Boolean = true): KtScope = buildList {
        if (withCompanionScope) {
            addIfNotNull(companionObject?.getMemberScope())
        }
        add(getStaticMemberScope())
    }.asCompositeScope()
}

internal class FirCallableReferenceCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCallableCompletionContributor(basicContext, priority) {
    context(KtAnalysisSession)
    override fun getImportStrategy(signature: KtCallableSignature<*>, isImportDefinitelyNotRequired: Boolean): ImportStrategy {
        if (isImportDefinitelyNotRequired) return ImportStrategy.DoNothing

        return signature.callableIdIfNonLocal?.let { ImportStrategy.AddImport(it.asSingleFqName()) } ?: ImportStrategy.DoNothing
    }

    context(KtAnalysisSession)
    override fun getInsertionStrategy(signature: KtCallableSignature<*>): CallableInsertionStrategy =
        CallableInsertionStrategy.AsIdentifier

    context(KtAnalysisSession)
    override fun getInsertionStrategyForExtensionFunction(
        signature: KtCallableSignature<*>,
        applicabilityResult: KtExtensionApplicabilityResult?
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KtExtensionApplicabilityResult.ApplicableAsExtensionCallable -> CallableInsertionStrategy.AsIdentifier
        is KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> null
        else -> null
    }

    context(KtAnalysisSession)
    override fun filter(symbol: KtCallableSymbol, sessionParameters: FirCompletionSessionParameters): Boolean = when {
        // References to elements which are members and extensions at the same time are not allowed
        symbol.isExtension && symbol.symbolKind == KtSymbolKind.CLASS_MEMBER -> false

        // References to variables and parameters are unsupported
        symbol is KtValueParameterSymbol || symbol is KtLocalVariableSymbol || symbol is KtBackingFieldSymbol -> false

        // References to enum entries aren't supported
        symbol is KtEnumEntrySymbol -> false

        else -> true
    }


    context(KtAnalysisSession)
    override fun collectDotCompletion(
        scopeContext: KtScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: ExtensionApplicabilityChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        return when (val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()) {
            is KtPackageSymbol -> emptySequence()
            is KtNamedClassOrObjectSymbol -> sequence {
                val type = symbol.buildSelfClassType()
                val nonExtensionMembers = collectNonExtensionsForType(
                    type,
                    visibilityChecker,
                    scopeNameFilter,
                    sessionParameters,
                ) { filter(it, sessionParameters) }

                val staticScopeKind = KtScopeKind.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)
                val staticMembers = collectNonExtensionsFromScope(
                    symbol.staticScope(),
                    visibilityChecker,
                    scopeNameFilter,
                    sessionParameters,
                ) { filter(it, sessionParameters) }.map { KtCallableSignatureWithContainingScopeKind(it, staticScopeKind) }

                (nonExtensionMembers + staticMembers).forEach {
                    yield(
                        createCallableWithMetadata(
                            it.signature,
                            it.scopeKind,
                            isImportDefinitelyNotRequired = true,
                        )
                    )
                }
            }

            else -> {
                collectDotCompletionForCallableReceiver(
                    scopeContext,
                    explicitReceiver,
                    extensionChecker,
                    visibilityChecker,
                    sessionParameters,
                )
            }
        }
    }
}

internal class FirInfixCallableCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCallableCompletionContributor(basicContext, priority) {
    context(KtAnalysisSession)
    override fun getInsertionStrategy(signature: KtCallableSignature<*>): CallableInsertionStrategy =
        insertionStrategy

    context(KtAnalysisSession)
    override fun getInsertionStrategyForExtensionFunction(
        signature: KtCallableSignature<*>,
        applicabilityResult: KtExtensionApplicabilityResult?
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KtExtensionApplicabilityResult.ApplicableAsExtensionCallable -> getInsertionStrategy(signature)
        is KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> null
        else -> null
    }

    context(KtAnalysisSession)
    override fun filter(symbol: KtCallableSymbol, sessionParameters: FirCompletionSessionParameters): Boolean {
        return symbol is KtFunctionSymbol && symbol.isInfix && super.filter(symbol, sessionParameters)
    }

    companion object {
        private val insertionStrategy = CallableInsertionStrategy.AsIdentifierCustom {
            insertStringAndInvokeCompletion(" ")
        }
    }
}

internal class FirKDocCallableCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCallableCompletionContributor(basicContext, priority) {
    context(KtAnalysisSession)
    override fun getInsertionStrategy(signature: KtCallableSignature<*>): CallableInsertionStrategy =
        CallableInsertionStrategy.AsIdentifier

    context(KtAnalysisSession)
    override fun getInsertionStrategyForExtensionFunction(
        signature: KtCallableSignature<*>,
        applicabilityResult: KtExtensionApplicabilityResult?
    ): CallableInsertionStrategy = CallableInsertionStrategy.AsIdentifier

    context(KtAnalysisSession)
    override fun collectDotCompletion(
        scopeContext: KtScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: ExtensionApplicabilityChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        if (explicitReceiver !is KDocName) return@sequence

        val resolvedSymbols = explicitReceiver.mainReference.resolveToSymbols()
        val scopesWithKinds = resolvedSymbols.flatMap { parentSymbol ->
            when (parentSymbol) {
                is KtPackageSymbol -> {
                    val packageScopeKind = KtScopeKind.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)
                    listOf(KtScopeWithKind(parentSymbol.getPackageScope(), packageScopeKind, token))
                }

                is KtNamedClassOrObjectSymbol -> buildList {
                    val type = parentSymbol.buildSelfClassType()

                    type.getTypeScope()?.getDeclarationScope()?.let { typeScope ->
                        val typeScopeKind = KtScopeKind.TypeScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)
                        add(KtScopeWithKind(typeScope, typeScopeKind, token))
                    }

                    val staticScopeKind = KtScopeKind.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)
                    add(KtScopeWithKind(parentSymbol.staticScope(), staticScopeKind, token))
                }

                else -> emptyList()
            }
        }

        for (scopeWithKind in scopesWithKinds) {
            scopeWithKind.scope.getCallableSymbols(scopeNameFilter)
                .filter { it !is KtSyntheticJavaPropertySymbol }
                .forEach { symbol ->
                    yield(
                        createCallableWithMetadata(
                            symbol.asSignature(),
                            scopeWithKind.kind,
                            isImportDefinitelyNotRequired = true
                        )
                    )
                }
        }
    }
}
