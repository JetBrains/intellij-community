// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

abstract class ReplaceCallFix(
    element: KtQualifiedExpression,
    private val operation: String,
    private val notNullNeeded: Boolean = false
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtQualifiedExpression>(element) {

    override fun getActionPresentation(context: ActionContext, element: KtQualifiedExpression): Presentation? =
        Presentation.of(familyName).takeIf { element.selectorExpression != null }

    override fun invoke(context: ActionContext, element: KtQualifiedExpression, updater: ModPsiUpdater) {
        replace(element, context.project, updater)
    }

    protected fun replace(element: KtQualifiedExpression?, project: Project, updater: ModPsiUpdater): KtExpression? {
        val selectorExpression = element?.selectorExpression ?: return null
        val elvis = element.elvisOrEmpty(notNullNeeded)
        val betweenReceiverAndOperation = element.elementsBetweenReceiverAndOperation().joinToString(separator = "") { it.text }
        val newExpression = KtPsiFactory(project).createExpressionByPattern(
            "$0$betweenReceiverAndOperation$operation$1$elvis",
            element.receiverExpression,
            selectorExpression,
        )

        val replacement = element.replace(newExpression)
        if (elvis.isNotEmpty()) {
            replacement.startTemplateForElvisTodo(updater)
        }

        return replacement as? KtExpression
    }

    private fun KtQualifiedExpression.elementsBetweenReceiverAndOperation(): List<PsiElement> {
        val receiver = receiverExpression
        val operation = operationTokenNode as? PsiElement ?: return emptyList()
        val start = receiver.nextSibling?.takeIf { it != operation } ?: return emptyList()
        val end = operation.prevSibling?.takeIf { it != receiver } ?: return emptyList()
        return PsiTreeUtil.getElementsOfRange(start, end)
    }
}

class ReplaceImplicitReceiverCallFix(
    element: KtExpression,
    private val notNullNeeded: Boolean
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtExpression>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.safe.this.call")

    override fun invoke(context: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
        val elvis = element.elvisOrEmpty(notNullNeeded)
        val newExpression = KtPsiFactory(context.project).createExpressionByPattern("this?.$0$elvis", element)
        val replacement = element.replace(newExpression)
        if (elvis.isNotEmpty()) {
            replacement.startTemplateForElvisTodo(updater)
        }
    }
}

class RemoveRedundantCallsOfConversionMethodsFix(
    element: KtQualifiedExpression
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtQualifiedExpression>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.redundant.calls.of.the.conversion.method")

    override fun getActionPresentation(
        context: ActionContext,
        element: KtQualifiedExpression,
    ): Presentation = Presentation.of(familyName).withFixAllOption(this)

    override fun invoke(context: ActionContext, element: KtQualifiedExpression, updater: ModPsiUpdater) {
        element.replace(element.receiverExpression)
    }
}

class ReplaceWithSafeCallFix(
    element: KtDotQualifiedExpression,
    notNullNeeded: Boolean
) : ReplaceCallFix(element, "?.", notNullNeeded) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.safe.call")
}

class ReplaceWithSafeCallForScopeFunctionFix(
    element: KtDotQualifiedExpression,
    notNullNeeded: Boolean
) : ReplaceCallFix(element, "?.", notNullNeeded) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.scope.function.with.safe.call")
}

class ReplaceWithDotCallFix(
    element: KtSafeQualifiedExpression,
    private val callChainCount: Int = 0
) : ReplaceCallFix(element, "."), CleanupFix.ModCommand {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.dot.call")

    override fun invoke(context: ActionContext, element: KtQualifiedExpression, updater: ModPsiUpdater) {
        var replaced = replace(element, context.project, updater) ?: return
        repeat(callChainCount) {
            val parent = replaced.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression ?: return
            replaced = replace(parent, context.project, updater) ?: return
        }
    }
}

fun KtExpression.elvisOrEmpty(notNullNeeded: Boolean): String {
    if (!notNullNeeded) return ""
    val binaryExpression = getStrictParentOfType<KtBinaryExpression>()
    return if (binaryExpression?.left == this && binaryExpression.operationToken == KtTokens.ELVIS) "" else " ?: TODO()"
}

fun PsiElement.startTemplateForElvisTodo(updater: ModPsiUpdater) {
    val expression = this as? KtExpression ?: return
    val unwrapped = KtPsiUtil.deparenthesize(expression)
    val todoExpression = (unwrapped as? KtBinaryExpression)
        ?.takeIf { it.operationToken == KtTokens.ELVIS }
        ?.right
        ?: return
    updater.moveCaretTo(todoExpression)
    updater.templateBuilder().field(todoExpression, todoExpression.text)
}
