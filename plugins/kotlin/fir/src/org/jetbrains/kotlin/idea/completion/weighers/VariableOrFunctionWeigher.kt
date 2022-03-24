/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.psi.UserDataProperty

object VariableOrFunctionWeigher {
    private enum class Weight {
        VARIABLE,
        FUNCTION
    }

    const val WEIGHER_ID = "kotlin.variableOrFunction"
    private var LookupElement.variableOrFunction by UserDataProperty(Key<Weight>("KOTLIN_VARIABLE_OR_FUNCTION_WEIGHT"))

    fun KtAnalysisSession.addWeight(lookupElement: LookupElement, symbol: KtSymbol) {
        when (symbol) {
            is KtVariableLikeSymbol -> {
                lookupElement.variableOrFunction = Weight.VARIABLE
            }
            is KtFunctionLikeSymbol -> {
                lookupElement.variableOrFunction = Weight.FUNCTION
            }
            else -> {
            }
        }
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*>? = element.variableOrFunction
    }
}