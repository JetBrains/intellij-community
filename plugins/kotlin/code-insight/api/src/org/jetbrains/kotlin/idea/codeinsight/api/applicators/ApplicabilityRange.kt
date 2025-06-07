// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.textRangeIn

/**
 * The ranges are relative to the passed element,
 *  i.e., if range covers the whole element when it should return `[0, element.length)`.
 *
 *  See [org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges] for the commonly occurring applicability ranges.
 */
object ApplicabilityRange {

    fun self(element: PsiElement): List<TextRange> =
        single(element) { it }

    inline fun <E : PsiElement> single(
        element: E,
        function: (E) -> PsiElement?,
    ): List<TextRange> = multiple(element) { listOfNotNull(function(element)) }

    inline fun <E : PsiElement> multiple(
        element: E,
        function: (E) -> List<PsiElement>,
    ): List<TextRange> = function(element).map { it.textRangeIn(element) }

    inline fun <E : PsiElement> union(
        element: E,
        function: (E) -> List<PsiElement>,
    ): List<TextRange> {
        val ranges = multiple(element, function)
        val commonUnion = ranges.reduceOrNull(TextRange::union)
        return listOfNotNull(commonUnion)
    }
}

