// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class RemoveEmptySecondaryConstructorBodyInspection :
    KotlinApplicableInspectionBase.Simple<KtBlockExpression, Unit>(), CleanupLocalInspectionTool {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitBlockExpression(expression: KtBlockExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtBlockExpression): Unit? {
        if (element.parent !is KtSecondaryConstructor) return null
        if (element.statements.isNotEmpty()) return null

        return Unit.takeIf { element.text.replace("{", "").replace("}", "").isBlank() }
    }

    override fun getProblemDescription(
        element: KtBlockExpression,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("remove.empty.constructor.body")

    override fun createQuickFix(
        element: KtBlockExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtBlockExpression> = object : KotlinModCommandQuickFix<KtBlockExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.empty.constructor.body")

        override fun applyFix(
            project: Project,
            element: KtBlockExpression,
            updater: ModPsiUpdater,
        ) {
            element.delete()
        }
    }
}