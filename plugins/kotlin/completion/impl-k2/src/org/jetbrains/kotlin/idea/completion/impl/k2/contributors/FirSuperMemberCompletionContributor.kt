// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.analysis.api.types.KtIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirSuperReceiverNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.collectNonExtensionsForType
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperExpression

internal class FirSuperMemberCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCompletionContributorBase<FirSuperReceiverNameReferencePositionContext>(basicContext, priority) {

    private val excludeEnumEntries =
        !basicContext.project.languageVersionSettings.supportsFeature(LanguageFeature.EnumEntries)


    private data class CallableInfo(val type: KtType, val symbol: KtCallableSymbol, val scopeKind: KtScopeKind?)

    override fun KtAnalysisSession.complete(
        positionContext: FirSuperReceiverNameReferencePositionContext,
        weighingContext: WeighingContext
    ) = with(positionContext) {
        val superReceiver = positionContext.superExpression
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val superType = superReceiver.getKtType() ?: return

        val (nonExtensionMembers: Iterable<CallableInfo>, namesNeedDisambiguation: Set<Name>) =
            if (superType !is KtIntersectionType) {
                getNonExtensionsMemberSymbols(superType, visibilityChecker).asIterable() to emptySet()
            } else {
                getSymbolsAndNamesNeedDisambiguation(superType.conjuncts, visibilityChecker)
            }
        collectCallToSuperMember(superReceiver, nonExtensionMembers, weighingContext, namesNeedDisambiguation)
        collectDelegateCallToSuperMember(weighingContext, superReceiver, nonExtensionMembers, namesNeedDisambiguation)

    }

    private fun KtAnalysisSession.getSymbolsAndNamesNeedDisambiguation(
        superTypes: List<KtType>,
        visibilityChecker: CompletionVisibilityChecker
    ): Pair<List<CallableInfo>, Set<Name>> {
        val allSymbols = mutableListOf<CallableInfo>()
        val symbolsInAny = mutableSetOf<KtCallableSymbol>()
        val symbolCountsByName = mutableMapOf<Name, Int>()
        for (superType in superTypes) {
            for ((_, symbol, scopeKind) in getNonExtensionsMemberSymbols(superType, visibilityChecker)) {
                // Abstract symbol does not participate completion.
                if (symbol !is KtSymbolWithModality || symbol.modality == Modality.ABSTRACT) continue

                // Unlike typical diamond cases, calls to method of `Any` always do not need extra qualification.
                if (symbol.callableIdIfNonLocal?.classId == StandardClassIds.Any) {
                    if (symbol in symbolsInAny) continue
                    symbolsInAny.add(symbol)
                }

                allSymbols.add(CallableInfo(superType, symbol, scopeKind))
                val name = symbol.callableIdIfNonLocal?.callableName ?: continue
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

    private fun KtAnalysisSession.getNonExtensionsMemberSymbols(
        receiverType: KtType,
        visibilityChecker: CompletionVisibilityChecker
    ): Sequence<CallableInfo> {
        return collectNonExtensionsForType(receiverType, visibilityChecker, scopeNameFilter, excludeEnumEntries).map { (symbol, scopeKind) ->
            CallableInfo(receiverType, symbol, scopeKind)
        }
    }

    private fun KtAnalysisSession.collectCallToSuperMember(
        superReceiver: KtSuperExpression,
        nonExtensionMembers: Iterable<CallableInfo>,
        context: WeighingContext,
        namesNeedDisambiguation: Set<Name>
    ) {
        nonExtensionMembers.forEach { (superType, callableSymbol, scopeKind) ->
            addCallableSymbolToCompletion(
                context,
                callableSymbol,
                CallableInsertionOptions(
                    importStrategyDetector.detectImportStrategy(callableSymbol),
                    wrapWithDisambiguationIfNeeded(
                        getInsertionStrategy(callableSymbol),
                        superType,
                        callableSymbol,
                        namesNeedDisambiguation,
                        superReceiver
                    )
                ),
                scopeKind
            )
        }
    }

    private fun KtAnalysisSession.getInsertionStrategy(symbol: KtCallableSymbol): CallableInsertionStrategy = when (symbol) {
        is KtFunctionLikeSymbol -> CallableInsertionStrategy.AsCall
        else -> CallableInsertionStrategy.AsIdentifier
    }

    private fun KtAnalysisSession.collectDelegateCallToSuperMember(
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

        for ((superType, callableSymbol, scopeKind) in nonExtensionMembers) {
            val matchedContainingFunction = superFunctionToContainingFunction[callableSymbol] ?: continue
            if (callableSymbol !is KtFunctionSymbol) continue
            if (callableSymbol.valueParameters.isEmpty()) continue
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
                callableSymbol,
                CallableInsertionOptions(
                    importStrategyDetector.detectImportStrategy(callableSymbol),
                    wrapWithDisambiguationIfNeeded(
                        CallableInsertionStrategy.WithCallArgs(args),
                        superType,
                        callableSymbol,
                        namesNeedDisambiguation,
                        superReceiver
                    )
                ),
                scopeKind,
                priority = ItemPriority.SUPER_METHOD_WITH_ARGUMENTS
            )
        }
    }

    private fun KtAnalysisSession.wrapWithDisambiguationIfNeeded(
        insertionStrategy: CallableInsertionStrategy,
        superType: KtType,
        callableSymbol: KtCallableSymbol,
        namesNeedDisambiguation: Set<Name>,
        superReceiver: KtSuperExpression
    ): CallableInsertionStrategy {
        val superClassId = (superType as? KtUsualClassType)?.classId
        val needDisambiguation = callableSymbol.callableIdIfNonLocal?.callableName in namesNeedDisambiguation
        return if (needDisambiguation && superClassId != null) {
            CallableInsertionStrategy.WithSuperDisambiguation(superReceiver.createSmartPointer(), superClassId, insertionStrategy)
        } else {
            insertionStrategy
        }
    }
}