// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * A simple [LocalInspectionTool] that visits *one* element type and produces *a single* quickfix. Marks an element with a warning if the
 * inspection is applicable via [isApplicableByPsi] and [prepareContext]. The quickfix is based on [apply] given some [CONTEXT] from
 * [prepareContext].
 *
 * For more complex inspections that should either visit multiple kinds of elements or register multiple (or zero) problems, simply use
 * [LocalInspectionTool].
 */
abstract class KotlinApplicableInspectionWithContext<ELEMENT : KtElement, CONTEXT>(
    elementType: KClass<ELEMENT>,
) : KotlinApplicableInspectionBase<ELEMENT>(elementType), KotlinApplicableToolWithContext<ELEMENT, CONTEXT> {
    /**
     * [getProblemDescription] must be lightweight: it should not perform expensive computations so that it doesn't cause performance
     * issues.
     *
     * @see com.intellij.codeInspection.CommonProblemDescriptor.getDescriptionTemplate
     */
    open fun getProblemDescription(element: ELEMENT, context: CONTEXT): @InspectionMessage String = getFamilyName()

    /**
     * Returns the [ProblemHighlightType] for the inspection's registered problem.
     */
    open fun getProblemHighlightType(element: ELEMENT, context: CONTEXT): ProblemHighlightType =
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    final override fun buildProblemInfo(element: ELEMENT): ProblemInfo? {
        val context = prepareContextWithAnalyze(element, needsReadAction = true) ?: return null

        val elementPointer = element.createSmartPointer()
        val quickFix = object : KotlinApplicableInspectionQuickFix<ELEMENT>() {
            override fun applyTo(element: ELEMENT) {
                apply(element, context, element.project, element.findExistingEditor())
            }

            override fun getFamilyName(): String = this@KotlinApplicableInspectionWithContext.getFamilyName()
            override fun getName(): String = elementPointer.element?.let { getActionName(element, context) } ?: familyName
        }

        val description = getProblemDescription(element, context)
        val highlightType = getProblemHighlightType(element, context)
        return ProblemInfo(description, highlightType, quickFix)
    }
}