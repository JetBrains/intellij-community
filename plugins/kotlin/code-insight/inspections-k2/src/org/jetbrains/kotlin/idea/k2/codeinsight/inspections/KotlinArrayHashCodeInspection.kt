// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinArrayHashCodeInspection.Context
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern

private val HASH_CODE_CALLABLE_ID = CallableId(StandardClassIds.Any, Name.identifier("hashCode"))

internal class KotlinArrayHashCodeInspection : KotlinApplicableInspectionBase<KtQualifiedExpression, Context>() {
    data class Context(val isNestedArray: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtQualifiedExpression): Boolean {
        val callExpression = element.selectorExpression as? KtCallExpression ?: return false
        if (callExpression.valueArguments.isNotEmpty()) return false
        val calleeName = callExpression.calleeExpression?.text ?: return false
        return calleeName == "hashCode"
    }

    override fun KaSession.prepareContext(element: KtQualifiedExpression): Context? {
        val receiverType = element.receiverExpression.expressionType ?: return null
        if (!receiverType.isArrayOrPrimitiveArray) return null

        val callExpression = element.selectorExpression as? KtCallExpression ?: return null
        val call = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val functionSymbol = call.symbol as? KaNamedFunctionSymbol ?: return null
        if (functionSymbol.callableId != HASH_CODE_CALLABLE_ID) return null

        return Context(receiverType.isNestedArray)
    }

    override fun getApplicableRanges(element: KtQualifiedExpression): List<TextRange> {
        val selectorExpression = element.selectorExpression ?: return emptyList()
        return ApplicabilityRange.single(element) { selectorExpression }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtQualifiedExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val message = KotlinBundle.message("array.hash.code.problem.descriptor")
        val fixes = if (context.isNestedArray) {
            arrayOf(ReplaceWithContentHashCodeFix(), ReplaceWithContentDeepHashCodeFix())
        } else {
            arrayOf(ReplaceWithContentHashCodeFix())
        }

        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ message,
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ *fixes
        )
    }
}

private class ReplaceWithContentHashCodeFix : KotlinModCommandQuickFix<KtQualifiedExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.content.hash.code")

    override fun applyFix(project: Project, element: KtQualifiedExpression, updater: ModPsiUpdater) {
        val receiver = element.receiverExpression
        val operator = element.operationSign.value // . or ?.
        val newExpression = KtPsiFactory(project).createExpressionByPattern("$0${operator}contentHashCode()", receiver)
        element.replace(newExpression)
    }
}

private class ReplaceWithContentDeepHashCodeFix : KotlinModCommandQuickFix<KtQualifiedExpression>(), HighPriorityAction {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.content.deep.hash.code")

    override fun applyFix(project: Project, element: KtQualifiedExpression, updater: ModPsiUpdater) {
        val receiver = element.receiverExpression
        val operator = element.operationSign.value // . or ?.
        val newExpression = KtPsiFactory(project).createExpressionByPattern("$0${operator}contentDeepHashCode()", receiver)
        element.replace(newExpression)
    }
}
