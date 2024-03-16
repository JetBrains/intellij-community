// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableTool
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableWithAnalyze
import org.jetbrains.kotlin.psi.KtElement

/**
 * A simple [LocalInspectionTool] that visits *one* element type and produces *a single* quickfix. Marks an element with a warning if the
 * inspection is applicable via [isApplicableByPsi] and [isApplicableByAnalyze]. The quickfix is based on [apply].
 *
 * For more complex inspections that should either visit multiple kinds of elements or register multiple (or zero) problems, simply use
 * [LocalInspectionTool].
 */
abstract class AbstractKotlinApplicableInspection<ELEMENT : KtElement> : AbstractKotlinApplicableInspectionBase<ELEMENT>(),
                                                                         KotlinApplicableTool<ELEMENT> {
    /**
     * @see com.intellij.codeInspection.CommonProblemDescriptor.getDescriptionTemplate
     */
    abstract fun getProblemDescription(element: ELEMENT): @InspectionMessage String

    /**
     * Returns the [ProblemHighlightType] for the inspection's registered problem.
     */
    open fun getProblemHighlightType(element: ELEMENT): ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    final override fun shouldApplyInWriteAction(): Boolean {
        return false
    }

    protected abstract fun createQuickFix(element: ELEMENT): KotlinModCommandQuickFix<ELEMENT>

    final override fun buildProblemInfo(element: ELEMENT): ProblemInfo? {
        if (!isApplicableWithAnalyze(element)) return null

        return ProblemInfo(
            description = getProblemDescription(element),
            highlightType = getProblemHighlightType(element),
            quickFix = createQuickFix(element),
        )
    }
}