// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

internal class AddContextParameterToExistingContextFix(
    surroundingCall: KtCallExpression,
    private val arguments: List<ContextArgument>,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtCallExpression>(surroundingCall) {

    override fun invoke(context: ActionContext, element: KtCallExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(context.project)
        val argList = element.valueArgumentList ?: return
        val rightParen = argList.rightParenthesis ?: return
        val existingNames = argList.arguments
            .mapNotNullTo(hashSetOf()) { (it.getArgumentExpression() as? KtNameReferenceExpression)?.getReferencedName() }

        val todoExpressions = mutableListOf<KtExpression>()
        for (argument in arguments) {
            val candidateName = argument.candidateName
            if (candidateName != null && candidateName in existingNames) continue

            val hasTrailingComma = PsiTreeUtil.skipWhitespacesAndCommentsBackward(rightParen)
                ?.node?.elementType == KtTokens.COMMA
            if (argList.arguments.isNotEmpty() && !hasTrailingComma) {
                argList.addBefore(psiFactory.createComma(), rightParen)
            }
            val newElement =
                if (candidateName != null) {
                    psiFactory.createArgument(candidateName)
                } else {
                    psiFactory.createArgument(psiFactory.createExpression(argument.renderExpression()))
                }
            val insertedElement = argList.addBefore(newElement, rightParen) as? KtValueArgument ?: continue
            if (candidateName == null) {
                shortenReferences(insertedElement)
                insertedElement.getArgumentExpression()?.let(todoExpressions::add)
            } else {
                existingNames.add(candidateName)
            }
        }

        val firstTodo = todoExpressions.firstOrNull() ?: return
        updater.moveCaretTo(firstTodo)
        val templateBuilder = updater.templateBuilder()
        for (todoExpression in todoExpressions) {
            templateBuilder.field(todoExpression, todoExpression.text)
        }
    }

    override fun getActionPresentation(context: ActionContext, element: KtCallExpression): Presentation =
        Presentation.of(
            when {
                arguments.size != 1 -> KotlinBundle.message("fix.add.arguments.to.existing.context")
                else -> {
                    val argument = arguments.single()
                    val candidateName = argument.candidateName
                    if (candidateName != null) {
                        KotlinBundle.message(
                            "fix.add.argument.to.existing.context.with.name.and.type",
                            candidateName,
                            argument.typeText,
                        )
                    } else {
                        KotlinBundle.message(
                            "fix.add.todo.argument.to.existing.context.with.name.and.type",
                            argument.typeText,
                        )
                    }
                }
            }
        )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.argument.to.existing.context")
}
