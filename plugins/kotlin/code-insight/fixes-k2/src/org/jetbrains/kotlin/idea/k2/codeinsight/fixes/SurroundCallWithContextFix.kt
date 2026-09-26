// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class SurroundCallWithContextFix(
    element: KtExpression,
    private val wrapper: Wrapper,
    private val arguments: List<ContextArgument>,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtExpression>(element) {

    enum class Wrapper(val keyword: String) {
        CONTEXT("context"), WITH("with")
    }

    override fun invoke(context: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(context.project)
        val target = element.getExpressionToSurround()
        val expressionText = buildString {
            append(wrapper.keyword)
            append('(')
            arguments.joinTo(this) { it.renderExpression() }
            append(')')
            append("{ ${target.text} }")
        }
        val newExpression = psiFactory.createExpression(expressionText)
        shortenReferences(newExpression)
        val replaced = target.replace(newExpression) as? KtCallExpression ?: return

        val todoExpressions = replaced.valueArguments.zip(arguments)
            .mapNotNull { (valueArgument, argument) ->
                if (argument.candidateName == null) valueArgument.getArgumentExpression() else null
            }
        val firstTodo = todoExpressions.firstOrNull() ?: return
        updater.moveCaretTo(firstTodo)
        val templateBuilder = updater.templateBuilder()
        for (todoExpression in todoExpressions) {
            templateBuilder.field(todoExpression, todoExpression.text)
        }
    }

    private fun KtExpression.getExpressionToSurround(): KtExpression {
        var current = this
        while (true) {
            val parent = current.getStrictParentOfType<KtExpression>() ?: return current
            if (parent is KtDeclaration|| parent is KtBlockExpression || parent is KtReturnExpression ||
                parent is KtThrowExpression
            ) return current
            current = parent
        }
    }

    override fun getActionPresentation(context: ActionContext, element: KtExpression): Presentation =
        Presentation.of(
            when {
                arguments.size != 1 -> familyName
                else -> {
                    val candidateName = arguments.single().candidateName
                    if (candidateName != null)
                        KotlinBundle.message("fix.surround.call.with.0.argument.1", wrapper.keyword, candidateName)
                    else
                        KotlinBundle.message("fix.surround.call.with.0.todo.argument", wrapper.keyword)
                }
            }
        )

    override fun getFamilyName(): String =
        KotlinBundle.message("fix.surround.call.with.context.family")
}