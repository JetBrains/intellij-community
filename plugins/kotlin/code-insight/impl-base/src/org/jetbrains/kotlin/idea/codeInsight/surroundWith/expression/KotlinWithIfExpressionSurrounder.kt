// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurrounder
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinTryFinallySurrounder.moveCaretToBlockCenter
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.sure

class KotlinWithIfExpressionSurrounder(val withElse: Boolean) : KotlinExpressionSurrounder() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun isApplicable(expression: KtExpression): Boolean {
        allowAnalysisOnEdt {
            return super.isApplicable(expression) && analyze(expression) {
              expression.expressionType?.isBooleanType == true
            }
        }
    }

    override fun surroundExpression(context: ActionContext, expression: KtExpression, updater: ModPsiUpdater) {
        val project = context.project
        val factory = KtPsiFactory(project)
        val replaceResult = expression.replace(
          factory.createIf(
            expression,
            factory.createBlock(""),
            if (withElse) factory.createEmptyBody() else null
            )
        ) as KtExpression

        val ifExpression = KtPsiUtil.deparenthesizeOnce(replaceResult) as KtIfExpression
        moveCaretToBlockCenter(context, updater, ifExpression.then.sure {
            "Then branch should exist and be a block expression"
        })
    }

    @NlsSafe
    override fun getTemplateDescription(): String = "if (expr) { ... }" + (if (withElse) " else { ... }" else "")
}
