// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expectedType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames.KOTLIN_REFLECT_FQ_NAME
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.k2.refactoring.util.ConvertReferenceToLambdaUtil
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class ConvertReferenceToLambdaInspection : KotlinApplicableInspectionBase.Simple<KtCallableReferenceExpression, String>() {
    override fun getProblemDescription(
        element: KtCallableReferenceExpression,
        context: String,
    ): String = KotlinBundle.message("convert.reference.to.lambda.before.text")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {
        override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtCallableReferenceExpression): List<TextRange> =
        listOf(TextRange(0, element.textLength))

    override fun KaSession.prepareContext(element: KtCallableReferenceExpression): String? {
        if (skip(element)) return null
        return ConvertReferenceToLambdaUtil.prepareLambdaExpressionText(element)
    }

    context(_: KaSession)
    private fun skip(element: KtCallableReferenceExpression): Boolean {
        val expectedType = element.expectedType ?: return false
        val classId = (expectedType as? KaClassType)?.classId ?: return false
        val packageFqName = classId.packageFqName
        return !packageFqName.isRoot && packageFqName == KOTLIN_REFLECT_FQ_NAME
    }

    override fun createQuickFix(
        element: KtCallableReferenceExpression,
        context: String,
    ): KotlinModCommandQuickFix<KtCallableReferenceExpression> = object : KotlinModCommandQuickFix<KtCallableReferenceExpression>() {
        override fun getFamilyName(): String = KotlinBundle.message("convert.reference.to.lambda")

        override fun applyFix(
            project: Project,
            element: KtCallableReferenceExpression,
            updater: ModPsiUpdater,
        ) {
            ConvertReferenceToLambdaUtil.convertReferenceToLambdaExpression(element, context)
        }
    }
}
