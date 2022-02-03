// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.isAnyEquals
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

class UnusedEqualsInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            private fun reportIfNotUsedAsExpression(expression: KtExpression) {
                val context = expression.safeAnalyzeNonSourceRootCode()
                if (context != BindingContext.EMPTY && !expression.isUsedAsExpression(context)) {
                    holder.registerProblem(expression, KotlinBundle.message("unused.equals.expression"))
                }
            }

            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                super.visitBinaryExpression(expression)

                if (expression.operationToken == KtTokens.EQEQ) {
                    val parent = expression.parent

                    val shouldReport = when {
                        parent.parent is KtIfExpression -> true
                        parent is KtBlockExpression -> parent.parent !is KtCodeFragment || parent.statements.lastOrNull() != expression
                        else -> false
                    }

                    if (shouldReport) {
                        reportIfNotUsedAsExpression(expression)
                    }
                }
            }

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val calleeExpression = expression.calleeExpression as? KtSimpleNameExpression ?: return
                if (calleeExpression.getReferencedNameAsName() != OperatorNameConventions.EQUALS) return

                if (!expression.isAnyEquals()) return
                reportIfNotUsedAsExpression(expression.getQualifiedExpressionForSelectorOrThis())
            }
        }
    }

}