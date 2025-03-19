// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.ChooseStringExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.convertToIfNotNullExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.convertToIfNullExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.introduceValueForCondition
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.isPure
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver

internal class DoubleBangToIfThenIntention : SelfTargetingIntention<KtPostfixExpression>(
    KtPostfixExpression::class.java, { KotlinBundle.message("replace.expression.with.if.expression") }
) {
    override fun isApplicableTo(element: KtPostfixExpression, caretOffset: Int): Boolean = true

    override fun startInWriteAction(): Boolean = false

    override fun applyTo(element: KtPostfixExpression, editor: Editor?) {
        if (editor == null) throw IllegalArgumentException("This intention requires an editor")

        val base = KtPsiUtil.safeDeparenthesize(element.baseExpression!!, true)
        val isPure = base.isPure()

        val psiFactory = KtPsiFactory(element.project)
        val defaultException = psiFactory.createExpression("throw NullPointerException()")
        val isStatement = KtPsiUtil.isStatement(element)

        val project = element.project

        val ifStatement =
            runWithModalProgressBlocking(project, KotlinBundle.message("progress.title.converting.to.if.then.else.expression")) {
                edtWriteAction {
                    if (isStatement)
                        element.convertToIfNullExpression(base, defaultException)
                    else {
                        val qualifiedExpressionForReceiver = element.getQualifiedExpressionForReceiver()
                        val selectorExpression = qualifiedExpressionForReceiver?.selectorExpression
                        val thenClause = selectorExpression?.let { psiFactory.createExpressionByPattern("$0.$1", base, it) } ?: base
                        (qualifiedExpressionForReceiver ?: element).convertToIfNotNullExpression(base, thenClause, defaultException)
                    }
                }
            }

        val thrownExpression = ((if (isStatement) ifStatement.then else ifStatement.`else`) as KtThrowExpression).thrownExpression ?: return

        val expressionText = formatForUseInExceptionArgument(base.text)
        val message = StringUtil.escapeStringCharacters("Expression '$expressionText' must not be null")

        val nullPtrExceptionText = "NullPointerException(\"$message\")"
        val kotlinNullPtrExceptionText = "KotlinNullPointerException()"

        val exceptionLookupExpression = ChooseStringExpression(listOf(nullPtrExceptionText, kotlinNullPtrExceptionText))
        val builder = TemplateBuilderImpl(thrownExpression)
        builder.replaceElement(thrownExpression, exceptionLookupExpression)

        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        editor.caretModel.moveToOffset(thrownExpression.node!!.startOffset)

        runWithModalProgressBlocking(project, KotlinBundle.message("progress.title.introducing.value.for.condition")) {
            edtWriteAction {
                TemplateManager.getInstance(project)
                    .startTemplate(editor, builder.buildInlineTemplate(), object : TemplateEditingAdapter() {
                        override fun templateFinished(template: Template, brokenOff: Boolean) {
                            @OptIn(KaAllowAnalysisOnEdt::class)
                            allowAnalysisOnEdt {
                                @OptIn(KaAllowAnalysisFromWriteAction::class)
                                allowAnalysisFromWriteAction {
                                    if (!isPure && !isStatement) {
                                        ifStatement.introduceValueForCondition(ifStatement.then!!, editor)
                                    }
                                }
                            }
                        }
                    })
            }
        }
    }


    private fun formatForUseInExceptionArgument(expressionText: String): String {
        val lines = expressionText.split('\n')
        return if (lines.size > 1)
            lines.first().trim() + " ..."
        else
            expressionText.trim()
    }

}
