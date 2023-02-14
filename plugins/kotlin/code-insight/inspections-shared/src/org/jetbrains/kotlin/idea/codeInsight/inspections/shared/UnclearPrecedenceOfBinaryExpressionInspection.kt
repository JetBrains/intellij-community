// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.UnclearPrecedenceOfBinaryExpressionInspection.Holder.dfs
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.UnclearPrecedenceOfBinaryExpressionInspection.Holder.doNeedToPutParentheses
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.UnclearPrecedenceOfBinaryExpressionInspection.Holder.toUnified
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.parsing.KotlinExpressionParsing.Precedence
import org.jetbrains.kotlin.psi.*

class UnclearPrecedenceOfBinaryExpressionInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = MyVisitor(holder)

    var reportEvenObviousCases: Boolean = false

    override fun getOptionsPane() = OptPane.pane(
        OptPane.checkbox(this@UnclearPrecedenceOfBinaryExpressionInspection::reportEvenObviousCases.name,
                         KotlinBundle.message("unclear.precedence.of.binary.expression.report.even.obvious.cases.checkbox"))
    )

    private inner class MyVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
        override fun visitBinaryExpression(binaryExpression: KtBinaryExpression) {
            visit(binaryExpression.toUnified() ?: return)
        }

        override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
            visit(expression.toUnified() ?: return)
        }

        override fun visitIsExpression(expression: KtIsExpression) {
            visit(expression.toUnified() ?: return)
        }

        private fun visit(current: Holder.UnifiedBinaryExpression) {
            val notParenthesizedParent =
                generateSequence(current.expression.parent) { it.parent }.firstOrNull { it !is KtParenthesizedExpression }?.toUnified()
            if (notParenthesizedParent != null) {
                return
            }
            // I'm root of UnifiedBinaryExpression tree
            val highlightType = when {
                current.dfs().any { doNeedToPutParentheses(it, reportEvenObviousCases) } -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                current.dfs().any { doNeedToPutParentheses(it, reportEvenObviousCases = true) } -> ProblemHighlightType.INFORMATION
                else -> return
            }
            if (holder.isOnTheFly || highlightType !== ProblemHighlightType.INFORMATION) {
                holder.registerProblem(
                    current.expression,
                    KotlinBundle.message("unclear.precedence.of.binary.expression.inspection"),
                    highlightType,
                    AddParenthesesFix(putParenthesesInObviousCases = reportEvenObviousCases || highlightType == ProblemHighlightType.INFORMATION)
                )
            }
        }
    }

    private class AddParenthesesFix(private val putParenthesesInObviousCases: Boolean) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("unclear.precedence.of.binary.expression.quickfix")

        override fun getFamilyName() = name

        private fun addParenthesisRecursive(psi: PsiElement, out: StringBuilder) {
            val unified = psi.toUnified()
            val parentParentheses = psi.parent as? KtParenthesizedExpression
            val wrapWithParentheses =
                unified != null && doNeedToPutParentheses(unified, reportEvenObviousCases = putParenthesesInObviousCases) ||
                        parentParentheses != null // Don't accidentally remove already presented parentheses
            if (wrapWithParentheses) {
                out.append("(")
                if (parentParentheses != null) {
                    out.append(parentParentheses.firstChild.nextWhiteSpaceAndComments())
                }
            }
            if (unified != null) {
                addParenthesisRecursive(unified.left, out)
                out.append(unified.left.nextWhiteSpaceAndComments())
                out.append(unified.operation.text)
                out.append(unified.operation.nextWhiteSpaceAndComments())
                addParenthesisRecursive(unified.right, out)
            } else {
                out.append(psi.text)
            }
            if (wrapWithParentheses) {
                if (parentParentheses != null) {
                    out.append(psi.nextWhiteSpaceAndComments())
                }
                out.append(")")
            }
        }

        private fun PsiElement.nextWhiteSpaceAndComments() = this.siblings(forward = true, withSelf = false)
            .takeWhile { it is PsiWhiteSpace || it is PsiComment }
            .joinToString("") { it.text }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val psi = descriptor.psiElement ?: return
            val builder = StringBuilder()
            addParenthesisRecursive(psi, builder)
            psi.replace(KtPsiFactory(project).createExpression(builder.toString()))
        }
    }

    private object Holder {
        /**
         * [org.jetbrains.kotlin.parsing.KotlinExpressionParsing.Precedence]
         */
        data class UnifiedBinaryExpression(
            val left: KtElement,
            val expression: KtExpression,
            val operation: KtSimpleNameExpression,
            val right: KtElement
        )

        fun PsiElement.toUnified(): UnifiedBinaryExpression? {
            return when (this) {
                is KtBinaryExpression -> {
                    if (operationToken in KtTokens.ALL_ASSIGNMENTS) {
                        return null
                    }
                    val left = left?.flattenParentheses() ?: return null
                    val right = right?.flattenParentheses() ?: return null
                    UnifiedBinaryExpression(left, this, operationReference, right)
                }
                is KtBinaryExpressionWithTypeRHS -> {
                    val right = right ?: return null
                    val left = left.flattenParentheses() ?: return null
                    UnifiedBinaryExpression(left, this, operationReference, right)
                }
                is KtIsExpression -> {
                    val right = typeReference ?: return null
                    val left = leftHandSide.flattenParentheses() ?: return null
                    UnifiedBinaryExpression(left, this, operationReference, right)
                }
                else -> return null
            }
        }

        suspend fun SequenceScope<UnifiedBinaryExpression>.visit(node: UnifiedBinaryExpression) {
            node.left.toUnified()?.let { visit(it) }
            node.right.toUnified()?.let { visit(it) }
            yield(node)
        }

        fun UnifiedBinaryExpression.dfs() = sequence { visit(this@dfs) }

        fun KtExpression.flattenParentheses(): KtExpression? =
            generateSequence(this) { (it as? KtParenthesizedExpression)?.expression }.firstOrNull { it !is KtParenthesizedExpression }

        val childToUnclearPrecedenceParentsMapping = listOf(
            Precedence.ELVIS to listOf(Precedence.EQUALITY, Precedence.COMPARISON, Precedence.IN_OR_IS),
            Precedence.SIMPLE_NAME to listOf(Precedence.ELVIS),
            Precedence.ADDITIVE to listOf(Precedence.ELVIS),
            Precedence.MULTIPLICATIVE to listOf(Precedence.ELVIS)
        ).onEach { (key, value) ->
            value.forEach { check(key < it) }
        }.map { item -> Pair(item.first, item.second.flatMap { it.operations.types.toList() }) }

        fun isRecommendedToPutParentheses(unifiedBinaryExpression: UnifiedBinaryExpression): Boolean {
            val parent = unifiedBinaryExpression.expression.parent?.toUnified() ?: return false

            return childToUnclearPrecedenceParentsMapping.any { mappingItem ->
                unifiedBinaryExpression.operation.getReferencedNameElementType() in mappingItem.first.operations &&
                        parent.operation.getReferencedNameElementType() in mappingItem.second
            }
        }

        fun doNeedToPutParentheses(unified: UnifiedBinaryExpression, reportEvenObviousCases: Boolean): Boolean {
            if (isRecommendedToPutParentheses(unified)) {
                return true
            }
            if (reportEvenObviousCases) {
                val parentToken = unified.expression.parent?.toUnified()?.operation?.getReferencedNameElementType()
                    ?: return false
                return unified.operation.getReferencedNameElementType() != parentToken
            }
            return false
        }
    }
}