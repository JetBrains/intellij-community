// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.BranchedFoldingUtils
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isElseIf
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class LiftReturnOrAssignmentInspection @JvmOverloads constructor(private val skipLongExpressions: Boolean = true) :
    AbstractKotlinInspection() {

    @JvmField
    var reportOnlyIfSingleStatement = true

    override fun getOptionsPane(): OptPane {
        return pane(
            checkbox("reportOnlyIfSingleStatement", KotlinBundle.message("inspection.lift.return.or.assignment.option.only.single.statement")),
        )
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            override fun visitExpression(expression: KtExpression) {
                val states = Util.getState(expression, skipLongExpressions, reportOnlyIfSingleStatement) ?: return
                if (expression.isUsedAsExpression(expression.analyze(BodyResolveMode.PARTIAL_WITH_CFA))) return
                states.forEach { state ->
                    registerProblem(
                        expression,
                        state.keyword,
                        state.isSerious,
                        when (state.liftType) {
                            Util.LiftType.LIFT_RETURN_OUT -> LiftReturnOutFix(state.keyword.text)
                            Util.LiftType.LIFT_ASSIGNMENT_OUT -> LiftAssignmentOutFix(state.keyword.text)
                        },
                        state.highlightElement,
                        state.highlightType
                    )
                }
            }

            private fun registerProblem(
                expression: KtExpression,
                keyword: PsiElement,
                isSerious: Boolean,
                fix: LocalQuickFix,
                highlightElement: PsiElement = keyword,
                highlightType: ProblemHighlightType = if (isSerious) GENERIC_ERROR_OR_WARNING else INFORMATION
            ) {
                val subject = if (fix is LiftReturnOutFix) KotlinBundle.message("text.Return") else KotlinBundle.message("text.Assignment")
                holder.registerProblemWithoutOfflineInformation(
                    expression,
                    KotlinBundle.message("0.1.be.lifted.out.of.2", subject, keyword.text),
                    isOnTheFly,
                    highlightType,
                    highlightElement.textRange?.shiftRight(-expression.startOffset),
                    fix
                )
            }

        }

    private class LiftReturnOutFix(private val keyword: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("lift.return.out.fix.text.0", keyword)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val replaced = BranchedFoldingUtils.foldToReturn(descriptor.psiElement as KtExpression)
            replaced.findExistingEditor()?.caretModel?.moveToOffset(replaced.startOffset)
        }
    }

    private class LiftAssignmentOutFix(private val keyword: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("lift.assignment.out.fix.text.0", keyword)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            BranchedFoldingUtils.tryFoldToAssignment(descriptor.psiElement as KtExpression)
        }
    }

    object Util {
        private const val LINES_LIMIT = 15

        fun getState(
            expression: KtExpression,
            skipLongExpressions: Boolean,
            reportOnlyIfSingleStatement: Boolean = true
        ): List<LiftState>? = when (expression) {
            is KtWhenExpression ->
                getStateForWhenOrTry(expression, expression.whenKeyword, skipLongExpressions, reportOnlyIfSingleStatement)

            is KtIfExpression ->
                getStateForWhenOrTry(expression, expression.ifKeyword, skipLongExpressions, reportOnlyIfSingleStatement)

            is KtTryExpression ->
                expression.tryKeyword?.let { getStateForWhenOrTry(expression, it, skipLongExpressions, reportOnlyIfSingleStatement) }

            else -> null
        }

        private fun getStateForWhenOrTry(
            expression: KtExpression,
            keyword: PsiElement,
            skipLongExpressions: Boolean,
            reportOnlyIfSingleStatement: Boolean,
        ): List<LiftState>? {
            if (skipLongExpressions && expression.getLineCount() > LINES_LIMIT) return null
            if (expression.isElseIf()) return null

            val foldableReturns = BranchedFoldingUtils.getFoldableReturns(expression)
            if (foldableReturns?.isNotEmpty() == true) {
                val hasOtherReturns = expression.anyDescendantOfType<KtReturnExpression> { it !in foldableReturns }
                val allBranchesAreSingleStatement = foldableReturns.none { it.hasSiblings() }
                val isSerious = !hasOtherReturns &&
                        foldableReturns.size > 1 &&
                        (allBranchesAreSingleStatement || !reportOnlyIfSingleStatement)
                return foldableReturns.map { LiftState(keyword, isSerious, LiftType.LIFT_RETURN_OUT, it, INFORMATION) } +
                        LiftState(keyword, isSerious, LiftType.LIFT_RETURN_OUT)
            }

            val assignments = BranchedFoldingUtils.getFoldableAssignments(expression)
            if (assignments.isNotEmpty()) {
                val allBranchesAreSingleStatement = assignments.none { it.hasSiblings() }
                val isSerious = assignments.size > 1 && (allBranchesAreSingleStatement || !reportOnlyIfSingleStatement)
                return listOf(LiftState(keyword, isSerious, LiftType.LIFT_ASSIGNMENT_OUT))
            }
            return null
        }

        private fun KtExpression.hasSiblings(): Boolean =
            (parent as? KtBlockExpression)?.statements.orEmpty().size > 1

        enum class LiftType {
            LIFT_RETURN_OUT, LIFT_ASSIGNMENT_OUT
        }

        data class LiftState(
            val keyword: PsiElement,
            val isSerious: Boolean,
            val liftType: LiftType,
            val highlightElement: PsiElement = keyword,
            val highlightType: ProblemHighlightType = if (isSerious) GENERIC_ERROR_OR_WARNING else INFORMATION
        )
    }
}
