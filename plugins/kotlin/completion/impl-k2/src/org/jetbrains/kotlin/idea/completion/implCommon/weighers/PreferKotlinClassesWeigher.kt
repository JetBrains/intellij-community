// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.weighers

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.KotlinLookupElementWeigher
import org.jetbrains.kotlin.psi.KtElement

internal object PreferKotlinClassesWeigher: KotlinLookupElementWeigher("kotlin.preferKotlinClasses") {

    enum class Weight {
        KOTLIN,
        OTHER,
    }

    override fun weigh(element: LookupElement): Weight {
        return if (element.psiElement is KtElement) Weight.KOTLIN else Weight.OTHER
    }
}