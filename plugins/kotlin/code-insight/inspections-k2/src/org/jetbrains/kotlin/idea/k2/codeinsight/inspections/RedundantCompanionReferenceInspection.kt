// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.canBeRedundantCompanionReference
import org.jetbrains.kotlin.idea.base.analysis.api.utils.deleteReferenceFromQualifiedExpression
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isRedundantCompanionReference
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

class RedundantCompanionReferenceInspection : KotlinApplicableInspectionBase.Simple<KtSimpleNameExpression, Unit>(), CleanupLocalInspectionTool {
    override fun getProblemDescription(
        element: KtSimpleNameExpression,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("redundant.companion.reference")

    override fun createQuickFix(
        element: KtSimpleNameExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtSimpleNameExpression> = RemoveRedundantCompanionReferenceFix()

    override fun KaSession.prepareContext(element: KtSimpleNameExpression): Unit? {
        return element.isRedundantCompanionReference().asUnit
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {

        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtSimpleNameExpression): Boolean {
        return element.canBeRedundantCompanionReference()
    }

    private class RemoveRedundantCompanionReferenceFix : KotlinModCommandQuickFix<KtSimpleNameExpression>() {

        override fun getFamilyName() = KotlinBundle.message("remove.redundant.companion.reference.fix.text")

        override fun applyFix(
            project: Project,
            element: KtSimpleNameExpression,
            updater: ModPsiUpdater
        ) {
            element.deleteReferenceFromQualifiedExpression()
        }
    }

}