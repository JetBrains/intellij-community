// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.components.KtScopeWithKind
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.idea.references.KtReference

/**
 * Resolves [reference] to symbol and returns static scope for the obtained symbol. Note that if the symbol is [KtTypeAliasSymbol],
 * `null` is returned. See KT-34281 for more details.
 */
context(KtAnalysisSession)
internal fun getStaticScopes(reference: KtReference): List<KtScopeWithKind> {
    val scopeIndex = CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX

    return reference.resolveToSymbols().mapNotNull { symbol ->
        when (symbol) {
            is KtSymbolWithMembers -> KtScopeWithKind(symbol.getStaticMemberScope(), KtScopeKind.StaticMemberScope(scopeIndex), token)
            is KtPackageSymbol -> KtScopeWithKind(symbol.getPackageScope(), KtScopeKind.PackageMemberScope(scopeIndex), token)
            else -> null
        }
    }
}

internal data class KtClassifierSymbolWithContainingScopeKind(
    private val _symbol: KtClassifierSymbol,
    val scopeKind: KtScopeKind
) : KtLifetimeOwner {
    override val token: KtLifetimeToken
        get() = _symbol.token
    val symbol: KtClassifierSymbol get() = withValidityAssertion { _symbol }
}

internal data class KtCallableSignatureWithContainingScopeKind(
    private val _signature: KtCallableSignature<*>,
    val scopeKind: KtScopeKind
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