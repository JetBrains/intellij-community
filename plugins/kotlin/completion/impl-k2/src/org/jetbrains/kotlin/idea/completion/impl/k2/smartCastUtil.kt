// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.KaSmartCastSource
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.variable
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionCall
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionSymbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

/**
 * Returns the intersection of [originalType] and the applicable smart cast types for [symbol].
 * The [scopeKind] is used to select the implicit receiver used in case of multiple matches.
 * Returns null if no smart cast applies in [sectionContext].
 *
 * Note that intersection types should not be displayed to the user by default.
 *
 * Note: Receiver aliases are not tracked when matching smart casts.
 * Smart completion omits `value` in this example, although `alias.value` is valid as a `String`.
 * ```kotlin
 * class Box(val value: Any)
 *
 * fun test(box: Box): String {
 *     val alias = box
 *     if (box.value is String) {
 *         return alias.val<caret>
 *     }
 *     return ""
 * }
 * ```
 */
context(session: KaSession, sectionContext: K2CompletionSectionContext<*>)
internal fun smartCastTypeForSymbol(symbol: KaSymbol?, originalType: KaType, scopeKind: KaScopeKind?): KaType? {
    if (symbol !is KaVariableSymbol) return null
    val positionContext = sectionContext.positionContext as? KotlinNameReferencePositionContext ?: return null
    val receiverIndex = if (positionContext.explicitReceiver == null) {
        (scopeKind as? KaScopeKind.TypeScope)?.indexInTower
    } else null
    val smartCastType = sectionContext.smartCastTypeMap[SmartCastKey(symbol, receiverIndex)] ?: return null

    return session.typeCreator.intersectionType {
        conjunct(originalType)
        conjunct(smartCastType)
    }
}

/**
 * Stores smart cast types by symbol and implicit receiver within the completion section.
 */
private val K2CompletionSectionContext<*>.smartCastTypeMap: Map<SmartCastKey, KaType> by LazyCompletionSessionProperty {
    val sectionContext = contextOf<K2CompletionSectionContext<KotlinRawPositionContext>>()
    val session = contextOf<KaSession>()

    val scopeContext = sectionContext.weighingContext.scopeContext
    if (scopeContext.possibleSmartCasts.isEmpty()) return@LazyCompletionSessionProperty emptyMap()

    val nameRefPositionContext = sectionContext.positionContext as? KotlinNameReferencePositionContext
        ?: return@LazyCompletionSessionProperty emptyMap()
    val hasExplicitReceiver = nameRefPositionContext.explicitReceiver != null
    var resolvedExplicitReceiverChain = nameRefPositionContext.resolveExplicitReceiverChain()
    // Error in receiver chain, probably red code
    if (resolvedExplicitReceiverChain.any { it == null }) return@LazyCompletionSessionProperty emptyMap()
    resolvedExplicitReceiverChain = resolvedExplicitReceiverChain.filterNotNull()

    val implicitReceiverIndices = scopeContext.implicitReceivers.associate {
        val owner = it.ownerSymbol
        val symbol = if (owner is KaCallableSymbol) {
            owner.receiverParameter ?: owner
        } else owner
        symbol to it.scopeIndexInTower
    }

    val map = mutableMapOf<SmartCastKey, MutableSet<KaType>>()
    for (smartCast in scopeContext.possibleSmartCasts) {
        // Extension receivers cannot lead to stable smart casts, but we make sure not to include them here
        // explicitly anyway.
        if (!smartCast.isStable || smartCast.source.extensionReceiver != null) continue
        val source = smartCast.source

        val dispatchReceiverChain = source.toDispatchReceiverChain()

        val implicitReceiverIndex = if (!hasExplicitReceiver && dispatchReceiverChain.isNotEmpty()) {
            // If there is no explicit receiver, the `dispatchReceiverChain` should be exactly
            // the single implicit receiver used for the smart cast.
            val implicitReceiver = dispatchReceiverChain.singleOrNull() ?: continue
            implicitReceiverIndices[implicitReceiver] ?: continue
        } else if (dispatchReceiverChain != resolvedExplicitReceiverChain) {
            // We have an explicit receiver, but the chain does not match the resolved chain,
            // so the smart cast is not applicable.
            continue
        } else null

        val currentEntries = map.getOrPut(SmartCastKey(source.symbol, implicitReceiverIndex)) { mutableSetOf() }
        currentEntries.addAll(smartCast.smartCastTypes)
    }
    map.mapValues {
        session.typeCreator.intersectionType {
            conjuncts(it.value)
        }
    }
}

private data class SmartCastKey(val symbol: KaSymbol, val receiverIndex: Int?)

/**
 * Resolves the receiver chain of this context, starting with explicit receivers and followed by its implicit receiver if present.
 * For example:
 * ```kotlin
 * val a = someVar.access.otherAccess.value.<caret>
 * ```
 * Returns the symbols for `value`, `otherAccess`, `access`, and `someVar`, followed by the implicit receiver of `someVar`, if present.
 * Returns an empty list if there is no explicit receiver for this position context.
 */
context(_: KaSession)
private fun KotlinNameReferencePositionContext.resolveExplicitReceiverChain(): List<KaSymbol?> = buildList {
    // First, resolve all explicit receivers
    var receiver: KtExpression? = explicitReceiver as? KtExpression ?: return@buildList
    while (receiver != null) {
        if (receiver is KtParenthesizedExpression) {
            receiver = receiver.expression
            continue
        }

        // Call expressions cannot be used in stable smart casts
        val resolvedReceiver = receiver.takeIf { it !is KtCallExpression }?.resolveSuccessfulExpressionSymbol()
        // We stop at a package symbol because it is not a real receiver. There are also no implicit receivers
        // after it.
        if (resolvedReceiver is KaPackageSymbol) return@buildList
        add(resolvedReceiver)
        receiver = (receiver as? KtQualifiedExpression)?.receiverExpression ?: break
    }

    // Then check for the presence of any implicit receiver
    val dispatchReceiver = receiver?.resolveSuccessfulExpressionCall()?.variable?.dispatchReceiver?.unwrapSmartCasts()
    if (dispatchReceiver is KaImplicitReceiverValue) {
        add(dispatchReceiver.symbol)
    }
}

/**
 * A smart cast source can be a chain of multiple symbols that all need to be present
 * (either explicitly or implicitly) and returns a linearized list of the symbols.
 * Excludes this source’s own symbol.
 *
 * Note that [KaSmartCastSource]s with `extensionReceiver` cannot participate in stable smart-casts,
 * so we ignore them and assume they are always null.
 */
private fun KaSmartCastSource.toDispatchReceiverChain(): List<KaSymbol> = buildList {
    var current: KaSmartCastSource? = dispatchReceiver
    while (current != null) {
        add(current.symbol)
        current = current.dispatchReceiver
    }
}
