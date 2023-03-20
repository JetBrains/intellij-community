// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.BranchedFoldingUtils
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isElseIf
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class LiftReturnOrAssignmentInspection @JvmOverloads constructor(private val skipLongExpressions: Boolean = true) :
    AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            override fun visitExpression(expression: KtExpression) {
                val states = getState(expression, skipLongExpressions) ?: return
                if (expression.isUsedAsExpression(expression.analyze(BodyResolveMode.PARTIAL_WITH_CFA))) return
                states.forEach { state ->
                    registerProblem(
                        expression,
                        state.keyword,
                        state.isSerious,
                        when (state.liftType) {
                            LiftType.LIFT_RETURN_OUT -> LiftReturnOutFix(state.keyword.text)
                            LiftType.LIFT_ASSIGNMENT_OUT -> LiftAssignmentOutFix(state.keyword.text)
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

    companion object {
        private const val LINES_LIMIT = 15

        fun getState(expression: KtExpression, skipLongExpressions: Boolean) = when (expression) {
            is KtWhenExpression -> getStateForWhenOrTry(expression, expression.whenKeyword, skipLongExpressions)
            is KtIfExpression -> getStateForWhenOrTry(expression, expression.ifKeyword, skipLongExpressions)
            is KtTryExpression -> expression.tryKeyword?.let {
                getStateForWhenOrTry(expression, it, skipLongExpressions)
            }
            else -> null
        }

        private fun getStateForWhenOrTry(
            expression: KtExpression,
            keyword: PsiElement,
            skipLongExpressions: Boolean
        ): List<LiftState>? {
            if (skipLongExpressions && expression.getLineCount() > LINES_LIMIT) return null
            if (expression.isElseIf()) return null

            val foldableReturns = BranchedFoldingUtils.getFoldableReturns(expression)
            if (foldableReturns?.isNotEmpty() == true) {
                val hasOtherReturns = expression.anyDescendantOfType<KtReturnExpression> { it !in foldableReturns }
                val isSerious = !hasOtherReturns && foldableReturns.size > 1
                return foldableReturns.map {
                    LiftState(keyword, isSerious, LiftType.LIFT_RETURN_OUT, it, INFORMATION)
                } + LiftState(keyword, isSerious, LiftType.LIFT_RETURN_OUT)
            }

            val assignmentNumber = BranchedFoldingUtils.getFoldableAssignmentNumber(expression)
            if (assignmentNumber > 0) {
                val isSerious = assignmentNumber > 1
                return listOf(LiftState(keyword, isSerious, LiftType.LIFT_ASSIGNMENT_OUT))
            }
            return null
        }

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
