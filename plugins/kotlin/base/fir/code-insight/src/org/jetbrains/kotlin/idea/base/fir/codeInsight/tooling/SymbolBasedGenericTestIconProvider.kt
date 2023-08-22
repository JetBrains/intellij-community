// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight.tooling

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinTestAvailabilityChecker
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

object SymbolBasedGenericTestIconProvider : AbstractGenericTestIconProvider() {
    override fun isKotlinTestDeclaration(declaration: KtNamedDeclaration): Boolean {
        return analyze(declaration) {
            val symbol = when (declaration) {
                is KtClassOrObject -> declaration.getClassOrObjectSymbol()
                is KtNamedFunction -> declaration.getSymbol()
                else -> null
            } ?: return false
            isTestDeclaration(symbol)
        }
    }

    context(KtAnalysisSession)
    private fun isTestDeclaration(symbol: KtAnnotatedSymbol): Boolean {
        return when {
            isIgnored(symbol) -> false
            (symbol as? KtSymbolWithVisibility)?.visibility != Visibilities.Public -> false
            symbol.hasAnnotation(KotlinTestAvailabilityChecker.TEST_FQ_NAME) -> true
            symbol is KtClassOrObjectSymbol -> symbol.getDeclaredMemberScope().getCallableSymbols().any { isTestDeclaration(it) }
            else -> false
        }
    }

    context(KtAnalysisSession)
    private tailrec fun isIgnored(symbol: KtAnnotatedSymbol): Boolean {
        if (symbol.hasAnnotation(KotlinTestAvailabilityChecker.IGNORE_FQ_NAME)) {
            return true
        }

        val containingSymbol = symbol.getContainingSymbol() as? KtClassOrObjectSymbol ?: return false
        return isIgnored(containingSymbol)
    }
}