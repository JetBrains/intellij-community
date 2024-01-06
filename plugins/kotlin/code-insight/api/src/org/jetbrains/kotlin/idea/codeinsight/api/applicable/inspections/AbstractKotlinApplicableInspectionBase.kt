// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.internal.statistic.ReportingClassSubstitutor
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolBase
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfNeeded
import org.jetbrains.kotlin.psi.KtElement

/**
 * [AbstractKotlinApplicableInspectionBase] is a base implementation for [AbstractKotlinApplicableInspection] and
 * [AbstractKotlinApplicableInspectionWithContext].
 */
abstract class AbstractKotlinApplicableInspectionBase<ELEMENT : KtElement> : LocalInspectionTool(), KotlinApplicableToolBase<ELEMENT> {
    /**
     * The action family name is an action name without any element-specific information. For example, the family name for an action
     * "Replace 'get' call with indexing operator" would be "Replace 'get' or 'set' call with indexing operator".
     *
     * This is currently used as a fallback for when an element isn't available to build an action name, but may also be used in the future
     * as a group name for multiple quick fixes, as [QuickFix.getFamilyName] intends. (Once the applicable inspections API supports
     * multiple quick fixes.)
     *
     * @see com.intellij.codeInspection.QuickFix.getFamilyName
     */
    abstract fun getActionFamilyName(): @IntentionFamilyName String

    /**
     * By default, a problem is registered for every [TextRange] produced by [getApplicabilityRange]. [getProblemRanges] can be overridden
     * to customize this behavior, e.g. to register a problem only for the first [TextRange].
     */
    open fun getProblemRanges(ranges: List<TextRange>): List<TextRange> = ranges

    internal class ProblemInfo(
        val description: @InspectionMessage String,
        val highlightType: ProblemHighlightType,
        val quickFix: LocalQuickFix,
    )

    /**
     * Builds a [ProblemInfo] instance for an element that this inspection is proven to be applicable to by PSI and applicability ranges.
     * The [ProblemInfo] will be used to register an appropriate problem with a quick fix.
     */
    internal abstract fun buildProblemInfo(element: ELEMENT): ProblemInfo?

    final fun visitTargetElement(element: ELEMENT, holder: ProblemsHolder, isOnTheFly: Boolean) {
        if (!isApplicableByPsi(element)) return
        val ranges = getApplicabilityRange().getApplicabilityRanges(element)
        if (ranges.isEmpty()) return

        val problemInfo = buildProblemInfo(element) ?: return
        if (!isOnTheFly && problemInfo.highlightType == ProblemHighlightType.INFORMATION) {
            return
        }
        getProblemRanges(ranges).forEach { range ->
            holder.registerProblem(
                element,
                problemInfo.description,
                problemInfo.highlightType,
                range,
                problemInfo.quickFix,
            )
        }
    }

    abstract override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor
}

internal abstract class AbstractKotlinApplicableInspectionQuickFix<ELEMENT : KtElement> : LocalQuickFix, ReportingClassSubstitutor {
    abstract fun applyTo(element: ELEMENT)

    abstract fun shouldApplyInWriteAction(): Boolean

    abstract override fun getName(): String

    final override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        @Suppress("UNCHECKED_CAST")
        val element = descriptor.psiElement as ELEMENT
        runWriteActionIfNeeded(shouldApplyInWriteAction() && element.isPhysical) {
            applyTo(element)
        }
    }

    final override fun startInWriteAction() = false

    final override fun getElementToMakeWritable(currentFile: PsiFile) = currentFile
}

internal abstract class AbstractKotlinModCommandApplicableInspectionQuickFix<ELEMENT : KtElement> : PsiUpdateModCommandQuickFix(), ReportingClassSubstitutor {
    abstract override fun getName(): String

    abstract override fun getFamilyName(): @IntentionFamilyName String

    final override fun applyFix(
        project: Project,
        element: PsiElement,
        updater: ModPsiUpdater
    ) {
        @Suppress("UNCHECKED_CAST")
        val e = element as ELEMENT
        applyFix(project, e, updater)
    }

    abstract fun applyFix(
        project: Project,
        element: ELEMENT,
        updater: ModPsiUpdater
    )
}