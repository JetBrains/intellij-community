// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

internal object PreferFewerParametersWeigher: KotlinLookupElementWeigher("kotlin.preferFewerParameters"), KotlinSectionContextWeigher {

    private var LookupElement.parametersCount: Int
            by NotNullableUserDataProperty(Key("KOTLIN_PREFER_FEWER_PARAMETERS_WEIGHER"), 0)

    context(_: KaSession, sectionContext: K2CompletionSectionContext<*>)
    override fun addWeight(lookupElement: LookupElement, symbolWithOrigin: KtSymbolWithOrigin<*>?) {
        val symbol = symbolWithOrigin?.symbol as? KaCallableSymbol ?: return
        lookupElement.parametersCount = (symbol as? KaFunctionSymbol)?.valueParameters?.size ?: 0
    }

    override fun weigh(element: LookupElement): Int = element.parametersCount
}