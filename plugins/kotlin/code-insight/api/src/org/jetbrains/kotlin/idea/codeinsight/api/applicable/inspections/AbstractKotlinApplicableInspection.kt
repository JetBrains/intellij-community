// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.runReadAction
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableTool
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableWithAnalyze
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * A simple [LocalInspectionTool] that visits *one* element type and produces *a single* quickfix. Marks an element with a warning if the
 * inspection is applicable via [isApplicableByPsi] and [isApplicableByAnalyze]. The quickfix is based on [apply].
 *
 * For more complex inspections that should either visit multiple kinds of elements or register multiple (or zero) problems, simply use
 * [LocalInspectionTool].
 */
abstract class AbstractKotlinApplicableInspection<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>,
) : AbstractKotlinApplicableInspectionBase<ELEMENT>(elementType), KotlinApplicableTool<ELEMENT> {
    /**
     * @see com.intellij.codeInspection.CommonProblemDescriptor.getDescriptionTemplate
     */
    abstract fun getProblemDescription(element: ELEMENT): @InspectionMessage String

    /**
     * Returns the [ProblemHighlightType] for the inspection's registered problem.
     */
    open fun getProblemHighlightType(element: ELEMENT): ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    override fun getActionName(element: ELEMENT): @IntentionName String = getActionFamilyName()

    final override fun buildProblemInfo(element: ELEMENT): ProblemInfo? {
        val isApplicable = runReadAction { isApplicableWithAnalyze(element) }
        if (!isApplicable) return null

        val elementPointer = element.createSmartPointer()
        val quickFix = object : AbstractKotlinApplicableInspectionQuickFix<ELEMENT>() {
            override fun applyTo(element: ELEMENT) {
                apply(element, element.project, element.findExistingEditor())
            }

            override fun getFamilyName(): String = this@AbstractKotlinApplicableInspection.getActionFamilyName()
            override fun getName(): String = elementPointer.element?.let { getActionName(element) } ?: familyName
        }

        val description = getProblemDescription(element)
        val highlightType = getProblemHighlightType(element)
        return ProblemInfo(description, highlightType, quickFix)
    }
}