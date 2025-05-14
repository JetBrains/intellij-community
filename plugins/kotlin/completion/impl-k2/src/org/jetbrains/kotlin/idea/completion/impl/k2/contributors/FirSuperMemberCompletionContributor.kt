// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtOutsideTowerScopeKinds
import org.jetbrains.kotlin.idea.completion.contributors.helpers.collectNonExtensionsForType
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.priority
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSuperReceiverNameReferencePositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperExpression

internal class FirSuperMemberCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinSuperReceiverNameReferencePositionContext>(parameters, sink, priority) {

    private data class CallableInfo(
        private val _type: KaType,
        private val _signature: KaCallableSignature<*>,
    ) : KaLifetimeOwner {

        val scopeKind: KaScopeKind
            get() = KtOutsideTowerScopeKinds.TypeScope

        override val token: KaLifetimeToken
            get() = _signature.token

        val type: KaType
            get() = withValidityAssertion { _type }

        val signature: KaCallableSignature<*>
            get() = withValidityAssertion { _signature }
    }

    context(KaSession)
    override fun complete(
        positionContext: KotlinSuperReceiverNameReferencePositionContext,
        weighingContext: WeighingContext,
    ) {
        val superReceiver = positionContext.explicitReceiver
        val superType = superReceiver.expressionType ?: return

        val (nonExtensionMembers: Iterable<CallableInfo>, namesNeedDisambiguation: Set<Name>) =
            if (superType !is KaIntersectionType) {
                getNonExtensionsMemberSymbols(positionContext, superType).asIterable() to emptySet()
            } else {
                getSymbolsAndNamesNeedDisambiguation(positionContext, superType.conjuncts)
            }

        nonExtensionMembers.flatMap {
            collectCallToSuperMember(
                superReceiver = superReceiver,
                callableInfo = it,
                context = weighingContext,
                namesNeedDisambiguation = namesNeedDisambiguation,
            )
        }.forEach(sink::addElement)

        collectDelegateCallToSuperMember(
            context = weighingContext,
            superReceiver = superReceiver,
            nonExtensionMembers = nonExtensionMembers,
            namesNeedDisambiguation = namesNeedDisambiguation,
        ).forEach(sink::addElement)
    }

    context(KaSession)
    private fun getSymbolsAndNamesNeedDisambiguation(
        positionContext: KotlinNameReferencePositionContext,
        superTypes: List<KaType>,
    ): Pair<List<CallableInfo>, Set<Name>> {
        val allSymbols = mutableListOf<CallableInfo>()
        val symbolsInAny = mutableSetOf<KaCallableSymbol>()
        val symbolCountsByName = mutableMapOf<Name, Int>()
        for (superType in superTypes) {
            for (callableInfo in getNonExtensionsMemberSymbols(positionContext, superType)) {
                val symbol = callableInfo.signature.symbol

                // Abstract symbol does not participate completion.
                if (symbol.modality == KaSymbolModality.ABSTRACT) continue

                // Unlike typical diamond cases, calls to method of `Any` always do not need extra qualification.
                if (symbol.callableId?.classId == StandardClassIds.Any) {
                    if (symbol in symbolsInAny) continue
                    symbolsInAny.add(symbol)
                }

                allSymbols.add(callableInfo.copy(superType))
                val name = callableInfo.signature.callableId?.callableName ?: continue
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

    context(KaSession)
    private fun getNonExtensionsMemberSymbols(
        positionContext: KotlinNameReferencePositionContext,
        receiverType: KaType,
    ): Sequence<CallableInfo> = collectNonExtensionsForType(
        parameters = parameters,
        positionContext = positionContext,
        receiverType = receiverType,
        visibilityChecker = visibilityChecker,
        scopeNameFilter = scopeNameFilter,
    ).map {
        CallableInfo(
            _type = receiverType,
            _signature = it,
        )
    }

    context(KaSession)
    private fun collectCallToSuperMember(
        superReceiver: KtSuperExpression,
        callableInfo: CallableInfo,
        context: WeighingContext,
        namesNeedDisambiguation: Set<Name>
    ): Sequence<LookupElement> {
        val signature = callableInfo.signature
        return createCallableLookupElements(
            context = context,
            signature = signature,
            options = CallableInsertionOptions(
                importStrategyDetector.detectImportStrategyForCallableSymbol(signature.symbol),
                wrapWithDisambiguationIfNeeded(
                    insertionStrategy = getInsertionStrategy(signature),
                    superType = callableInfo.type,
                    callableSignature = signature,
                    namesNeedDisambiguation = namesNeedDisambiguation,
                    superReceiver = superReceiver,
                )
            ),
            scopeKind = callableInfo.scopeKind,
            withTrailingLambda = true,
        )
    }

    context(KaSession)
    private fun getInsertionStrategy(signature: KaCallableSignature<*>): CallableInsertionStrategy = when (signature) {
        is KaFunctionSignature<*> -> CallableInsertionStrategy.AsCall
        else -> CallableInsertionStrategy.AsIdentifier
    }

    context(KaSession)
    private fun collectDelegateCallToSuperMember(
        context: WeighingContext,
        superReceiver: KtSuperExpression,
        nonExtensionMembers: Iterable<CallableInfo>,
        namesNeedDisambiguation: Set<Name>
    ): Sequence<LookupElement> {
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
            .map { getOriginalDeclarationOrSelf(it, originalKtFile) }
            .flatMap { containingFunction ->
                containingFunction
                    .symbol
                    .allOverriddenSymbols
                    .map { superFunctionSymbol ->
                        superFunctionSymbol to containingFunction
                    }
            }.toMap()

        if (superFunctionToContainingFunction.isEmpty()) return emptySequence()

        return sequence {
            for (callableInfo in nonExtensionMembers) {
                val signature = callableInfo.signature
                val matchedContainingFunction = superFunctionToContainingFunction[callableInfo.signature.symbol] ?: continue
                if (signature !is KaFunctionSignature<*>) continue
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

                val elements = createCallableLookupElements(
                    context = context,
                    signature = signature,
                    options = CallableInsertionOptions(
                        importStrategyDetector.detectImportStrategyForCallableSymbol(callableInfo.signature.symbol),
                        wrapWithDisambiguationIfNeeded(
                            CallableInsertionStrategy.WithCallArgs(args),
                            callableInfo.type,
                            callableInfo.signature,
                            namesNeedDisambiguation,
                            superReceiver,
                        )
                    ),
                    scopeKind = callableInfo.scopeKind,
                )

                for (element in elements) {
                    element.priority = ItemPriority.SUPER_METHOD_WITH_ARGUMENTS
                    yield(element)
                }
            }
        }
    }

    context(KaSession)
    private fun wrapWithDisambiguationIfNeeded(
        insertionStrategy: CallableInsertionStrategy,
        superType: KaType,
        callableSignature: KaCallableSignature<*>,
        namesNeedDisambiguation: Set<Name>,
        superReceiver: KtSuperExpression
    ): CallableInsertionStrategy {
        val superClassId = (superType as? KaUsualClassType)?.classId
        val needDisambiguation = callableSignature.callableId?.callableName in namesNeedDisambiguation
        return if (needDisambiguation && superClassId != null) {
            CallableInsertionStrategy.WithSuperDisambiguation(superReceiver.createSmartPointer(), superClassId, insertionStrategy)
        } else {
            insertionStrategy
        }
    }
}