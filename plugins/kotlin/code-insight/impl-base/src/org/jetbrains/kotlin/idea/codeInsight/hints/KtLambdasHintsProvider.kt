// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousFunctionSymbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.siblings

class KtLambdasHintsProvider : InlayHintsProvider {
    override fun createCollector(
        file: PsiFile,
        editor: Editor
    ): InlayHintsCollector? {
        val project = editor.project ?: file.project
        if (project.isDefault) return null

        return object : SharedBypassCollector {
            override fun collectFromElement(
                element: PsiElement,
                sink: InlayTreeSink
            ) {
                collectFromLambdaReturnExpression(element, sink)
                collectFromLambdaImplicitParameterReceiver(element, sink)
            }
        }
    }

    private fun isLambdaReturnExpression(e: PsiElement): Boolean =
        e is KtExpression && e !is KtFunctionLiteral && !e.isNameReferenceInCall() && e.isLambdaReturnValueHintsApplicable()

    fun collectFromLambdaReturnExpression(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isLambdaReturnExpression(element)) return

        val expression = element as? KtExpression ?: return
        val functionLiteral = expression.getStrictParentOfType<KtFunctionLiteral>() ?: return
        val lambdaExpression = functionLiteral.getStrictParentOfType<KtLambdaExpression>() ?: return
        val lambdaName = lambdaExpression.getNameOfFunctionThatTakesLambda() ?: "lambda"

        sink.whenOptionEnabled(SHOW_RETURN_EXPRESSIONS.name) {
            val isUsedAsExpression = analyze(lambdaExpression) {
                expression.isUsedAsExpression()
            }
            if (!isUsedAsExpression) return@whenOptionEnabled

            sink.addPresentation(InlineInlayPosition(expression.endOffset, true), hasBackground = true) {
                text("^")
                text(lambdaName,
                     lambdaExpression.createSmartPointer().let {
                         InlayActionData(
                             PsiPointerInlayActionPayload(it),
                             PsiPointerInlayActionNavigationHandler.HANDLER_ID
                         )
                     })
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
                val anonymousFunctionSymbol = functionLiteral.getSymbol() as? KtAnonymousFunctionSymbol ?: return@whenOptionEnabled
                anonymousFunctionSymbol.receiverParameter?.let { receiverSymbol ->
                    sink.addPresentation(InlineInlayPosition(lbrace.textRange.endOffset, true), hasBackground = true) {
                        text("this: ")
                        printKtType(receiverSymbol.type)
                    }
                }

                anonymousFunctionSymbol.valueParameters.singleOrNull()?.let { singleParameterSymbol ->
                    val type = singleParameterSymbol.takeIf { it.isImplicitLambdaParameter }
                        ?.returnType?.takeUnless { it.isUnit } ?: return@let
                    sink.addPresentation(InlineInlayPosition(lbrace.textRange.endOffset, true), hasBackground = true) {
                        text("it: ")
                        printKtType(type)
                    }
                }
            }
        }
    }

}
@ApiStatus.Internal
fun ASTNode.isFollowedByNewLine(): Boolean {
    for (sibling in siblings()) {
        if (sibling.elementType != TokenType.WHITE_SPACE && sibling.psi !is PsiComment) {
            continue
        }
        if (sibling.elementType == TokenType.WHITE_SPACE && sibling.textContains('\n')) {
            return true
        }
    }
    return false
}