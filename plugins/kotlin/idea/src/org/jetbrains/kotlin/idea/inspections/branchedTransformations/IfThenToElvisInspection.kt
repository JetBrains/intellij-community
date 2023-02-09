// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.branchedTransformations

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.formatter.rightMarginOrDefault
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.*
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class IfThenToElvisInspection @JvmOverloads constructor(
    @JvmField var highlightStatement: Boolean = false,
    private val inlineWithPrompt: Boolean = true
) : AbstractApplicabilityBasedInspection<KtIfExpression>(KtIfExpression::class.java) {
    override fun inspectionText(element: KtIfExpression): String = KotlinBundle.message("if.then.foldable.to")

    override val defaultFixText: String get() = INTENTION_TEXT

    override fun isApplicable(element: KtIfExpression): Boolean = isApplicableTo(element, expressionShouldBeStable = true)

    override fun inspectionHighlightType(element: KtIfExpression): ProblemHighlightType =
        if (element.shouldBeTransformed() && (highlightStatement || element.isUsedAsExpression(element.analyze(BodyResolveMode.PARTIAL_WITH_CFA))))
            super.inspectionHighlightType(element)
        else
            ProblemHighlightType.INFORMATION

    override fun applyTo(element: KtIfExpression, project: Project, editor: Editor?) {
        convert(element, editor, inlineWithPrompt)
    }

    override fun inspectionHighlightRangeInElement(element: KtIfExpression) = element.fromIfKeywordToRightParenthesisTextRangeInThis()

    override fun getOptionsPane() = pane(
        checkbox("highlightStatement", KotlinBundle.message("report.also.on.statement"))
    )

    companion object {
        val INTENTION_TEXT get() = KotlinBundle.message("replace.if.expression.with.elvis.expression")

        fun convert(element: KtIfExpression, editor: Editor?, inlineWithPrompt: Boolean) {
            val ifThenToSelectData = element.buildSelectTransformationData() ?: return

            val psiFactory = KtPsiFactory(element.project)

            val commentSaver = CommentSaver(element, saveLineBreaks = false)
            val margin = element.containingKtFile.rightMarginOrDefault
            val elvis = runWriteAction {
                val replacedBaseClause = ifThenToSelectData.replacedBaseClause(psiFactory)
                val negatedClause = ifThenToSelectData.negatedClause!!
                val newExpr = element.replaced(
                    psiFactory.createExpressionByPattern(
                        elvisPattern(replacedBaseClause.textLength + negatedClause.textLength + 5 >= margin),
                        replacedBaseClause,
                        negatedClause
                    )
                )

                (KtPsiUtil.deparenthesize(newExpr) as KtBinaryExpression).also {
                    commentSaver.restore(it)
                }
            }

            if (editor != null) {
                elvis.inlineLeftSideIfApplicable(editor, inlineWithPrompt)
                with(IfThenToSafeAccessInspection) {
                    (elvis.left as? KtSafeQualifiedExpression)?.renameLetParameter(editor)
                }
            }
        }

        fun isApplicableTo(element: KtIfExpression, expressionShouldBeStable: Boolean): Boolean {
            val ifThenToSelectData = element.buildSelectTransformationData() ?: return false
            if (expressionShouldBeStable &&
                !ifThenToSelectData.receiverExpression.isStableSimpleExpression(ifThenToSelectData.context)
            ) return false

            val type = element.getType(ifThenToSelectData.context) ?: return false
            if (KotlinBuiltIns.isUnit(type)) return false

            return ifThenToSelectData.clausesReplaceableByElvis()
        }

        private fun KtExpression.isNullOrBlockExpression(): Boolean {
            val innerExpression = this.unwrapBlockOrParenthesis()
            return innerExpression is KtBlockExpression || innerExpression.node.elementType == KtNodeTypes.NULL
        }

        private fun IfThenToSelectData.clausesReplaceableByElvis(): Boolean =
            when {
                baseClause == null || negatedClause == null || negatedClause.isNullOrBlockExpression() ->
                    false
                negatedClause is KtThrowExpression && negatedClause.throwsNullPointerExceptionWithNoArguments() ->
                    false
                baseClause.evaluatesTo(receiverExpression) ->
                    true
                baseClause.anyArgumentEvaluatesTo(receiverExpression) ->
                    true
                hasImplicitReceiverReplaceableBySafeCall() || baseClause.hasFirstReceiverOf(receiverExpression) ->
                    !baseClause.hasNullableType(context)
                else ->
                    false
            }
    }
}
