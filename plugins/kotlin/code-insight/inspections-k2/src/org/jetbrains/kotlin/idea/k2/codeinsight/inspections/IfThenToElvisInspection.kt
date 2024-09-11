// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getImplicitReceivers
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationData
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationStrategy
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationUtils
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isSimplifiableTo
import org.jetbrains.kotlin.idea.formatter.rightMarginOrDefault
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*

internal class IfThenToElvisInspectionData(
    val isUsedAsExpression: Boolean,
    val transformationStrategy: IfThenTransformationStrategy
)

internal class IfThenToElvisInspection @JvmOverloads constructor(
    @JvmField var highlightStatement: Boolean = false
) : KotlinApplicableInspectionBase.Simple<KtIfExpression, IfThenToElvisInspectionData>(), CleanupLocalInspectionTool {

    override fun getProblemDescription(element: KtIfExpression, context: IfThenToElvisInspectionData): String =
        KotlinBundle.message("if.then.foldable.to")

    override fun createQuickFix(
        element: KtIfExpression,
        context: IfThenToElvisInspectionData
    ): KotlinModCommandQuickFix<KtIfExpression> = object : KotlinModCommandQuickFix<KtIfExpression>() {
        override fun getFamilyName(): String = KotlinBundle.message("replace.if.expression.with.elvis.expression")
        override fun applyFix(
            project: Project,
            element: KtIfExpression,
            updater: ModPsiUpdater
        ) {
            val transformationData = IfThenTransformationUtils.buildTransformationData(element) as IfThenTransformationData
            val negatedClause = transformationData.negatedClause ?: return
            val psiFactory = KtPsiFactory(element.project)
            val commentSaver = CommentSaver(element, saveLineBreaks = false)
            val margin = element.containingKtFile.rightMarginOrDefault
            val replacedBaseClause = IfThenTransformationUtils.transformBaseClause(
                data = transformationData,
                strategy = context.transformationStrategy.withWritableData(updater)
            )
            val newExpr = element.replaced(
                psiFactory.createExpressionByPattern(
                    elvisPattern(replacedBaseClause.textLength + negatedClause.textLength + 5 >= margin),
                    replacedBaseClause,
                    negatedClause
                )
            )

            (KtPsiUtil.deparenthesize(newExpr) as KtBinaryExpression).also {
                commentSaver.restore(it)
            }
        }
    }

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> =
        ApplicabilityRanges.ifExpressionExcludingBranches(element)

    override fun getProblemHighlightType(element: KtIfExpression, context: IfThenToElvisInspectionData): ProblemHighlightType {
        if (context.transformationStrategy.shouldSuggestTransformation() && (highlightStatement || context.isUsedAsExpression)) {
            return ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        }
        else {
            return ProblemHighlightType.INFORMATION
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = ifExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    context(KaSession)
    override fun prepareContext(element: KtIfExpression): IfThenToElvisInspectionData? {
        val transformationData = IfThenTransformationUtils.buildTransformationData(element) ?: return null
        val transformationStrategy = IfThenTransformationStrategy.create(transformationData) ?: return null

        if (element.expressionType?.isUnitType != false) return null
        if (!clausesReplaceableByElvis(transformationData)) return null
        val checkedExpressions = listOfNotNull(
            transformationData.checkedExpression,
            transformationData.baseClause,
            transformationData.negatedClause,
        )
        if (checkedExpressions.any { it.hasUnstableSmartCast() }) return null

        return IfThenToElvisInspectionData(
            isUsedAsExpression = element.isUsedAsExpression,
            transformationStrategy = transformationStrategy
        )
    }

    private fun KtExpression.isNullOrBlockExpression(): Boolean {
        val innerExpression = this.getSingleUnwrappedStatementOrThis()
        return innerExpression is KtBlockExpression || innerExpression.node.elementType == KtNodeTypes.NULL
    }

    private fun elvisPattern(newLine: Boolean): String = if (newLine) "$0\n?: $1" else "$0 ?: $1"

    private fun KaSession.conditionHasIncompatibleTypes(data: IfThenTransformationData): Boolean {
        val isExpression = data.condition as? KtIsExpression ?: return false
        val targetType = isExpression.typeReference?.type ?: return true
        if (targetType.canBeNull) return true
        // TODO: the following check can be removed after fix of KT-14576
        val originalType = data.checkedExpression.expressionType ?: return true

        return !targetType.isSubtypeOf(originalType)
    }

    private fun KtExpression.anyArgumentEvaluatesTo(argument: KtExpression): Boolean {
        val callExpression = this as? KtCallExpression ?: return false
        val arguments = callExpression.valueArguments.map { it.getArgumentExpression() }
        return arguments.any { it?.isSimplifiableTo(argument) == true } && arguments.all { it is KtNameReferenceExpression }
    }

    private fun KaSession.hasImplicitReceiverReplaceableBySafeCall(data: IfThenTransformationData): Boolean =
        data.checkedExpression is KtThisExpression && getImplicitReceiverValue(data) != null

    private fun KaSession.getImplicitReceiverValue(data: IfThenTransformationData): KaReceiverValue? {
        val resolvedCall = data.baseClause.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return null
        return resolvedCall.getImplicitReceivers().firstOrNull()
    }

    private fun KtExpression.hasFirstReceiverOf(receiver: KtExpression): Boolean {
        val actualReceiver = (this as? KtDotQualifiedExpression)?.getLeftMostReceiverExpression() ?: return false
        return actualReceiver.isSimplifiableTo(receiver)
    }

    private val nullPointerExceptionNames = setOf(
        "kotlin.KotlinNullPointerException",
        "kotlin.NullPointerException",
        "java.lang.NullPointerException"
    )

    private fun KaSession.throwsNullPointerExceptionWithNoArguments(throwExpression: KtThrowExpression): Boolean {
        val thrownExpression = throwExpression.thrownExpression as? KtCallExpression ?: return false

        val nameExpression = thrownExpression.calleeExpression as? KtNameReferenceExpression ?: return false
        val resolveCall = nameExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
        val declDescriptor = resolveCall.symbol.containingDeclaration ?: return false

        val exceptionName = (declDescriptor as? KaClassLikeSymbol)?.classId?.asSingleFqName()?.asString() ?: return false
        return exceptionName in nullPointerExceptionNames && thrownExpression.valueArguments.isEmpty()
    }

    context(KaSession)
    private fun KtExpression.hasUnstableSmartCast(): Boolean {
        val expressionToCheck = when (this) {
            is KtThisExpression -> instanceReference
            else -> this
        }
        return expressionToCheck.smartCastInfo?.isStable == false
    }

    private fun KaSession.clausesReplaceableByElvis(data: IfThenTransformationData): Boolean =
        when {
            data.negatedClause == null || data.negatedClause?.isNullOrBlockExpression() == true ->
                false
            (data.negatedClause as? KtThrowExpression)?.let { throwsNullPointerExceptionWithNoArguments(it) } == true ->
                false
            conditionHasIncompatibleTypes(data) ->
                false
            data.baseClause.isSimplifiableTo(data.checkedExpression) ->
                true
            data.baseClause.anyArgumentEvaluatesTo(data.checkedExpression) ->
                true
            hasImplicitReceiverReplaceableBySafeCall(data) || data.baseClause.hasFirstReceiverOf(data.checkedExpression) ->
                data.baseClause.expressionType?.canBeNull == false
            else ->
                false
        }

    override fun getOptionsPane() = pane(
        checkbox("highlightStatement", KotlinBundle.message("report.also.on.statement"))
    )
}