// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin

internal abstract class KotlinLookupElementWeigher(
    val id: String,
    negated: Boolean = false,
    dependsOnPrefix: Boolean = false,
) : LookupElementWeigher(id, negated, dependsOnPrefix)

internal interface KotlinSectionContextWeigher {

    context(_: KaSession, sectionContext: K2CompletionSectionContext<*>)
    fun addWeight(lookupElement: LookupElement, symbolWithOrigin: KtSymbolWithOrigin<*>? = null)
}
