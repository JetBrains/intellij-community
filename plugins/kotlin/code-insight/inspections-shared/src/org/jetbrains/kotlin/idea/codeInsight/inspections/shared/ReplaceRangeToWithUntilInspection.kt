// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.AbstractReplaceRangeToInspection.Context
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.canUseRangeUntil
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.isFloatingPointType
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.isIntegralType
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.createExpressionByPattern

abstract class AbstractReplaceRangeToInspection : AbstractRangeInspection<Context>() {
    data class Context(
        val left: SmartPsiElementPointer<KtExpression>,
        val right: SmartPsiElementPointer<KtExpression>,
    )

    abstract fun KaSession.isApplicableToRangeExpression(expression: KtExpression): Boolean
    abstract fun KaSession.isApplicableArgumentType(type: KaType): Boolean

    abstract val replacementPattern: String
    abstract val problemDescription: @InspectionMessage String
    abstract val quickFixDescription: @IntentionFamilyName String

    override fun getProblemDescription(
        range: RangeExpression,
        context: Context
    ): @InspectionMessage String = problemDescription

    override fun isApplicableByPsi(range: RangeExpression): Boolean =
        range.type == RangeKtExpressionType.RANGE_TO

    override fun KaSession.prepareContext(range: RangeExpression): Context? {
        if (!isApplicableToRangeExpression(range.expression)) return null

        val (left, right) = range.arguments
        val leftType = left?.expressionType ?: return null
        val rightType = right?.expressionType ?: return null
        if (!isApplicableArgumentType(leftType) || !isApplicableArgumentType(rightType)) return null

        val rightUnfolded = unfoldMinusOne(KtPsiUtil.safeDeparenthesize(right)) ?: return null
        return Context(
            left.createSmartPointer(),
            rightUnfolded.createSmartPointer(),
        )
    }

    private fun KaSession.unfoldMinusOne(expression: KtExpression): KtExpression? {
        if (expression !is KtBinaryExpression || expression.operationToken != KtTokens.MINUS) return null

        val constantValue = expression.right?.evaluate() ?: return null
        if ((constantValue.value as? Number)?.toInt() != 1) return null
        return expression.left
    }

    private class ReplaceRangeToQuickFix(
        private val replacementPattern: String,
        private val description: @IntentionFamilyName String,
        private val context: Context,
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String = description

        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater
        ) {
            val left = context.left.element ?: return
            val right = context.right.element ?: return

            element.replace(
                KtPsiFactory(project)
                    .createExpressionByPattern(replacementPattern, left, right)
            )
        }
    }

    override fun createQuickFix(
        range: RangeExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtExpression> =
        ReplaceRangeToQuickFix(
            replacementPattern = replacementPattern,
            description = quickFixDescription,
            context = context,
        )
}

class ReplaceRangeToWithUntilInspection : AbstractReplaceRangeToInspection() {
    override fun KaSession.isApplicableToRangeExpression(expression: KtExpression): Boolean =
        !expression.canUseRangeUntil()

    override fun KaSession.isApplicableArgumentType(type: KaType): Boolean =
        type.isIntegralType

    override val replacementPattern: String
        get() = "$0 until $1"

    override val problemDescription: @InspectionMessage String
        get() = KotlinBundle.message("inspection.replace.range.to.with.until.display.name")
    override val quickFixDescription: @IntentionFamilyName String
        get() = KotlinBundle.message("replace.with.until.quick.fix.text")
}

class ReplaceRangeToWithRangeUntilInspection : AbstractReplaceRangeToInspection() {
    override fun KaSession.isApplicableToRangeExpression(expression: KtExpression): Boolean =
        expression.canUseRangeUntil()

    override fun KaSession.isApplicableArgumentType(type: KaType): Boolean =
        type.isIntegralType || type.isFloatingPointType

    override val replacementPattern: String
        get() = "$0..<$1"

    override val problemDescription: @InspectionMessage String
        get() = KotlinBundle.message("inspection.replace.range.to.with.rangeUntil.display.name")
    override val quickFixDescription: @IntentionFamilyName String
        get() = KotlinBundle.message("replace.with.rangeUntil.quick.fix.text")
}