// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight.tooling

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithVisibility
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinTestAvailabilityChecker
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal object SymbolBasedGenericTestIconProvider : AbstractGenericTestIconProvider() {

    override fun isKotlinTestDeclaration(declaration: KtNamedDeclaration): Boolean {
        return analyze(declaration) {
            val symbol = when (declaration) {
                is KtClassOrObject -> declaration.getClassOrObjectSymbol()
                is KtNamedFunction -> declaration.symbol
                else -> null
            } ?: return false
            isTestDeclaration(symbol)
        }
    }

    context(KaSession)
    private fun isTestDeclaration(symbol: KaAnnotatedSymbol): Boolean {
        return when {
            isIgnored(symbol) -> false
            (symbol as? KaSymbolWithVisibility)?.visibility != KaSymbolVisibility.PUBLIC -> false
            symbol.hasAnnotation(KotlinTestAvailabilityChecker.TEST_FQ_NAME) -> true
            symbol is KaClassSymbol -> symbol.declaredMemberScope.getCallableSymbols().any { isTestDeclaration(it) }
            else -> false
        }
    }

    context(KaSession)
    private tailrec fun isIgnored(symbol: KaAnnotatedSymbol): Boolean {
        if (symbol.hasAnnotation(KotlinTestAvailabilityChecker.IGNORE_FQ_NAME)) {
            return true
        }

        val containingSymbol = symbol.containingDeclaration as? KaClassSymbol ?: return false
        return isIgnored(containingSymbol)
    }
}