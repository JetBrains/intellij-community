// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.psi.shouldLambdaParameterBeNamed
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

internal class MoveLambdaInsideParenthesesIntention :
    KotlinApplicableModCommandAction<KtLambdaArgument, MoveLambdaInsideParenthesesIntention.Context>(KtLambdaArgument::class) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtLambdaArgument,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        val ktExpression = element.getArgumentExpression()?.let(updater::getWritable)
            ?: throw KotlinExceptionWithAttachments("no argument expression for $this")
                .withPsiAttachment("lambdaExpression", element)
        element.moveInsideParenthesesAndReplaceWith(ktExpression, elementContext.lambdaArgumentName)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("move.lambda.argument.into.parentheses")

    override fun getPresentation(context: ActionContext, element: KtLambdaArgument): Presentation? =
        if (isElementApplicable(element, context)) {
            Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)
        } else {
            null
        }

    override fun KaSession.prepareContext(element: KtLambdaArgument): Context? {
        if (element.getArgumentName() != null) {
            // Already used as a named argument
            return null
        }
        val lambdaArgumentName = if (shouldLambdaParameterBeNamed(element)) {
            val callExpression = element.parent as KtCallExpression
            element.getArgumentExpression()?.let { expr ->
                analyze(callExpression) {
                    callExpression.resolveToCall()?.successfulFunctionCallOrNull()?.argumentMapping[expr]?.name
                }
            }
        } else {
            null
        }
        return Context(lambdaArgumentName)
    }

    data class Context(val lambdaArgumentName: Name?)
}
