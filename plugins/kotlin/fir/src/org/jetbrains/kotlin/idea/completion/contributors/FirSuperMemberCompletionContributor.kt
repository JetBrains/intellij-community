// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.collectNonExtensions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.detectImportStrategy
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperExpression

internal class FirSuperMemberCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCompletionContributorBase<FirNameReferencePositionContext>(basicContext, priority) {
    override fun KtAnalysisSession.complete(positionContext: FirNameReferencePositionContext) = with(positionContext) {
        val superReceiver = explicitReceiver as? KtSuperExpression ?: return
        val expectedType = nameExpression.getExpectedType()
        val visibilityChecker = CompletionVisibilityChecker.create(basicContext, positionContext)
        val possibleReceiverScope = superReceiver.getKtType()?.getTypeScope() ?: return
        val nonExtensionMembers = collectNonExtensions(possibleReceiverScope, visibilityChecker, scopeNameFilter)
        collectDelegateCallToSuperMember(superReceiver, nonExtensionMembers, expectedType)
    }

    private fun KtAnalysisSession.collectDelegateCallToSuperMember(
        superReceiver: KtSuperExpression,
        nonExtensionMembers: Sequence<KtCallableSymbol>,
        expectedType: KtType?
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

        for (callableSymbol in nonExtensionMembers) {
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
                expectedType,
                callableSymbol,
                CallableInsertionOptions(
                    detectImportStrategy(callableSymbol),
                    CallableInsertionStrategy.WithCallArgs(args)
                ),
                ItemPriority.SUPER_METHOD_WITH_ARGUMENTS
            )
        }
    }
}