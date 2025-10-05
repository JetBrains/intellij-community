// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class SelfAssignmentInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, String>(),
                                          CleanupLocalInspectionTool {

    override fun getProblemDescription(element: KtBinaryExpression, context: String): String =
        KotlinBundle.message("variable.0.is.assigned.to.itself", context)

    override fun createQuickFix(
        element: KtBinaryExpression,
        context: String,
    ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.self.assignment.fix.text")

        override fun applyFix(
            project: Project,
            element: KtBinaryExpression,
            updater: ModPsiUpdater,
        ) {
            element.delete()
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression): String? {
        val left = element.left
        val right = element.right

        val leftResolvedCall = left?.resolveToCall()?.singleVariableAccessCall()
        val leftCallee = leftResolvedCall?.symbol ?: return null
        val rightResolvedCall = right?.resolveToCall()?.singleVariableAccessCall()
        val rightCallee = rightResolvedCall?.symbol ?: return null

        if (leftCallee != rightCallee) return null

        val rightDeclaration = rightCallee.psi?.safeAs<KtVariableDeclaration>() ?: return null

        if (!rightDeclaration.isVar) return null
        if (rightDeclaration is KtProperty) {
            if (rightDeclaration.isOverridable()) return null
            if (rightDeclaration.accessors.any { !it.symbol.isDefault }) return null
        }

        if (left.receiverSymbol() != right.receiverSymbol()) return null

        return rightDeclaration.name
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        if (element.operationToken != KtTokens.EQ) return false
        val leftRefExpr = element.left?.asNameReferenceExpression() ?: return false
        val rightRefExpr = element.right?.asNameReferenceExpression() ?: return false

        return leftRefExpr.text == rightRefExpr.text
    }

    private fun KtExpression.asNameReferenceExpression(): KtNameReferenceExpression? = when (this) {
        is KtNameReferenceExpression -> this
        is KtDotQualifiedExpression -> (selectorExpression as? KtNameReferenceExpression).takeIf {
            receiverExpression is KtThisExpression || receiverExpression is KtNameReferenceExpression
        }
        else -> null
    }

    context(_: KaSession)
    private fun KtExpression.receiverSymbol(): KaSymbol? {
        when (val receiverExpression = (this as? KtDotQualifiedExpression)?.receiverExpression) {
            is KtThisExpression -> return receiverExpression.instanceReference.mainReference.resolveToSymbol()
            is KtNameReferenceExpression -> return receiverExpression.mainReference.resolveToSymbol()
        }

        return getImplicitReceiverSymbolIfExists()
    }

    context(_: KaSession)
    private fun KtExpression.getImplicitReceiverSymbolIfExists(): KaSymbol? {
        val implicitReceiver = this.resolveToCall()?.singleVariableAccessCall()?.partiallyAppliedSymbol?.let {
            it.dispatchReceiver ?: it.extensionReceiver
        }

        return when (implicitReceiver) {
            is KaImplicitReceiverValue -> implicitReceiver.symbol
            is KaSmartCastedReceiverValue -> {
                implicitReceiver.original.safeAs<KaImplicitReceiverValue>()?.symbol
            }
            else -> null
        }
    }
}
