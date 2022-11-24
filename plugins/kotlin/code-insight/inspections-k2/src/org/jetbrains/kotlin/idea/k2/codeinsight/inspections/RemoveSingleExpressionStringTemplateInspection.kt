// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern

internal class RemoveSingleExpressionStringTemplateInspection :
    AbstractKotlinApplicableInspectionWithContext<KtStringTemplateExpression, RemoveSingleExpressionStringTemplateInspection.Context>(
        KtStringTemplateExpression::class
    ) {

    class Context(val isString: Boolean)

    override fun getProblemDescription(element: KtStringTemplateExpression, context: Context): String =
        KotlinBundle.message("remove.single.expression.string.template")

    override fun getActionFamilyName(): String = KotlinBundle.message("remove.single.expression.string.template")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtStringTemplateExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean = element.singleExpressionOrNull() != null

    context(KtAnalysisSession)
    override fun prepareContext(element: KtStringTemplateExpression): Context? {
        val expression = element.singleExpressionOrNull() ?: return null
        return Context(expression.getKtType()?.isString == true)
    }

    override fun apply(element: KtStringTemplateExpression, context: Context, project: Project, editor: Editor?) {
        // Note that we do not reuse the result of `stringTemplateExpression.singleExpressionOrNull()`
        // from `getInputProvider()` method because PsiElement may become invalidated between read actions
        // e.g., it may be reparsed and recreated and old reference will become stale and invalid.
        val expression = element.singleExpressionOrNull() ?: return

            val newElement = if (context.isString) {
                expression
            } else {
                KtPsiFactory(stringTemplateExpression.project).createExpressionByPattern(
                    pattern = "$0.$1()", expression, "toString"
                )
            }
            stringTemplateExpression.replace(newElement)
        }
        element.replace(newElement)
    }

    private fun KtStringTemplateExpression.singleExpressionOrNull() = children.singleOrNull()?.children?.firstOrNull() as? KtExpression
}