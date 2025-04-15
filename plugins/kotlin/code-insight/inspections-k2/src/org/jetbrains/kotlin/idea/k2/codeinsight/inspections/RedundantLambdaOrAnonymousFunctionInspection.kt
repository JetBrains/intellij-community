// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.k2.refactoring.inline.KotlinInlineAnonymousFunctionProcessor
import org.jetbrains.kotlin.psi.*

class RedundantLambdaOrAnonymousFunctionInspection : KotlinApplicableInspectionBase.Simple<KtFunction, Unit>() {
    override fun getProblemDescription(
        element: KtFunction,
        context: Unit
    ): @InspectionMessage String = if (element is KtNamedFunction)
        KotlinBundle.message("inspection.redundant.anonymous.function.description")
    else
        KotlinBundle.message("inspection.redundant.lambda.description")

    override fun KaSession.prepareContext(element: KtFunction) {
        return Unit
    }

    override fun isApplicableByPsi(element: KtFunction): Boolean {
        return element.hasBody() && KotlinInlineAnonymousFunctionProcessor.findCallExpression(element) != null
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
            visitTargetElement(lambdaExpression.functionLiteral, holder, isOnTheFly)
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            visitTargetElement(function, holder, isOnTheFly)
        }
    }

    override fun createQuickFix(
        element: KtFunction,
        context: Unit
    ): KotlinModCommandQuickFix<KtFunction> = RedundantLambdaOrAnonymousFunctionFix()

    private class RedundantLambdaOrAnonymousFunctionFix : KotlinModCommandQuickFix<KtFunction>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("inspection.redundant.lambda.or.anonymous.function.fix")

        override fun applyFix(project: Project, element: KtFunction, updater: ModPsiUpdater) {
            val call = KotlinInlineAnonymousFunctionProcessor.findCallExpression(element) ?: return
            KotlinInlineAnonymousFunctionProcessor.performRefactoring(call, element.findExistingEditor())
        }
    }
}
