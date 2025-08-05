// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.k2.refactoring.util.BranchedFoldingUtils
import org.jetbrains.kotlin.idea.k2.refactoring.util.BranchedFoldingUtils.getFoldableAssignmentsFromBranches
import org.jetbrains.kotlin.idea.k2.refactoring.util.BranchedFoldingUtils.getFoldableReturnsFromBranches
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * The maximum number of lines of if/when/try expression to try the lift.
 */
private const val LINES_LIMIT = 15

/**
 * An inspection to lift return or assignment within expressions with branches e.g., if/when/try expressions.
 *
 * Example:
 *
 *   - Lift assignment:
 *     // Before
 *     when(foo) {
 *       1 -> bar = 2
 *       2 -> bar = 3
 *       else -> bar = 4
 *     }
 *     // After
 *     bar = when(foo) {
 *       1 -> 2
 *       2 -> 3
 *       else -> 4
 *     }
 *
 *   - Lift return:
 *     // Before
 *     when(foo) {
 *       1 -> return 2
 *       2 -> return 3
 *       else -> return 4
 *     }
 *     // After
 *     return when(foo) {
 *       1 -> 2
 *       2 -> 3
 *       else -> 4
 *     }
 */
internal class LiftReturnOrAssignmentInspection @JvmOverloads constructor(private val skipLongExpressions: Boolean = true) :
    AbstractKotlinInspection() {

    @JvmField
    var reportOnlyIfSingleStatement = true

    override fun getOptionsPane(): OptPane {
        return OptPane.pane(
            OptPane.checkbox(
                "reportOnlyIfSingleStatement",
                KotlinBundle.message("inspection.lift.return.or.assignment.option.only.single.statement")
            )
        )
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            override fun visitExpression(expression: KtExpression) {
                // Note that we'd better run the following line first instead of running
                // `if (analyze(expression) { expression.isUsedAsExpression }) return`
                // because `getState(expression)` will filter many expressions after checking only PSI.
                val states = getState(expression) ?: return

                // This inspection targets only return and assignment within expressions with branches.
                // Their values must not be used by other expressions.
                if (expression.parent !is KtBlockExpression && analyze(expression) { expression.isUsedAsExpression }) return

                states.forEach { state ->
                    if (expression is KtIfExpression && PsiTreeUtil.getParentOfType(state.highlightElement, KtIfExpression::class.java, true) != expression
                        || expression is KtTryExpression && PsiTreeUtil.getParentOfType(state.highlightElement, KtTryExpression::class.java, true) != expression) {
                        // already highlighted when visited nested if/try
                        return@forEach
                    }
                    val problemMessage = KotlinBundle.message(
                        "0.1.be.lifted.out.of.2",
                        when (state.liftType) {
                            LiftType.LIFT_RETURN_OUT -> KotlinBundle.message("text.Return")
                            LiftType.LIFT_ASSIGNMENT_OUT -> KotlinBundle.message("text.Assignment")
                        },
                        state.keyword.text,
                    )

                    registerProblem(
                        expression,
                        state.keyword,
                        state.isSerious,
                        when (state.liftType) {
                            LiftType.LIFT_RETURN_OUT -> LiftReturnOutFix(state.keyword.text)
                            LiftType.LIFT_ASSIGNMENT_OUT -> LiftAssignmentOutFix(state.keyword.text)
                        },
                        problemMessage,
                        state.highlightElement,
                        state.highlightType,
                    )
                }
            }

            private fun registerProblem(
                expression: KtExpression,
                keyword: PsiElement,
                isSerious: Boolean,
                fix: LocalQuickFix,
                @InspectionMessage message: String,
                highlightElement: PsiElement = keyword,
                highlightType: ProblemHighlightType = if (isSerious) GENERIC_ERROR_OR_WARNING else INFORMATION,
            ) {
                holder.registerProblemWithoutOfflineInformation(
                    expression, message, isOnTheFly, highlightType, highlightElement.textRange?.shiftRight(-expression.startOffset), fix
                )
            }

        }

    private class LiftReturnOutFix(private val keyword: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("lift.return.out.fix.text.0", keyword)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtExpression ?: return
            val replaced = BranchedFoldingUtils.foldToReturn(element)
            replaced.findExistingEditor()?.caretModel?.moveToOffset(replaced.startOffset)
        }
    }

    private class LiftAssignmentOutFix(private val keyword: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("lift.assignment.out.fix.text.0", keyword)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtExpression ?: return
            BranchedFoldingUtils.tryFoldToAssignment(element)
        }
    }

    context(_: KaSession)
    private fun getStateForWhenOrTry(expression: KtExpression, keyword: PsiElement): List<LiftState>? {
        if (skipLongExpressions && expression.getLineCount() > LINES_LIMIT) return null
        if (expression.parent.node.elementType == KtNodeTypes.ELSE) return null

        val foldableReturns = getFoldableReturnsFromBranches(expression)
        if (foldableReturns.isNotEmpty()) {
            val returns = foldableReturns.returnExpressions
            val hasOtherReturns = expression.anyDescendantOfType<KtReturnExpression> { it !in returns }
            val allBranchesAreSingleStatement = returns.none { it.hasSiblings() }
            val isSerious = !hasOtherReturns && returns.size > 1 && (allBranchesAreSingleStatement || !reportOnlyIfSingleStatement)
            return returns.map { LiftState(keyword, isSerious, LiftType.LIFT_RETURN_OUT, it, INFORMATION) } +
                    LiftState(keyword, isSerious, LiftType.LIFT_RETURN_OUT)
        }

        val assignments = getFoldableAssignmentsFromBranches(expression)
        if (assignments.isNotEmpty()) {
            val allBranchesAreSingleStatement = assignments.none { it.hasSiblings() }
            val isSerious = assignments.size > 1 && (allBranchesAreSingleStatement || !reportOnlyIfSingleStatement)
            return listOf(LiftState(keyword, isSerious, LiftType.LIFT_ASSIGNMENT_OUT))
        }
        return null
    }

    private fun KtExpression.hasSiblings(): Boolean =
        (parent as? KtBlockExpression)?.statements.orEmpty().size > 1

    private fun getState(expression: KtExpression) = analyze(expression) {
        when (expression) {
            is KtWhenExpression -> getStateForWhenOrTry(expression, expression.whenKeyword)
            is KtIfExpression -> getStateForWhenOrTry(expression, expression.ifKeyword)
            is KtTryExpression -> expression.tryKeyword?.let {
                getStateForWhenOrTry(expression, it)
            }

            else -> null
        }
    }

    enum class LiftType {
        LIFT_RETURN_OUT,
        LIFT_ASSIGNMENT_OUT,
    }

    data class LiftState(
        val keyword: PsiElement,
        val isSerious: Boolean,
        val liftType: LiftType,
        val highlightElement: PsiElement = keyword,
        val highlightType: ProblemHighlightType = if (isSerious) GENERIC_ERROR_OR_WARNING else INFORMATION
    )
}
