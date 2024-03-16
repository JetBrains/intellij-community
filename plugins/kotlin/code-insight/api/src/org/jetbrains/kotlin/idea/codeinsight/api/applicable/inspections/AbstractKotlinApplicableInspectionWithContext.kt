// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.prepareContextWithAnalyze
import org.jetbrains.kotlin.psi.KtElement

/**
 * A simple [LocalInspectionTool] that visits *one* element type and produces *a single* quickfix. Marks an element with a warning if the
 * inspection is applicable via [isApplicableByPsi] and [prepareContext]. The quickfix is based on [apply] given some [CONTEXT] from
 * [prepareContext].
 *
 * For more complex inspections that should either visit multiple kinds of elements or register multiple (or zero) problems, simply use
 * [LocalInspectionTool].
 */
abstract class AbstractKotlinApplicableInspectionWithContext<ELEMENT : KtElement, CONTEXT> :
    AbstractKotlinApplicableInspectionBase<ELEMENT>(),
    KotlinApplicableToolWithContext<ELEMENT, CONTEXT> {

    /**
     * @see com.intellij.codeInspection.CommonProblemDescriptor.getDescriptionTemplate
     */
    abstract fun getProblemDescription(element: ELEMENT, context: CONTEXT): @InspectionMessage String

    /**
     * Returns the [ProblemHighlightType] for the inspection's registered problem.
     */
    open fun getProblemHighlightType(element: ELEMENT, context: CONTEXT): ProblemHighlightType =
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    abstract fun createQuickFix(
        element: ELEMENT,
        context: CONTEXT,
    ): KotlinModCommandQuickFix<ELEMENT>

    final override fun buildProblemInfo(element: ELEMENT): ProblemInfo? {
        val context = prepareContextWithAnalyze(element) ?: return null

        return ProblemInfo(
            description = getProblemDescription(element, context),
            highlightType = getProblemHighlightType(element, context),
            quickFix = createQuickFix(element, context),
        )
    }
}