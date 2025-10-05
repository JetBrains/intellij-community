// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.isEqualsMethodSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class UnusedEqualsInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KtVisitorVoid() {
        private fun KaSession.reportIfNotUsedAsExpression(expression: KtExpression) {
            if (!expression.isUsedAsExpression) {
                holder.registerProblem(expression, KotlinBundle.message("unused.equals.expression"))
            }
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            super.visitBinaryExpression(expression)

            if (expression.operationToken != KtTokens.EQEQ) return

            val parent = expression.parent
            val shouldReport = when {
                parent.parent is KtIfExpression -> true
                parent is KtBlockExpression -> parent.parent !is KtCodeFragment || parent.statements.lastOrNull() != expression
                else -> false
            }
            if (!shouldReport) return

            analyze(expression) {
                reportIfNotUsedAsExpression(expression)
            }
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            super.visitCallExpression(expression)

            val calleeExpression = expression.calleeExpression as? KtSimpleNameExpression ?: return
            if (calleeExpression.getReferencedNameAsName() != OperatorNameConventions.EQUALS) return

            analyze(expression) {
                if (!isAnyEquals(expression)) return@analyze
                val target = expression.getQualifiedExpressionForSelectorOrThis()
                reportIfNotUsedAsExpression(target)
            }
        }

        private fun KaSession.isAnyEquals(expression: KtCallExpression): Boolean {
            val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
            val symbol = call.symbol as? KaNamedFunctionSymbol ?: return false
            return symbol.isEqualsMethodSymbol()
        }
    }
}