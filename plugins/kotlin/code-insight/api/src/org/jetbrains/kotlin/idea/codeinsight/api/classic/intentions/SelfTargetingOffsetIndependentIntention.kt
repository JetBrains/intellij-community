// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.psi.KtElement

abstract class SelfTargetingOffsetIndependentIntention<TElement : KtElement>(
  elementType: Class<TElement>,
  textGetter: () -> @IntentionName String,
  familyNameGetter: () -> @IntentionFamilyName String = textGetter,
) : SelfTargetingRangeIntention<TElement>(elementType, textGetter, familyNameGetter) {
    abstract fun isApplicableTo(element: TElement): Boolean

    final override fun applicabilityRange(element: TElement): TextRange? {
        return if (isApplicableTo(element)) element.textRange else null
    }
}