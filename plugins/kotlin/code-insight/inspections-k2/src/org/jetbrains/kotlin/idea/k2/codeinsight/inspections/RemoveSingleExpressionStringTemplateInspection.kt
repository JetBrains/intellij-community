// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*

internal class RemoveSingleExpressionStringTemplateInspection :
  AbstractKotlinApplicableInspectionWithContext<KtStringTemplateExpression, RemoveSingleExpressionStringTemplateInspection.Context>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
    class Context(val isString: Boolean)

    override fun getProblemDescription(element: KtStringTemplateExpression, context: Context): String =
        KotlinBundle.message("remove.single.expression.string.template")

    override fun getActionFamilyName(): String = KotlinBundle.message("remove.single.expression.string.template")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtStringTemplateExpression> = ApplicabilityRanges.SELF

    override fun getProblemHighlightType(element: KtStringTemplateExpression, context: Context): ProblemHighlightType =
      if (context.isString) ProblemHighlightType.GENERIC_ERROR_OR_WARNING else ProblemHighlightType.INFORMATION

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean = element.singleExpressionOrNull() != null

    context(KtAnalysisSession)
    override fun prepareContext(element: KtStringTemplateExpression): Context? {
        val expression = element.singleExpressionOrNull() ?: return null
        val type = expression.getKtType()
        return Context(type?.isString == true && !type.isMarkedNullable)
    }

    override fun apply(element: KtStringTemplateExpression, context: Context, project: Project, updater: ModPsiUpdater) {
        // Note that we do not reuse the result of `stringTemplateExpression.singleExpressionOrNull()`
        // from `getInputProvider()` method because PsiElement may become invalidated between read actions
        // e.g., it may be reparsed and recreated and old reference will become stale and invalid.
        val expression = element.singleExpressionOrNull() ?: return

        val newElement = if (context.isString) {
            expression
        } else {
            KtPsiFactory(project).createExpressionByPattern(
                pattern = "$0.$1()", expression, "toString"
            )
        }
        element.replace(newElement)
    }

    private fun KtStringTemplateExpression.singleExpressionOrNull() = children.singleOrNull()?.children?.firstOrNull() as? KtExpression
}