// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.JavaMapForEachInspectionUtils
import org.jetbrains.kotlin.idea.refactoring.singleLambdaArgumentExpression
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

internal class JavaMapForEachInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, JavaMapForEachInspection.Context>() {

    internal class Context(val lambda: KtLambdaExpression)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor { call ->
        visitTargetElement(call, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        return JavaMapForEachInspectionUtils.applicableByPsi(element)
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val receiverType = call.partiallyAppliedSymbol.dispatchReceiver?.type ?: return null
        if (!receiverType.isSubtypeOf(StandardClassIds.Map)) return null

        val lambda = element.singleLambdaArgumentExpression() ?: return null
        return Context(lambda)
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.calleeExpression }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context,
    ): @InspectionMessage String =
        KotlinBundle.message("java.map.foreach.method.call.should.be.replaced.with.kotlin.s.foreach")

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.kotlin.s.foreach")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val lambda = updater.getWritable(context.lambda)
            val valueParameters = lambda.valueParameters
            if (valueParameters.size != 2) return
            val oldParameterList = lambda.functionLiteral.valueParameterList ?: return

            val psiFactory = KtPsiFactory(project)
            val newParameterList = psiFactory.createLambdaParameterList("(${valueParameters[0].text}, ${valueParameters[1].text})")
            oldParameterList.replace(newParameterList)
        }
    }
}
