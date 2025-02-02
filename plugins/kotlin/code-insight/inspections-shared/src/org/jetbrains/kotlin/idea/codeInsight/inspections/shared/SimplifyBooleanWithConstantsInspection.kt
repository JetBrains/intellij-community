// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SimplifyBooleanWithConstantsUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SimplifyBooleanWithConstantsUtils.performSimplification
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SimplifyBooleanWithConstantsUtils.topBinary
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.binaryExpressionVisitor

internal class SimplifyBooleanWithConstantsInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {
    override fun getProblemDescription(element: KtBinaryExpression, context: Unit): @InspectionMessage String {
        return KotlinBundle.message("inspection.simplify.boolean.with.constants.display.name")
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitorVoid = binaryExpressionVisitor { expression ->
        visitTargetElement(expression, holder, isOnTheFly)
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit? {
        return SimplifyBooleanWithConstantsUtils.areThereExpressionsToBeSimplified(element.topBinary()).asUnit
    }

    override fun createQuickFixes(
        element: KtBinaryExpression,
        context: Unit,
    ): Array<KotlinModCommandQuickFix<KtBinaryExpression>> = arrayOf(object : KotlinModCommandQuickFix<KtBinaryExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("simplify.boolean.expression")

        override fun applyFix(project: Project, element: KtBinaryExpression, updater: ModPsiUpdater) {
            performSimplification(element)
        }
    })
}