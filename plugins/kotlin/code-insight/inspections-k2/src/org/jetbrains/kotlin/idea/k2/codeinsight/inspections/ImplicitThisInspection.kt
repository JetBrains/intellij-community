// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableModCommandInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.ImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsight.utils.getImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render

internal class ImplicitThisInspection :
    AbstractKotlinApplicableModCommandInspectionWithContext<KtExpression, ImplicitReceiverInfo>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitExpression(expression: KtExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
    override fun getProblemDescription(element: KtExpression, context: ImplicitReceiverInfo): String =
        KotlinBundle.message("inspection.implicit.this.display.name")

    override fun getActionFamilyName(): String = KotlinBundle.message("inspection.implicit.this.action.name")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        return when (element) {
            is KtSimpleNameExpression -> {
                if (element !is KtNameReferenceExpression) return false
                if (element.parent is KtThisExpression) return false
                if (element.parent is KtCallableReferenceExpression) return false
                if (element.isSelectorOfDotQualifiedExpression()) return false
                val parent = element.parent
                if (parent is KtCallExpression && parent.isSelectorOfDotQualifiedExpression()) return false
                true
            }
            is KtCallableReferenceExpression -> element.receiverExpression == null
            else -> false
        }
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtExpression): ImplicitReceiverInfo? {
        return element.getImplicitReceiverInfo()
    }

    override fun apply(element: KtExpression, context: ImplicitReceiverInfo, project: Project, updater: ModPsiUpdater) {
        element.addImplicitThis(context)
    }
}

private fun KtExpression.isSelectorOfDotQualifiedExpression(): Boolean {
    val parent = parent
    return parent is KtDotQualifiedExpression && parent.selectorExpression == this
}

private fun KtExpression.addImplicitThis(input: ImplicitReceiverInfo) {
    val reference = if (this is KtCallableReferenceExpression) callableReference else this
    val thisExpressionText = if (input.isUnambiguousLabel) "this" else "this@${input.receiverLabel?.render()}"
    val factory = KtPsiFactory(project)
    with(reference) {
        when (parent) {
            is KtCallExpression -> parent.replace(factory.createExpressionByPattern("$0.$1", thisExpressionText, parent))
            is KtCallableReferenceExpression -> parent.replace(
                factory.createExpressionByPattern(
                    "$0::$1", thisExpressionText, this
                )
            )
            else -> this.replace(factory.createExpressionByPattern("$0.$1", thisExpressionText, this))
        }
    }
}
