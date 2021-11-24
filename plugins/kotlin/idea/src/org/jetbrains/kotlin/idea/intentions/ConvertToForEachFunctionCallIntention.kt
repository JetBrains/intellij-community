// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ConvertToForEachFunctionCallIntention : SelfTargetingIntention<KtForExpression>(
    KtForExpression::class.java,
    KotlinBundle.lazyMessage("replace.with.a.foreach.function.call", "forEach")
) {
    companion object {
        private const val withIndexFunctionName = "withIndex"
        private val withIndexedFunctionFqNames = listOf("collections", "sequences", "text", "ranges").map {
            FqName("kotlin.$it.$withIndexFunctionName")
        }
    }

    override fun isApplicableTo(element: KtForExpression, caretOffset: Int): Boolean {
        val rParen = element.rightParenthesis ?: return false
        if (caretOffset > rParen.endOffset) return false // available only on the loop header, not in the body
        if (element.loopRange == null || element.loopParameter == null || element.body == null) return false
        if (element.body?.getExpressionsWithLabel<KtBreakExpression>(element.getLabelName())?.isNotEmpty() == true) return false
        val callExpression = element.loopRange?.callExpression()
        val forEachText = if (callExpression?.isCalling(withIndexedFunctionFqNames) == true) {
            if (element.loopParameter?.destructuringDeclaration?.entries?.size != 2) return false
            "forEachIndexed"
        } else {
            "forEach"
        }
        setTextGetter(KotlinBundle.lazyMessage("replace.with.a.foreach.function.call", forEachText))
        return true
    }

    override fun applyTo(element: KtForExpression, editor: Editor?) {
        val commentSaver = CommentSaver(element, saveLineBreaks = true)
        val psiFactory = KtPsiFactory(element)
        val forEachExpression = element.createForEachExpression(psiFactory) ?: return
        val forEachText = forEachExpression.calleeText() ?: return
        val labelName = element.getLabelName()
        val result = element.replace(forEachExpression) as KtElement
        result.findDescendantOfType<KtFunctionLiteral>()?.getExpressionsWithLabel<KtContinueExpression>(labelName)?.forEach {
            it.replace(psiFactory.createExpression("return@$forEachText"))
        }
        commentSaver.restore(result)
    }

    private fun KtExpression.callExpression(): KtCallExpression? = getPossiblyQualifiedCallExpression() ?: safeAs()

    private fun KtForExpression.createForEachExpression(psiFactory: KtPsiFactory = KtPsiFactory(this)): KtExpression? {
        val body = body ?: return null
        val loopParameter = loopParameter ?: return null
        val loopRange = loopRange ?: return null
        val functionBodyArgument: Any = (body as? KtBlockExpression)?.contentRange() ?: body

        val callExpression = loopRange.callExpression()
        if (callExpression?.isCalling(withIndexedFunctionFqNames) == true) {
            val entries = loopParameter.destructuringDeclaration?.entries ?: return null
            val lambdaParameter1 = entries.getOrNull(0)?.text ?: return null
            val lambdaParameter2 = entries.getOrNull(1)?.text ?: return null
            val receiver = callExpression.getQualifiedExpressionForSelector()?.receiverExpression
            return if (receiver == null) {
                val pattern = "forEachIndexed{$0, $1->\n$2}"
                psiFactory.createExpressionByPattern(pattern, lambdaParameter1, lambdaParameter2, functionBodyArgument)
            } else {
                val pattern = "$0.forEachIndexed{$1, $2->\n$3}"
                psiFactory.createExpressionByPattern(pattern, receiver, lambdaParameter1, lambdaParameter2, functionBodyArgument)
            }
        }

        return if (loopRange is KtThisExpression) {
            psiFactory.createExpressionByPattern("forEach{$0->\n$1}", loopParameter, functionBodyArgument)
        } else {
            psiFactory.createExpressionByPattern("$0.forEach{$1->\n$2}", loopRange, loopParameter, functionBodyArgument)
        }
    }

    private fun KtExpression.calleeText(): String? {
        val callExpression = safeAs<KtQualifiedExpression>()?.callExpression ?: safeAs() ?: return ""
        return callExpression.calleeExpression?.text
    }

    private inline fun <reified T: KtExpressionWithLabel> KtElement.getExpressionsWithLabel(labelName: String?): List<T> {
        val continueElements = ArrayList<T>()

        forEachDescendantOfType<T>({ it.shouldEnterForUnqualified(this) }) {
            if (it.getLabelName() == null) {
                continueElements += it
            }
        }

        if (labelName != null) {
            forEachDescendantOfType<T>({ it.shouldEnterForQualified(this, labelName) }) {
                if (it.getLabelName() == labelName) {
                    continueElements += it
                }
            }
        }

        return continueElements
    }

    private fun PsiElement.shouldEnterForUnqualified(allow: PsiElement): Boolean {
        if (this == allow) return true
        if (shouldNeverEnter()) return false
        return this !is KtLoopExpression
    }

    private fun PsiElement.shouldEnterForQualified(allow: PsiElement, labelName: String): Boolean {
        if (this == allow) return true
        if (shouldNeverEnter()) return false
        return this !is KtLoopExpression || getLabelName() != labelName
    }

    private fun PsiElement.shouldNeverEnter() = this is KtLambdaExpression || this is KtClassOrObject || this is KtFunction

    private fun KtLoopExpression.getLabelName() = (parent as? KtExpressionWithLabel)?.getLabelName()
}
