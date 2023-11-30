// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.EmptinessCheckFunctionUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class ConvertBinaryExpressionWithDemorgansLawIntention :
    AbstractKotlinModCommandWithContext<KtBinaryExpression, ConvertBinaryExpressionWithDemorgansLawIntention.Context>(
        KtBinaryExpression::class
    ) {

    class Context(val pointers: List<SmartPsiElementPointer<KtExpression>>)

    @Suppress("DialogTitleCapitalization")
    override fun getFamilyName(): String = KotlinBundle.message("demorgan.law")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtBinaryExpression> = ApplicabilityRanges.SELF

    override fun apply(element: KtBinaryExpression, context: AnalysisActionContext<Context>, updater: ModPsiUpdater) {
        val expression = element.topmostBinaryExpression()
        val operatorText = when (expression.operationToken) {
            KtTokens.ANDAND -> KtTokens.OROR.value
            KtTokens.OROR -> KtTokens.ANDAND.value
            else -> throw IllegalArgumentException()
        }
        val newExpression = KtPsiFactory(expression.project).buildExpression {
            val negatedOperands = context.analyzeContext.pointers.map { it.element }
            appendExpressions(negatedOperands, separator = operatorText)
        }
        expression.parents.match(KtParenthesizedExpression::class, last = KtPrefixExpression::class)
            ?.takeIf { it.operationReference.getReferencedNameElementType() == KtTokens.EXCL }
            ?.replace(newExpression)
            ?: expression.replace(newExpression.negate())
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtBinaryExpression): Context? {
        val expr = element.topmostBinaryExpression()
        val operands = splitBooleanSequence(expr) ?: return null
        if (!expr.left.isBoolean || !expr.right.isBoolean) return null
        val pointers = operands.asReversed().map { operand ->
            operand.safeAs<KtQualifiedExpression>()?.invertSelectorFunction() ?: operand.negate(false) { it.isBoolean }
        }.map { it.createSmartPointer() }
        return Context(pointers)
    }

    override fun getActionName(element: KtBinaryExpression, context: Context): String {
        val expression = element.topmostBinaryExpression()
        return when (expression.operationToken) {
            KtTokens.ANDAND -> KotlinBundle.message("replace.&&.with.||")
            KtTokens.OROR -> KotlinBundle.message("replace.||.with.&&")
            else -> throw IllegalArgumentException()
        }
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        val expression = element.topmostBinaryExpression()
        val operationToken = expression.operationToken
        if (operationToken != KtTokens.ANDAND && operationToken != KtTokens.OROR) return false
        return splitBooleanSequence(expression) != null
    }

    private fun KtBinaryExpression.topmostBinaryExpression(): KtBinaryExpression =
        parentsWithSelf.takeWhile { it is KtBinaryExpression }.last() as KtBinaryExpression

    private fun splitBooleanSequence(expression: KtBinaryExpression): List<KtExpression>? {
        val result = ArrayList<KtExpression>()
        val firstOperator = expression.operationToken
        var remainingExpression: KtExpression = expression
        while (true) {
            if (remainingExpression !is KtBinaryExpression) break

            if (KtPsiUtil.deparenthesize(remainingExpression.left) is KtStatementExpression ||
                KtPsiUtil.deparenthesize(remainingExpression.right) is KtStatementExpression
            ) return null

            val operation = remainingExpression.operationToken
            if (operation != KtTokens.ANDAND && operation != KtTokens.OROR) break
            if (operation != firstOperator) return null //Boolean sequence must be homogenous

            result.add(remainingExpression.right ?: return null)
            remainingExpression = remainingExpression.left ?: return null
        }
        result.add(remainingExpression)
        return result
    }

    context(KtAnalysisSession)
    private val KtExpression?.isBoolean: Boolean
        get() = this != null && this.getKtType()?.isBoolean == true

    context(KtAnalysisSession)
    private fun KtQualifiedExpression.invertSelectorFunction(): KtQualifiedExpression? {
        return EmptinessCheckFunctionUtils.invertFunctionCall(this) as? KtQualifiedExpression
    }
}
