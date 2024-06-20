// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.KtScopeWithKind
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithMembers
import org.jetbrains.kotlin.idea.references.KtReference

/**
 * Resolves [reference] to symbol and returns static scope for the obtained symbol.
 * Note that if the symbol is [org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol], `null` is returned.
 * See KT-34281 for more details.
 */
context(KaSession)
internal fun getStaticScopes(reference: KtReference): List<KtScopeWithKind> {
    val scopeIndex = CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX

    return reference.resolveToSymbols().mapNotNull { symbol ->
        when (symbol) {
            is KaSymbolWithMembers -> {
                val scope = if (symbol is KaNamedClassOrObjectSymbol && symbol.classKind.isObject) {
                    symbol.memberScope
                } else {
                    symbol.staticMemberScope
                }

                KtScopeWithKind(scope, KaScopeKind.StaticMemberScope(scopeIndex), token)
            }

            is KtPackageSymbol -> KtScopeWithKind(symbol.packageScope, KaScopeKind.PackageMemberScope(scopeIndex), token)
            else -> null
        }
    }
}

internal data class KaClassifierSymbolWithContainingScopeKind(
    private val _symbol: KaClassifierSymbol,
    val scopeKind: KaScopeKind
) : KtLifetimeOwner {
    override val token: KtLifetimeToken
        get() = _symbol.token
    val symbol: KaClassifierSymbol get() = withValidityAssertion { _symbol }
}

internal data class KtCallableSignatureWithContainingScopeKind(
    private val _signature: KtCallableSignature<*>,
    val scopeKind: KaScopeKind
) : KtLifetimeOwner {
    override val token: KtLifetimeToken
        get() = _signature.token
    val signature: KtCallableSignature<*> get() = withValidityAssertion { _signature }
}

internal data class KtSymbolWithOrigin(
    private val _symbol: KtSymbol,
    val origin: CompletionSymbolOrigin,
) : KtLifetimeOwner {
    override val token: KtLifetimeToken
        get() = _symbol.token
    val symbol: KtSymbol get() = withValidityAssertion { _symbol }
}