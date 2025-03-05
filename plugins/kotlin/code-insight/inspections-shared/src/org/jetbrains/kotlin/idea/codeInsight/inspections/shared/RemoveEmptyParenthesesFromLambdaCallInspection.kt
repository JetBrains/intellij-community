// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaSuccessCallInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RemoveEmptyParenthesesFromLambdaCallUtils.canRemoveByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RemoveEmptyParenthesesFromLambdaCallUtils.removeArgumentList
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class RemoveEmptyParenthesesFromLambdaCallInspection : KotlinApplicableInspectionBase.Simple<KtValueArgumentList, Unit>(),
                                                                CleanupLocalInspectionTool {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitValueArgumentList(list: KtValueArgumentList) {
            visitTargetElement(list, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtValueArgumentList,
        context: Unit,
    ): String = KotlinBundle.message("inspection.remove.empty.parentheses.from.lambda.call.display.name")

    override fun isApplicableByPsi(element: KtValueArgumentList): Boolean = canRemoveByPsi(element)

    override fun KaSession.prepareContext(element: KtValueArgumentList): Unit? =
        ((element.parent as? KtCallExpression)
            ?.resolveToCall() is KaSuccessCallInfo)
            .asUnit

    override fun createQuickFix(
        element: KtValueArgumentList,
        context: Unit,
    ): KotlinModCommandQuickFix<KtValueArgumentList> = object : KotlinModCommandQuickFix<KtValueArgumentList>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.remove.empty.parentheses.from.lambda.call.action.name")

        override fun applyFix(
            project: Project,
            element: KtValueArgumentList,
            updater: ModPsiUpdater,
        ) {
            removeArgumentList(element)
        }
    }
}