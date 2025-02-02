// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.getParentLambdaLabelName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.returnExpressionVisitor

/**
 * Tests:
 * - [org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SharedK1LocalInspectionTestGenerated.RedundantLabeledReturnOnLastExpressionInLambda]
 * - [org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.SharedK2LocalInspectionTestGenerated.RedundantLabeledReturnOnLastExpressionInLambda]
 */
internal class RedundantLabeledReturnOnLastExpressionInLambdaInspection :
    KotlinApplicableInspectionBase.Simple<KtReturnExpression, Unit>() {

    override fun getProblemDescription(
        element: KtReturnExpression,
        context: Unit,
    ): String = KotlinBundle.message("inspection.redundant.labeled.return.on.last.expression.in.lambda.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = returnExpressionVisitor {
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

    override fun getApplicableRanges(element: KtReturnExpression): List<TextRange> {
        val labelRange = element.labeledExpression
            ?.textRangeInParent
            ?.let { element.returnKeyword.textRangeInParent.union(it) }
        return listOfNotNull(labelRange)
    }

    override fun KaSession.prepareContext(element: KtReturnExpression) {
    }

    override fun createQuickFixes(
        element: KtReturnExpression,
        context: Unit,
    ): Array<KotlinModCommandQuickFix<KtReturnExpression>> {
        val smartPointer = element.createSmartPointer()

        return arrayOf(object : KotlinModCommandQuickFix<KtReturnExpression>() {

            override fun getFamilyName(): String =
                KotlinBundle.message("remove.labeled.return.from.last.expression.in.a.lambda")

            override fun getName(): String = getName(smartPointer) {
                KotlinBundle.message("remove.return.0", it.getLabelName().orEmpty())
            }

            override fun applyFix(
                project: Project,
                element: KtReturnExpression,
                updater: ModPsiUpdater,
            ) {
                val returnedExpression = element.returnedExpression
                if (returnedExpression == null) {
                    element.delete()
                } else {
                    element.replace(returnedExpression)
                }
            }
        })
    }
}
