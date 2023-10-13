// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.analysis.api.types.KtIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.collectNonExtensionsForType
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSuperReceiverNameReferencePositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperExpression

internal class FirSuperMemberCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCompletionContributorBase<KotlinSuperReceiverNameReferencePositionContext>(basicContext, priority) {
    private data class CallableInfo(
        private val _type: KtType,
        private val _signature: KtCallableSignature<*>,
        val scopeKind: KtScopeKind
    ) : KtLifetimeOwner {
        override val token: KtLifetimeToken
            get() = _signature.token
        val type: KtType get() = withValidityAssertion { _type }
        val signature: KtCallableSignature<*> get() = withValidityAssertion { _signature }
    }

    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinSuperReceiverNameReferencePositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) = with(positionContext) {
        val superReceiver = positionContext.superExpression
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val superType = superReceiver.getKtType() ?: return

        val (nonExtensionMembers: Iterable<CallableInfo>, namesNeedDisambiguation: Set<Name>) =
            if (superType !is KtIntersectionType) {
                getNonExtensionsMemberSymbols(superType, visibilityChecker, sessionParameters).asIterable() to emptySet()
            } else {
                getSymbolsAndNamesNeedDisambiguation(superType.conjuncts, visibilityChecker, sessionParameters)
            }
        collectCallToSuperMember(superReceiver, nonExtensionMembers, weighingContext, namesNeedDisambiguation)
        collectDelegateCallToSuperMember(weighingContext, superReceiver, nonExtensionMembers, namesNeedDisambiguation)

    }

    context(KtAnalysisSession)
    private fun getSymbolsAndNamesNeedDisambiguation(
        superTypes: List<KtType>,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Pair<List<CallableInfo>, Set<Name>> {
        val allSymbols = mutableListOf<CallableInfo>()
        val symbolsInAny = mutableSetOf<KtCallableSymbol>()
        val symbolCountsByName = mutableMapOf<Name, Int>()
        for (superType in superTypes) {
            for (callableInfo in getNonExtensionsMemberSymbols(superType, visibilityChecker, sessionParameters)) {
                val symbol = callableInfo.signature.symbol

                // Abstract symbol does not participate completion.
                if (symbol !is KtSymbolWithModality || symbol.modality == Modality.ABSTRACT) continue

                // Unlike typical diamond cases, calls to method of `Any` always do not need extra qualification.
                if (symbol.callableIdIfNonLocal?.classId == StandardClassIds.Any) {
                    if (symbol in symbolsInAny) continue
                    symbolsInAny.add(symbol)
                }

                allSymbols.add(CallableInfo(superType, callableInfo.signature, callableInfo.scopeKind))
                val name = callableInfo.signature.callableIdIfNonLocal?.callableName ?: continue
                symbolCountsByName[name] = (symbolCountsByName[name] ?: 0) + 1
            }
        }

        val nameNeedDisambiguation = mutableSetOf<Name>()
        for ((name, count) in symbolCountsByName) {
            if (count > 1) {
                nameNeedDisambiguation.add(name)
            }
        }
        return Pair(allSymbols, nameNeedDisambiguation)
    }

    context(KtAnalysisSession)
    private fun getNonExtensionsMemberSymbols(
        receiverType: KtType,
        visibilityChecker: CompletionVisibilityChecker,
        sessionParameters: FirCompletionSessionParameters,
    ): Sequence<CallableInfo> {
        return collectNonExtensionsForType(receiverType, visibilityChecker, scopeNameFilter, sessionParameters, symbolFilter = { true })
            .map { CallableInfo(receiverType, it.signature, it.scopeKind) }
    }

    context(KtAnalysisSession)
    private fun collectCallToSuperMember(
        superReceiver: KtSuperExpression,
        nonExtensionMembers: Iterable<CallableInfo>,
        context: WeighingContext,
        namesNeedDisambiguation: Set<Name>
    ) {
        nonExtensionMembers.forEach { callableInfo ->
            addCallableSymbolToCompletion(
                context,
                callableInfo.signature,
                CallableInsertionOptions(
                    importStrategyDetector.detectImportStrategyForCallableSymbol(callableInfo.signature.symbol),
                    wrapWithDisambiguationIfNeeded(
                        getInsertionStrategy(callableInfo.signature),
                        callableInfo.type,
                        callableInfo.signature,
                        namesNeedDisambiguation,
                        superReceiver
                    )
                ),
                CompletionSymbolOrigin.Scope(callableInfo.scopeKind),
            )
        }
    }

    context(KtAnalysisSession)
    private fun getInsertionStrategy(signature: KtCallableSignature<*>): CallableInsertionStrategy = when (signature) {
        is KtFunctionLikeSignature<*> -> CallableInsertionStrategy.AsCall
        else -> CallableInsertionStrategy.AsIdentifier
    }

    context(KtAnalysisSession)
    private fun collectDelegateCallToSuperMember(
        context: WeighingContext,
        superReceiver: KtSuperExpression,
        nonExtensionMembers: Iterable<CallableInfo>,
        namesNeedDisambiguation: Set<Name>
    ) {
        // A map that contains all containing functions as values, each of which is indexed by symbols it overrides. For example, consider
        // the following code
        // ```
        // class A : Runnable {
        //   override fun run() {
        //     val o = object: Callable<String> {
        //       override fun call(): String {
        //         super.<caret>
        //       }
        //     }
        //   }
        // }
        // ```
        // The map would contain
        //
        // * Runnable.run -> A.run
        // * Callable.call -> <anonymous object>.call
        val superFunctionToContainingFunction = superReceiver
            .parentsOfType<KtNamedFunction>(withSelf = false)
            .flatMap { containingFunction ->
                containingFunction
                    .getFunctionLikeSymbol()
                    .getAllOverriddenSymbols()
                    .map { superFunctionSymbol ->
                        superFunctionSymbol to containingFunction
                    }
            }.toMap()

        if (superFunctionToContainingFunction.isEmpty()) return

        for (callableInfo in nonExtensionMembers) {
            val signature = callableInfo.signature
            val matchedContainingFunction = superFunctionToContainingFunction[callableInfo.signature.symbol] ?: continue
            if (signature !is KtFunctionLikeSignature<*>) continue
            if (signature.valueParameters.isEmpty()) continue
            val args = matchedContainingFunction.valueParameters.mapNotNull {
                val name = it.name ?: return@mapNotNull null
                if (it.isVarArg) {
                    "*$name"
                } else {
                    name
                }
            }
            if (args.size < matchedContainingFunction.valueParameters.size) continue
            addCallableSymbolToCompletion(
                context,
                signature,
                CallableInsertionOptions(
                    importStrategyDetector.detectImportStrategyForCallableSymbol(callableInfo.signature.symbol),
                    wrapWithDisambiguationIfNeeded(
                        CallableInsertionStrategy.WithCallArgs(args),
                        callableInfo.type,
                        callableInfo.signature,
                        namesNeedDisambiguation,
                        superReceiver
                    )
                ),
                CompletionSymbolOrigin.Scope(callableInfo.scopeKind),
                priority = ItemPriority.SUPER_METHOD_WITH_ARGUMENTS
            )
        }
    }

    context(KtAnalysisSession)
    private fun wrapWithDisambiguationIfNeeded(
        insertionStrategy: CallableInsertionStrategy,
        superType: KtType,
        callableSignature: KtCallableSignature<*>,
        namesNeedDisambiguation: Set<Name>,
        superReceiver: KtSuperExpression
    ): CallableInsertionStrategy {
        val superClassId = (superType as? KtUsualClassType)?.classId
        val needDisambiguation = callableSignature.callableIdIfNonLocal?.callableName in namesNeedDisambiguation
        return if (needDisambiguation && superClassId != null) {
            CallableInsertionStrategy.WithSuperDisambiguation(superReceiver.createSmartPointer(), superClassId, insertionStrategy)
        } else {
            insertionStrategy
        }
    }
}