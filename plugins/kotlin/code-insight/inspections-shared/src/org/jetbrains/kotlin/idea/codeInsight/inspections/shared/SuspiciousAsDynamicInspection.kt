// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaDynamicType
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal class SuspiciousAsDynamicInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {

    override fun buildVisitor(
      holder: ProblemsHolder,
      isOnTheFly: Boolean,
    ) = callExpressionVisitor {
      visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (!element.platform.isJs()) return false
        if (element.calleeExpression?.text != "asDynamic") return false
        return element.getQualifiedExpressionForSelector()?.receiverExpression != null
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        val receiver = element.getQualifiedExpressionForSelector()?.receiverExpression ?: return null
        return (receiver.expressionType is KaDynamicType).asUnit
    }

    override fun getProblemDescription(
      element: KtCallExpression,
      context: Unit,
    ): @InspectionMessage String = KotlinBundle.message("suspicious.asdynamic.member.invocation")

    override fun createQuickFix(
      element: KtCallExpression,
      context: Unit
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.as.dynamic.call.fix.text")

        override fun applyFix(
          project: Project,
          element: KtCallExpression,
          updater: ModPsiUpdater,
        ) {
            val qualifiedExpression = element.getQualifiedExpressionForSelector() ?: return
            qualifiedExpression.replace(qualifiedExpression.receiverExpression)
        }
    }
}