// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.quickfix.ChangeToLabeledReturnFix
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.returnExpressionVisitor

internal class UnlabeledReturnInsideLambdaInspection : AbstractKotlinInspection() {

    @OptIn(KaExperimentalApi::class)
    private fun hasDiagnosticError(returnExpression: KtReturnExpression): Boolean {
        return analyze(returnExpression) {
            returnExpression.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).any {
                it.severity == KaSeverity.ERROR
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        returnExpressionVisitor(fun(returnExpression: KtReturnExpression) {
            if (returnExpression.labelQualifier != null) return
            val lambda = returnExpression.getParentOfType<KtLambdaExpression>(true, KtNamedFunction::class.java) ?: return
            val parentFunction = lambda.getStrictParentOfType<KtNamedFunction>() ?: return

            if (hasDiagnosticError(returnExpression)) return

            val action = ChangeToLabeledReturnFix(returnExpression, labeledReturn = "return@${parentFunction.name}")
            holder.registerProblem(
                returnExpression.returnKeyword,
                KotlinBundle.message("unlabeled.return.inside.lambda"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                IntentionWrapper(action.asIntention())
            )
        })
}