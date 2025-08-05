// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.isUsedAsExpression
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.isImplicitInvokeCall
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.resolve.references.ReferenceAccess

internal class OperatorToFunctionIntention :
    KotlinApplicableModCommandAction<KtExpression, Unit>(KtExpression::class) {

    override fun getApplicableRanges(element: KtExpression): List<TextRange> = ApplicabilityRange.multiple(element) { expression ->
        when (expression) {
            is KtUnaryExpression -> listOf(expression.operationReference)

            is KtBinaryExpression -> listOf(expression.operationReference)

            is KtArrayAccessExpression -> {
                val lbrace = expression.leftBracket
                val rbrace = expression.rightBracket

                if (lbrace == null || rbrace == null) {
                    emptyList()
                } else {
                    listOf(lbrace, rbrace)
                }
            }

            is KtCallExpression -> {
                val lbrace = expression.valueArgumentList?.leftParenthesis
                    ?: expression.lambdaArguments.firstOrNull()?.getLambdaExpression()?.leftCurlyBrace

                listOfNotNull(lbrace as PsiElement?)
            }

            else -> emptyList()
        }
    }

    override fun KaSession.prepareContext(element: KtExpression): Unit? = (when (element) {
        is KtUnaryExpression -> isApplicableUnary(element)
        is KtBinaryExpression -> isApplicableBinary(element)
        is KtArrayAccessExpression -> isApplicableArrayAccess(element)
        is KtCallExpression -> isApplicableCall(element)
        else -> false
    }).asUnit

    context(_: KaSession)
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
    context(_: KaSession)
    private fun isUsedAsExpression(element: KtExpression): Boolean {
        val parent = element.parent
        return if (parent is KtBlockExpression) parent.lastBlockStatementOrThis() == element && parentIsUsedAsExpression(parent.parent)
        else parentIsUsedAsExpression(parent)
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun parentIsUsedAsExpression(element: PsiElement): Boolean =
        when (val parent = element.parent) {
            is KtLoopExpression, is KtFile -> false
            is KtIfExpression, is KtWhenExpression -> (parent as KtExpression).isUsedAsExpression
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

    context(_: KaSession)
    private fun isApplicableCall(element: KtCallExpression): Boolean {
        if (element.isImplicitInvokeCall() == true) {
            return element.valueArgumentList != null || element.lambdaArguments.isNotEmpty()
        }
        return false
    }

    override fun getFamilyName(): String = KotlinBundle.message("replace.overloaded.operator.with.function.call")

    override fun invoke(
      actionContext: ActionContext,
      element: KtExpression,
      elementContext: Unit,
      updater: ModPsiUpdater,
    ) {
        OperatorToFunctionConverter.convert(element)
    }
}
