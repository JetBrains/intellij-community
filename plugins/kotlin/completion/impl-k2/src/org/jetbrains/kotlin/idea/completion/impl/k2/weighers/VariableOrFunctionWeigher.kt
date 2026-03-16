/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.psi.UserDataProperty

object VariableOrFunctionWeigher {
    private enum class Weight {
        VARIABLE,
        FUNCTION
    }

    const val WEIGHER_ID = "kotlin.variableOrFunction"
    private var LookupElement.variableOrFunction by UserDataProperty(Key<Weight>("KOTLIN_VARIABLE_OR_FUNCTION_WEIGHT"))

    context(_: KaSession)
fun addWeight(lookupElement: LookupElement, symbol: KaSymbol) {
        when (symbol) {
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

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*>? = element.variableOrFunction
    }
}