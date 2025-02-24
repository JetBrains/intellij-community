// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.branches
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.convertIfToWhen
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.getWhenConditionSubjectCandidate
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.matches
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis

class CascadeIfInspection : KotlinApplicableInspectionBase.Simple<KtIfExpression, Unit>() {

    override fun getProblemDescription(
        element: KtIfExpression,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("cascade.if.should.be.replaced.with.when")

    override fun createQuickFix(
        element: KtIfExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtIfExpression> = object : KotlinModCommandQuickFix<KtIfExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.if.with.when")

        override fun applyFix(
            project: Project,
            element: KtIfExpression,
            updater: ModPsiUpdater
        ) {
            convertIfToWhen(element, updater)
        }
    }

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> = ApplicabilityRanges.ifKeyword(element)

    override fun KaSession.prepareContext(element: KtIfExpression): Unit? {
        var current: KtIfExpression? = element
        var lastSubjectCandidate: KtExpression? = null
        while (current != null) {
            val subjectCandidate = current.condition.getWhenConditionSubjectCandidate(checkConstants = false) ?: return null
            if (lastSubjectCandidate != null && !lastSubjectCandidate.matches(subjectCandidate)) return null
            lastSubjectCandidate = subjectCandidate
            current = current.`else` as? KtIfExpression
        }
        return Unit
    }

    override fun isApplicableByPsi(element: KtIfExpression): Boolean {
        val branches = element.branches
        if (branches.size <= 2) return false
        if (element.isOneLiner()) return false

        if (branches.any {
                it == null || it.lastBlockStatementOrThis() is KtIfExpression
            }
        ) return false

        if (element.parent.node.elementType == KtNodeTypes.ELSE) return false

        if (element.anyDescendantOfType<KtExpressionWithLabel> {
                it is KtBreakExpression || it is KtContinueExpression
            }
        ) return false
        return true
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitIfExpression(expression: KtIfExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }
}
