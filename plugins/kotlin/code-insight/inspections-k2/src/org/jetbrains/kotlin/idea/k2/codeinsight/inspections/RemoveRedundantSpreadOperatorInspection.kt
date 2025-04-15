// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isArrayOfFunction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RemoveRedundantSpreadOperatorInspection : KotlinApplicableInspectionBase.Simple<KtValueArgument, Unit>() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = valueArgumentVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(element: KtValueArgument, context: Unit): @InspectionMessage String =
        KotlinBundle.message("remove.redundant.spread.operator.quickfix.text")

    override fun getApplicableRanges(element: KtValueArgument): List<TextRange> {
        val spreadElement = element.getSpreadElement() ?: return emptyList()
        if (element.isNamed()) return emptyList()
        val argumentExpression = element.getArgumentExpression() ?: return emptyList()
        val argumentOffset = element.startOffset
        val startOffset = spreadElement.startOffset - argumentOffset

        val endOffset = when (argumentExpression) {
            is KtCallExpression -> argumentExpression.calleeExpression!!.endOffset - argumentOffset
            is KtCollectionLiteralExpression -> startOffset + 1
            else -> return emptyList()
        }

        return listOf(TextRange(startOffset, endOffset))
    }

    override fun KaSession.prepareContext(element: KtValueArgument): Unit? {
        return when (val argumentExpression = element.getArgumentExpression()) {
            is KtCallExpression -> {
                if (!argumentExpression.isArrayOfFunction()) return null
                val call = element.getStrictParentOfType<KtCallExpression>() ?: return null
                val argumentIndex = call.valueArguments.indexOfFirst { it == element }
                val callCopyPointer = (call.copy() as? KtCallExpression)?.createSmartPointer() ?: return null
                val elementCopy = callCopyPointer.element?.valueArgumentList?.arguments?.getOrNull(argumentIndex) ?: return null

                removeRedundantSpreadOperator(element.project, elementCopy)

                val newCall = KtPsiFactory(element.project).createExpressionCodeFragment(
                    (callCopyPointer.element ?: return null).text,
                    element,
                ).getContentElement() as? KtCallExpression ?: return null

                val oldTarget = resolveCallToPsiElement(call) ?: return null

                oldTarget == resolveCallToPsiElement(newCall)
            }
            is KtCollectionLiteralExpression -> true
            else -> false
        }.asUnit
    }

    override fun createQuickFix(
        element: KtValueArgument,
        context: Unit,
    ): KotlinModCommandQuickFix<KtValueArgument> = object : KotlinModCommandQuickFix<KtValueArgument>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.redundant.spread.operator.quickfix.text")

        override fun applyFix(
            project: Project,
            element: KtValueArgument,
            updater: ModPsiUpdater,
        ): Unit = removeRedundantSpreadOperator(project, element)
    }
}

private fun KaSession.resolveCallToPsiElement(call: KtExpression): PsiElement? = call.resolveToCall()
    ?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
    ?.partiallyAppliedSymbol
    ?.symbol
    ?.psi

private fun removeRedundantSpreadOperator(
    project: Project,
    element: KtValueArgument,
) {
    // Argument & expression under *
    val spreadArgumentExpression = element.getArgumentExpression() ?: return
    val outerArgumentList = element.getStrictParentOfType<KtValueArgumentList>() ?: return
    // Arguments under arrayOf or []
    val innerArgumentExpressions =
        when (spreadArgumentExpression) {
            is KtCallExpression -> spreadArgumentExpression.valueArgumentList?.arguments?.map {
                it.getArgumentExpression() to it.isSpread
            }
            is KtCollectionLiteralExpression -> spreadArgumentExpression.getInnerExpressions().map { it to false }
            else -> null
        } ?: return

    val factory = KtPsiFactory(project)
    innerArgumentExpressions.reversed().forEach { (expression, isSpread) ->
        outerArgumentList.addArgumentAfter(factory.createArgument(expression, isSpread = isSpread), element)
    }
    outerArgumentList.removeArgument(element)
}
