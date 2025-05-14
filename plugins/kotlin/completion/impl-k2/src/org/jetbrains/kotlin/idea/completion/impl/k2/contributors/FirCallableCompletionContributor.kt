// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parents
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.containers.sequenceOfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.*
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
import org.jetbrains.kotlin.idea.core.NotPropertiesService
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.nextSiblingOfSameType
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.exceptions.KotlinIllegalArgumentExceptionWithAttachments

private val NOT_PROPERTIES = NotPropertiesService.DEFAULT.toSet()

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
    protected open fun filter(symbol: KaCallableSymbol): Boolean = !symbol.isIgnoredExpectDeclaration()

    // todo replace with a sealed hierarchy; too many arguments
    protected data class CallableWithMetadataForCompletion(
        private val _signature: KaCallableSignature<*>,
        val options: CallableInsertionOptions,
        val scopeKind: KaScopeKind? = null, // index
        val showReceiver: Boolean = false, // todo extract; only used for objects/enums/static members
        private val _explicitReceiverTypeHint: KaType? = null, // todo extract; only used for smart casts
    ) : KaLifetimeOwner {
        override val token: KaLifetimeToken
            get() = _signature.token

        val signature: KaCallableSignature<*> get() = withValidityAssertion { _signature }

        val explicitReceiverTypeHint: KaType? get() = withValidityAssertion { _explicitReceiverTypeHint }

        val itemText: @NlsSafe String?
            get() {
                val callableId = signature.takeIf { showReceiver }
                    ?.callableId
                    ?: return null

                val className = callableId.className
                    ?: return null

                return "$className.${callableId.callableName}"
            }
    }

    context(KaSession)
    override fun complete(
        positionContext: KotlinNameReferencePositionContext,
        weighingContext: WeighingContext,
    ) {
        val scopeContext = weighingContext.scopeContext

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
            null -> completeWithoutReceiver(positionContext, scopeContext, expectedType, extensionChecker)

            else -> collectDotCompletion(positionContext, scopeContext, receiver, extensionChecker)
        }.filterIfInsideAnnotationEntryArgument(positionContext.position, expectedType)
            .mapNotNull(shadowIfNecessary(expectedType))
            .filterNot(isUninitializedCallable(positionContext.position))
            .flatMap { callableWithMetadata ->
                createCallableLookupElements(
                    context = weighingContext,
                    signature = callableWithMetadata.signature,
                    options = callableWithMetadata.options,
                    scopeKind = callableWithMetadata.scopeKind,
                    presentableText = callableWithMetadata.itemText,
                    withTrailingLambda = withTrailingLambda,
                ).map { builder ->
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
    private fun completeExpectedEnumEntries(
        expectedType: KaType?,
    ): Sequence<KaCallableSymbol> {
        val expectedEnumType = expectedType?.takeUnless { it is KaErrorType }
            ?.withNullability(KaTypeNullability.NON_NULLABLE)
            ?.takeIf { it.isEnum() }

        val enumClass = expectedEnumType?.symbol?.psi
        return when (enumClass) {
            is KtClassOrObject -> {
                enumClass.body?.enumEntries?.asSequence()?.map { it.symbol } ?: emptySequence()
            }

            is PsiClass -> {
                enumClass.childrenOfType<PsiEnumConstant>().asSequence().mapNotNull { it.callableSymbol }
            }

            else -> emptySequence()
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createAndFilterMetadataForMemberCallables(
        callables: Sequence<KaCallableSymbol>,
        positionContext: KotlinNameReferencePositionContext,
    ): Sequence<CallableWithMetadataForCompletion> = callables.filter { filter(it) }
        .filter { runCatchingKnownIssues { visibilityChecker.isVisible(it, positionContext) } == true }
        .map { it.asSignature() }
        .map { signature ->
            CallableWithMetadataForCompletion(
                _signature = signature,
                options = getOptions(signature),
                showReceiver = true,
            )
        }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun completeWithoutReceiver(
        positionContext: KotlinNameReferencePositionContext,
        scopeContext: KaScopeContext,
        expectedType: KaType?,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        // If the expected type is an enum, we want to yield the enum entries very early on so they will
        // definitely be available before the elements are frozen.
        yieldAll(createAndFilterMetadataForMemberCallables(completeExpectedEnumEntries(expectedType), positionContext))

        val availableLocalAndMemberNonExtensions = collectLocalAndMemberNonExtensionsFromScopeContext(
            parameters = parameters,
            positionContext = positionContext,
            scopeContext = scopeContext,
            visibilityChecker = visibilityChecker,
            scopeNameFilter = scopeNameFilter,
        ) { filter(it) }
            .map { signatureWithKind ->
                signatureWithKind.signature
                    .createCallableWithMetadata(signatureWithKind.scopeKind)
            }

        val extensionsWhichCanBeCalled = collectSuitableExtensions(
            positionContext = positionContext,
            scopeContext = scopeContext,
            extensionChecker = extensionChecker,
        )
        val availableStaticAndTopLevelNonExtensions = scopeContext.scopes
            .asSequence()
            .filterNot { it.kind is KaScopeKind.LocalScope || it.kind is KaScopeKind.TypeScope }
            .flatMap { scopeWithKind ->
                collectNonExtensionsFromScope(
                    parameters = parameters,
                    positionContext = positionContext,
                    scope = scopeWithKind.scope,
                    visibilityChecker = visibilityChecker,
                    scopeNameFilter = scopeNameFilter,
                    symbolFilter = { filter(it) },
                ).map {
                    it.createCallableWithMetadata(scopeWithKind.kind)
                }
            }

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
        yieldAll(availableLocalAndMemberNonExtensions)
        yieldAll(extensionsWhichCanBeCalled)
        yieldAll(availableStaticAndTopLevelNonExtensions)

        val members = sequence {
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
                            && runCatchingKnownIssues { enumEntry.returnType }
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
                        expectedType == null || runCatchingKnownIssues { enumEntrySymbol.returnType }
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

        yieldAll(createAndFilterMetadataForMemberCallables(members, positionContext))

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
        showReceiver: Boolean = false,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        return when (val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()) {
            is KaPackageSymbol -> collectDotCompletionForPackageReceiver(positionContext, symbol)

            else -> sequence {
                if (symbol is KaNamedClassSymbol) {
                    yieldAll(collectDotCompletionFromStaticScope(positionContext, symbol, showReceiver))
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

                if (showReceiver) return@sequence
                runChainCompletion(positionContext, explicitReceiver) { receiverExpression,
                                                                        positionContext,
                                                                        importingStrategy ->
                    val weighingContext = WeighingContext.create(parameters, positionContext)

                    collectDotCompletion(
                        positionContext = positionContext,
                        scopeContext = weighingContext.scopeContext,
                        explicitReceiver = receiverExpression,
                        extensionChecker = null,
                        showReceiver = true,
                    ).flatMap { callableWithMetadata ->
                        val signature = callableWithMetadata.signature

                        createCallableLookupElements(
                            context = weighingContext,
                            signature = signature,
                            options = callableWithMetadata.options.copy(importingStrategy),
                            scopeKind = callableWithMetadata.scopeKind,
                            presentableText = callableWithMetadata.itemText,
                            withTrailingLambda = true,
                        )
                    }
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
    private fun collectDotCompletionForPackageReceiver(
        positionContext: KotlinNameReferencePositionContext,
        packageSymbol: KaPackageSymbol,
    ): Sequence<CallableWithMetadataForCompletion> = packageSymbol.packageScope
        .callables(scopeNameFilter)
        .filterNot { it.isExtension }
        .filter { visibilityChecker.isVisible(it, positionContext) }
        .filter { filter(it) }
        .map { @OptIn(KaExperimentalApi::class) (it.asSignature()) }
        .map { signature ->
            CallableWithMetadataForCompletion(
                _signature = signature,
                options = CallableInsertionOptions(
                    importingStrategy = ImportStrategy.DoNothing,
                    insertionStrategy = getInsertionStrategy(signature),
                ),
                scopeKind = KtOutsideTowerScopeKinds.PackageMemberScope,
            )
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
                symbolFilter = { filter(it) },
            ).map {
                it.createCallableWithMetadata(
                    scopeKind = KtOutsideTowerScopeKinds.TypeScope,
                    isImportDefinitelyNotRequired = true,
                )
            }
        }
        val extensionNonMembers = collectSuitableExtensions(
            positionContext = positionContext,
            scopeContext = scopeContext,
            extensionChecker = extensionChecker,
            explicitReceiverTypes = typesOfPossibleReceiver,
        )

        yieldAll(nonExtensionMembers)
        yieldAll(extensionNonMembers)

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
        showReceiver: Boolean,
    ): Sequence<CallableWithMetadataForCompletion> {
        if (!symbol.hasImportantStaticMemberScope) return emptySequence()

        val nonExtensions = collectNonExtensionsFromScope(
            parameters = parameters,
            positionContext = positionContext,
            scope = symbol.staticScope(withCompanionScope = false),
            visibilityChecker = visibilityChecker,
            scopeNameFilter = scopeNameFilter,
        ) { filter(it) }

        return nonExtensions.map { signature ->
            CallableWithMetadataForCompletion(
                _signature = signature,
                options = CallableInsertionOptions(
                    importingStrategy = ImportStrategy.DoNothing,
                    insertionStrategy = getInsertionStrategy(signature),
                ),
                scopeKind = KtOutsideTowerScopeKinds.StaticMemberScope,
                showReceiver = showReceiver,
            )
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
                )
            }
    }

    context(KaSession)
    private fun collectSuitableExtensions(
        positionContext: KotlinNameReferencePositionContext,
        scopeContext: KaScopeContext,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        explicitReceiverTypes: List<KaType>? = null,
    ): Sequence<CallableWithMetadataForCompletion> {
        val receiverTypes = (explicitReceiverTypes
            ?: scopeContext.implicitReceivers.map { it.type })
            .filterNot { it is KaErrorType }
        if (receiverTypes.isEmpty()) return emptySequence()

        return scopeContext.scopes
            .asSequence()
            .flatMap { scopeWithKind ->
                val suitableExtensions = collectSuitableExtensions(
                    positionContext = positionContext,
                    scope = scopeWithKind.scope,
                    hasSuitableExtensionReceiver = extensionChecker,
                ).toList()

                ShadowedCallablesFilter.sortExtensions(suitableExtensions, receiverTypes)
                    .map { extension ->
                        CallableWithMetadataForCompletion(
                            _signature = extension.signature,
                            options = extension.insertionOptions,
                            scopeKind = scopeWithKind.kind,
                        )
                    }
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
     * If the signature is that of certain synthetic java properties (e.g. `AtomicInteger.getAndIncrement`),
     * we do not want to use the synthetic property (e.g. `andIncrement`) because it would be unnatural as they are not real properties.
     * For these cases, this function will return the signature of the underlying Java getter instead.
     *
     * @see NotPropertiesService
     */
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KaCallableSignature<*>.getJavaGetterSignatureIfNotProperty(): KaCallableSignature<*>? {
        if (this !is KaVariableSignature<*>) return null
        val symbol = symbol
        if (symbol !is KaSyntheticJavaPropertySymbol || symbol.javaSetterSymbol != null) return null

        val fqName = symbol.javaGetterSymbol.callableId?.asSingleFqName()?.asString() ?: return null
        if (fqName !in NOT_PROPERTIES) return null
        return symbol.javaGetterSymbol.asSignature()
    }

    /**
     * Note, that [isImportDefinitelyNotRequired] should be set to true only if the callable is available without import, and it doesn't
     * require import or fully-qualified name to be resolved unambiguously.
     */
    context(KaSession)
    protected fun KaCallableSignature<*>.createCallableWithMetadata(
        scopeKind: KaScopeKind,
        isImportDefinitelyNotRequired: Boolean = false,
        options: CallableInsertionOptions = getOptions(this, isImportDefinitelyNotRequired),
    ): CallableWithMetadataForCompletion {
        val javaGetterIfNotProperty = this.getJavaGetterSignatureIfNotProperty()
        val optionsToUse = if (javaGetterIfNotProperty != null && options.insertionStrategy == CallableInsertionStrategy.AsIdentifier) {
            options.copy(insertionStrategy = CallableInsertionStrategy.AsCall)
        } else {
            options
        }
        return CallableWithMetadataForCompletion(
            _signature = javaGetterIfNotProperty ?: this,
            options = optionsToUse,
            scopeKind = scopeKind,
        )
    }

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
                scopeKind = callableWithMetadata.scopeKind,
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
        showReceiver: Boolean,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        return when (val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()) {
            is KaPackageSymbol -> emptySequence()
            is KaNamedClassSymbol -> sequence {
                yieldAll(collectDotCompletionFromStaticScope(positionContext, symbol, showReceiver))

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
    ): ApplicableExtension = createApplicableExtension(signature = candidate.asSignature())

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun collectDotCompletion(
        positionContext: KotlinNameReferencePositionContext,
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        extensionChecker: KaCompletionExtensionCandidateChecker?,
        showReceiver: Boolean,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        if (explicitReceiver !is KDocName) return@sequence

        val resolvedSymbols = explicitReceiver.mainReference.resolveToSymbols()
        val scopesWithKinds = resolvedSymbols.flatMap { parentSymbol ->
            when (parentSymbol) {
                is KaPackageSymbol -> sequenceOfNotNull(parentSymbol.staticScope)

                is KaNamedClassSymbol -> sequence {
                    parentSymbol.defaultType
                        .scope
                        ?.declarationScope
                        ?.let {
                            KaScopeWithKindImpl(
                                backingScope = it,
                                backingKind = KtOutsideTowerScopeKinds.TypeScope,
                            )
                        }?.let { yield(it) }

                    val scopeWithKind = KaScopeWithKindImpl(
                        backingScope = parentSymbol.staticScope(),
                        backingKind = KtOutsideTowerScopeKinds.StaticMemberScope,
                    )
                    yield(scopeWithKind)
                }

                else -> emptySequence()
            }
        }

        for (scopeWithKind in scopesWithKinds) {
            for (callableSymbol in scopeWithKind.scope.callables(scopeNameFilter)) {
                if (callableSymbol is KaSyntheticJavaPropertySymbol) continue

                val value = callableSymbol.asSignature()
                    .createCallableWithMetadata(scopeWithKind.kind, isImportDefinitelyNotRequired = true)
                yield(value)
            }
        }
    }
}

context(KaSession)
private fun <T> runCatchingKnownIssues(
    action: () -> T,
): T? = try {
    action()
} catch (e: Exception) {
    val ticketId = when (e) {
        is NoSuchElementException -> "KT-72988"
        is KotlinIllegalArgumentExceptionWithAttachments -> "KT-73334"
        else -> throw e
    }

    logger<FirCallableCompletionContributor>().debug("Temporal wrapping for $ticketId", e)
    null
}
