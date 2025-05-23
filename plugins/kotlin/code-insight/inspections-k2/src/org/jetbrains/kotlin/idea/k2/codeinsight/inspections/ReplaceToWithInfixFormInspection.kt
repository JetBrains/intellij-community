// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.psi.*

private const val TO_FUNCTION_NAME = "to"

internal class ReplaceToWithInfixFormInspection : KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, Unit>() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = dotQualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): @InspectionMessage String = KotlinBundle.message("inspection.replace.to.with.infix.form.display.name")

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val callExpression = element.callExpression ?: return false
        return callExpression.valueArguments.size == 1 &&
                callExpression.typeArgumentList == null &&
                element.calleeName == TO_FUNCTION_NAME
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val functionSymbol = call.symbol as? KaNamedFunctionSymbol ?: return null
        return functionSymbol.isInfix.asUnit
    }

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = ReplaceToWithInfixFormQuickFix()
}

private class ReplaceToWithInfixFormQuickFix : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.to.with.infix.form.quickfix.text")

    override fun applyFix(
        project: Project,
        element: KtDotQualifiedExpression,
        updater: ModPsiUpdater,
    ) {
        val argumentExpression = element.callExpression?.valueArguments?.first()?.getArgumentExpression() ?: return
        val newExpression = createInfixExpression(project, element.receiverExpression, argumentExpression)
        element.replace(newExpression)
    }

    private fun createInfixExpression(
        project: Project,
        receiver: KtExpression,
        argument: KtExpression,
    ): KtExpression = KtPsiFactory(project).createExpressionByPattern("$0 to $1", receiver, argument)
}

private val KtQualifiedExpression.calleeName: String?
    get() = (callExpression?.calleeExpression as? KtNameReferenceExpression)?.text
