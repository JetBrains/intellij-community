// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter


context(_: KaSession)
val KaValueParameterSymbol.defaultValue: KtExpression?
    get() = KtValueParameterSymbolDefaultValueProvider.getDefaultParameterValue(this)

private object KtValueParameterSymbolDefaultValueProvider {

    context(_: KaSession)
    fun getDefaultParameterValue(parameterSymbol: KaValueParameterSymbol): KtExpression? {
        if (!parameterSymbol.hasDefaultValue) return null
        return parameterSymbol.allOverriddenSymbolsWithSelf
            .filterIsInstance<KaValueParameterSymbol>()
            .firstNotNullOfOrNull { parameter ->
                val ktParameter = parameter.psi as? KtParameter ?: return@firstNotNullOfOrNull null
                (ktParameter.navigationElement as? KtParameter ?: ktParameter).defaultValue
            }
    }

}