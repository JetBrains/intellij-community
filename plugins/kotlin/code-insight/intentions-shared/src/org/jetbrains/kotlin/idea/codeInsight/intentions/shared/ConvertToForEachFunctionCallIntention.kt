// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

internal class ConvertToForEachFunctionCallIntention :
    KotlinApplicableModCommandAction<KtForExpression, Unit>(KtForExpression::class) {

    override fun getFamilyName(): String =
        KotlinBundle.message("replace.with.a.foreach.function.call", "forEach")

    override fun getPresentation(
        context: ActionContext,
        element: KtForExpression,
    ): Presentation {
        val calleeExpression = element.loopRange
            ?.getPossiblyQualifiedCallExpression()
            ?.calleeExpression
        val actionName = KotlinBundle.message(
            "replace.with.a.foreach.function.call",
            if (calleeExpression?.text == WITH_INDEX_NAME) "forEachIndexed" else "forEach",
        )
        return Presentation.of(actionName)
    }

    override fun isApplicableByPsi(element: KtForExpression): Boolean {
        if (element.loopRange == null || element.loopParameter == null || element.body == null) return false
        return element.body.getJumpExpressions<KtBreakExpression>(element.getLabelName()).isEmpty()
    }

    override fun getApplicableRanges(element: KtForExpression): List<TextRange> {
        val rParen = element.rightParenthesis
            ?: return emptyList()

        val textRange = TextRange(element.startOffset, rParen.endOffset)
            .shiftLeft(element.startOffset)
        return listOf(textRange)
    }


    override fun KaSession.prepareContext(element: KtForExpression): Unit? {
        val loopRange = element.loopRange ?: return null

        val calleeExpression = loopRange.getPossiblyQualifiedCallExpression()?.calleeExpression
        if (calleeExpression?.text == WITH_INDEX_NAME) {
            if (element.loopParameter?.destructuringDeclaration?.entries?.size != 2) return null
            val symbol = calleeExpression.mainReference?.resolveToSymbol() as? KaNamedFunctionSymbol ?: return null
            if (symbol.callableId?.asSingleFqName() !in withIndexedFunctionFqNames) return null
        }

        return loopRange.expressionType
            ?.isLoopRangeType()
            ?.asUnit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtForExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val commentSaver = CommentSaver(element, saveLineBreaks = true)

        val labelName = element.getLabelName()

        val psiFactory = KtPsiFactory(element.project)
        val foreachExpression = createForEachExpression(element, psiFactory) ?: return
        val calleeText = foreachExpression.getPossiblyQualifiedCallExpression()?.calleeExpression?.text ?: return
        val result = element.replace(foreachExpression) as KtElement
        result.findDescendantOfType<KtFunctionLiteral>()?.getJumpExpressions<KtContinueExpression>(labelName)?.forEach {
            it.replace(psiFactory.createExpression("return@$calleeText"))
        }

        commentSaver.restore(result)
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun KaType.isLoopRangeType(): Boolean {
        fun KaType.fqNameMatches() = (this as? KaUsualClassType)?.classId?.asSingleFqName() in loopRangeTypeFqNames

        return fqNameMatches() || allSupertypes.any { it.fqNameMatches() }
    }

    private fun createForEachExpression(element: KtForExpression, psiFactory: KtPsiFactory): KtExpression? {
        val body = element.body ?: return null
        val loopParameter = element.loopParameter ?: return null
        val loopRange = element.loopRange ?: return null

        val functionBodyArgument: Any = (body as? KtBlockExpression)?.contentRange() ?: body

        val callExpression = loopRange.getPossiblyQualifiedCallExpression()
        return if (callExpression?.calleeExpression?.text == WITH_INDEX_NAME) {
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
        } else {
            if (loopRange is KtThisExpression && loopRange.labelQualifier == null) {
                psiFactory.createExpressionByPattern("forEach{$0->\n$1}", loopParameter, functionBodyArgument)
            } else {
                psiFactory.createExpressionByPattern("$0.forEach{$1->\n$2}", loopRange, loopParameter, functionBodyArgument)
            }
        }
    }

    private inline fun <reified T: KtExpressionWithLabel> KtExpression?.getJumpExpressions(labelName: String?): List<T> {
        if (this == null) return emptyList()
        val jumpExpressions = ArrayList<T>()

        forEachDescendantOfType<T>({ it.shouldEnterForUnqualified(this) }) {
            if (it.getLabelName() == null) {
                jumpExpressions += it
            }
        }

        if (labelName != null) {
            forEachDescendantOfType<T>({ it.shouldEnterForQualified(this, labelName) }) {
                if (it.getLabelName() == labelName) {
                    jumpExpressions += it
                }
            }
        }

        return jumpExpressions
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

private val loopRangeTypeFqNames: List<FqName> = listOf(
    "kotlin.collections.Iterable",
    "kotlin.sequences.Sequence",
    "kotlin.CharSequence",
    "kotlin.Array",
    "kotlin.DoubleArray",
    "kotlin.FloatArray",
    "kotlin.LongArray",
    "kotlin.IntArray",
    "kotlin.CharArray",
    "kotlin.ShortArray",
    "kotlin.ByteArray",
    "kotlin.BooleanArray",
).map { FqName(it) }

private const val WITH_INDEX_NAME = "withIndex"

private val withIndexedFunctionFqNames: List<FqName> = listOf("collections", "sequences", "text", "ranges").map {
    FqName("kotlin.$it.$WITH_INDEX_NAME")
}
