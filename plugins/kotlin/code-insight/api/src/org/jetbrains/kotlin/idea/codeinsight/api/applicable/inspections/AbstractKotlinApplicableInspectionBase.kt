// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisAllowanceManager
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolBase
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitor

/**
 * [AbstractKotlinApplicableInspectionBase] is a base implementation for [AbstractKotlinApplicableInspection] and
 * [AbstractKotlinApplicableInspectionWithContext].
 */
abstract class AbstractKotlinApplicableInspectionBase<ELEMENT : KtElement> : LocalInspectionTool(),
                                                                             KotlinApplicableToolBase<ELEMENT> {

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
        val ranges = KtAnalysisAllowanceManager.forbidAnalysisInside("getApplicabilityRanges") {
            getApplicableRanges(element)
        }
        if (ranges.isEmpty()) return

        val problemInfo = buildProblemInfo(element) ?: return
        if (!isOnTheFly && problemInfo.highlightType == ProblemHighlightType.INFORMATION) {
            return
        }

        ranges.asSequence()
            .map { rangeInElement ->
                holder.manager.createProblemDescriptor(
                    /* psiElement = */ element,
                    /* rangeInElement = */ rangeInElement,
                    /* descriptionTemplate = */ problemInfo.description,
                    /* highlightType = */ problemInfo.highlightType,
                    /* onTheFly = */ isOnTheFly,
                    /* ...fixes = */ problemInfo.quickFix,
                )
            }.forEach(holder::registerProblem)
    }

    abstract override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *>

    final override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor = super.buildVisitor(holder, isOnTheFly, session)
}
