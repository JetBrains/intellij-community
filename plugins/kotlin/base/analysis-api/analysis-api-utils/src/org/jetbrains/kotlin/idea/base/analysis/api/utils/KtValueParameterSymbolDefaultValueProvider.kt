// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter


context(KtAnalysisSession)
val KtValueParameterSymbol.defaultValue: KtExpression?
    get() = KtValueParameterSymbolDefaultValueProvider.getDefaultParameterValue(this)

private object KtValueParameterSymbolDefaultValueProvider {

    context(KtAnalysisSession)
    fun getDefaultParameterValue(parameterSymbol: KtValueParameterSymbol): KtExpression? {
        if (!parameterSymbol.hasDefaultValue) return null
        return sequence {
            yield(parameterSymbol)
            yieldAll(parameterSymbol.getAllOverriddenSymbols().filterIsInstance<KtValueParameterSymbol>())
        }.firstNotNullOfOrNull { parameter ->
            val ktParameter = parameter.psi as? KtParameter ?: return@firstNotNullOfOrNull null
            (ktParameter.navigationElement as? KtParameter ?: ktParameter).defaultValue
        }
    }

}