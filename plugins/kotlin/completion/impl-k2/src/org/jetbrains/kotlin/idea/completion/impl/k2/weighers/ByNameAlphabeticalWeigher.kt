// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.KotlinLookupObject

internal object ByNameAlphabeticalWeigher: KotlinLookupElementWeigher("kotlin.byNameAlphabetical") {

    override fun weigh(element: LookupElement): String? = (element.`object` as? KotlinLookupObject)?.shortName?.asString()
}
