// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinPsiDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.lambdaExpressionVisitor
import kotlin.reflect.KClass

internal class UnusedLambdaExpressionInspection : KotlinPsiDiagnosticBasedInspectionBase<KtLambdaExpression, KaFirDiagnostic.UnusedLambdaExpression, Unit>() {
    override val diagnosticType: KClass<KaFirDiagnostic.UnusedLambdaExpression>
        get() = KaFirDiagnostic.UnusedLambdaExpression::class

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = lambdaExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtLambdaExpression,
        context: Unit,
    ): @InspectionMessage String = KotlinBundle.message("inspection.unused.lambda.expression.inspection.problem.description")

    override fun KaSession.prepareContextByDiagnostic(
        element: KtLambdaExpression,
        diagnostic: KaFirDiagnostic.UnusedLambdaExpression,
    ): Unit = Unit

    override fun createQuickFix(
        element: KtLambdaExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtLambdaExpression> = object : KotlinModCommandQuickFix<KtLambdaExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("fix.add.return.before.lambda.expression")

        override fun applyFix(
            project: Project,
            element: KtLambdaExpression,
            updater: ModPsiUpdater
        ) {
            val factory = KtPsiFactory(project)
            element.replace(factory.createExpression("run ${element.text}"))
        }
    }
}
