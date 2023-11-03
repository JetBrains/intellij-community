// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

context(KtAnalysisSession)
@OptIn(UnsafeCastFunction::class)
fun prepareDemorgansLawContext(operands: List<KtExpression>): DemorgansLawContext {
    val pointers = operands.asReversed().map { operand ->
        operand.safeAs<KtQualifiedExpression>()?.invertSelectorFunction() ?: operand.negate(false) { it.isBoolean }
    }.map { it.createSmartPointer() }
    return DemorgansLawContext(pointers)
}

fun applyDemorgansLaw(expression: KtBinaryExpression, context: DemorgansLawContext) {
    val operatorText = when (expression.operationToken) {
        KtTokens.ANDAND -> KtTokens.OROR.value
        KtTokens.OROR -> KtTokens.ANDAND.value
        else -> throw IllegalArgumentException()
    }
    val newExpression = KtPsiFactory(expression.project).buildExpression {
        val negatedOperands = context.pointers.map { it.element }
        appendExpressions(negatedOperands, separator = operatorText)
    }
    expression.parents.match(KtParenthesizedExpression::class, last = KtPrefixExpression::class)
        ?.takeIf { it.operationReference.getReferencedNameElementType() == KtTokens.EXCL }
        ?.replace(newExpression)
        ?: expression.replace(newExpression.negate())
}

fun splitBooleanSequence(expression: KtBinaryExpression): List<KtExpression>? {
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
fun KtQualifiedExpression.invertSelectorFunction(): KtQualifiedExpression? {
    return EmptinessCheckFunctionUtils.invertFunctionCall(this) as? KtQualifiedExpression
}

context(KtAnalysisSession)
val KtExpression?.isBoolean: Boolean
    get() = this != null && this.getKtType()?.isBoolean == true

fun KtBinaryExpression.topmostBinaryExpression(): KtBinaryExpression =
    parentsWithSelf.takeWhile { it is KtBinaryExpression }.last() as KtBinaryExpression

class DemorgansLawContext(val pointers: List<SmartPsiElementPointer<KtExpression>>)