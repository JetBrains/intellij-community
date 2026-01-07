// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeinsight.utils.getRangeBinaryExpressionType
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

/**
 * Highlights ranges that are known to be empty at compile time and suggests replacing the operator.
 */
internal class EmptyRangeInspection : KotlinApplicableInspectionBase<KtElement, EmptyRangeInspection.Context>() {

    data class Context(val replacementOperator: String, val messageKey: String, val messageParam: String?)

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtElement): Context? {
        if (!isAvailable(element)) return null
        return determineContextFromElement(element as? KtExpression ?: return null)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = 
        object : KtTreeVisitorVoid() {
            override fun visitKtElement(element: KtElement) = visitTargetElement(element, holder, isOnTheFly)
        }
    override fun InspectionManager.createProblemDescriptor(
        element: KtElement,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val message = KotlinBundle.message(context.messageKey, *listOfNotNull(context.messageParam).toTypedArray())
        val fixes = if (context.messageParam != null) arrayOf(ReplaceFix(context.replacementOperator)) else emptyArray()
        
        return createProblemDescriptor(element, rangeInElement, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly, *fixes)
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isAvailable(element: KtElement): Boolean =
        //checks if the reference expressions are used in the range
        (element as? KtBinaryExpression)?.let { it.left is KtNameReferenceExpression || it.right is KtNameReferenceExpression } == true ||
        element.diagnostics(KaDiagnosticCheckerFilter.ONLY_EXPERIMENTAL_CHECKERS).any { it is KaFirDiagnostic.EmptyRange }


    private fun KaSession.determineContextFromElement(element: KtExpression): Context? {
        val rangeType = getRangeBinaryExpressionType(element) ?: return null
        val (start, end) = getComparableArguments<Comparable<Any>>(element) ?: return null
        val isIterable = element.expressionType?.isSubtypeOf(StandardClassIds.Iterable) == true
        
        return when (rangeType) {
            RangeKtExpressionType.RANGE_TO -> 
                if (start > end) createContext("downTo", isIterable) else null
                
            RangeKtExpressionType.UNTIL, RangeKtExpressionType.RANGE_UNTIL -> when {
                start > end -> createContext("downTo", isIterable)
                start == end -> Context("..", "this.range.is.empty.did.you.mean.to.use.0", "rangeTo")
                else -> null
            }
            
            RangeKtExpressionType.DOWN_TO -> 
                if (start < end) createContext("..", isIterable, "rangeTo") else null
        }
    }
    
    private fun createContext(operator: String, isIterable: Boolean, param: String = operator): Context =
        if (isIterable) Context(operator, "this.range.is.empty.did.you.mean.to.use.0", param)
        else Context("", "this.range.is.empty", null)

    @Suppress("UNCHECKED_CAST")
    private fun <T> KaSession.getComparableArguments(element: KtExpression): Pair<T, T>? where T : Comparable<T> {
        val (left, right) = getRangeArguments(element) ?: return null
        
        fun KtExpression.normalizedValue(): T? = when (this) {
            is KtNameReferenceExpression -> {
                val initializer = when (val resolved = mainReference.resolve()) {
                    is KtProperty -> if (resolved.isVar) null else resolved.initializer
                    is KtParameter -> resolved.defaultValue
                    else -> null
                }
                initializer?.normalizedValue()
            }
            else -> evaluate()?.value?.let { if (it is Number && it !is Double && it !is Float) it.toLong() else it } as? T
        }

        return left.normalizedValue()?.let { start -> right.normalizedValue()?.let { end -> start to end } }
    }

    private class ReplaceFix(private val rangeOperator: String) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getName() = KotlinBundle.message("replace.with.0", rangeOperator)
        override fun getFamilyName(): @IntentionFamilyName String = name

        override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
            val (left, right) = getRangeArguments(element) ?: return
            element.replace(KtPsiFactory(project).createExpressionByPattern("$0 $rangeOperator $1", left, right))
        }
    }

}

private fun getRangeArguments(expression: KtExpression): Pair<KtExpression, KtExpression>? {
    return when (expression) {
        is KtBinaryExpression -> expression.left to expression.right
        is KtDotQualifiedExpression -> {
            val right = expression.callExpression?.valueArguments?.singleOrNull()?.getArgumentExpression()
            expression.receiverExpression to right
        }

        else -> null
    }?.let { (left, right) ->
        if (left != null && right != null) left to right else null
    }
}
