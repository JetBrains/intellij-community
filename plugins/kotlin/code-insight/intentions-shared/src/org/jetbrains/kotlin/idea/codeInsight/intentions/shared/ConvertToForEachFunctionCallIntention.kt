// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

class ConvertToForEachFunctionCallIntention : AbstractKotlinApplicableIntention<KtForExpression>(KtForExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("replace.with.a.foreach.function.call")

    override fun getActionName(element: KtForExpression): String = familyName

    override fun isApplicableByPsi(element: KtForExpression): Boolean =
        element.loopRange != null && element.loopParameter != null && element.body != null

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtForExpression> = applicabilityRange {
        val rParen = it.rightParenthesis ?: return@applicabilityRange null
        TextRange(it.startOffset, rParen.endOffset).shiftLeft(it.startOffset)
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtForExpression): Boolean =
        element.loopRange?.getKtType()?.isIterableOrArray() == true

    override fun apply(element: KtForExpression, project: Project, editor: Editor?) {
        val commentSaver = CommentSaver(element, saveLineBreaks = true)

        val labelName = element.getLabelName()

        val body = element.body ?: return
        val loopParameter = element.loopParameter ?: return
        val loopRange = element.loopRange ?: return

        val functionBodyArgument: Any = (body as? KtBlockExpression)?.contentRange() ?: body

        val psiFactory = KtPsiFactory(element.project)
        val foreachExpression = psiFactory.createExpressionByPattern(
            "$0.forEach{$1->\n$2}", loopRange, loopParameter, functionBodyArgument
        )

        val result = element.replace(foreachExpression) as KtElement
        result.findDescendantOfType<KtFunctionLiteral>()?.getContinuesWithLabel(labelName)?.forEach {
            it.replace(psiFactory.createExpression("return@forEach"))
        }

        commentSaver.restore(result)
    }

    context(KtAnalysisSession)
    private fun KtType.isIterableOrArray(): Boolean {
        fun KtType.fqNameMatches() = (this as? KtUsualClassType)?.classId?.asSingleFqName() in iterableOrArrayFqNames

        return fqNameMatches() || getAllSuperTypes().any { it.fqNameMatches() }
    }

    private fun KtElement.getContinuesWithLabel(labelName: String?): List<KtContinueExpression> {
        val continueElements = ArrayList<KtContinueExpression>()

        forEachDescendantOfType<KtContinueExpression>({ it.shouldEnterForUnqualified(this) }) {
            if (it.getLabelName() == null) {
                continueElements += it
            }
        }

        if (labelName != null) {
            forEachDescendantOfType<KtContinueExpression>({ it.shouldEnterForQualified(this, labelName) }) {
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

private val iterableOrArrayFqNames: List<FqName> = listOf(
    "kotlin.collections.Iterable",
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
