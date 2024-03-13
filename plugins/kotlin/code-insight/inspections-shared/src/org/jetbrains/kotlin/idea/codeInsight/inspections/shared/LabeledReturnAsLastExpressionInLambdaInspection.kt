// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.psi.getParentLambdaLabelName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.returnExpressionVisitor

/**
 * Tests:
 * - [org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SharedK1LocalInspectionTestGenerated.LabeledReturnAsLastExpressionInLambda]
 * - [org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.SharedK2LocalInspectionTestGenerated.LabeledReturnAsLastExpressionInLambda]
 */
internal class LabeledReturnAsLastExpressionInLambdaInspection : AbstractKotlinApplicableInspection<KtReturnExpression>() {

    override fun getProblemDescription(element: KtReturnExpression): @InspectionMessage String =
        KotlinBundle.message("inspection.labeled.return.on.last.expression.in.lambda.display.name")

    override fun getActionFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("remove.labeled.return.from.last.expression.in.a.lambda")

    override fun getActionName(element: KtReturnExpression): @InspectionMessage String {
        val labelName = element.getLabelName().orEmpty()

        return KotlinBundle.message("remove.return.0", labelName)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
        returnExpressionVisitor {
            visitTargetElement(it, holder, isOnTheFly)
        }

    override fun isApplicableByPsi(element: KtReturnExpression): Boolean {
        val labelName = element.getLabelName() ?: return false
        val block = element.getStrictParentOfType<KtBlockExpression>() ?: return false
        if (block.statements.lastOrNull() != element) return false
        val callName = block.getParentLambdaLabelName() ?: return false
        if (labelName != callName) return false
        return true
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtReturnExpression> = applicabilityRange { returnExpression ->
        val keywordRange = returnExpression.returnKeyword.textRangeInParent
        val labelRange = returnExpression.labeledExpression?.textRangeInParent

        if (labelRange != null) keywordRange.union(labelRange) else null
    }

    override fun apply(
        element: KtReturnExpression,
        project: Project,
        updater: ModPsiUpdater
    ) {
        val returnedExpression = element.returnedExpression
        if (returnedExpression == null) {
            element.delete()
        } else {
            element.replace(returnedExpression)
        }
    }
}
