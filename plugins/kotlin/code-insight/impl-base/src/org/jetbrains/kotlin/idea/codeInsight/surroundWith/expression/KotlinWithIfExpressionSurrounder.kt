// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurrounder
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.sure

class KotlinWithIfExpressionSurrounder(val withElse: Boolean) : KotlinExpressionSurrounder() {
    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun isApplicable(expression: KtExpression): Boolean {
        allowAnalysisOnEdt {
            return super.isApplicable(expression) &&
                    analyze(expression) {
                        expression.getKtType()?.isBoolean ?: false
                    }
        }
    }

    override fun surroundExpression(project: Project, editor: Editor, expression: KtExpression): TextRange {
        val factory = KtPsiFactory(project)
        val replaceResult = expression.replace(
          factory.createIf(
            expression,
            factory.createBlock("blockStubContentToBeRemovedLater"),
            if (withElse) factory.createEmptyBody() else null
            )
        ) as KtExpression

        val ifExpression = KtPsiUtil.deparenthesizeOnce(replaceResult) as KtIfExpression

        CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(ifExpression)

        val firstStatementInThenRange = (ifExpression.then as? KtBlockExpression).sure {
            "Then branch should exist and be a block expression"
        }.statements.first().textRange

        editor.document.deleteString(firstStatementInThenRange.startOffset, firstStatementInThenRange.endOffset)

        return TextRange(firstStatementInThenRange.startOffset, firstStatementInThenRange.startOffset)
    }

    @NlsSafe
    override fun getTemplateDescription() = "if (expr) { ... }" + (if (withElse) " else { ... }" else "")
}
