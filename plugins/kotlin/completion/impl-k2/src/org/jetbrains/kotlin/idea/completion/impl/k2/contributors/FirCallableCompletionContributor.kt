// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.util.parents
import com.intellij.util.applyIf
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForExplicitReceiverExpression
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isJavaSourceOrLibrary
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.resolveToExpandedSymbol
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeUsedAsExtension
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.checkers.ApplicableExtension
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.contributors.helpers.*
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionInsertionHelper
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.platform.isMultiPlatform
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.nextSiblingOfSameType
import org.jetbrains.kotlin.resolve.ArrayFqNames

internal open class FirCallableCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int = 0,
    private val withTrailingLambda: Boolean = false, // TODO find a better solution
) : FirCompletionContributorBase<KotlinNameReferencePositionContext>(basicContext, priority) {

    context(KaSession)
    protected open fun getImportStrategy(signature: KaCallableSignature<*>, isImportDefinitelyNotRequired: Boolean): ImportStrategy =
        if (isImportDefinitelyNotRequired) {
            ImportStrategy.DoNothing
        } else {
            importStrategyDetector.detectImportStrategyForCallableSymbol(signature.symbol)
        }

    context(KaSession)
    protected open fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        when (signature) {
            is KaFunctionSignature<*> -> CallableInsertionStrategy.AsCall
            else -> CallableInsertionStrategy.AsIdentifier
        }

    context(KaSession)
    @KaExperimentalApi
    protected open fun getInsertionStrategyForExtensionFunction(
        signature: KaCallableSignature<*>,
        applicabilityResult: KaExtensionApplicabilityResult?
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KaExtensionApplicabilityResult.ApplicableAsExtensionCallable -> getInsertionStrategy(signature)
        is KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> CallableInsertionStrategy.AsCall
        else -> null
    }

    context(KaSession)
    private fun getOptions(
        signature: KaCallableSignature<*>,
        isImportDefinitelyNotRequired: Boolean = false
    ): CallableInsertionOptions = CallableInsertionOptions(
        getImportStrategy(signature, isImportDefinitelyNotRequired),
        getInsertionStrategy(signature)
    )

    context(KaSession)
    @KaExperimentalApi
    private fun getExtensionOptions(
        signature: KaCallableSignature<*>,
        applicability: KaExtensionApplicabilityResult?
    ): CallableInsertionOptions? {
        val insertionStrategy = getInsertionStrategyForExtensionFunction(signature, applicability) ?: return null
        val isFunctionalVariableCall = applicability is KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall
        val importStrategy = importStrategyDetector.detectImportStrategyForCallableSymbol(signature.symbol, isFunctionalVariableCall)
        return CallableInsertionOptions(importStrategy, insertionStrategy)
    }

    context(KaSession)
    protected open fun filter(symbol: KaCallableSymbol): Boolean =
        targetPlatform.isMultiPlatform()
                || !symbol.isExpect

    // todo replace with a sealed hierarchy; too many arguments
    protected data class CallableWithMetadataForCompletion(
        private val _signature: KaCallableSignature<*>,
        private val _explicitReceiverTypeHint: KaType? = null,
        val options: CallableInsertionOptions,
        val symbolOrigin: CompletionSymbolOrigin,
        val lookupString: @NlsSafe String? = null, // todo extract; only used for objects/enums
    ) : KaLifetimeOwner {
        override val token: KaLifetimeToken
            get() = _signature.token
        val signature: KaCallableSignature<*> get() = withValidityAssertion { _signature }
        val explicitReceiverTypeHint: KaType? get() = withValidityAssertion { _explicitReceiverTypeHint }
    }

    context(KaSession)
    override fun complete(
        positionContext: KotlinNameReferencePositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ): Unit = with(positionContext) {
        val visibilityChecker = CompletionVisibilityChecker(basicContext, positionContext)
        val scopesContext = originalKtFile.scopeContext(nameExpression)

        val extensionChecker = if (positionContext is KotlinSimpleNameReferencePositionContext) {
            CachingKtCompletionExtensionCandidateChecker(
                createExtensionCandidateChecker(
                    originalKtFile,
                    positionContext.nameExpression,
                    positionContext.explicitReceiver
                )
            )
        } else null

        val receiver = explicitReceiver

        val callablesWithMetadata: Sequence<CallableWithMetadataForCompletion> = when {
            receiver != null -> collectDotCompletion(scopesContext, receiver, extensionChecker, visibilityChecker, sessionParameters)
            else -> completeWithoutReceiver(scopesContext, extensionChecker, visibilityChecker, sessionParameters)
        }
            .filterIfInsideAnnotationEntryArgument(positionContext.position, weighingContext.expectedType)
            .filterOutShadowedCallables(weighingContext.expectedType)
            .filterNot(isUninitializedCallable(positionContext.position))

        for (callableWithMetadata in callablesWithMetadata) {
            addCallableSymbolToCompletion(
                context = weighingContext,
                signature = callableWithMetadata.signature,
                options = callableWithMetadata.options,
                symbolOrigin = callableWithMetadata.symbolOrigin,
                lookupString = callableWithMetadata.lookupString,
                priority = null,
                explicitReceiverTypeHint = callableWithMetadata.explicitReceiverTypeHint,
                withTrailingLambda = withTrailingLambda,
            )
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun completeWithoutReceiver(
        scopeContext: KaScopeContext,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
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
        ) { filter(it) }
        val extensionsWhichCanBeCalled = collectSuitableExtensions(scopeContext, extensionChecker, visibilityChecker, sessionParameters)
        val availableStaticAndTopLevelNonExtensions = collectStaticAndTopLevelNonExtensionsFromScopeContext(
            scopeContext,
            visibilityChecker,
            scopeNameFilter,
            sessionParameters,
        ) { filter(it) }

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

        if (prefixMatcher.prefix.isNotEmpty()) {
            val members = if (parameters.invocationCount > 1)
                symbolFromIndexProvider.getKotlinCallableSymbolsByNameFilter(scopeNameFilter) { declaration ->
                    !visibilityChecker.isDefinitelyInvisibleByPsi(declaration)
                } + symbolFromIndexProvider.getKotlinEnumEntriesByNameFilter(scopeNameFilter) {
                    !visibilityChecker.isDefinitelyInvisibleByPsi(it)
                } + symbolFromIndexProvider.getJavaFieldsByNameFilter(scopeNameFilter) {
                    it is PsiEnumConstant
                }
            else
                symbolFromIndexProvider.getTopLevelCallableSymbolsByNameFilter(scopeNameFilter) {
                    !visibilityChecker.isDefinitelyInvisibleByPsi(it)
                }

            members.filter { filter(it) }
                .filter { visibilityChecker.isVisible(it) }
                .map { it.asSignature() }
                .map { signature ->
                    val lookupString = signature.callableId?.let { id ->
                        id.className?.let { className ->
                            className.asString() + "." + id.callableName.asString()
                        }
                    }

                    CallableWithMetadataForCompletion(
                        _signature = signature,
                        options = getOptions(signature),
                        symbolOrigin = CompletionSymbolOrigin.Index,
                        lookupString = lookupString,
                    )
                }.forEach { yield(it) }
        }

        collectExtensionsFromIndexAndResolveExtensionScope(
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

    context(KaSession)
    protected open fun collectDotCompletion(
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()
        return when {
            symbol is KaPackageSymbol -> collectDotCompletionForPackageReceiver(symbol, visibilityChecker, sessionParameters)

            else -> sequence {
                if (symbol is KaNamedClassSymbol && symbol.hasImportantStaticMemberScope) {
                    yieldAll(collectDotCompletionFromStaticScope(symbol, withCompanionScope = false, visibilityChecker, sessionParameters))
                }

                if (symbol !is KaNamedClassSymbol || symbol.canBeUsedAsReceiver) {
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

    protected val KaNamedClassSymbol.hasImportantStaticMemberScope: Boolean
        get() = classKind == KaClassKind.ENUM_CLASS ||
                origin.isJavaSourceOrLibrary()

    private val KaNamedClassSymbol.canBeUsedAsReceiver: Boolean
        get() = classKind.isObject || companionObject != null

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun collectDotCompletionForPackageReceiver(
        packageSymbol: KaPackageSymbol,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> {
        val packageScope = packageSymbol.packageScope
        val packageScopeKind = KaScopeKinds.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

        return packageScope
            .callables(scopeNameFilter)
            .filterNot { it.isExtension }
            .filter { visibilityChecker.isVisible(it) }
            .filter { filter(it) }
            .map { callable ->
                val callableSignature = callable.asSignature()
                val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(callableSignature))
                createCallableWithMetadata(callableSignature, packageScopeKind, options = options)
            }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    protected fun collectDotCompletionForCallableReceiver(
        scopeContext: KaScopeContext,
        explicitReceiver: KtExpression,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val receiverType = explicitReceiver.expressionType.takeUnless { it is KaErrorType } ?: return@sequence
        val callablesWithMetadata = collectDotCompletionForCallableReceiver(
            listOf(receiverType),
            visibilityChecker,
            scopeContext,
            extensionChecker,
            sessionParameters,
        )
        yieldAll(callablesWithMetadata)

        val smartCastInfo = explicitReceiver.smartCastInfo
        if (smartCastInfo?.isStable == false) {
            // Collect members available from unstable smartcast as well.
            val callablesWithMetadataFromUnstableSmartCast = collectDotCompletionForCallableReceiver(
                listOf(smartCastInfo.smartCastType),
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

    context(KaSession)
    protected fun collectDotCompletionForCallableReceiver(
        typesOfPossibleReceiver: List<KaType>,
        visibilityChecker: CompletionVisibilityChecker,
        scopeContext: KaScopeContext,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        sessionParameters: FirCompletionSessionParameters,
        explicitReceiverTypeHint: KaType? = null
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val nonExtensionMembers = typesOfPossibleReceiver.flatMap { typeOfPossibleReceiver ->
            collectNonExtensionsForType(
                typeOfPossibleReceiver,
                visibilityChecker,
                scopeNameFilter,
                sessionParameters,
            ) { filter(it) }
        }
        val extensionNonMembers = collectSuitableExtensions(
            scopeContext,
            extensionChecker,
            visibilityChecker,
            sessionParameters,
            typesOfPossibleReceiver,
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

        collectExtensionsFromIndexAndResolveExtensionScope(
            typesOfPossibleReceiver,
            extensionChecker,
            visibilityChecker,
            sessionParameters
        )
            .filter { filter(it.signature.symbol) }
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

    context(KaSession)
    protected fun collectDotCompletionFromStaticScope(
        symbol: KaNamedClassSymbol,
        withCompanionScope: Boolean,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> {
        val staticScope = symbol.staticScope(withCompanionScope)
        val staticScopeKind = KaScopeKinds.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

        val nonExtensions = collectNonExtensionsFromScope(
            staticScope,
            visibilityChecker,
            scopeNameFilter,
            sessionParameters,
        ) { filter(it) }

        return nonExtensions.map { member ->
            val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(member))
            createCallableWithMetadata(member, staticScopeKind, options = options)
        }
    }

    context(KaSession)
    private fun collectExtensionsFromIndexAndResolveExtensionScope(
        receiverTypes: List<KaType>,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Collection<ApplicableExtension> {
        if (receiverTypes.isEmpty()) return emptyList()

        val extensionsFromIndex = symbolFromIndexProvider.getExtensionCallableSymbolsByNameFilter(
            scopeNameFilter,
            receiverTypes,
        ) { !visibilityChecker.isDefinitelyInvisibleByPsi(it) && it.canBeAnalysed() }

        return extensionsFromIndex
            .filter { filter(it) }
            .filter { visibilityChecker.isVisible(it) }
            .mapNotNull { checkApplicabilityAndSubstitute(it, extensionChecker) }
            .let { ShadowedCallablesFilter.sortExtensions(it.toList(), receiverTypes) }
    }

    context(KaSession)
    private fun collectSuitableExtensions(
        scopeContext: KaScopeContext,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
        explicitReceiverTypes: List<KaType>? = null,
    ): Sequence<Pair<KtCallableSignatureWithContainingScopeKind, CallableInsertionOptions>> {
        val receiversTypes = explicitReceiverTypes ?: scopeContext.implicitReceivers.map { it.type }

        return scopeContext.scopes.asSequence().flatMap { scopeWithKind ->
            collectSuitableExtensions(scopeWithKind.scope, receiversTypes, extensionChecker, visibilityChecker, sessionParameters)
                .map { KtCallableSignatureWithContainingScopeKind(it.signature, scopeWithKind.kind) to it.insertionOptions }
        }
    }

    context(KaSession)
    private fun collectSuitableExtensions(
        scope: KaScope,
        receiverTypes: List<KaType>,
        hasSuitableExtensionReceiver: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Collection<ApplicableExtension> =
        scope.callables(scopeNameFilter)
            .filter { it.canBeUsedAsExtension() }
            .filter { visibilityChecker.isVisible(it) }
            .filter { filter(it) }
            .mapNotNull { callable -> checkApplicabilityAndSubstitute(callable, hasSuitableExtensionReceiver) }
            .let { ShadowedCallablesFilter.sortExtensions(it.toList(), receiverTypes) }

    /**
     * If [callableSymbol] is applicable returns substituted signature and insertion options, otherwise, null.
     * When [extensionChecker] is null, no check is carried and applicability result is null.
     */
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun checkApplicabilityAndSubstitute(
        callableSymbol: KaCallableSymbol,
        extensionChecker: KaCompletionExtensionCandidateChecker?
    ): ApplicableExtension? {
        val (signature, applicabilityResult) = if (extensionChecker != null) {
            val result = extensionChecker.computeApplicability(callableSymbol) as? KaExtensionApplicabilityResult.Applicable ?: return null
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
    context(KaSession)
    protected fun createCallableWithMetadata(
        signature: KaCallableSignature<*>,
        scopeKind: KaScopeKind,
        isImportDefinitelyNotRequired: Boolean = false,
        options: CallableInsertionOptions = getOptions(signature, isImportDefinitelyNotRequired),
        explicitReceiverTypeHint: KaType? = null,
    ): CallableWithMetadataForCompletion {
        val symbolOrigin = CompletionSymbolOrigin.Scope(scopeKind)
        return CallableWithMetadataForCompletion(signature, explicitReceiverTypeHint, options, symbolOrigin)
    }

    context(KaSession)
    private fun createCallableWithMetadata(
        signature: KaCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
        options: CallableInsertionOptions = getOptions(signature),
        explicitReceiverTypeHint: KaType? = null,
    ) = CallableWithMetadataForCompletion(signature, explicitReceiverTypeHint, options, symbolOrigin)

    private fun isUninitializedCallable(
        position: PsiElement,
    ): (CallableWithMetadataForCompletion) -> Boolean {
        val uninitializedCallablesForPosition = buildSet<KtCallableDeclaration> {
            for (parent in position.parents(withSelf = false)) {
                when (val grandParent = parent.parent) {
                    is KtParameter -> {
                        if (grandParent.defaultValue == parent) {
                            // Filter out the current parameter and all parameters initialized after the current one.
                            // In the following example:
                            // ```
                            // fun test(a, b: Int = <caret>, c: Int) {}
                            // ```
                            // `a` and `b` should not show up in completion.
                            val originalOrSelf = getOriginalDeclarationOrSelf(
                                declaration = grandParent,
                                originalKtFile = basicContext.originalKtFile,
                            )
                            generateSequence(originalOrSelf) { it.nextSiblingOfSameType() }
                                .forEach(::add)
                        }
                    }

                    is KtProperty -> {
                        if (grandParent.initializer == parent) {
                            val declaration = getOriginalDeclarationOrSelf(
                                declaration = grandParent,
                                originalKtFile = basicContext.originalKtFile,
                            )
                            add(declaration)
                        }
                    }
                }

                if (parent is KtDeclaration) break // we can use variable inside lambda or anonymous object located in its initializer
            }
        }

        return { callable: CallableWithMetadataForCompletion ->
            callable.signature.symbol.psi in uninitializedCallablesForPosition
        }
    }

    context(KaSession)
    private fun Sequence<CallableWithMetadataForCompletion>.filterOutShadowedCallables(
        expectedType: KaType?,
    ): Sequence<CallableWithMetadataForCompletion> =
        sequence {
            val shadowedCallablesFilter = ShadowedCallablesFilter()

            for (callableWithMetadata in this@filterOutShadowedCallables) {
                val callableFqName = callableWithMetadata.signature.callableId?.asSingleFqName()
                val isAlreadyImported = with(importStrategyDetector) { callableFqName?.isAlreadyImported() == true }
                val typeArgumentsAreRequired = (callableWithMetadata.signature.symbol as? KaFunctionSymbol)?.let {
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

    context(KaSession)
    private fun Sequence<CallableWithMetadataForCompletion>.filterIfInsideAnnotationEntryArgument(
        position: PsiElement,
        expectedType: KaType?,
    ): Sequence<CallableWithMetadataForCompletion> {
        if (!position.isInsideAnnotationEntryArgumentList()) return this

        return filter { callableWithMetadata ->
            val symbol = callableWithMetadata.signature.symbol

            if (symbol.hasConstEvaluationAnnotation()) return@filter true

            when (symbol) {
                is KaJavaFieldSymbol -> symbol.isStatic && symbol.isVal && symbol.hasPrimitiveOrStringReturnType()
                is KaKotlinPropertySymbol -> symbol.isConst
                is KaEnumEntrySymbol -> true
                is KaNamedFunctionSymbol -> {
                    val isArrayOfCall = symbol.callableId?.asSingleFqName() in ArrayFqNames.ARRAY_CALL_FQ_NAMES

                    isArrayOfCall && expectedType?.let { symbol.returnType.isPossiblySubTypeOf(it) } != false
                }

                else -> false
            }
        }
    }

    context(KaSession)
    private fun KaJavaFieldSymbol.hasPrimitiveOrStringReturnType(): Boolean =
        (psi as? PsiField)?.type is PsiPrimitiveType || returnType.isStringType

    context(KaSession)
    private fun KaCallableSymbol.hasConstEvaluationAnnotation(): Boolean =
        annotations.any { it.classId == StandardClassIds.Annotations.IntrinsicConstEvaluation }

    context(KaSession)
    protected fun KaNamedClassSymbol.staticScope(withCompanionScope: Boolean = true): KaScope = buildList {
        if (withCompanionScope) {
            addIfNotNull(companionObject?.memberScope)
        }
        add(staticMemberScope)
    }.asCompositeScope()
}

internal class FirCallableReferenceCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCallableCompletionContributor(basicContext, priority) {
    context(KaSession)
    override fun getImportStrategy(signature: KaCallableSignature<*>, isImportDefinitelyNotRequired: Boolean): ImportStrategy {
        if (isImportDefinitelyNotRequired) return ImportStrategy.DoNothing

        return signature.callableId?.let { ImportStrategy.AddImport(it.asSingleFqName()) } ?: ImportStrategy.DoNothing
    }

    context(KaSession)
    override fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        CallableInsertionStrategy.AsIdentifier

    context(KaSession)
    @KaExperimentalApi
    override fun getInsertionStrategyForExtensionFunction(
        signature: KaCallableSignature<*>,
        applicabilityResult: KaExtensionApplicabilityResult?
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KaExtensionApplicabilityResult.ApplicableAsExtensionCallable -> CallableInsertionStrategy.AsIdentifier
        is KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> null
        else -> null
    }

    context(KaSession)
    override fun filter(symbol: KaCallableSymbol): Boolean = when {
        // References to elements which are members and extensions at the same time are not allowed
        symbol.isExtension && symbol.location == KaSymbolLocation.CLASS -> false

        // References to variables and parameters are unsupported
        symbol is KaValueParameterSymbol || symbol is KaLocalVariableSymbol || symbol is KaBackingFieldSymbol -> false

        // References to enum entries aren't supported
        symbol is KaEnumEntrySymbol -> false

        else -> true
    }


    context(KaSession)
    override fun collectDotCompletion(
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        return when (val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()) {
            is KaPackageSymbol -> emptySequence()
            is KaNamedClassSymbol -> sequence {
                if (symbol.hasImportantStaticMemberScope) {
                    yieldAll(collectDotCompletionFromStaticScope(symbol, withCompanionScope = false, visibilityChecker, sessionParameters))
                }
                val types = collectReceiverTypesForExplicitReceiverExpression(explicitReceiver)
                yieldAll(
                    collectDotCompletionForCallableReceiver(
                        types,
                        visibilityChecker,
                        scopeContext,
                        extensionChecker,
                        sessionParameters
                    )
                )
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
    priority: Int = 0,
) : FirCallableCompletionContributor(basicContext, priority) {

    context(KaSession)
    override fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        infixCallableInsertionStrategy

    context(KaSession)
    @KaExperimentalApi
    override fun getInsertionStrategyForExtensionFunction(
        signature: KaCallableSignature<*>,
        applicabilityResult: KaExtensionApplicabilityResult?
    ): CallableInsertionStrategy? = when (applicabilityResult) {
        is KaExtensionApplicabilityResult.ApplicableAsExtensionCallable -> getInsertionStrategy(signature)
        is KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> null
        else -> null
    }

    context(KaSession)
    override fun filter(symbol: KaCallableSymbol): Boolean =
        symbol is KaNamedFunctionSymbol
                && symbol.isInfix
                && super.filter(symbol)

    companion object {
        private val infixCallableInsertionStrategy = CallableInsertionStrategy.AsIdentifierCustom {
            if (completionChar == ' ') {
                setAddCompletionChar(false)
            }

            insertStringAndInvokeCompletion(" ")
        }
    }
}

internal class FirKDocCallableCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int = 0,
) : FirCallableCompletionContributor(basicContext, priority) {

    context(KaSession)
    override fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        CallableInsertionStrategy.AsIdentifier

    context(KaSession)
    @KaExperimentalApi
    override fun getInsertionStrategyForExtensionFunction(
        signature: KaCallableSignature<*>,
        applicabilityResult: KaExtensionApplicabilityResult?
    ): CallableInsertionStrategy = CallableInsertionStrategy.AsIdentifier

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun collectDotCompletion(
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        if (explicitReceiver !is KDocName) return@sequence

        val resolvedSymbols = explicitReceiver.mainReference.resolveToSymbols()
        val scopesWithKinds = resolvedSymbols.flatMap { parentSymbol ->
            when (parentSymbol) {
                is KaPackageSymbol -> {
                    val packageScopeKind = KaScopeKinds.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)
                    listOf(KaScopeWithKindImpl(parentSymbol.packageScope, packageScopeKind))
                }

                is KaNamedClassSymbol -> buildList {
                    val type = parentSymbol.defaultType

                    type.scope?.declarationScope?.let { typeScope ->
                        val typeScopeKind = KaScopeKinds.TypeScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)
                        add(KaScopeWithKindImpl(typeScope, typeScopeKind))
                    }

                    val staticScopeKind = KaScopeKinds.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)
                    add(KaScopeWithKindImpl(parentSymbol.staticScope(), staticScopeKind))
                }

                else -> emptyList()
            }
        }

        for (scopeWithKind in scopesWithKinds) {
            scopeWithKind.scope.callables(scopeNameFilter)
                .filter { it !is KaSyntheticJavaPropertySymbol }
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

private class CachingKtCompletionExtensionCandidateChecker(
    private val delegate: KaCompletionExtensionCandidateChecker
) : KaCompletionExtensionCandidateChecker {
    /**
     * Cached applicability results for callable extension symbols.
     * The cache **must not outlive the lifetime of a single completion session**.
     *
     * If an extension is applicable but some of its type parameters are substituted to error types, then multiple calls to
     * [computeApplicability] produce unequal substitutors, and subsequently unequal signatures, because
     * error types are considered equal only if their underlying types are referentially equal, so we need to use [cache] in order
     * to avoid unexpected unequal signatures.
     *
     * The cache also helps to avoid recalculation of applicability for extensions which are suggested twice:
     * the first time while processing the scope context and the second time while processing callables from indexes.
     */
    @OptIn(KaExperimentalApi::class)
    private val cache: MutableMap<KaCallableSymbol, KaExtensionApplicabilityResult> = mutableMapOf()

    @KaExperimentalApi
    override fun computeApplicability(candidate: KaCallableSymbol): KaExtensionApplicabilityResult {
        return cache.computeIfAbsent(candidate) {
            delegate.computeApplicability(candidate)
        }
    }
}