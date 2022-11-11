// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisAllowanceManager
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.psi.KtElement

/**
 * Provide list of ranges on which [KotlinApplicator] is available
 *
 * It should not do some additional checks to verify that  [KotlinApplicator] is applicable
 * as it is responsibility of [KotlinApplicator.isApplicableByPsi]
 *
 * No resolve operations should be called inside [getApplicabilityRanges]
 * I.e no [org.jetbrains.kotlin.analysis.api.KtAnalysisSession] or [PsiElement] resolve can be used inside
 *
 * [getApplicabilityRanges] is guarantied to be called inside read action
 */
@FileModifier.SafeTypeForPreview
sealed class KotlinApplicabilityRange<in ELEMENT : PsiElement> {
    /**
     * Return the list of ranges on which [KotlinApplicator] is available
     *
     * The ranges are relative to [element]
     *  i.e. if range covers the whole element when it should return `[0, element.length)`
     */
    fun getApplicabilityRanges(element: ELEMENT): List<TextRange> = KtAnalysisAllowanceManager.forbidAnalysisInside("getApplicabilityRanges") {
        getApplicabilityRangesImpl(element)
    }

    protected abstract fun getApplicabilityRangesImpl(element: ELEMENT): List<TextRange>
}

private class KotlinApplicabilityRangeImpl<ELEMENT : PsiElement>(
    private val getApplicabilityRanges: (ELEMENT) -> List<TextRange>,
) : KotlinApplicabilityRange<ELEMENT>() {
    override fun getApplicabilityRangesImpl(element: ELEMENT): List<TextRange> =
        getApplicabilityRanges.invoke(element)
}

/**
 * Create [KotlinApplicabilityRange] by list of possible ranges
 * [getRanges] should return `empty list if no applicability ranges found

 * [getRanges] should return ranges relative to passed [ELEMENT]
 * i.e. if range covers the whole element when it should return `[0, element.length)`
 *
 * No resolve operations should be called inside [getRanges]
 * I.e no [KtAnalyisSession] or [PsiElement] resolve can be used inside
 *
 * [getRanges] is guarantied to be called inside read action
 *
 * @see applicabilityRange
 * @see applicabilityTarget
 */
fun <ELEMENT : KtElement> applicabilityRanges(
    getRanges: (ELEMENT) -> List<TextRange>
): KotlinApplicabilityRange<ELEMENT> =
    KotlinApplicabilityRangeImpl(getRanges)

/**
 * Create [KotlinApplicabilityRange] with a single applicability range
 * [getRange] should return `null` if no applicability ranges found
 *
 * No resolve operations should be called inside [getRanges]
 * I.e no [KtAnalyisSession] or [PsiElement] resolve can be used inside
 *
 * [getRange] should return range relative to passed [ELEMENT]
 * i.e. if range covers the whole element when it should return `[0, element.length)`
 *
 * [getRange] is guarantied to be called inside read action
 *
 * @see applicabilityRanges
 * @see applicabilityTarget
 */
fun <ELEMENT : KtElement> applicabilityRange(
    getRange: (ELEMENT) -> TextRange?
): KotlinApplicabilityRange<ELEMENT> =
    KotlinApplicabilityRangeImpl { listOfNotNull(getRange(it)) }

/**
 * Create [KotlinApplicabilityRange] with a single applicability range represented by [PsiElement]
 * [getTarget] should return [PsiElement] which range will be used or `null` if no applicability ranges found
 *
 * No resolve operations should be called inside [getTarget]
 * I.e no [KtAnalyisSession] or [PsiElement] resolve can be used inside
 *
 * [getTarget] should return element inside the element passed
 *
 * [getTarget] is guarantied to be called inside read action
 *
 * @see applicabilityRanges
 * @see applicabilityTarget
 */
fun <ELEMENT : PsiElement> applicabilityTarget(
    getTarget: (ELEMENT) -> PsiElement?
): KotlinApplicabilityRange<ELEMENT> =
    KotlinApplicabilityRangeImpl { element ->
        when (val target = getTarget(element)) {
            null -> emptyList()
            element -> listOf(TextRange(0, element.textLength))
            else -> listOf(target.textRangeIn(element))
        }
    }

