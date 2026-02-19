// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtElement

@ApiStatus.Internal
object PreferKotlinClassesWeigher {
    const val WEIGHER_ID = "kotlin.preferKotlinClasses"

    enum class Weight {
        KOTLIN,
        OTHER,
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Weight {
            return if (element.psiElement is KtElement) Weight.KOTLIN else Weight.OTHER
        }
    }
}