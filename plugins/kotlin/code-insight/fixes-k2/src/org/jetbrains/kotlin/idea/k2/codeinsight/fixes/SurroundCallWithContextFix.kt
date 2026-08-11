// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class SurroundCallWithContextFix(
    element: KtExpression,
    private val wrapper: Wrapper,
    private val candidateName: String?,
    private val type: String,
    private val typeFqName: String
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtExpression>(element) {

    enum class Wrapper(val keyword: String) {
        CONTEXT("context"), WITH("with")
    }

    override fun invoke(context: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(context.project)
        val expressionText = buildString {
            append(wrapper.keyword)
            append('(')
            if (candidateName != null) {
                append(candidateName)
            } else {
                append("TODO(\"Provide $type\") as $typeFqName")
            }
            append(')')
            append("{ ${element.text} }")
        }
        val newExpression = psiFactory.createExpression(expressionText)
        shortenReferences(newExpression)
        val replace = element.replace(newExpression) as? KtCallExpression

        if (candidateName == null && replace != null) {
            val valueArgument = replace.valueArguments.firstOrNull()
            val insertedExpression = valueArgument?.getArgumentExpression() ?: return
            updater.moveCaretTo(insertedExpression)
            updater.templateBuilder().field(insertedExpression, insertedExpression.text)
        }
    }

    override fun getActionPresentation(context: ActionContext, element: KtExpression): Presentation =
        Presentation.of(
            if (candidateName != null)
                KotlinBundle.message("fix.surround.call.with.0.argument.1", wrapper.keyword, candidateName)
            else
                KotlinBundle.message("fix.surround.call.with.0.todo.argument", wrapper.keyword)
        )

    override fun getFamilyName(): String =
        KotlinBundle.message("fix.surround.call.with.context.family")
}
