// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RedundantCompanionReferenceInspection : KotlinApplicableInspectionBase.Simple<KtReferenceExpression, Unit>() {
    override fun getProblemDescription(
        element: KtReferenceExpression,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("redundant.companion.reference")

    override fun createQuickFix(
        element: KtReferenceExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtReferenceExpression> = RemoveRedundantCompanionReferenceFix()

    override fun KaSession.prepareContext(element: KtReferenceExpression): Unit? {
        return isRedundantCompanionReference(element).asUnit
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {

        override fun visitReferenceExpression(expression: KtReferenceExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtReferenceExpression): Boolean {
        val parent = element.parent as? KtDotQualifiedExpression ?: return false
        if (parent.getStrictParentOfType<KtImportDirective>() != null) return false
        val grandParent = parent.parent as? KtElement
        val selectorExpression = parent.selectorExpression
        if (element == selectorExpression && grandParent !is KtDotQualifiedExpression) return false
        return element == selectorExpression || element.text != (selectorExpression as? KtNameReferenceExpression)?.text
    }

    private fun KaSession.isRedundantCompanionReference(reference: KtReferenceExpression): Boolean {
        val parent = reference.parent as? KtDotQualifiedExpression ?: return false

        val referenceName = reference.text

        val symbol = reference.mainReference.resolveToSymbol()
        val objectDeclaration =
            if (symbol is KaNamedClassSymbol && symbol.classKind == KaClassKind.COMPANION_OBJECT) {
                // Try to get the PSI for the companion object
                symbol.psi as? KtObjectDeclaration
            } else {
                null
            } ?: return false

        if (referenceName != objectDeclaration.name) return false

        val grandParent = parent.parent as? KtElement
        val selectorExpression = parent.selectorExpression

        val (oldTargetExpression, simplifiedText) = if (grandParent is KtDotQualifiedExpression && reference == selectorExpression) {
            grandParent.selectorExpression to (parent.receiverExpression.text + "." + grandParent.selectorExpression?.text)
        } else {
            parent.selectorExpression to parent.selectorExpression!!.text
        }

        val oldTarget = oldTargetExpression?.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.symbol?.psi ?: return false
        val fragment = KtPsiFactory(reference.project).createExpressionCodeFragment(
            simplifiedText,
            reference
        )
        val q = fragment.getContentElement() ?: return false
        return oldTarget == analyze(q) {
            val partiallyAppliedSymbol = q.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
            partiallyAppliedSymbol?.symbol?.psi
        }
    }

    private class RemoveRedundantCompanionReferenceFix : KotlinModCommandQuickFix<KtReferenceExpression>() {

        override fun getFamilyName() = KotlinBundle.message("remove.redundant.companion.reference.fix.text")

        override fun applyFix(
            project: Project,
            element: KtReferenceExpression,
            updater: ModPsiUpdater
        ) {
            val parent = element.parent as? KtDotQualifiedExpression ?: return
            val selector = parent.selectorExpression ?: return
            val receiver = parent.receiverExpression
            if (element == receiver) parent.replace(selector) else parent.replace(receiver)
        }
    }

}