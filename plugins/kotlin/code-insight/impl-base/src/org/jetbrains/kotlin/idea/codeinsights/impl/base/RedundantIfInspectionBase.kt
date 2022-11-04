// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * A parent class for K1 and K2 RedundantIfInspection.
 *
 * This class contains most parts of RedundantIfInspection that are common between K1 and K2.
 * The only thing K1 and K2 RedundantIfInspection have to implement is `isBooleanExpression`
 * that is a function variable for a lambda expression that returns whether the given KtExpression
 * is an expression with the boolean type. Since `isBooleanExpression` uses the type analysis,
 * K1/K2 must have different implementations.
 */
abstract class RedundantIfInspectionBase : AbstractKotlinInspection(), CleanupLocalInspectionTool {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return ifExpressionVisitor { expression ->
            if (expression.condition == null) return@ifExpressionVisitor
            val (redundancyType, branchType) = RedundancyType.of(expression)
            if (redundancyType == RedundancyType.NONE) return@ifExpressionVisitor

            holder.registerProblem(
                expression,
                KotlinBundle.message("redundant.if.statement"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                RemoveRedundantIf(redundancyType, branchType)
            )
        }
    }

    /**
     * Tells whether the given [expression] is of the boolean type.
     *
     * Called from read action and from modal window, so it's safe to use resolve here.
     */
    abstract fun isBooleanExpression(expression: KtExpression): Boolean

    private sealed class BranchType {
        object Simple : BranchType()

        object Return : BranchType()

        data class LabeledReturn(val label: String) : BranchType()

        class Assign(val lvalue: KtExpression) : BranchType() {
            override fun equals(other: Any?) = other is Assign && lvalue.text == other.lvalue.text

            override fun hashCode() = lvalue.text.hashCode()
        }
    }

    private enum class RedundancyType {
        NONE,
        THEN_TRUE,
        ELSE_TRUE;

        companion object {
            fun of(expression: KtIfExpression): Pair<RedundancyType, BranchType> {
                val (thenReturn, thenType) = expression.then?.getBranchExpression() ?: return NONE to BranchType.Simple
                val (elseReturn, elseType) = expression.`else`?.getBranchExpression() ?: return NONE to BranchType.Simple

                return when {
                    thenType != elseType -> NONE to BranchType.Simple
                    KtPsiUtil.isTrueConstant(thenReturn) && KtPsiUtil.isFalseConstant(elseReturn) -> THEN_TRUE to thenType
                    KtPsiUtil.isFalseConstant(thenReturn) && KtPsiUtil.isTrueConstant(elseReturn) -> ELSE_TRUE to thenType
                    else -> NONE to BranchType.Simple
                }
            }

            private fun KtExpression.getBranchExpression(): Pair<KtExpression?, BranchType>? {
                return when (this) {
                    is KtReturnExpression -> {
                        val branchType = labeledExpression?.let { BranchType.LabeledReturn(it.text) } ?: BranchType.Return
                        returnedExpression to branchType
                    }

                    is KtBlockExpression -> statements.singleOrNull()?.getBranchExpression()
                    is KtBinaryExpression -> if (operationToken == KtTokens.EQ && left != null) right to BranchType.Assign(left!!)
                    else null

                    else -> this to BranchType.Simple
                }
            }
        }
    }

    private inner class RemoveRedundantIf(
        private val redundancyType: RedundancyType,
        @SafeFieldForPreview // may refer to PsiElement of original file but we are only reading from it
        private val branchType: BranchType,
    ) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("remove.redundant.if.text")
        override fun getFamilyName() = name

        override fun startInWriteAction(): Boolean = false

        override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as KtIfExpression
            val condition = when (redundancyType) {
                RedundancyType.NONE -> return
                RedundancyType.THEN_TRUE -> element.condition
                RedundancyType.ELSE_TRUE -> element.condition?.negate(isBooleanExpression = ::checkIsBooleanExpressionFromModalWindow)
            } ?: return
            val factory = KtPsiFactory(element)
            val newExpressionOnlyWithCondition = when (branchType) {
                is BranchType.Return -> factory.createExpressionByPattern("return $0", condition)
                is BranchType.LabeledReturn -> factory.createExpressionByPattern("return${branchType.label} $0", condition)
                is BranchType.Assign -> factory.createExpressionByPattern("$0 = $1", branchType.lvalue, condition)
                else -> condition
            }

            runWriteAction {
                element.replace(newExpressionOnlyWithCondition)
            }
        }

        private fun checkIsBooleanExpressionFromModalWindow(expression: KtExpression): Boolean =
            ActionUtil.underModalProgress(
                expression.project,
                KotlinBundle.message("redundant.if.statement.analyzing.type"),
            ) {
                isBooleanExpression(expression)
            }
    }
}