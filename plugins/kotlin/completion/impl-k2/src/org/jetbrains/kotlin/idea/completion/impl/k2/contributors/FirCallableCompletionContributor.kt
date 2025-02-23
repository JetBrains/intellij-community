// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.util.parents
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
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForExplicitReceiverExpression
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isJavaSourceOrLibrary
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.resolveToExpandedSymbol
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeUsedAsExtension
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.*
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.checkers.ApplicableExtension
import org.jetbrains.kotlin.idea.completion.impl.k2.checkers.KtCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionInsertionHelper
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.CallableWeigher.callableWeight
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
import org.jetbrains.kotlin.types.Variance

internal open class FirCallableCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
    private val withTrailingLambda: Boolean = false, // TODO find a better solution
) : FirCompletionContributorBase<KotlinNameReferencePositionContext>(parameters, sink, priority) {

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
    protected open fun getInsertionStrategyForFunctionalVariables(
        applicabilityResult: KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall,
    ): CallableInsertionStrategy? = CallableInsertionStrategy.AsCall

    context(KaSession)
    private fun getOptions(
        signature: KaCallableSignature<*>,
        isImportDefinitelyNotRequired: Boolean = false
    ): CallableInsertionOptions = CallableInsertionOptions(
        getImportStrategy(signature, isImportDefinitelyNotRequired),
        getInsertionStrategy(signature)
    )

    context(KaSession)
    protected open fun filter(symbol: KaCallableSymbol): Boolean =
        targetPlatform.isMultiPlatform()
                || !symbol.isExpect

    // todo replace with a sealed hierarchy; too many arguments
    protected data class CallableWithMetadataForCompletion(
        private val _signature: KaCallableSignature<*>,
        val options: CallableInsertionOptions,
        val symbolOrigin: CompletionSymbolOrigin,
        val itemText: @NlsSafe String? = null, // todo extract; only used for objects/enums
        private val _explicitReceiverTypeHint: KaType? = null, // todo extract; only used for smart casts
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
    ) {
        val scopesContext = originalKtFile.scopeContext(positionContext.nameExpression)

        val extensionChecker = (positionContext as? KotlinSimpleNameReferencePositionContext)?.let {
            KtCompletionExtensionCandidateChecker.create(
                originalFile = originalKtFile,
                nameExpression = it.nameExpression,
                explicitReceiver = it.explicitReceiver
            )
        }

        val receiver = positionContext.explicitReceiver
        val expectedType = weighingContext.expectedType
        when (receiver) {
            null -> completeWithoutReceiver(positionContext, scopesContext, expectedType, extensionChecker)

            else -> collectDotCompletion(positionContext, scopesContext, receiver, extensionChecker)
        }.filterIfInsideAnnotationEntryArgument(positionContext.position, expectedType)
            .mapNotNull(shadowIfNecessary(expectedType))
            .filterNot(isUninitializedCallable(positionContext.position))
            .flatMap { callableWithMetadata ->
                createCallableLookupElements(
                    context = weighingContext,
                    signature = callableWithMetadata.signature,
                    options = callableWithMetadata.options,
                    symbolOrigin = callableWithMetadata.symbolOrigin,
                    withTrailingLambda = withTrailingLambda,
                ).map { builder ->
                    val itemText = callableWithMetadata.itemText
                        ?: return@map builder

                    builder.withPresentableText(itemText)
                }.map { builder ->
                    receiver ?: return@map builder

                    if (builder.callableWeight?.kind != CallableMetadataProvider.CallableKind.RECEIVER_CAST_REQUIRED)
                        return@map builder

                    val explicitReceiverTypeHint = callableWithMetadata.explicitReceiverTypeHint
                        ?: return@map builder

                    builder.adaptToExplicitReceiver(
                        receiver = receiver,
                        typeText = @OptIn(KaExperimentalApi::class) explicitReceiverTypeHint.render(position = Variance.INVARIANT),
                    )
                }
            }.forEach(sink::addElement)

        logger<FirCallableCompletionContributor>().debug("Suspicious calculations took ${sink.duration}")
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun completeWithoutReceiver(
        positionContext: KotlinNameReferencePositionContext,
        scopeContext: KaScopeContext,
        expectedType: KaType?,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val availableLocalAndMemberNonExtensions = collectLocalAndMemberNonExtensionsFromScopeContext(
            parameters = parameters,
            positionContext = positionContext,
            scopeContext = scopeContext,
            visibilityChecker = visibilityChecker,
            scopeNameFilter = scopeNameFilter,
        ) { filter(it) }
        val extensionsWhichCanBeCalled = collectSuitableExtensions(
            positionContext = positionContext,
            scopeContext = scopeContext,
            extensionChecker = extensionChecker,
        )
        val availableStaticAndTopLevelNonExtensions = collectStaticAndTopLevelNonExtensionsFromScopeContext(
            parameters = parameters,
            positionContext = positionContext,
            scopeContext = scopeContext,
            visibilityChecker = visibilityChecker,
            scopeNameFilter = scopeNameFilter,
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

        val members = sequence<KaCallableSymbol> {
            val prefix = prefixMatcher.prefix
            val invocationCount = parameters.invocationCount

            val expectedType = expectedType?.takeUnless { it is KaErrorType }
                ?.withNullability(KaTypeNullability.NON_NULLABLE)
                ?.takeIf { it.isEnum() }

            yieldAll(sequence {
                val psiFilter: (KtEnumEntry) -> Boolean = if (invocationCount > 2) { _ -> true }
                else if (invocationCount > 1 && prefix.isNotEmpty()) visibilityChecker::canBeVisible
                else if (expectedType != null) { enumEntry ->
                    visibilityChecker.canBeVisible(enumEntry)
                            && runCatchingNSEE { enumEntry.returnType }
                        ?.withNullability(KaTypeNullability.NON_NULLABLE)
                        ?.semanticallyEquals(expectedType) == true
                }
                else return@sequence

                val enumEntries = symbolFromIndexProvider.getKotlinEnumEntriesByNameFilter(
                    nameFilter = scopeNameFilter,
                    psiFilter = psiFilter,
                )
                yieldAll(enumEntries)
            })

            yieldAll(sequence {
                // todo KtCodeFragments
                if (invocationCount <= 2
                    && (invocationCount <= 1 || prefix.isEmpty())
                    && expectedType == null
                ) return@sequence

                val enumConstants = symbolFromIndexProvider.getJavaFieldsByNameFilter(scopeNameFilter) {
                    it is PsiEnumConstant
                }.filterIsInstance<KaEnumEntrySymbol>()
                    .filter { enumEntrySymbol ->
                        expectedType == null || runCatchingNSEE { enumEntrySymbol.returnType }
                            ?.withNullability(KaTypeNullability.NON_NULLABLE)
                            ?.semanticallyEquals(expectedType) == true
                    }
                yieldAll(enumConstants)
            })

            if (prefix.isEmpty()) return@sequence

            val callables = if (invocationCount > 1) {
                symbolFromIndexProvider.getKotlinCallableSymbolsByNameFilter(scopeNameFilter) {
                    visibilityChecker.canBeVisible(it)
                }
            } else {
                symbolFromIndexProvider.getTopLevelCallableSymbolsByNameFilter(scopeNameFilter) {
                    visibilityChecker.canBeVisible(it)
                }
            }
            yieldAll(callables)
        }

        val memberDescriptors = members.filter { filter(it) }
            .filter { runCatchingNSEE { visibilityChecker.isVisible(it, positionContext) } == true }
            .map { it.asSignature() }
            .map { signature ->
                val itemText = signature.callableId?.let { id ->
                    id.className?.let { className ->
                        className.asString() + "." + id.callableName.asString()
                    }
                }

                CallableWithMetadataForCompletion(
                    _signature = signature,
                    options = getOptions(signature),
                    symbolOrigin = CompletionSymbolOrigin.Index,
                    itemText = itemText, // todo should be only for enums
                )
            }
        yieldAll(memberDescriptors)

        val extensionDescriptors = collectExtensionsFromIndexAndResolveExtensionScope(
            positionContext = positionContext,
            receiverTypes = scopeContext.implicitReceivers.map { it.type },
            extensionChecker = extensionChecker,
        )
        yieldAll(extensionDescriptors)
    }

    context(KaSession)
    protected open fun collectDotCompletion(
        positionContext: KotlinNameReferencePositionContext,
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        return when (val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()) {
            is KaPackageSymbol -> collectDotCompletionForPackageReceiver(positionContext, symbol)

            else -> sequence {
                if (symbol is KaNamedClassSymbol && symbol.hasImportantStaticMemberScope) {
                    yieldAll(
                        collectDotCompletionFromStaticScope(
                            positionContext = positionContext,
                            symbol = symbol,
                            withCompanionScope = false,
                        )
                    )
                }

                if (symbol !is KaNamedClassSymbol || symbol.canBeUsedAsReceiver) {
                    yieldAll(
                        collectDotCompletionForCallableReceiver(
                            positionContext = positionContext,
                            scopeContext = scopeContext,
                            explicitReceiver = explicitReceiver,
                            extensionChecker = extensionChecker,
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
        positionContext: KotlinNameReferencePositionContext,
        packageSymbol: KaPackageSymbol,
    ): Sequence<CallableWithMetadataForCompletion> {
        val packageScope = packageSymbol.packageScope
        val packageScopeKind = KaScopeKinds.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

        return packageScope
            .callables(scopeNameFilter)
            .filterNot { it.isExtension }
            .filter { visibilityChecker.isVisible(it, positionContext) }
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
        positionContext: KotlinNameReferencePositionContext,
        scopeContext: KaScopeContext,
        explicitReceiver: KtExpression,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val receiverType = explicitReceiver.expressionType ?: return@sequence
        val callablesWithMetadata = collectDotCompletionForCallableReceiver(
            positionContext = positionContext,
            typesOfPossibleReceiver = listOf(receiverType),
            scopeContext = scopeContext,
            extensionChecker = extensionChecker,
        )
        yieldAll(callablesWithMetadata)

        val smartCastInfo = explicitReceiver.smartCastInfo
            ?: return@sequence
        if (smartCastInfo.isStable) return@sequence
        val smartCastType = smartCastInfo.smartCastType
        val explicitReceiverTypeHint = smartCastType.takeIf { it.approximateToSuperPublicDenotable(true) == null }

        // Collect members available from unstable smartcast as well.
        val callablesWithMetadataFromUnstableSmartCast = collectDotCompletionForCallableReceiver(
            positionContext = positionContext,
            typesOfPossibleReceiver = listOf(smartCastType),
            scopeContext = scopeContext,
            extensionChecker = extensionChecker,
        ).map {
            if (explicitReceiverTypeHint != null) {
                // Only offer the hint if the type is denotable.
                it.copy(_explicitReceiverTypeHint = explicitReceiverTypeHint)
            } else {
                it
            }
        }
        yieldAll(callablesWithMetadataFromUnstableSmartCast)
    }

    context(KaSession)
    protected fun collectDotCompletionForCallableReceiver(
        positionContext: KotlinNameReferencePositionContext,
        typesOfPossibleReceiver: List<KaType>,
        scopeContext: KaScopeContext,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val nonExtensionMembers = typesOfPossibleReceiver.flatMap { typeOfPossibleReceiver ->
            collectNonExtensionsForType(
                parameters = parameters,
                positionContext = positionContext,
                receiverType = typeOfPossibleReceiver,
                visibilityChecker = visibilityChecker,
                scopeNameFilter = scopeNameFilter,
            ) { filter(it) }
        }
        val extensionNonMembers = collectSuitableExtensions(
            positionContext = positionContext,
            scopeContext = scopeContext,
            extensionChecker = extensionChecker,
            explicitReceiverTypes = typesOfPossibleReceiver,
        )

        nonExtensionMembers.forEach { signatureWithScopeKind ->
            val callableWithMetadata = createCallableWithMetadata(
                signatureWithScopeKind.signature,
                signatureWithScopeKind.scopeKind,
                isImportDefinitelyNotRequired = true,
            )
            yield(callableWithMetadata)
        }

        extensionNonMembers.forEach { (signatureWithScopeKind, insertionOptions) ->
            val signature = signatureWithScopeKind.signature
            val scopeKind = signatureWithScopeKind.scopeKind
            yield(
                createCallableWithMetadata(
                    signature = signature,
                    scopeKind = scopeKind,
                    isImportDefinitelyNotRequired = false,
                    options = insertionOptions,
                )
            )
        }

        val extensionDescriptors = collectExtensionsFromIndexAndResolveExtensionScope(
            positionContext = positionContext,
            receiverTypes = typesOfPossibleReceiver,
            extensionChecker = extensionChecker,
        ).filter { filter(it.signature.symbol) }
        yieldAll(extensionDescriptors)
    }

    context(KaSession)
    protected fun collectDotCompletionFromStaticScope(
        positionContext: KotlinNameReferencePositionContext,
        symbol: KaNamedClassSymbol,
        withCompanionScope: Boolean,
    ): Sequence<CallableWithMetadataForCompletion> {
        val staticScope = symbol.staticScope(withCompanionScope)
        val staticScopeKind = KaScopeKinds.StaticMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX)

        val nonExtensions = collectNonExtensionsFromScope(
            parameters = parameters,
            positionContext = positionContext,
            scope = staticScope,
            visibilityChecker = visibilityChecker,
            scopeNameFilter = scopeNameFilter,
        ) { filter(it) }

        return nonExtensions.map { member ->
            val options = CallableInsertionOptions(ImportStrategy.DoNothing, getInsertionStrategy(member))
            createCallableWithMetadata(member, staticScopeKind, options = options)
        }
    }

    context(KaSession)
    private fun collectExtensionsFromIndexAndResolveExtensionScope(
        positionContext: KotlinNameReferencePositionContext,
        receiverTypes: List<KaType>,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
    ): Collection<CallableWithMetadataForCompletion> {
        if (receiverTypes.isEmpty()) return emptyList()

        val extensionsFromIndex = symbolFromIndexProvider.getExtensionCallableSymbolsByNameFilter(
            scopeNameFilter,
            receiverTypes,
        ) { visibilityChecker.canBeVisible(it) && it.canBeAnalysed() }

        return extensionsFromIndex
            .filter { filter(it) }
            .filter { visibilityChecker.isVisible(it, positionContext) }
            .mapNotNull { checkApplicabilityAndSubstitute(it, extensionChecker) }
            .let {
                ShadowedCallablesFilter.sortExtensions(it.toList(), receiverTypes)
            }.map { applicableExtension ->
                CallableWithMetadataForCompletion(
                    _signature = applicableExtension.signature,
                    options = applicableExtension.insertionOptions,
                    symbolOrigin = CompletionSymbolOrigin.Index,
                )
            }
    }

    context(KaSession)
    private fun collectSuitableExtensions(
        positionContext: KotlinNameReferencePositionContext,
        scopeContext: KaScopeContext,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        explicitReceiverTypes: List<KaType>? = null,
    ): Sequence<Pair<KtCallableSignatureWithContainingScopeKind, CallableInsertionOptions>> {
        val receiverTypes = (explicitReceiverTypes
            ?: scopeContext.implicitReceivers.map { it.type })
            .filterNot { it is KaErrorType }
        if (receiverTypes.isEmpty()) return emptySequence()

        return scopeContext.scopes.asSequence().flatMap { scopeWithKind ->
            val suitableExtensions = collectSuitableExtensions(
                positionContext = positionContext,
                scope = scopeWithKind.scope,
                hasSuitableExtensionReceiver = extensionChecker,
            ).toList()
            ShadowedCallablesFilter.sortExtensions(suitableExtensions, receiverTypes)
                .map { KtCallableSignatureWithContainingScopeKind(it.signature, scopeWithKind.kind) to it.insertionOptions }
        }
    }

    context(KaSession)
    private fun collectSuitableExtensions(
        positionContext: KotlinNameReferencePositionContext,
        scope: KaScope,
        hasSuitableExtensionReceiver: KaCompletionExtensionCandidateChecker?,
    ): Sequence<ApplicableExtension> =
        scope.callables(scopeNameFilter)
            .filter { it.canBeUsedAsExtension() }
            .filter { visibilityChecker.isVisible(it, positionContext) }
            .filter { filter(it) }
            .mapNotNull { callable -> checkApplicabilityAndSubstitute(callable, hasSuitableExtensionReceiver) }

    context(KaSession)
    protected fun createApplicableExtension(
        signature: KaCallableSignature<*>,
        importingStrategy: ImportStrategy = importStrategyDetector.detectImportStrategyForCallableSymbol(symbol = signature.symbol),
        insertionStrategy: CallableInsertionStrategy = getInsertionStrategy(signature),
    ): ApplicableExtension = ApplicableExtension(
        _signature = signature,
        insertionOptions = CallableInsertionOptions(importingStrategy, insertionStrategy),
    )

    /**
     * If [candidate] is applicable returns substituted signature and insertion options, otherwise, null.
     * When [extensionChecker] is null, no check is carried and applicability result is null.
     */
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    protected open fun checkApplicabilityAndSubstitute(
        candidate: KaCallableSymbol,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
    ): ApplicableExtension? {
        val applicabilityResult = extensionChecker?.computeApplicability(candidate) as? KaExtensionApplicabilityResult.Applicable
            ?: return null

        val substitutor = applicabilityResult.substitutor
        return when (applicabilityResult) {
            is KaExtensionApplicabilityResult.ApplicableAsExtensionCallable ->
                createApplicableExtension(signature = candidate.substitute(substitutor))

            is KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> {
                val insertionStrategy = getInsertionStrategyForFunctionalVariables(applicabilityResult)
                    ?: return null

                createApplicableExtension(
                    signature = candidate.substitute(substitutor), // do not run before null check
                    importingStrategy = importStrategyDetector.detectImportStrategyForCallableSymbol(
                        symbol = candidate,
                        isFunctionalVariableCall = true,
                    ),
                    insertionStrategy = insertionStrategy,
                )
            }
        }
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
    ): CallableWithMetadataForCompletion = CallableWithMetadataForCompletion(
        _signature = signature,
        options = options,
        symbolOrigin = CompletionSymbolOrigin.Scope(scopeKind),
    )

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
                                originalKtFile = originalKtFile,
                            )
                            generateSequence(originalOrSelf) { it.nextSiblingOfSameType() }
                                .forEach(::add)
                        }
                    }

                    is KtProperty -> {
                        if (grandParent.initializer == parent) {
                            val declaration = getOriginalDeclarationOrSelf(
                                declaration = grandParent,
                                originalKtFile = originalKtFile,
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
    private fun shadowIfNecessary(
        expectedType: KaType?,
    ) = object : Function1<CallableWithMetadataForCompletion, CallableWithMetadataForCompletion?> {

        private val shadowedCallablesFilter = ShadowedCallablesFilter()

        override fun invoke(callableWithMetadata: CallableWithMetadataForCompletion): CallableWithMetadataForCompletion? {
            val insertionOptions = callableWithMetadata.options
            val (excludeFromCompletion, newImportStrategy) = shadowedCallablesFilter.excludeFromCompletion(
                callableSignature = callableWithMetadata.signature,
                options = insertionOptions,
                symbolOrigin = callableWithMetadata.symbolOrigin,
                importStrategyDetector = importStrategyDetector,
            ) {
                !FunctionInsertionHelper.functionCanBeCalledWithoutExplicitTypeArguments(it, expectedType)
            }

            return if (excludeFromCompletion) null
            else if (newImportStrategy == null) callableWithMetadata
            else callableWithMetadata.copy(options = insertionOptions.copy(importingStrategy = newImportStrategy))
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
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int,
) : FirCallableCompletionContributor(parameters, sink, priority) {

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
    override fun getInsertionStrategyForFunctionalVariables(
        applicabilityResult: KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall,
    ): CallableInsertionStrategy? = null

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
        positionContext: KotlinNameReferencePositionContext,
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        return when (val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()) {
            is KaPackageSymbol -> emptySequence()
            is KaNamedClassSymbol -> sequence {
                if (symbol.hasImportantStaticMemberScope) {
                    yieldAll(
                        collectDotCompletionFromStaticScope(
                            positionContext = positionContext,
                            symbol = symbol,
                            withCompanionScope = false,
                        )
                    )
                }
                val types = collectReceiverTypesForExplicitReceiverExpression(explicitReceiver)
                yieldAll(
                    collectDotCompletionForCallableReceiver(
                        positionContext = positionContext,
                        typesOfPossibleReceiver = types,
                        scopeContext = scopeContext,
                        extensionChecker = extensionChecker,
                    )
                )
            }

            else -> collectDotCompletionForCallableReceiver(
                positionContext = positionContext,
                scopeContext = scopeContext,
                explicitReceiver = explicitReceiver,
                extensionChecker = extensionChecker,
            )
        }
    }
}

internal class FirInfixCallableCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCallableCompletionContributor(parameters, sink, priority) {

    context(KaSession)
    override fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        infixCallableInsertionStrategy

    context(KaSession)
    @KaExperimentalApi
    override fun getInsertionStrategyForFunctionalVariables(
        applicabilityResult: KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall,
    ): CallableInsertionStrategy? = null

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
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCallableCompletionContributor(parameters, sink, priority) {

    context(KaSession)
    override fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        CallableInsertionStrategy.AsIdentifier

    /**
     * Is not used directly, @see [checkApplicabilityAndSubstitute].
     */
    context(KaSession)
    @KaExperimentalApi
    override fun getInsertionStrategyForFunctionalVariables(
        applicabilityResult: KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall,
    ): CallableInsertionStrategy = throw RuntimeException("Should not be used directly")

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun checkApplicabilityAndSubstitute(
        candidate: KaCallableSymbol,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
    ): ApplicableExtension? = createApplicableExtension(signature = candidate.asSignature())

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun collectDotCompletion(
        positionContext: KotlinNameReferencePositionContext,
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
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

context(KaSession)
private fun <T> runCatchingNSEE(
    action: () -> T,
): T? = try {
    action()
} catch (e: NoSuchElementException) {
    logger<FirCallableCompletionContributor>().debug("Temporal wrapping for KT-72988", e)
    null
}
