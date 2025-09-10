// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElement
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
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.*
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeUsedAsExtension
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.idea.completion.contributors.helpers.*
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSetupScope
import org.jetbrains.kotlin.idea.completion.impl.k2.K2ContributorSectionPriority
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.allowsOnlyNamedArguments
import org.jetbrains.kotlin.idea.completion.impl.k2.checkers.ApplicableExtension
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.impl.k2.isAfterRangeOperator
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionInsertionHelper
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.CallableWeigher.callableWeight
import org.jetbrains.kotlin.idea.core.NotPropertiesService
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.nextSiblingOfSameType
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.types.Variance
import kotlin.reflect.KClass

private val NOT_PROPERTIES = NotPropertiesService.DEFAULT.toSet()

internal abstract class K2AbstractCallableCompletionContributor<P : KotlinNameReferencePositionContext>(
    positionContextClass: KClass<P>,
) : K2SimpleCompletionContributor<P>(
    positionContextClass = positionContextClass,
    priority = K2ContributorSectionPriority.HEURISTIC,
) {

    context(_: KaSession)
    protected open fun getImportStrategy(
        context: K2CompletionSectionContext<P>,
        signature: KaCallableSignature<*>,
        isImportDefinitelyNotRequired: Boolean
    ): ImportStrategy =
        if (isImportDefinitelyNotRequired) {
            ImportStrategy.DoNothing
        } else {
            context.importStrategyDetector.detectImportStrategyForCallableSymbol(signature.symbol)
        }

    protected open fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        when (signature) {
            is KaFunctionSignature<*> -> CallableInsertionStrategy.AsCall
            else -> CallableInsertionStrategy.AsIdentifier
        }

    context(_: KaSession)
    @KaExperimentalApi
    protected open fun getInsertionStrategyForFunctionalVariables(
        applicabilityResult: KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall,
    ): CallableInsertionStrategy? = CallableInsertionStrategy.AsCall

    context(_: KaSession)
    private fun getOptions(
        context: K2CompletionSectionContext<P>,
        signature: KaCallableSignature<*>,
        isImportDefinitelyNotRequired: Boolean = false
    ): CallableInsertionOptions = CallableInsertionOptions(
        getImportStrategy(context, signature, isImportDefinitelyNotRequired),
        getInsertionStrategy(signature)
    )

    context(_: KaSession)
    protected open fun filter(symbol: KaCallableSymbol): Boolean = !symbol.isIgnoredExpectDeclaration()

    // todo replace with a sealed hierarchy; too many arguments
    protected data class CallableWithMetadataForCompletion(
        private val _signature: KaCallableSignature<*>,
        val options: CallableInsertionOptions,
        val scopeKind: KaScopeKind? = null, // index
        val showReceiver: Boolean = false, // todo extract; only used for objects/enums/static members
        private val _explicitReceiverTypeHint: KaType? = null, // todo extract; only used for smart casts
        val aliasName: Name? = null
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

    private fun K2CompletionSectionContext<P>.isWithTrailingLambda(): Boolean =
        positionContext is KotlinExpressionNameReferencePositionContext

    context(_: KaSession)
    private fun Sequence<CallableWithMetadataForCompletion>.createFilteredLookupElements(
        context: K2CompletionSectionContext<P>,
        shadowedCallablesFilter: ShadowedCallablesFilter
    ): Sequence<LookupElement> =
        filterIfInsideAnnotationEntryArgument(context.positionContext.position, context.weighingContext.expectedType)
            .mapNotNull(shadowIfNecessary(context, shadowedCallablesFilter))
            .filterNot(isUninitializedCallable(context))
            .flatMap { callableWithMetadata ->
                createCallableLookupElements(
                    context = context.weighingContext,
                    signature = callableWithMetadata.signature,
                    options = callableWithMetadata.options,
                    scopeKind = callableWithMetadata.scopeKind,
                    presentableText = callableWithMetadata.itemText,
                    withTrailingLambda = context.isWithTrailingLambda(),
                    aliasName = callableWithMetadata.aliasName,
                    parameters = context.parameters,
                ).map { builder ->
                    val receiver = context.positionContext.explicitReceiver ?: return@map builder

                    if (builder.callableWeight?.kind != CallableMetadataProvider.CallableKind.RECEIVER_CAST_REQUIRED)
                        return@map builder

                    val explicitReceiverTypeHint = callableWithMetadata.explicitReceiverTypeHint
                        ?: return@map builder

                    builder.adaptToExplicitReceiver(
                        receiver = receiver,
                        typeText = @OptIn(KaExperimentalApi::class) explicitReceiverTypeHint.render(position = Variance.INVARIANT),
                    )
                }
            }

    context(_: KaSession)
    fun completeFromLocalScope(
        context: K2CompletionSectionContext<P>,
        shadowedCallablesFilter: ShadowedCallablesFilter,
    ) {
        val positionContext = context.positionContext
        val weighingContext = context.weighingContext
        val scopeContext = weighingContext.scopeContext

        val receiver = positionContext.explicitReceiver
        if (receiver == null) return
        val elements = collectDotCompletionFromLocalScope(context, scopeContext, receiver)
        elements.createFilteredLookupElements(context, shadowedCallablesFilter)
            .forEach { context.addElement(it) }
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createAndFilterMetadataForMemberCallables(
        context: K2CompletionSectionContext<P>,
        callables: Sequence<KaCallableSymbol>,
    ): Sequence<CallableWithMetadataForCompletion> = callables.filter { filter(it) }
        .filter { context.visibilityChecker.isVisible(it, context.positionContext) }
        .map { it.asSignature() }
        .map { signature ->
            CallableWithMetadataForCompletion(
                _signature = signature,
                options = getOptions(context, signature),
                showReceiver = true,
            )
        }

    context(_: KaSession)
    private fun completeWithoutReceiverFromIndex(
        context: K2CompletionSectionContext<P>
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val prefix = context.prefixMatcher.prefix
        val invocationCount = context.parameters.invocationCount
        val scopeContext = context.weighingContext.scopeContext


        if (prefix.isNotEmpty()) {
            val callablesFromIndex = if (invocationCount > 1) {
                context.symbolFromIndexProvider.getKotlinCallableSymbolsByNameFilter(context.completionContext.scopeNameFilter) {
                    if (!context.visibilityChecker.canBeVisible(it)) return@getKotlinCallableSymbolsByNameFilter false
                    // We should not show class members when we do not have a receiver.
                    // See: KT-78882 for why we need `runCatching` here.
                    val containingSymbol = runCatching { it.symbol.containingSymbol }.getOrNull()
                    containingSymbol !is KaClassSymbol || containingSymbol.classKind.isObject
                }
            } else {
                context.symbolFromIndexProvider.getTopLevelCallableSymbolsByNameFilter(context.completionContext.scopeNameFilter) {
                    context.visibilityChecker.canBeVisible(it)
                }
            }
            yieldAll(createAndFilterMetadataForMemberCallables(context, callablesFromIndex))
        }

        val extensionDescriptors = collectExtensionsFromIndexAndResolveExtensionScope(
            context = context,
            receiverTypes = scopeContext.implicitReceivers.map { it.type },
        )
        yieldAll(extensionDescriptors)
    }

    context(_: KaSession)
    fun completeFromIndex(
        context: K2CompletionSectionContext<P>,
        shadowedCallablesFilter: ShadowedCallablesFilter,
    ) {
        val positionContext = context.positionContext

        val receiver = positionContext.explicitReceiver
        val elements = if (receiver == null) {
            completeWithoutReceiverFromIndex(context)
        } else {
            collectDotCompletionFromIndex(context, receiver)
        }
        elements.createFilteredLookupElements(context, shadowedCallablesFilter)
            .forEach { context.addElement(it) }
    }

    context(_: KaSession)
    protected open fun collectDotCompletionFromLocalScope(
        context: K2CompletionSectionContext<P>,
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        showReceiver: Boolean = false,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        return when (val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()) {
            is KaPackageSymbol -> collectDotCompletionForPackageReceiver(context, symbol)

            else -> sequence {
                if (symbol is KaNamedClassSymbol) {
                    yieldAll(collectDotCompletionFromStaticScope(context, symbol, showReceiver))
                }

                if (symbol !is KaNamedClassSymbol || symbol.canBeUsedAsReceiver) {
                    yieldAll(
                        collectDotCompletionForCallableReceiver(
                            context = context,
                            scopeContext = scopeContext,
                            explicitReceiver = explicitReceiver,
                        )
                    )
                }

                if (!showReceiver && this@K2AbstractCallableCompletionContributor is K2ChainCompletionContributor) {
                    context.sink.registerChainContributor(this@K2AbstractCallableCompletionContributor)
                }
            }
        }
    }

    context(_: KaSession)
    protected open fun collectDotCompletionFromIndex(
        context: K2CompletionSectionContext<P>,
        explicitReceiver: KtElement,
        showReceiver: Boolean = false,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        return when (val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()) {
            is KaPackageSymbol -> return emptySequence()

            else -> sequence {
                if (symbol !is KaNamedClassSymbol || symbol.canBeUsedAsReceiver) {
                    val types = collectReceiverTypesForExplicitReceiverExpression(explicitReceiver)
                    yieldAll(
                        collectDotCompletionForCallableReceiverFromIndex(
                            context = context,
                            typesOfPossibleReceiver = types,
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

    context(_: KaSession)
    private fun collectDotCompletionForPackageReceiver(
        context: K2CompletionSectionContext<P>,
        packageSymbol: KaPackageSymbol,
    ): Sequence<CallableWithMetadataForCompletion> = packageSymbol.packageScope
        .callables(context.completionContext.scopeNameFilter)
        .filterNot { it.isExtension }
        .filter { context.visibilityChecker.isVisible(it, context.positionContext) }
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

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    protected fun collectDotCompletionForCallableReceiver(
        context: K2CompletionSectionContext<P>,
        scopeContext: KaScopeContext,
        explicitReceiver: KtExpression,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val receiverType = explicitReceiver.expressionType ?: return@sequence
        val callablesWithMetadata = collectDotCompletionForCallableReceiver(
            context = context,
            typesOfPossibleReceiver = listOf(receiverType),
            scopeContext = scopeContext,
        )
        yieldAll(callablesWithMetadata)

        val smartCastInfo = explicitReceiver.smartCastInfo
            ?: return@sequence
        if (smartCastInfo.isStable) return@sequence
        val smartCastType = smartCastInfo.smartCastType
        val explicitReceiverTypeHint = smartCastType.takeIf { it.isDenotable }

        // Collect members available from unstable smartcast as well.
        val callablesWithMetadataFromUnstableSmartCast = collectDotCompletionForCallableReceiver(
            context = context,
            typesOfPossibleReceiver = listOf(smartCastType),
            scopeContext = scopeContext,
        ) + collectDotCompletionForCallableReceiverFromIndex(
            context = context,
            typesOfPossibleReceiver = listOf(smartCastType),
        )
        yieldAll(callablesWithMetadataFromUnstableSmartCast.map {
            if (explicitReceiverTypeHint != null) {
                // Only offer the hint if the type is denotable.
                it.copy(_explicitReceiverTypeHint = explicitReceiverTypeHint)
            } else {
                it
            }
        })
    }

    context(_: KaSession)
    protected fun collectDotCompletionForCallableReceiver(
        context: K2CompletionSectionContext<P>,
        typesOfPossibleReceiver: List<KaType>,
        scopeContext: KaScopeContext,
    ): Sequence<CallableWithMetadataForCompletion> = sequence {
        val nonExtensionMembers = typesOfPossibleReceiver.flatMap { typeOfPossibleReceiver ->
            collectNonExtensionsForType(
                parameters = context.parameters,
                positionContext = context.positionContext,
                receiverType = typeOfPossibleReceiver,
                visibilityChecker = context.visibilityChecker,
                scopeNameFilter = context.completionContext.scopeNameFilter,
                symbolFilter = { filter(it) },
            ).map {
                it.createCallableWithMetadata(
                    context = context,
                    scopeKind = KtOutsideTowerScopeKinds.TypeScope,
                    isImportDefinitelyNotRequired = true,
                )
            }
        }
        val extensionNonMembers = collectSuitableExtensions(
            context = context,
            scopeContext = scopeContext,
            explicitReceiverTypes = typesOfPossibleReceiver,
        )

        yieldAll(nonExtensionMembers)
        yieldAll(extensionNonMembers)
    }

    context(_: KaSession)
    protected fun collectDotCompletionForCallableReceiverFromIndex(
        context: K2CompletionSectionContext<P>,
        typesOfPossibleReceiver: List<KaType>,
    ): Sequence<CallableWithMetadataForCompletion> {
        return collectExtensionsFromIndexAndResolveExtensionScope(
            context = context,
            receiverTypes = typesOfPossibleReceiver,
        ).filter { filter(it.signature.symbol) }.asSequence()
    }

    context(_: KaSession)
    protected fun collectDotCompletionFromStaticScope(
        context: K2CompletionSectionContext<P>,
        symbol: KaNamedClassSymbol,
        showReceiver: Boolean,
    ): Sequence<CallableWithMetadataForCompletion> {
        if (!symbol.hasImportantStaticMemberScope) return emptySequence()

        val nonExtensions = collectNonExtensionsFromScope(
            parameters = context.parameters,
            positionContext = context.positionContext,
            scope = symbol.staticScope(withCompanionScope = false),
            visibilityChecker = context.visibilityChecker,
            scopeNameFilter = context.completionContext.scopeNameFilter,
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

    context(_: KaSession)
    private fun collectExtensionsFromIndexAndResolveExtensionScope(
        context: K2CompletionSectionContext<P>,
        receiverTypes: List<KaType>,
    ): Collection<CallableWithMetadataForCompletion> {
        if (receiverTypes.isEmpty()) return emptyList()

        val extensionsFromIndex = context.symbolFromIndexProvider.getExtensionCallableSymbolsByNameFilter(
            context.completionContext.scopeNameFilter,
            receiverTypes,
        ) { context.visibilityChecker.canBeVisible(it) && it.canBeAnalysed() }

        return extensionsFromIndex
            .filter { filter(it) }
            .filter { context.visibilityChecker.isVisible(it, context.positionContext) }
            .mapNotNull { checkApplicabilityAndSubstitute(context, it) }
            .let {
                ShadowedCallablesFilter.sortExtensions(it.toList(), receiverTypes)
            }.map { applicableExtension ->
                CallableWithMetadataForCompletion(
                    _signature = applicableExtension.signature,
                    options = applicableExtension.insertionOptions,
                )
            }
    }

    context(_: KaSession)
    private fun collectSuitableExtensions(
        context: K2CompletionSectionContext<P>,
        scopeContext: KaScopeContext,
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
                    context = context,
                    scope = scopeWithKind.scope,
                ).toList()

                ShadowedCallablesFilter.sortExtensions(suitableExtensions, receiverTypes)
                    .map { extension ->
                        val aliasName = context.parameters.completionFile.getAliasNameIfExists(extension.signature.symbol)
                        CallableWithMetadataForCompletion(
                            _signature = extension.signature,
                            options = extension.insertionOptions,
                            scopeKind = scopeWithKind.kind,
                            aliasName = aliasName,
                        )
                    }
            }
    }

    context(_: KaSession)
    private fun collectSuitableExtensions(
        context: K2CompletionSectionContext<P>,
        scope: KaScope,
    ): Sequence<ApplicableExtension> =
        scope.callables(context.completionContext.scopeNameFilter)
            .filter { it.canBeUsedAsExtension() }
            .filter { context.visibilityChecker.isVisible(it, context.positionContext) }
            .filter { filter(it) }
            .mapNotNull { callable -> checkApplicabilityAndSubstitute(context, callable) }

    context(_: KaSession)
    protected fun createApplicableExtension(
        signature: KaCallableSignature<*>,
        importingStrategy: ImportStrategy,
        insertionStrategy: CallableInsertionStrategy = getInsertionStrategy(signature),
    ): ApplicableExtension = ApplicableExtension(
        _signature = signature,
        insertionOptions = CallableInsertionOptions(importingStrategy, insertionStrategy),
    )

    /**
     * If [candidate] is applicable returns substituted signature and insertion options, otherwise, null.
     * When the extensionChecker from the [context] is null, no check is carried and applicability result is null.
     */
    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    protected open fun checkApplicabilityAndSubstitute(
        context: K2CompletionSectionContext<P>,
        candidate: KaCallableSymbol,
    ): ApplicableExtension? {
        val applicabilityResult = context.extensionChecker?.computeApplicability(candidate) as? KaExtensionApplicabilityResult.Applicable
            ?: return null

        val substitutor = applicabilityResult.substitutor
        return when (applicabilityResult) {
            is KaExtensionApplicabilityResult.ApplicableAsExtensionCallable -> {
                val signature = candidate.substitute(substitutor)
                createApplicableExtension(
                    signature = signature,
                    importingStrategy = context.importStrategyDetector.detectImportStrategyForCallableSymbol(symbol = signature.symbol)
                )
            }

            is KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall -> {
                val insertionStrategy = getInsertionStrategyForFunctionalVariables(applicabilityResult)
                    ?: return null

                createApplicableExtension(
                    signature = candidate.substitute(substitutor), // do not run before null check
                    importingStrategy = context.importStrategyDetector.detectImportStrategyForCallableSymbol(
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
    context(_: KaSession)
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
    context(_: KaSession)
    protected fun KaCallableSignature<*>.createCallableWithMetadata(
        context: K2CompletionSectionContext<P>,
        scopeKind: KaScopeKind,
        isImportDefinitelyNotRequired: Boolean = false,
        options: CallableInsertionOptions = getOptions(context, this, isImportDefinitelyNotRequired),
        aliasName: Name? = null,
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
            aliasName = aliasName
        )
    }

    private fun isUninitializedCallable(
        context: K2CompletionSectionContext<P>,
    ): (CallableWithMetadataForCompletion) -> Boolean {
        val uninitializedCallablesForPosition = buildSet<KtCallableDeclaration> {
            for (parent in context.positionContext.position.parents(withSelf = false)) {
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
                                originalKtFile = context.parameters.originalFile,
                            )
                            generateSequence(originalOrSelf) { it.nextSiblingOfSameType() }
                                .forEach(::add)
                        }
                    }

                    is KtProperty -> {
                        if (grandParent.initializer == parent) {
                            val declaration = getOriginalDeclarationOrSelf(
                                declaration = grandParent,
                                originalKtFile = context.parameters.originalFile,
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

    context(_: KaSession)
    private fun shadowIfNecessary(
        context: K2CompletionSectionContext<P>,
        shadowedCallablesFilter: ShadowedCallablesFilter
    ) = object : Function1<CallableWithMetadataForCompletion, CallableWithMetadataForCompletion?> {

        override fun invoke(callableWithMetadata: CallableWithMetadataForCompletion): CallableWithMetadataForCompletion? {
            val insertionOptions = callableWithMetadata.options
            val (excludeFromCompletion, newImportStrategy) = shadowedCallablesFilter.excludeFromCompletion(
                callableSignature = callableWithMetadata.signature,
                options = insertionOptions,
                scopeKind = callableWithMetadata.scopeKind,
                importStrategyDetector = context.importStrategyDetector,
            ) {
                !FunctionInsertionHelper.functionCanBeCalledWithoutExplicitTypeArguments(it, context.weighingContext.expectedType)
            }

            return if (excludeFromCompletion) null
            else if (newImportStrategy == null) callableWithMetadata
            else callableWithMetadata.copy(options = insertionOptions.copy(importingStrategy = newImportStrategy))
        }
    }

    context(_: KaSession)
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

    context(_: KaSession)
    private fun KaJavaFieldSymbol.hasPrimitiveOrStringReturnType(): Boolean =
        (psi as? PsiField)?.type is PsiPrimitiveType || returnType.isStringType

    context(_: KaSession)
    private fun KaCallableSymbol.hasConstEvaluationAnnotation(): Boolean =
        annotations.any { it.classId == StandardClassIds.Annotations.IntrinsicConstEvaluation }

    context(_: KaSession)
    protected fun KaNamedClassSymbol.staticScope(withCompanionScope: Boolean = true): KaScope = buildList {
        if (withCompanionScope) {
            addIfNotNull(companionObject?.memberScope)
        }
        add(staticMemberScope)
    }.asCompositeScope()


    context(_: KaSession)
    fun completeEnumEntriesFromPsi(
        context: K2CompletionSectionContext<P>,
        shadowedCallablesFilter: ShadowedCallablesFilter,
    ) {
        // If the expected type is an enum, we want to yield the enum entries very early on so they will
        // definitely be available before the elements are frozen.
        val receiver = context.positionContext.explicitReceiver
        if (receiver != null) return

        val expectedEnumType = context.weighingContext.expectedType?.takeUnless { it is KaErrorType }
            ?.withNullability(false)
            ?.takeIf { it.isEnum() }

        val enumEntries = when (val enumClass = expectedEnumType?.symbol?.psi) {
            is KtClassOrObject -> {
                enumClass.body?.enumEntries?.asSequence()?.map { it.symbol } ?: emptySequence()
            }

            is PsiClass -> {
                enumClass.childrenOfType<PsiEnumConstant>().asSequence().mapNotNull { it.callableSymbol }
            }

            else -> emptySequence()
        }

        createAndFilterMetadataForMemberCallables(context, enumEntries)
            .createFilteredLookupElements(context, shadowedCallablesFilter)
            .forEach { context.addElement(it) }
    }

    context(_: KaSession)
    fun completeLocalVariables(
        context: K2CompletionSectionContext<P>,
        shadowedCallablesFilter: ShadowedCallablesFilter,
    ) {
        if (context.positionContext.explicitReceiver != null) return
        val availableLocalAndMemberNonExtensions = collectLocalAndMemberNonExtensionsFromScopeContext(
            parameters = context.parameters,
            positionContext = context.positionContext,
            scopeContext = context.weighingContext.scopeContext,
            visibilityChecker = context.visibilityChecker,
            scopeNameFilter = context.completionContext.scopeNameFilter,
        ) { filter(it) }
            .map { signatureWithKind ->
                signatureWithKind.signature
                    .createCallableWithMetadata(context, signatureWithKind.scopeKind)
            }

        availableLocalAndMemberNonExtensions.createFilteredLookupElements(context, shadowedCallablesFilter)
            .forEach { context.addElement(it) }
    }

    context(_: KaSession)
    fun completeLocalExtensions(
        context: K2CompletionSectionContext<P>,
        shadowedCallablesFilter: ShadowedCallablesFilter,
    ) {
        if (context.positionContext.explicitReceiver != null) return
        val scopeContext = context.weighingContext.scopeContext
        val extensionsWhichCanBeCalled = collectSuitableExtensions(
            context = context,
            scopeContext = scopeContext,
        )
        val availableStaticAndTopLevelNonExtensions = scopeContext.scopes
            .asSequence()
            .filterNot { it.kind is KaScopeKind.LocalScope || it.kind is KaScopeKind.TypeScope }
            .flatMap { scopeWithKind ->
                collectNonExtensionsFromScope(
                    parameters = context.parameters,
                    positionContext = context.positionContext,
                    scope = scopeWithKind.scope,
                    visibilityChecker = context.visibilityChecker,
                    scopeNameFilter = context.completionContext.scopeNameFilter,
                    symbolFilter = { filter(it) },
                ).map { signature ->
                    val aliasName = context.parameters.completionFile.getAliasNameIfExists(signature.symbol)
                    signature.createCallableWithMetadata(context, scopeWithKind.kind, aliasName = aliasName)
                }
            }

        (extensionsWhichCanBeCalled + availableStaticAndTopLevelNonExtensions)
            .createFilteredLookupElements(context, shadowedCallablesFilter)
            .forEach { context.addElement(it) }
    }

    context(_: KaSession)
    fun completeEnumEntriesFromIndex(
        context: K2CompletionSectionContext<P>,
        shadowedCallablesFilter: ShadowedCallablesFilter,
    ) {
        if (context.positionContext.explicitReceiver != null) return
        val prefix = context.prefixMatcher.prefix
        val invocationCount = context.parameters.invocationCount

        val expectedType = context.weighingContext.expectedType?.takeUnless { it is KaErrorType }
            ?.withNullability(false)
            ?.takeIf { it.isEnum() }

        val enumEntries = sequence {
            val psiFilter: (KtEnumEntry) -> Boolean = if (invocationCount > 2) { _ -> true }
            else if (invocationCount > 1 && prefix.isNotEmpty()) context.visibilityChecker::canBeVisible
            else if (expectedType != null) { enumEntry ->
                context.visibilityChecker.canBeVisible(enumEntry)
                        && enumEntry.returnType
                    .withNullability(false)
                    .semanticallyEquals(expectedType)
            }
            else return@sequence

            val enumEntries = context.symbolFromIndexProvider.getKotlinEnumEntriesByNameFilter(
                nameFilter = context.completionContext.scopeNameFilter,
                psiFilter = psiFilter,
            )
            yieldAll(enumEntries)
        }

        val enumConstants = sequence {
            // todo KtCodeFragments
            if (invocationCount <= 2
                && (invocationCount <= 1 || prefix.isEmpty())
                && expectedType == null
            ) return@sequence

            val constants = context.symbolFromIndexProvider.getJavaFieldsByNameFilter(context.completionContext.scopeNameFilter) {
                it is PsiEnumConstant
            }.filterIsInstance<KaEnumEntrySymbol>()
                .filter { enumEntrySymbol ->
                    expectedType == null
                            || enumEntrySymbol.returnType
                        .withNullability(false)
                        .semanticallyEquals(expectedType)
                }
            yieldAll(constants)
        }
        createAndFilterMetadataForMemberCallables(context, enumEntries + enumConstants)
            .createFilteredLookupElements(context, shadowedCallablesFilter)
            .forEach { context.addElement(it) }
    }

    override fun KaSession.complete(context: K2CompletionSectionContext<P>) {
        // TODO: Apart from de-duplication this an all be done in the separate threads.
        //  we should consider doing the filtering later in the pipeline and run these all in parallel.
        val shadowedCallablesFilter = ShadowedCallablesFilter()
        context.completeLaterInSameSession("Enum Entries from PSI", priority = K2ContributorSectionPriority.HEURISTIC) {
            completeEnumEntriesFromPsi(it, shadowedCallablesFilter)
        }
        context.completeLaterInSameSession("Local Variables", priority = K2ContributorSectionPriority.HEURISTIC) {
            completeLocalVariables(it, shadowedCallablesFilter)
        }
        context.completeLaterInSameSession("Local Extensions") {
            completeLocalExtensions(it, shadowedCallablesFilter)
        }
        context.completeLaterInSameSession("Local Completion") {
            completeFromLocalScope(it, shadowedCallablesFilter)
        }
        context.completeLaterInSameSession("Enums from Index", K2ContributorSectionPriority.FROM_INDEX) {
            completeEnumEntriesFromIndex(it, shadowedCallablesFilter)
        }
        context.completeLaterInSameSession("Index Completion", K2ContributorSectionPriority.FROM_INDEX) {
            completeFromIndex(it, shadowedCallablesFilter)
        }
    }
}

internal class K2CallableCompletionContributor : K2AbstractCallableCompletionContributor<KotlinNameReferencePositionContext>(
    KotlinNameReferencePositionContext::class
), K2ChainCompletionContributor {
    override fun K2CompletionSetupScope<KotlinNameReferencePositionContext>.isAppropriatePosition(): Boolean = when (position) {
        is KotlinExpressionNameReferencePositionContext,
        is KotlinWithSubjectEntryPositionContext -> true

        else -> false
    }

    override fun K2CompletionSectionContext<KotlinNameReferencePositionContext>.getGroupPriority(): Int = when (positionContext) {
        is KotlinWithSubjectEntryPositionContext -> 2
        else -> 0
    }

    override fun KaSession.shouldExecute(context: K2CompletionSectionContext<KotlinNameReferencePositionContext>): Boolean {
        return !context.positionContext.isAfterRangeOperator() && !context.positionContext.allowsOnlyNamedArguments()
    }

    context(_: KaSession)
    override fun createChainedLookupElements(
        context: K2CompletionSectionContext<KotlinExpressionNameReferencePositionContext>,
        receiverExpression: KtDotQualifiedExpression,
        importingStrategy: ImportStrategy
    ): Sequence<LookupElement> {
        return collectDotCompletionFromLocalScope(
            context = context,
            scopeContext = context.weighingContext.scopeContext,
            explicitReceiver = receiverExpression,
            showReceiver = true,
        ).flatMap { callableWithMetadata ->
            val signature = callableWithMetadata.signature

            createCallableLookupElements(
                context = context.weighingContext,
                parameters = context.parameters,
                signature = signature,
                options = callableWithMetadata.options.copy(importingStrategy = importingStrategy),
                scopeKind = callableWithMetadata.scopeKind,
                presentableText = callableWithMetadata.itemText,
                withTrailingLambda = true,
            )
        }
    }
}

internal class K2CallableReferenceCompletionContributor : K2AbstractCallableCompletionContributor<KotlinCallableReferencePositionContext>(
    KotlinCallableReferencePositionContext::class
) {
    override fun K2CompletionSectionContext<KotlinCallableReferencePositionContext>.getGroupPriority(): Int = 1

    context(_: KaSession)
    override fun getImportStrategy(
        context: K2CompletionSectionContext<KotlinCallableReferencePositionContext>,
        signature: KaCallableSignature<*>,
        isImportDefinitelyNotRequired: Boolean
    ): ImportStrategy {
        if (isImportDefinitelyNotRequired) return ImportStrategy.DoNothing

        return signature.callableId?.let { ImportStrategy.AddImport(it.asSingleFqName()) } ?: ImportStrategy.DoNothing
    }

    override fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        CallableInsertionStrategy.AsIdentifier

    context(_: KaSession)
    @KaExperimentalApi
    override fun getInsertionStrategyForFunctionalVariables(
        applicabilityResult: KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall,
    ): CallableInsertionStrategy? = null

    context(_: KaSession)
    override fun filter(symbol: KaCallableSymbol): Boolean = when {
        // References to elements which are members and extensions at the same time are not allowed
        symbol.isExtension && symbol.location == KaSymbolLocation.CLASS -> false

        // References to variables and parameters are unsupported
        symbol is KaValueParameterSymbol || symbol is KaLocalVariableSymbol || symbol is KaBackingFieldSymbol -> false

        // References to enum entries aren't supported
        symbol is KaEnumEntrySymbol -> false

        else -> true
    }


    context(_: KaSession)
    override fun collectDotCompletionFromLocalScope(
        context: K2CompletionSectionContext<KotlinCallableReferencePositionContext>,
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
        showReceiver: Boolean,
    ): Sequence<CallableWithMetadataForCompletion> {
        explicitReceiver as KtExpression

        return when (val symbol = explicitReceiver.reference()?.resolveToExpandedSymbol()) {
            is KaPackageSymbol -> emptySequence()
            is KaNamedClassSymbol -> sequence {
                yieldAll(collectDotCompletionFromStaticScope(context, symbol, showReceiver))

                val types = collectReceiverTypesForExplicitReceiverExpression(explicitReceiver)
                yieldAll(
                    collectDotCompletionForCallableReceiver(
                        context = context,
                        typesOfPossibleReceiver = types,
                        scopeContext = scopeContext,
                    )
                )
            }

            else -> collectDotCompletionForCallableReceiver(
                context = context,
                scopeContext = scopeContext,
                explicitReceiver = explicitReceiver,
            )
        }
    }
}

internal class K2InfixCallableCompletionContributor : K2AbstractCallableCompletionContributor<KotlinInfixCallPositionContext>(
    KotlinInfixCallPositionContext::class
) {

    override fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        CallableInsertionStrategy.InfixCallableInsertionStrategy

    context(_: KaSession)
    @KaExperimentalApi
    override fun getInsertionStrategyForFunctionalVariables(
        applicabilityResult: KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall,
    ): CallableInsertionStrategy? = null

    context(_: KaSession)
    override fun filter(symbol: KaCallableSymbol): Boolean =
        symbol is KaNamedFunctionSymbol
                && symbol.isInfix
                && super.filter(symbol)
}

internal class K2KDocCallableCompletionContributor : K2AbstractCallableCompletionContributor<KDocLinkNamePositionContext>(
    KDocLinkNamePositionContext::class
) {

    override fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy =
        CallableInsertionStrategy.AsIdentifier

    /**
     * Is not used directly, @see [checkApplicabilityAndSubstitute].
     */
    context(_: KaSession)
    @KaExperimentalApi
    override fun getInsertionStrategyForFunctionalVariables(
        applicabilityResult: KaExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall,
    ): CallableInsertionStrategy = throw RuntimeException("Should not be used directly")

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun checkApplicabilityAndSubstitute(
        context: K2CompletionSectionContext<KDocLinkNamePositionContext>,
        candidate: KaCallableSymbol
    ): ApplicableExtension {
        val signature = candidate.asSignature()
        val importStrategy = context.importStrategyDetector.detectImportStrategyForCallableSymbol(symbol = signature.symbol)
        return createApplicableExtension(signature, importStrategy)
    }

    // No dot completion from index necessary for KDocs
    context(_: KaSession)
    override fun collectDotCompletionFromIndex(
        context: K2CompletionSectionContext<KDocLinkNamePositionContext>,
        explicitReceiver: KtElement,
        showReceiver: Boolean
    ): Sequence<CallableWithMetadataForCompletion> = emptySequence()

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun collectDotCompletionFromLocalScope(
        context: K2CompletionSectionContext<KDocLinkNamePositionContext>,
        scopeContext: KaScopeContext,
        explicitReceiver: KtElement,
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
            for (callableSymbol in scopeWithKind.scope.callables(context.completionContext.scopeNameFilter)) {
                if (callableSymbol is KaSyntheticJavaPropertySymbol) continue

                val aliasName = context.parameters.completionFile.getAliasNameIfExists(callableSymbol)
                val value = callableSymbol.asSignature()
                    .createCallableWithMetadata(context, scopeWithKind.kind, isImportDefinitelyNotRequired = true, aliasName = aliasName)
                yield(value)
            }
        }
    }
}
