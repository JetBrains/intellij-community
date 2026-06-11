// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.analysis.isNotInjectedOrShouldBeAnalyzed
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.k2.refactoring.util.isRedundantUnit
import org.jetbrains.kotlin.idea.k2.refactoring.util.isUnitLiteral
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.referenceExpressionVisitor

class RedundantUnitExpressionInspection : KotlinApplicableInspectionBase.Simple<KtReferenceExpression, Unit>(), CleanupLocalInspectionTool {
    override fun isAvailableForFile(file: PsiFile): Boolean =
        file.isNotInjectedOrShouldBeAnalyzed

    override fun isApplicableByPsi(element: KtReferenceExpression): Boolean =
        element.isUnitLiteral()

    override fun KaSession.prepareContext(element: KtReferenceExpression): Unit? =
        isRedundantUnit(element).asUnit

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = referenceExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtReferenceExpression,
        context: Unit
    ): @InspectionMessage String =
        KotlinBundle.message("redundant.unit")

    override fun createQuickFix(
        element: KtReferenceExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtReferenceExpression> = object : KotlinModCommandQuickFix<KtReferenceExpression>() {
        override fun getFamilyName(): String = KotlinBundle.message("remove.redundant.unit.fix.text")

        override fun applyFix(project: Project, element: KtReferenceExpression, updater: ModPsiUpdater) {
            element.delete()
        }
    }
}