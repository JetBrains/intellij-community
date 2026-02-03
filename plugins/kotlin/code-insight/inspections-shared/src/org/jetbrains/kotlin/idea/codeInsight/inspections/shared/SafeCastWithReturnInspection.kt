// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class SafeCastWithReturnInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {
    override fun getProblemDescription(
        element: KtBinaryExpression,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("should.be.replaced.with.if.type.check")

    override fun createQuickFix(
        element: KtBinaryExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {
        override fun getFamilyName() = KotlinBundle.message("replace.with.if.fix.text")

        override fun applyFix(
            project: Project,
            elvisExpression: KtBinaryExpression,
            updater: ModPsiUpdater
        ) {
            val returnExpression = KtPsiUtil.deparenthesize(elvisExpression.right) ?: return
            val safeCastExpression = KtPsiUtil.deparenthesize(elvisExpression.left) as? KtBinaryExpressionWithTypeRHS ?: return
            val typeReference = safeCastExpression.right ?: return
            val commentSaver = CommentSaver(elvisExpression)
            val result = elvisExpression.replace(
                KtPsiFactory(project).createExpressionByPattern(
                    "if ($0 !is $1) $2",
                    safeCastExpression.left,
                    typeReference,
                    returnExpression
                )
            )
            commentSaver.restore(result)
        }
    }

    override fun isApplicableByPsi(expression: KtBinaryExpression): Boolean {
        val left = expression.left?.safeDeparenthesize() as? KtBinaryExpressionWithTypeRHS ?: return false

        if (left.right == null) return false

        if (expression.operationReference.getReferencedName() != "?:") return false
        if (KtPsiUtil.deparenthesize(expression.right) !is KtReturnExpression) return false

        val leftExpressionReferenceName = left.operationReference.getReferencedName()
        return !(leftExpressionReferenceName != "as?" && leftExpressionReferenceName != "as")
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit? {
        val withRHS = element.left?.safeDeparenthesize() as? KtBinaryExpressionWithTypeRHS ?: return null
        if (element.isUsedAsExpression) {
            val lambda = withRHS.getStrictParentOfType<KtLambdaExpression>() ?: return null
            if (lambda.functionLiteral.bodyExpression?.statements?.lastOrNull() != element) return null
            val call = lambda.getStrictParentOfType<KtCallExpression>() ?: return null
            if (call.isUsedAsExpression) return null
        }
        return Unit
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }
}