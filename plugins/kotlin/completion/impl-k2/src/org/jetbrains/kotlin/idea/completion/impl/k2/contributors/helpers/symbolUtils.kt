// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.idea.references.KtReference

internal fun KtAnalysisSession.getStaticScope(reference: KtReference): KtScope? =
    when (val symbol = reference.resolveToSymbol()) {
        is KtSymbolWithMembers -> symbol.getStaticMemberScope()
        is KtPackageSymbol -> symbol.getPackageScope()
        else -> null
    }

data class KtClassifierSymbolWithContainingScopeKind(
    private val _symbol: KtClassifierSymbol,
    val scopeKind: KtScopeKind?
): KtLifetimeOwner {
    override val token: KtLifetimeToken
        get() = _symbol.token
    val symbol: KtClassifierSymbol get() = withValidityAssertion { _symbol }
}

data class KtCallableSignatureWithContainingScopeKind(
    private val _signature: KtCallableSignature<*>,
    val scopeKind: KtScopeKind?
): KtLifetimeOwner {
    override val token: KtLifetimeToken
        get() = _signature.token
    val signature: KtCallableSignature<*> get() = withValidityAssertion { _signature }
}