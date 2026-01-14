// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType.*
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.getRangeBinaryExpressionType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ConvertRangeCheckToTwoComparisonsIntention :
    KotlinApplicableModCommandAction<KtBinaryExpression, ConvertRangeCheckToTwoComparisonsIntention.Context>(KtBinaryExpression::class) {

    data class Context(
        val pattern: String,
        val left: KtExpression,
        val arg: KtExpression,
        val right: KtExpression
    )

    private fun KtExpression?.isSimple() = this is KtConstantExpression || this is KtNameReferenceExpression

    override fun getFamilyName(): String =
        KotlinBundle.message("convert.to.comparisons")

    override fun KaSession.prepareContext(element: KtBinaryExpression): Context? {
        val isNegated = when (element.operationToken){
            KtTokens.IN_KEYWORD -> false
            KtTokens.NOT_IN -> true
            else -> return null
        }
        // ignore for-loop. for(x in 1..2) should not be convert to for(1<=x && x<=2)
        if (element.parent is KtForExpression) return null
        val rangeExpression = element.right ?: return null

        val arg = element.left ?: return null
        val (left, right) = rangeExpression.getArguments() ?: return null
        if (!arg.isSimple() || left?.isSimple() != true || right?.isSimple() != true) return null

        val argType = arg.expressionType ?: return null
        val leftType = left.expressionType ?: return null
        val rightType = right.expressionType ?: return null

        if (!argType.semanticallyEquals(leftType) || !argType.semanticallyEquals(rightType)) return null

        val pattern = when (rangeExpression.getRangeBinaryExpressionTypeValidated()) {
            RANGE_TO -> if (isNegated) "$1 < $0 || $1 > $2" else "$0 <= $1 && $1 <= $2"
            UNTIL, RANGE_UNTIL -> if (isNegated) "$1 < $0 || $1 >= $2" else "$0 <= $1 && $1 < $2"
            DOWN_TO -> if (isNegated) "$1 > $0 || $1 < $2" else "$0 >= $1 && $1 >= $2"
            null -> return null
        }

        return Context(pattern, left, arg, right)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtBinaryExpression,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(element.project)
        val newExpression = psiFactory.createExpressionByPattern(
            elementContext.pattern,
            elementContext.left,
            elementContext.arg,
            elementContext.right,
            reformat = false
        )
        element.replace(newExpression)
    }

    private fun KtExpression.getArguments(): Pair<KtExpression?, KtExpression?>? = when (this) {
        is KtBinaryExpression -> this.left to this.right
        is KtDotQualifiedExpression -> this.receiverExpression to this.callExpression?.valueArguments?.singleOrNull()
            ?.getArgumentExpression()

        else -> null
    }

    private fun KtExpression.getRangeBinaryExpressionTypeValidated(): RangeKtExpressionType? {
        val basicType = getRangeBinaryExpressionType(this) ?: return null

        analyze(this) {
            val call = resolveToCall()?.successfulFunctionCallOrNull() ?: return null
            val symbol = call.partiallyAppliedSymbol.signature.symbol as? KaCallableSymbol ?: return null
            val fqName = symbol.callableId?.asSingleFqName()?.asString() ?: return null

            if (!fqName.startsWith("kotlin.")) return null
        }

        return basicType
    }
}