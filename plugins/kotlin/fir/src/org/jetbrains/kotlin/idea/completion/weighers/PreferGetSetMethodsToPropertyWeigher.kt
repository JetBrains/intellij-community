/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.lookup.WeighingContext
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.UserDataProperty

internal object PreferGetSetMethodsToPropertyWeigher {
    const val WEIGHER_ID = "kotlin.preferGetSetMethodsToProperty"
    private var LookupElement.propertyName by UserDataProperty(Key<Name>("KOTLIN_PROPERTY_NAME"))

    fun KtAnalysisSession.addWeight(lookupElement: LookupElement, symbol: KtSymbol) {
        lookupElement.propertyName = (symbol as? KtPropertySymbol)?.name
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID, false, true) {
        override fun weigh(element: LookupElement, context: WeighingContext): Boolean {
            val propertyName = element.propertyName?.asString() ?: return false
            val prefixMatcher = context.itemMatcher(element)
            if (prefixMatcher.prefixMatches(propertyName)) return false
            val matchedLookupStrings = element.allLookupStrings.filter { prefixMatcher.prefixMatches(it) }
            return matchedLookupStrings.all { it.startsWith("get") || it.startsWith("set") }
        }
    }
}