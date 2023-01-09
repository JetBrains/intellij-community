// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTargets
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class OperatorToFunctionIntention : AbstractKotlinApplicableIntention<KtExpression>(KtExpression::class) {
    companion object {
        fun replaceExplicitInvokeCallWithImplicit(qualifiedExpression: KtDotQualifiedExpression): KtExpression? {
            /*
            * `a.b.invoke<>(){}` -> `a.b<>(){}`
            * `a.b<>(){}.invoke<>(){}` -> `a.b<>(){}<>(){}`
            * `b.invoke<>(){}` -> `b<>(){}`
            * `b<>(){}.invoke<>(){}` -> `b<>(){}<>(){}`
            * `invoke<>(){}` -> not applicable
            */

            val callExpression = qualifiedExpression.selectorExpression.safeAs<KtCallExpression>()?.copied() ?: return null
            val calleExpression = callExpression.calleeExpression as KtNameReferenceExpression
            val receiverExpression = qualifiedExpression.receiverExpression
            val selectorInReceiver = receiverExpression.safeAs<KtDotQualifiedExpression>()?.selectorExpression
            return if (selectorInReceiver is KtNameReferenceExpression) {
                calleExpression.rawReplace(selectorInReceiver.copied())
                selectorInReceiver.rawReplace(callExpression)
                qualifiedExpression.replaced(receiverExpression)
            } else {
                if ((receiverExpression is KtCallExpression || receiverExpression is KtDotQualifiedExpression) &&
                    callExpression.valueArgumentList == null && callExpression.typeArgumentList == null) {
                    calleExpression.replace(receiverExpression)
                } else {
                    calleExpression.rawReplace(receiverExpression)
                }

                qualifiedExpression.replaced(callExpression)
            }
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
            val functionCall = element.resolveCall().singleFunctionCallOrNull()
            return functionCall is KtSimpleFunctionCall && functionCall.isImplicitInvoke
        }

        context(KtAnalysisSession)
        private fun isApplicableCall(element: KtCallExpression): Boolean {
            if (isImplicitInvokeFunctionCall(element)) {
                return element.valueArgumentList != null || element.lambdaArguments.isNotEmpty()
            }
            return false
        }

        private fun convertUnary(element: KtUnaryExpression): KtExpression {
            val operatorName = when (element.operationReference.getReferencedNameElementType()) {
                KtTokens.PLUSPLUS, KtTokens.MINUSMINUS -> return convertUnaryWithAssignFix(element)
                KtTokens.PLUS -> OperatorNameConventions.UNARY_PLUS
                KtTokens.MINUS -> OperatorNameConventions.UNARY_MINUS
                KtTokens.EXCL -> OperatorNameConventions.NOT
                else -> return element
            }

            val transformed = KtPsiFactory(element.project)
                .createExpressionByPattern("$0.$1()", element.baseExpression!!, operatorName)

            return element.replace(transformed) as KtExpression
        }

        private fun convertUnaryWithAssignFix(element: KtUnaryExpression): KtExpression {
            val operatorName = when (element.operationReference.getReferencedNameElementType()) {
                KtTokens.PLUSPLUS -> OperatorNameConventions.INC
                KtTokens.MINUSMINUS -> OperatorNameConventions.DEC
                else -> return element
            }

            val transformed = KtPsiFactory(element.project)
                .createExpressionByPattern("$0 = $0.$1()", element.baseExpression!!, operatorName)

            return element.replace(transformed) as KtExpression
        }

        //TODO: don't use creation by plain text
        private fun convertBinary(element: KtBinaryExpression): KtExpression {
            val op = element.operationReference.getReferencedNameElementType()
            val left = element.left!!
            val right = element.right!!

            if (op == KtTokens.EQ) {
                if (left is KtArrayAccessExpression) {
                    convertArrayAccess(left)
                }
                return element
            }

            val functionName = getCalledFunctionName(element)?.asString()
            val receiverIsNullable = isOfNullableType(left)

            @NonNls
            val pattern = when (op) {
                KtTokens.PLUS -> "$0.plus($1)"
                KtTokens.MINUS -> "$0.minus($1)"
                KtTokens.MUL -> "$0.times($1)"
                KtTokens.DIV -> "$0.div($1)"
                KtTokens.PERC -> "$0.rem($1)"
                KtTokens.RANGE -> "$0.rangeTo($1)"
                KtTokens.RANGE_UNTIL -> "$0.rangeUntil($1)"
                KtTokens.IN_KEYWORD -> "$1.contains($0)"
                KtTokens.NOT_IN -> "!$1.contains($0)"
                KtTokens.PLUSEQ -> if (functionName == "plusAssign") "$0.plusAssign($1)" else "$0 = $0.plus($1)"
                KtTokens.MINUSEQ -> if (functionName == "minusAssign") "$0.minusAssign($1)" else "$0 = $0.minus($1)"
                KtTokens.MULTEQ -> if (functionName == "timesAssign") "$0.timesAssign($1)" else "$0 = $0.times($1)"
                KtTokens.DIVEQ -> if (functionName == "divAssign") "$0.divAssign($1)" else "$0 = $0.div($1)"
                KtTokens.PERCEQ -> {
                    val remSupported = element.languageVersionSettings.supportsFeature(LanguageFeature.OperatorRem)
                    if (remSupported && functionName == "remAssign") "$0.remAssign($1)"
                    else if (functionName == "modAssign") "$0.modAssign($1)"
                    else if (remSupported) "$0 = $0.rem($1)"
                    else "$0 = $0.mod($1)"
                }
                KtTokens.EQEQ -> if (receiverIsNullable != false) "$0?.equals($1) ?: ($1 == null)" else "$0.equals($1)"
                KtTokens.EXCLEQ -> if (receiverIsNullable != false) "!($0?.equals($1) ?: ($1 == null))" else "!$0.equals($1)"
                KtTokens.GT -> "$0.compareTo($1) > 0"
                KtTokens.LT -> "$0.compareTo($1) < 0"
                KtTokens.GTEQ -> "$0.compareTo($1) >= 0"
                KtTokens.LTEQ -> "$0.compareTo($1) <= 0"
                else -> return element
            }

            val transformed = KtPsiFactory(element.project).createExpressionByPattern(pattern, left, right)
            return element.replace(transformed) as KtExpression
        }

        @OptIn(KtAllowAnalysisOnEdt::class)
        private fun isOfNullableType(expression: KtExpression): Boolean? = allowAnalysisOnEdt {
            analyze(expression) {
                expression.getKtType()?.isMarkedNullable
            }
        }

        @OptIn(KtAllowAnalysisOnEdt::class)
        private fun getCalledFunctionName(element: KtBinaryExpression): Name? = allowAnalysisOnEdt {
            analyze(element) {
                val resolvedCall = element.resolveCall()?.singleFunctionCallOrNull()
                val targetSymbol = resolvedCall?.partiallyAppliedSymbol?.symbol

                (targetSymbol as? KtNamedSymbol)?.name
            }
        }

        private fun convertArrayAccess(element: KtArrayAccessExpression): KtExpression {
            var expressionToReplace: KtExpression = element
            val transformed = KtPsiFactory(element.project).buildExpression {
                appendExpression(element.arrayExpression)

                appendFixedText(".")

                if (isAssignmentLeftSide(element)) {
                    val parent = element.parent
                    expressionToReplace = parent as KtBinaryExpression

                    appendFixedText("set(")
                    appendExpressions(element.indexExpressions)
                    appendFixedText(",")
                    appendExpression(parent.right)
                } else {
                    appendFixedText("get(")
                    appendExpressions(element.indexExpressions)
                }

                appendFixedText(")")
            }

            return expressionToReplace.replace(transformed) as KtExpression
        }

        private fun isAssignmentLeftSide(element: KtArrayAccessExpression): Boolean {
            val parent = element.parent
            return parent is KtBinaryExpression &&
                    parent.operationReference.getReferencedNameElementType() == KtTokens.EQ && element == parent.left
        }

        //TODO: don't use creation by plain text
        private fun convertCall(element: KtCallExpression): KtExpression {
            val callee = element.calleeExpression!!
            val receiver = element.parent?.safeAs<KtQualifiedExpression>()?.receiverExpression
            val isAnonymousFunctionWithReceiver = receiver != null && callee.safeDeparenthesize() is KtNamedFunction
            val argumentsList = element.valueArgumentList
            val argumentString = argumentsList?.text?.removeSurrounding("(", ")") ?: ""
            val argumentsWithReceiverIfNeeded = if (isAnonymousFunctionWithReceiver) {
                val receiverText = receiver?.text ?: ""
                val delimiter = if (receiverText.isNotEmpty() && argumentString.isNotEmpty()) ", " else ""
                receiverText + delimiter + argumentString
            } else {
                argumentString
            }

            val funcLitArgs = element.lambdaArguments
            val calleeText = callee.text
            val transformation = "$calleeText.${OperatorNameConventions.INVOKE.asString()}" + "($argumentsWithReceiverIfNeeded)"
            val transformed = KtPsiFactory(element.project).createExpression(transformation)
            val callExpression = transformed.getCalleeExpressionIfAny()?.parent as? KtCallExpression
            if (callExpression != null && funcLitArgs.isNotEmpty()) {
                funcLitArgs.forEach { callExpression.add(it) }
                if (argumentsWithReceiverIfNeeded.isEmpty()) {
                    callExpression.valueArgumentList?.delete()
                }
            }

            val elementToReplace = if (isAnonymousFunctionWithReceiver) element.parent else callee.parent
            return elementToReplace.replace(transformed) as KtExpression
        }

        /**
         * Converts operator call to an explicit function call.
         *
         * N.B. Has to use some resolve inside, so resorts to [allowAnalysisOnEdt].
         */
        fun convert(element: KtExpression): Pair<KtExpression, KtSimpleNameExpression> {
            var elementToBeReplaced = element
            if (element is KtArrayAccessExpression && isAssignmentLeftSide(element)) {
                elementToBeReplaced = element.parent as KtExpression
            }

            val commentSaver = CommentSaver(elementToBeReplaced, saveLineBreaks = true)

            val result = when (element) {
                is KtUnaryExpression -> convertUnary(element)
                is KtBinaryExpression -> convertBinary(element)
                is KtArrayAccessExpression -> convertArrayAccess(element)
                is KtCallExpression -> convertCall(element)
                else -> throw IllegalArgumentException(element.toString())
            }

            commentSaver.restore(result)

            val callName = findCallName(result) ?: error("No call name found in ${result.text}")
            return result to callName
        }

        private fun findCallName(result: KtExpression): KtSimpleNameExpression? = when (result) {
            is KtBinaryExpression -> {
                if (KtPsiUtil.isAssignment(result))
                    findCallName(result.right!!)
                else
                    findCallName(result.left!!)
            }
            is KtUnaryExpression -> result.baseExpression?.let { findCallName(it) }
            is KtParenthesizedExpression -> result.expression?.let { findCallName(it) }
            else -> result.getQualifiedElementSelector() as KtSimpleNameExpression?
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("replace.overloaded.operator.with.function.call")

    override fun getActionName(element: KtExpression): String = familyName

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

    override fun isApplicableByPsi(element: KtExpression): Boolean = true

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtExpression): Boolean = when (element) {
        is KtUnaryExpression -> isApplicableUnary(element)
        is KtBinaryExpression -> isApplicableBinary(element)
        is KtArrayAccessExpression -> isApplicableArrayAccess(element)
        is KtCallExpression -> isApplicableCall(element)
        else -> false
    }

    override fun apply(element: KtExpression, project: Project, editor: Editor?) {
        convert(element)
    }
}
