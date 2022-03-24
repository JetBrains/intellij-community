// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.analysis.api.types.KtIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirSuperReceiverNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.collectNonExtensions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.detectImportStrategy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext.Companion.createWeighingContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.utils.addIfNotNull

internal class FirSuperMemberCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCompletionContributorBase<FirSuperReceiverNameReferencePositionContext>(basicContext, priority) {
    override fun KtAnalysisSession.complete(positionContext: FirSuperReceiverNameReferencePositionContext) = with(positionContext) {
        val superReceiver = positionContext.superExpression
        val expectedType = nameExpression.getExpectedType()
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val superType = superReceiver.getKtType() ?: return
        val weighingContext = createWeighingContext(
            superReceiver,
            expectedType,
            emptyList(), // Implicit receivers do not match for this completion contributor.
            fakeKtFile
        )

        val (nonExtensionMembers: Iterable<Pair<KtType, KtCallableSymbol>>, namesNeedDisambiguation: Set<Name>) = if (superType !is KtIntersectionType) {
            val scope = superType.getTypeScope() ?: return
            collectNonExtensions(scope, visibilityChecker, scopeNameFilter).map { superType to it }.asIterable() to emptySet()
        } else {
            getSymbolsAndNamesNeedDisambiguation(superType.conjuncts, visibilityChecker)
        }
        collectCallToSuperMember(superReceiver, nonExtensionMembers, weighingContext, namesNeedDisambiguation)
        collectDelegateCallToSuperMember(weighingContext, superReceiver, nonExtensionMembers, namesNeedDisambiguation)

    }

    private fun KtAnalysisSession.getSymbolsAndNamesNeedDisambiguation(
        superTypes: List<KtType>,
        visibilityChecker: CompletionVisibilityChecker
    ): Pair<List<Pair<KtType, KtCallableSymbol>>, Set<Name>> {
        val allSymbols = mutableListOf<Pair<KtType, KtCallableSymbol>>()
        val symbolsInAny = mutableSetOf<KtCallableSymbol>()
        val symbolCountsByName = mutableMapOf<Name, Int>()
        for (superType in superTypes) {
            for (symbol in getNonExtensionsMemberSymbols(superType, visibilityChecker)) {
                // Abstract symbol does not participate completion.
                if (symbol !is KtSymbolWithModality || symbol.modality == Modality.ABSTRACT) continue

                // Unlike typical diamond cases, calls to method of `Any` always do not need extra qualification.
                if (symbol.callableIdIfNonLocal?.classId == StandardClassIds.Any) {
                    if (symbol in symbolsInAny) continue
                    symbolsInAny.add(symbol)
                }

                allSymbols.add(superType to symbol)
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
    ): Sequence<KtCallableSymbol> {
        val possibleReceiverScope = receiverType.getTypeScope() ?: return emptySequence()
        return collectNonExtensions(possibleReceiverScope, visibilityChecker, scopeNameFilter)
    }

    private fun KtAnalysisSession.collectCallToSuperMember(
        superReceiver: KtSuperExpression,
        nonExtensionMembers: Iterable<Pair<KtType, KtCallableSymbol>>,
        context: WeighingContext,
        namesNeedDisambiguation: Set<Name>
    ) {
        val syntheticPropertyOrigins = mutableSetOf<KtFunctionSymbol>()
        nonExtensionMembers
            .onEach {
                if (it is KtSyntheticJavaPropertySymbol) {
                    syntheticPropertyOrigins.add(it.javaGetterSymbol)
                    syntheticPropertyOrigins.addIfNotNull(it.javaSetterSymbol)
                }
            }
            .forEach { (superType, callableSymbol) ->
                if (callableSymbol !in syntheticPropertyOrigins) {
                    // For basic completion, FE1.0 skips Java functions that are mapped to Kotlin properties.
                    addCallableSymbolToCompletion(
                        context,
                        callableSymbol,
                        CallableInsertionOptions(
                            detectImportStrategy(callableSymbol),
                            wrapWithDisambiguationIfNeeded(
                                getInsertionStrategy(callableSymbol),
                                superType,
                                callableSymbol,
                                namesNeedDisambiguation,
                                superReceiver
                            )
                        )
                    )
                }
            }
    }

    private fun getInsertionStrategy(symbol: KtCallableSymbol): CallableInsertionStrategy = when (symbol) {
        is KtFunctionLikeSymbol -> CallableInsertionStrategy.AsCall
        else -> CallableInsertionStrategy.AsIdentifier
    }

    private fun KtAnalysisSession.collectDelegateCallToSuperMember(
        context: WeighingContext,
        superReceiver: KtSuperExpression,
        nonExtensionMembers: Iterable<Pair<KtType, KtCallableSymbol>>,
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

        for ((superType, callableSymbol) in nonExtensionMembers) {
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
                    detectImportStrategy(callableSymbol),
                    wrapWithDisambiguationIfNeeded(
                        CallableInsertionStrategy.WithCallArgs(args),
                        superType,
                        callableSymbol,
                        namesNeedDisambiguation,
                        superReceiver
                    )
                ),
                priority = ItemPriority.SUPER_METHOD_WITH_ARGUMENTS
            )
        }
    }

    private fun wrapWithDisambiguationIfNeeded(
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