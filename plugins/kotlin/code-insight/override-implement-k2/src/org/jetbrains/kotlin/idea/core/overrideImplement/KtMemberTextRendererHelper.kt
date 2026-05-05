// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.overrideImplement

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaNonPublicApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.fakeOverrideOriginal
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtCallableDeclaration

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
internal fun renderMemberText(symbol: KaCallableSymbol, renderer: KaDeclarationRenderer = KtGenerateMembersHandler.renderer): String {
    if (symbol.returnType is KaErrorType) {
        val psiReturnType = (symbol.fakeOverrideOriginal.psi as? KtCallableDeclaration)?.typeReference?.text
        if (psiReturnType != null) {
            return symbol.render(renderer.withTypeFilter(symbol)) + ": $psiReturnType"
        }
    }
    return symbol.render(renderer)
}

@OptIn(KaExperimentalApi::class)
private fun KaDeclarationRenderer.withTypeFilter(targetSymbol: KaCallableSymbol) = this.with {
    returnTypeFilter = object : KaCallableReturnTypeFilter {
        override fun shouldRenderReturnType(analysisSession: KaSession, type: KaType, symbol: KaCallableSymbol): Boolean =
            symbol !== targetSymbol
    }
}
