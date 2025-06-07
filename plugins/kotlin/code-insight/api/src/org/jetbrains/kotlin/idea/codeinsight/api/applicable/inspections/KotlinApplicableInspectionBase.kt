// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.permissions.forbidAnalysis
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.ApplicableRangesProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.ContextProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.getElementContext
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

abstract class KotlinApplicableInspectionBase<E : KtElement, C : Any> : LocalInspectionTool(),
                                                                        ApplicableRangesProvider<E>,
                                                                        ContextProvider<E, C> {

    protected abstract fun InspectionManager.createProblemDescriptor(
        element: E,
        context: C,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor

    protected fun visitTargetElement(
        element: E,
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) {
        val isApplicableByPsi = forbidAnalysis("isApplicableByPsi") {
            isApplicableByPsi(element)
        }
        if (!isApplicableByPsi) return

        val ranges = forbidAnalysis("getApplicabilityRanges") {
            getApplicableRanges(element)
        }
        if (ranges.isEmpty()) return

        val context = try {
            getElementContext(element)
        } catch (e: Exception) {
            if (e is ControlFlowException) throw e
            throw KotlinExceptionWithAttachments("Unable to get element context", e)
                .withPsiAttachment("element.kt", element)
                .withPsiAttachment("file.kt", element.containingFile)
        } ?: return

        registerProblem(ranges, holder, element, context, isOnTheFly)
    }

    protected open fun registerProblem(
        ranges: List<TextRange>,
        holder: ProblemsHolder,
        element: E,
        context: C,
        isOnTheFly: Boolean
    ) {
        ranges.asSequence()
            .map { rangeInElement ->
                holder.manager.createProblemDescriptor(
                    element,
                    context,
                    rangeInElement,
                    isOnTheFly,
                )
            }.filterNot {
                it.highlightType == ProblemHighlightType.INFORMATION
                        && !isOnTheFly
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

    /**
     * A simplified version of [KotlinApplicableInspectionBase] designed for inspections that:
     * - Have exactly one fix for an identified problem.
     * - Do not require custom [ProblemDescriptor] creation logic.
     *
     * For cases where custom [ProblemDescriptor] creation is needed (e.g., multiple quick fixes
     * or no quick fix), extend [KotlinApplicableInspectionBase] and override [createProblemDescriptor]
     * to provide any custom [ProblemDescriptor].
     */
    abstract class Simple<E : KtElement, C : Any> : KotlinApplicableInspectionBase<E, C>() {

        protected abstract fun getProblemDescription(
            element: E,
            context: C,
        ): @InspectionMessage String

        protected open fun getProblemHighlightType(
            element: E,
            context: C,
        ): ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

        protected abstract fun createQuickFix(
            element: E,
            context: C,
        ): KotlinModCommandQuickFix<E>

        final override fun InspectionManager.createProblemDescriptor(
            element: E,
            context: C,
            rangeInElement: TextRange?,
            onTheFly: Boolean
        ): ProblemDescriptor = createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ getProblemDescription(element, context),
            /* highlightType = */ getProblemHighlightType(element, context),
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ createQuickFix(element, context),
        )
    }
}
