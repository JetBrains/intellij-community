// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTarget
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import kotlin.reflect.KClass

/**
 * [KotlinApplicableIntentionBase] is a base implementation for [KotlinApplicableIntention] and [KotlinApplicableIntentionWithContext].
 *
 * Note: A [familyNameGetter] for [SelfTargetingIntention] does not have to be set because inheritors of [KotlinApplicableIntentionBase]
 * must override [getFamilyName].
 */
sealed class KotlinApplicableIntentionBase<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>,
) : SelfTargetingIntention<ELEMENT>(elementType.java, { "" }) {
    /**
     * @see com.intellij.codeInsight.intention.IntentionAction.getFamilyName
     */
    abstract override fun getFamilyName(): @IntentionFamilyName String

    /**
     * The [KotlinApplicabilityRange] determines whether the intention is available in a range *after* [isApplicableByPsi] has been checked.
     *
     * The default applicability range is equivalent to `ApplicabilityRanges.SELF`. Configuration of the applicability range might be as
     * simple as choosing an existing one from `ApplicabilityRanges`.
     */
    open fun getApplicabilityRange(): KotlinApplicabilityRange<ELEMENT> = applicabilityTarget { it }

    /**
     * Whether this intention is applicable to [element] by PSI only. May not use the Analysis API due to performance concerns.
     */
    abstract fun isApplicableByPsi(element: ELEMENT): Boolean

    /**
     * Checks the intention's applicability based on [isApplicableByPsi] and [KotlinApplicabilityRange]. An override must invoke
     * [setTextGetter] to configure the action name.
     */
    override fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean {
        if (!isApplicableByPsi(element)) return false
        val ranges = getApplicabilityRange().getApplicabilityRanges(element)
        if (ranges.isEmpty()) return false

        // A KotlinApplicabilityRange should be relative to the element, while `caretOffset` is absolute.
        val relativeCaretOffset = caretOffset - element.startOffset
        return ranges.any { it.containsOffset(relativeCaretOffset) }
    }

    final override fun applyTo(element: ELEMENT, editor: Editor?) = applyTo(element, element.project, editor)
}