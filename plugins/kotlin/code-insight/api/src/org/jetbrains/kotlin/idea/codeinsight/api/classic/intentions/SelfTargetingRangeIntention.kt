// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import java.util.function.Supplier

abstract class SelfTargetingRangeIntention<TElement : PsiElement>(
    elementType: Class<TElement>,
    textGetter: Supplier<@IntentionName String>,
    familyNameGetter: Supplier<@IntentionFamilyName String> = textGetter,
) : SelfTargetingIntention<TElement>(elementType, textGetter, familyNameGetter) {

    @Deprecated(
        "Replace with primary constructor",
        ReplaceWith("SelfTargetingRangeIntention<TElement>(elementType, { text }, { familyName })")
    )
    constructor(
        elementType: Class<TElement>,
        text: @IntentionName String,
        familyName: @IntentionFamilyName String = text,
    ) : this(elementType, Supplier { text }, Supplier { familyName })

    @Suppress("HardCodedStringLiteral")
    @Deprecated("Replace with primary constructor")
    constructor(
        elementType: Class<TElement>,
        textGetter: () -> @IntentionName String,
        familyNameGetter: () -> @IntentionFamilyName String = textGetter,
    ) : this(elementType, Supplier { textGetter() }, Supplier { familyNameGetter() })

    abstract fun applicabilityRange(element: TElement): TextRange?

    final override fun isApplicableTo(element: TElement, caretOffset: Int): Boolean {
        val range = applicabilityRange(element) ?: return false
        return range.containsOffset(caretOffset)
    }
}