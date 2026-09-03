// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.WeighingContext as PlatformWeighingContext
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.UserDataProperty

internal object PreferGetSetMethodsToPropertyWeigher: KotlinLookupElementWeigher(id = "kotlin.preferGetSetMethodsToProperty", dependsOnPrefix = true),
    KotlinSectionContextWeigher {

    private var LookupElement.propertyName by UserDataProperty(Key<Name>("KOTLIN_PROPERTY_NAME"))

    context(_: KaSession, _: K2CompletionSectionContext<*>)
    override fun addWeight(lookupElement: LookupElement, symbolWithOrigin: KtSymbolWithOrigin<*>?) {
        val symbol = symbolWithOrigin?.symbol ?: return
        lookupElement.propertyName = (symbol as? KaPropertySymbol)?.name
    }

    override fun weigh(element: LookupElement, context: PlatformWeighingContext): Boolean {
        val propertyName = element.propertyName?.asString() ?: return false
        val prefixMatcher = context.itemMatcher(element)
        if (prefixMatcher.prefixMatches(propertyName)) return false
        val matchedLookupStrings = element.allLookupStrings.filter { prefixMatcher.prefixMatches(it) }
        return matchedLookupStrings.all { it.startsWith("get") || it.startsWith("set") }
    }
}