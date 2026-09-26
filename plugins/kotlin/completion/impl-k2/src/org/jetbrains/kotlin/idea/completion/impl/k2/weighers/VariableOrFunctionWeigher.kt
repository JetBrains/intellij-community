/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.psi.UserDataProperty

internal object VariableOrFunctionWeigher: KotlinLookupElementWeigher("kotlin.variableOrFunction"), KotlinSectionContextWeigher {

    private enum class Weight {
        VARIABLE,
        FUNCTION
    }

    private var LookupElement.variableOrFunction by UserDataProperty(Key<Weight>("KOTLIN_VARIABLE_OR_FUNCTION_WEIGHT"))

    context(_: KaSession, sectionContext: K2CompletionSectionContext<*>)
    override fun addWeight(lookupElement: LookupElement, symbolWithOrigin: KtSymbolWithOrigin<*>?) {
        when (symbolWithOrigin?.symbol) {
            is KaVariableSymbol -> {
                lookupElement.variableOrFunction = Weight.VARIABLE
            }
            is KaFunctionSymbol -> {
                lookupElement.variableOrFunction = Weight.FUNCTION
            }
            else -> {
            }
        }
    }

    override fun weigh(element: LookupElement): Comparable<*>? = element.variableOrFunction
}