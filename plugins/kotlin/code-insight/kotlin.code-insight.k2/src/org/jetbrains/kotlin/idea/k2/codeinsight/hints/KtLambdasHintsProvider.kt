// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_IMPLICIT_RECEIVERS_AND_PARAMS
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_RETURN_EXPRESSIONS
import org.jetbrains.kotlin.idea.codeInsight.hints.isFollowedByNewLine
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class KtLambdasHintsProvider : AbstractKtInlayHintsProvider() {
    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        collectFromLambdaReturnExpression(element, sink)
        collectFromLambdaImplicitParameterReceiver(element, sink)
    }

    @OptIn(ExperimentalContracts::class)
    private fun isLambdaReturnExpression(e: PsiElement): Boolean {
        contract {
            returns(true) implies (e is KtExpression)
        }
        return e is KtExpression && e !is KtFunctionLiteral && !e.isNameReferenceInCall() && e.isLambdaReturnValueHintsApplicable()
    }

    fun collectFromLambdaReturnExpression(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isLambdaReturnExpression(element)) return

        val functionLiteral = element.getStrictParentOfType<KtFunctionLiteral>() ?: return
        val lambdaExpression = functionLiteral.getStrictParentOfType<KtLambdaExpression>() ?: return
        val lambdaName = lambdaExpression.getNameOfFunctionThatTakesLambda() ?: "lambda"

        sink.whenOptionEnabled(SHOW_RETURN_EXPRESSIONS.name) {
            val isUsedAsExpression = analyze(lambdaExpression) {
                // TODO: KTIJ-16537 depends on KT-73473 : isUsedAsResultOfLambda should be used
                element.isUsedAsExpression
            }
            if (!isUsedAsExpression) return@whenOptionEnabled

            sink.addPresentation(InlineInlayPosition(element.endOffset, true), hintFormat = HintFormat.default) {
                text("^")
                text(lambdaName,
                     InlayActionData(
                         PsiPointerInlayActionPayload(lambdaExpression.createSmartPointer()),
                         PsiPointerInlayActionNavigationHandler.HANDLER_ID
                     )
                )
            }
        }
    }

    private fun KtLambdaExpression.getNameOfFunctionThatTakesLambda(): String? {
        val lambda = this
        val callExpression = this.getStrictParentOfType<KtCallExpression>() ?: return null
        return if (callExpression.lambdaArguments.any { it.getLambdaExpression() == lambda }) {
            val parent = lambda.parent
            if (parent is KtLabeledExpression) {
                parent.getLabelName()
            } else {
                (callExpression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
            }
        } else null
    }

    private fun isLambdaImplicitParameterReceiver(e: PsiElement): Boolean =
        e is KtFunctionLiteral && e.parent is KtLambdaExpression && (e.parent as KtLambdaExpression).leftCurlyBrace.isFollowedByNewLine()

    fun collectFromLambdaImplicitParameterReceiver(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isLambdaImplicitParameterReceiver(element)) return

        val functionLiteral = element as? KtFunctionLiteral ?: return
        val lambdaExpression = functionLiteral.parent as? KtLambdaExpression ?: return

        val lbrace = lambdaExpression.leftCurlyBrace
        if (!lbrace.isFollowedByNewLine()) return

        sink.whenOptionEnabled(SHOW_IMPLICIT_RECEIVERS_AND_PARAMS.name) {
            analyze(functionLiteral) {
                val anonymousFunctionSymbol = functionLiteral.symbol
                anonymousFunctionSymbol.receiverParameter?.let { receiverSymbol ->
                    sink.addPresentation(InlineInlayPosition(lbrace.textRange.endOffset, true), hintFormat = HintFormat.default) {
                        text("this: ")
                        printKtType(receiverSymbol.returnType)
                    }
                }

                anonymousFunctionSymbol.valueParameters.singleOrNull()?.let { singleParameterSymbol ->
                    val type = singleParameterSymbol.takeIf { it.isImplicitLambdaParameter }
                        ?.returnType?.takeUnless { it.isUnitType } ?: return@let
                    sink.addPresentation(InlineInlayPosition(lbrace.textRange.endOffset, true), hintFormat = HintFormat.default) {
                        text("it: ")
                        printKtType(type)
                    }
                }
            }
        }
    }
}
