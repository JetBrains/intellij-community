// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
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
abstract class AbstractKotlinApplicableInspectionWithContext<ELEMENT : KtElement, CONTEXT> : AbstractKotlinApplicableInspectionBase<ELEMENT>(), KotlinApplicableToolWithContext<ELEMENT, CONTEXT> {
    /**
     * @see com.intellij.codeInspection.CommonProblemDescriptor.getDescriptionTemplate
     */
    abstract fun getProblemDescription(element: ELEMENT, context: CONTEXT): @InspectionMessage String

    /**
     * Returns the [ProblemHighlightType] for the inspection's registered problem.
     */
    open fun getProblemHighlightType(element: ELEMENT, context: CONTEXT): ProblemHighlightType =
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    override fun getActionName(element: ELEMENT, context: CONTEXT): @IntentionName String = getActionFamilyName()

    final override fun apply(element: ELEMENT, context: CONTEXT, project: Project, editor: Editor?) {
        throw UnsupportedOperationException("apply(ELEMENT, CONTEXT, Project, Editor?) should not be invoked")
    }

    abstract fun apply(element: ELEMENT, context: CONTEXT, project: Project, updater: ModPsiUpdater)

    final override fun buildProblemInfo(element: ELEMENT): ProblemInfo? {
        val context = prepareContextWithAnalyze(element) ?: return null
        val name = getActionName(element, context)
        val inspectionWithContextClass = javaClass
        val quickFix = object : AbstractKotlinModCommandApplicableInspectionQuickFix<ELEMENT>() {

            override fun getFamilyName(): String = this@AbstractKotlinApplicableInspectionWithContext.getActionFamilyName()

            override fun applyFix(
                project: Project,
                element: ELEMENT,
                updater: ModPsiUpdater
            ) {
                apply(element, context, element.project, updater)
            }

            override fun getName(): String = name
            override fun getSubstitutedClass(): Class<*> = inspectionWithContextClass
        }

        val description = getProblemDescription(element, context)
        val highlightType = getProblemHighlightType(element, context)
        return ProblemInfo(description, highlightType, quickFix)
    }
}