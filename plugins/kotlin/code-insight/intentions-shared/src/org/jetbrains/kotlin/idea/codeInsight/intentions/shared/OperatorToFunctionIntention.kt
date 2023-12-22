// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableModCommandIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTargets
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.resolve.references.ReferenceAccess

internal class OperatorToFunctionIntention : AbstractKotlinApplicableModCommandIntention<KtExpression>(
    KtExpression::class
) {
    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtExpression> = applicabilityTargets { element ->
        when (element) {
            is KtUnaryExpression -> listOf(element.operationReference)

            is KtBinaryExpression -> listOf(element.operationReference)

            is KtArrayAccessExpression -> {
                val lbrace = element.leftBracket
                val rbrace = element.rightBracket

                if (lbrace == null || rbrace == null) {
                    emptyList()
                } else {
                    listOf(lbrace, rbrace)
                }
            }

            is KtCallExpression -> {
                val lbrace = element.valueArgumentList?.leftParenthesis
                    ?: element.lambdaArguments.firstOrNull()?.getLambdaExpression()?.leftCurlyBrace

                listOfNotNull(lbrace as PsiElement?)
            }

            else -> emptyList()
        }
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtExpression) = when (element) {
        is KtUnaryExpression -> isApplicableUnary(element)
        is KtBinaryExpression -> isApplicableBinary(element)
        is KtArrayAccessExpression -> isApplicableArrayAccess(element)
        is KtCallExpression -> isApplicableCall(element)
        else -> false
    }

    context(KtAnalysisSession)
    private fun isApplicableUnary(element: KtUnaryExpression): Boolean {
        if (element.baseExpression == null) return false
        val opRef = element.operationReference
        return when (opRef.getReferencedNameElementType()) {
            KtTokens.PLUS, KtTokens.MINUS, KtTokens.EXCL -> true
            KtTokens.PLUSPLUS, KtTokens.MINUSMINUS -> !isUsedAsExpression(element)
            else -> false
        }
    }

    // TODO: replace to `element.isUsedAsExpression(element.analyze(BodyResolveMode.PARTIAL_WITH_CFA))` after fix KT-25682
    context(KtAnalysisSession)
    private fun isUsedAsExpression(element: KtExpression): Boolean {
        val parent = element.parent
        return if (parent is KtBlockExpression) parent.lastBlockStatementOrThis() == element && parentIsUsedAsExpression(parent.parent)
        else parentIsUsedAsExpression(parent)
    }

    context(KtAnalysisSession)
    private fun parentIsUsedAsExpression(element: PsiElement): Boolean =
        when (val parent = element.parent) {
            is KtLoopExpression, is KtFile -> false
            is KtIfExpression, is KtWhenExpression -> (parent as KtExpression).isUsedAsExpression()
            else -> true
        }

    private fun isApplicableBinary(element: KtBinaryExpression): Boolean {
        if (element.left == null || element.right == null) return false
        val opRef = element.operationReference
        return when (opRef.getReferencedNameElementType()) {
            KtTokens.PLUS, KtTokens.MINUS, KtTokens.MUL, KtTokens.DIV, KtTokens.PERC, KtTokens.RANGE, KtTokens.RANGE_UNTIL,
            KtTokens.IN_KEYWORD, KtTokens.NOT_IN, KtTokens.PLUSEQ, KtTokens.MINUSEQ, KtTokens.MULTEQ, KtTokens.DIVEQ, KtTokens.PERCEQ,
            KtTokens.GT, KtTokens.LT, KtTokens.GTEQ, KtTokens.LTEQ
            -> true
            KtTokens.EQEQ, KtTokens.EXCLEQ -> listOf(element.left, element.right).none { it?.node?.elementType == KtNodeTypes.NULL }
            KtTokens.EQ -> element.left is KtArrayAccessExpression
            else -> false
        }
    }

    private fun isApplicableArrayAccess(element: KtArrayAccessExpression): Boolean {
        val access = element.readWriteAccess(useResolveForReadWrite = true)
        return access != ReferenceAccess.READ_WRITE // currently not supported
    }

    context(KtAnalysisSession)
    private fun isImplicitInvokeFunctionCall(element: KtCallExpression): Boolean {
        val functionCall = element.resolveCall()?.singleFunctionCallOrNull()
        return functionCall is KtSimpleFunctionCall && functionCall.isImplicitInvoke
    }

    context(KtAnalysisSession)
    private fun isApplicableCall(element: KtCallExpression): Boolean {
        if (isImplicitInvokeFunctionCall(element)) {
            return element.valueArgumentList != null || element.lambdaArguments.isNotEmpty()
        }
        return false
    }

    override fun getFamilyName(): String = KotlinBundle.message("replace.overloaded.operator.with.function.call")

    override fun getActionName(element: KtExpression): String = familyName

    override fun invoke(context: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
        OperatorToFunctionConverter.convert(element)
    }
}
