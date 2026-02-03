// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject

object ByNameAlphabeticalWeigher {
    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): String? = (element.`object` as? KotlinLookupObject)?.shortName?.asString()
    }

    const val WEIGHER_ID = "kotlin.byNameAlphabetical"
}