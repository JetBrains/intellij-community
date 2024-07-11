// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.KaScopeKinds
import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKindImpl
import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKind
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithMembers
import org.jetbrains.kotlin.idea.references.KtReference

/**
 * Resolves [reference] to symbol and returns static scope for the obtained symbol.
 * Note that if the symbol is [org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol], `null` is returned.
 * See KT-34281 for more details.
 */
context(KaSession)
internal fun getStaticScopes(reference: KtReference): List<KaScopeWithKind> {
    val scopeIndex = CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX

    return reference.resolveToSymbols().mapNotNull { symbol ->
        when (symbol) {
            is KaSymbolWithMembers -> {
                val scope = if (symbol is KaNamedClassSymbol && symbol.classKind.isObject) {
                    symbol.memberScope
                } else {
                    symbol.staticMemberScope
                }

                KaScopeWithKindImpl(scope, KaScopeKinds.StaticMemberScope(scopeIndex))
            }

            is KaPackageSymbol -> KaScopeWithKindImpl(symbol.packageScope, KaScopeKinds.PackageMemberScope(scopeIndex))
            else -> null
        }
    }
}

internal data class KaClassifierSymbolWithContainingScopeKind(
    private val _symbol: KaClassifierSymbol,
    val scopeKind: KaScopeKind
) : KaLifetimeOwner {
    override val token: KaLifetimeToken
        get() = _symbol.token
    val symbol: KaClassifierSymbol get() = withValidityAssertion { _symbol }
}

internal data class KtCallableSignatureWithContainingScopeKind(
    private val _signature: KaCallableSignature<*>,
    val scopeKind: KaScopeKind
) : KaLifetimeOwner {
    override val token: KaLifetimeToken
        get() = _signature.token
    val signature: KaCallableSignature<*> get() = withValidityAssertion { _signature }
}

internal data class KtSymbolWithOrigin(
    private val _symbol: KaSymbol,
    val origin: CompletionSymbolOrigin,
) : KaLifetimeOwner {
    override val token: KaLifetimeToken
        get() = _symbol.token
    val symbol: KaSymbol get() = withValidityAssertion { _symbol }
}