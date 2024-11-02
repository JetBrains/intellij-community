// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class ReplaceWithOrdinaryAssignmentIntention : KotlinApplicableModCommandAction<KtBinaryExpression, Unit>(
    KtBinaryExpression::class,
) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.ordinary.assignment")
    override fun getPresentation(context: ActionContext, element: KtBinaryExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun getApplicableRanges(element: KtBinaryExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.operationReference }

    context(KaSession)
    override fun prepareContext(element: KtBinaryExpression): Unit? = isApplicableTo(element).asUnit

    private fun isApplicableTo(element: KtBinaryExpression): Boolean {
        val operationReference = element.operationReference
        if (element.operationToken !in KtTokens.AUGMENTED_ASSIGNMENTS) return false
        val left = element.left ?: return false
        if (left.safeAs<KtQualifiedExpression>()?.receiverExpression is KtQualifiedExpression) return false
        if (element.right == null) return false

        analyze(element) {
            val resultingSymbol = operationReference.mainReference.resolveToSymbol() as? KaFunctionSymbol ?: return false
            return resultingSymbol.callableId?.callableName !in OperatorNameConventions.ASSIGNMENT_OPERATIONS
        }
    }

    override fun invoke(actionContext: ActionContext, element: KtBinaryExpression, elementContext: Unit, updater: ModPsiUpdater) {
        val left = element.left!!
        val right = element.right!!
        val psiFactory = KtPsiFactory(element.project)

        val assignOpText = element.operationReference.text
        assert(assignOpText.endsWith("="))
        val operationText = assignOpText.substring(0, assignOpText.length - 1)

        element.replace(psiFactory.createExpressionByPattern("$0 = $0 $operationText $1", left, right))
    }
}
