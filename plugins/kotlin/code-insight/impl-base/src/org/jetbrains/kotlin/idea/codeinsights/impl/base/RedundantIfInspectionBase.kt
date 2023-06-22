// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.codeInspection.*
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments

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

    private data class IfExpressionRedundancyInfo(
        val redundancyType: RedundancyType,
        val branchType: BranchType,
        val returnAfterIf: KtExpression?,
    )

    @JvmField
    var ignoreChainedIf = true

    override fun getOptionsPane(): OptPane {
        return pane(
            checkbox("ignoreChainedIf", KotlinBundle.message("redundant.if.option.ignore.chained")),
        )
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return ifExpressionVisitor { expression ->
            if (expression.condition == null) return@ifExpressionVisitor
            val (redundancyType, branchType, returnAfterIf) = RedundancyType.of(expression)
            if (redundancyType == RedundancyType.NONE) return@ifExpressionVisitor

            if (isUnitTestMode()) {
                ignoreChainedIf = expression.containingKtFile
                    .findDescendantOfType<PsiComment> { it.text.startsWith("// IGNORE_CHAINED_IF:") }
                    ?.let { it.text.removePrefix("// IGNORE_CHAINED_IF:").trim().toBoolean() }
                    ?: false
            }

            val isChainedIf = expression.getPrevSiblingIgnoringWhitespaceAndComments() is KtIfExpression ||
                    expression.parent.let { it is KtContainerNodeForControlStructureBody && it.expression == expression }

            val hasConditionWithFloatingPointType = expression.hasConditionWithFloatingPointType()

            val highlightType =
                if ((ignoreChainedIf && isChainedIf) || hasConditionWithFloatingPointType) INFORMATION
                else GENERIC_ERROR_OR_WARNING

            holder.registerProblemWithoutOfflineInformation(
                expression,
                KotlinBundle.message("redundant.if.statement"),
                isOnTheFly,
                highlightType,
                expression.ifKeyword.textRangeInParent,
                RemoveRedundantIf(redundancyType, branchType, returnAfterIf, hasConditionWithFloatingPointType)
            )
        }
    }

    /**
     * Tells whether the given [expression] is of the boolean type.
     *
     * Called from read action and from modal window, so it's safe to use resolve here.
     */
    abstract fun isBooleanExpression(expression: KtExpression): Boolean

    abstract fun invertEmptinessCheck(condition: KtExpression): KtExpression?

    abstract fun KtIfExpression.hasConditionWithFloatingPointType(): Boolean

    protected fun KtIfExpression.inequalityCondition(): KtBinaryExpression? {
        return (condition as? KtBinaryExpression)
            ?.takeIf { it.left != null && it.right != null }
            ?.takeIf {
                val operation = it.operationToken
                operation == KtTokens.LT || operation == KtTokens.LTEQ || operation == KtTokens.GT || operation == KtTokens.GTEQ
            }
    }

    private sealed class BranchType {
        object Simple : BranchType()

        object Return : BranchType()

        data class LabeledReturn(val label: String) : BranchType()

        class Assign(left: KtExpression) : BranchType() {
            val lvalue: SmartPsiElementPointer<KtExpression> = left.let(SmartPointerManager::createPointer)

            override fun equals(other: Any?) = other is Assign && lvalue.element?.text == other.lvalue.element?.text

            override fun hashCode() = lvalue.element?.text.hashCode()
        }
    }

    private enum class RedundancyType {
        NONE,
        THEN_TRUE,
        ELSE_TRUE;

        companion object {
            private val RedundancyInfoWithNone = IfExpressionRedundancyInfo(NONE, BranchType.Simple, null)

            fun of(expression: KtIfExpression): IfExpressionRedundancyInfo {
                val (thenReturn, thenType) = expression.then?.getBranchExpression() ?: return RedundancyInfoWithNone
                val elseOrReturnAfterIf = expression.`else` ?:
                    // When the target if-expression does not have an else expression and the next expression is a return expression,
                    // we can consider it as an else expression. For example,
                    //     fun foo(bar: String?):Boolean {
                    //       if (bar == null) return false
                    //       return true
                    //     }
                    expression.returnAfterIf() ?: return RedundancyInfoWithNone
                val (elseReturn, elseType) = elseOrReturnAfterIf.getBranchExpression() ?: return RedundancyInfoWithNone

                return when {
                    thenType != elseType -> RedundancyInfoWithNone
                    KtPsiUtil.isTrueConstant(thenReturn) && KtPsiUtil.isFalseConstant(elseReturn) -> IfExpressionRedundancyInfo(
                        THEN_TRUE, thenType, if (expression.`else` == null) elseOrReturnAfterIf else null
                    )

                    KtPsiUtil.isFalseConstant(thenReturn) && KtPsiUtil.isTrueConstant(elseReturn) -> IfExpressionRedundancyInfo(
                        ELSE_TRUE, thenType, if (expression.`else` == null) elseOrReturnAfterIf else null
                    )

                    else -> RedundancyInfoWithNone
                }
            }

            private fun KtExpression.getBranchExpression(): Pair<KtExpression?, BranchType>? {
                return when (this) {
                    is KtReturnExpression -> {
                        val branchType = labeledExpression?.let { BranchType.LabeledReturn(it.text) } ?: BranchType.Return
                        returnedExpression to branchType
                    }

                    is KtBlockExpression -> statements.singleOrNull()?.getBranchExpression()
                    is KtBinaryExpression -> {
                        val left = left
                        if (operationToken == KtTokens.EQ && left != null) right to BranchType.Assign(left)
                        else null
                    }

                    else -> this to BranchType.Simple
                }
            }
        }
    }

    private inner class RemoveRedundantIf(
        private val redundancyType: RedundancyType,
        private val branchType: BranchType,
        returnAfterIf: KtExpression?,
        private val mayChangeSemantics: Boolean,
    ) : LocalQuickFix {
        val returnExpressionAfterIf: SmartPsiElementPointer<KtExpression>? = returnAfterIf?.let(SmartPointerManager::createPointer)

        override fun getName(): String =
            if (mayChangeSemantics) KotlinBundle.message("remove.redundant.if.may.change.semantics.with.floating.point.types")
            else KotlinBundle.message("remove.redundant.if.text")

        override fun getFamilyName() = name

        override fun startInWriteAction(): Boolean = false

        override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as KtIfExpression
            val condition = when (redundancyType) {
                RedundancyType.NONE -> return
                RedundancyType.THEN_TRUE -> element.condition
                RedundancyType.ELSE_TRUE -> negate(element.condition)
            } ?: return
            val factory = KtPsiFactory(project)
            val newExpressionOnlyWithCondition = when (branchType) {
                is BranchType.Return -> factory.createExpressionByPattern("return $0", condition)
                is BranchType.LabeledReturn -> factory.createExpressionByPattern("return${branchType.label} $0", condition)
                is BranchType.Assign -> factory.createExpressionByPattern("$0 = $1", branchType.lvalue.element!!, condition)
                else -> condition
            }

            val commentSaver = CommentSaver(element)
            runWriteAction {
                /**
                 * This is the case that we used the next expression of the if expression as the else expression.
                 * See the code and comment in [RedundancyType.of].
                 */
                returnExpressionAfterIf?.element?.let {
                    it.parent.deleteChildRange(it.prevSibling as? PsiWhiteSpace ?: it, it)
                }

                val replaced = element.replace(newExpressionOnlyWithCondition)
                commentSaver.restore(replaced)
            }
        }

        private fun negate(expression: KtExpression?): KtExpression? {
            if (expression == null) return null
            invertEmptinessCheck(expression)?.let { return it }
            return expression.negate(optionalBooleanExpressionCheck = ::checkIsBooleanExpressionFromModalWindow)
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

/**
 * Returns the sibling expression after [KtIfExpression] if it is [KtReturnExpression]. Otherwise, returns null.
 */
private fun KtIfExpression.returnAfterIf(): KtReturnExpression? = getNextSiblingIgnoringWhitespaceAndComments() as? KtReturnExpression