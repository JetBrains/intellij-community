// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight.tooling

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinTestAvailabilityChecker
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal object SymbolBasedGenericTestIconProvider : AbstractGenericTestIconProvider() {

    override fun isKotlinTestDeclaration(declaration: KtNamedDeclaration): Boolean {
        return analyze(declaration) {
            val symbol = when (declaration) {
                is KtClassOrObject -> declaration.classSymbol
                is KtNamedFunction -> declaration.symbol
                else -> null
            } ?: return false
            isTestDeclaration(symbol)
        }
    }

    context(_: KaSession)
    private fun isTestDeclaration(symbol: KaAnnotatedSymbol): Boolean {
        return when {
            isIgnored(symbol) -> false
            (symbol as? KaDeclarationSymbol)?.visibility != KaSymbolVisibility.PUBLIC -> false
            KotlinTestAvailabilityChecker.TEST_FQ_NAME in symbol.annotations -> true
            symbol is KaClassSymbol -> symbol.declaredMemberScope.callables.any { isTestDeclaration(it) }
            else -> false
        }
    }

    context(_: KaSession)
    private tailrec fun isIgnored(symbol: KaAnnotatedSymbol): Boolean {
        if (KotlinTestAvailabilityChecker.IGNORE_FQ_NAME in symbol.annotations) {
            return true
        }

        val containingSymbol = symbol.containingDeclaration as? KaClassSymbol ?: return false
        return isIgnored(containingSymbol)
    }
}