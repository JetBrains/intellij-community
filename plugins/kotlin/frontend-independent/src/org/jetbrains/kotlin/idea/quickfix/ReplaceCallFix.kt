// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

abstract class ReplaceCallFix(
    element: KtQualifiedExpression,
    private val operation: String,
    private val notNullNeeded: Boolean = false
) : PsiUpdateModCommandAction<KtQualifiedExpression>(element) {

    override fun getPresentation(context: ActionContext, element: KtQualifiedExpression): Presentation? =
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
            replacement.moveCaretToEnd(project, updater)
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
) : PsiUpdateModCommandAction<KtExpression>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.safe.this.call")

    override fun invoke(context: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
        val elvis = element.elvisOrEmpty(notNullNeeded)
        val newExpression = KtPsiFactory(context.project).createExpressionByPattern("this?.$0$elvis", element)
        val replacement = element.replace(newExpression)
        if (elvis.isNotEmpty()) {
            replacement.moveCaretToEnd(context.project, updater)
        }
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
    return if (binaryExpression?.left == this && binaryExpression.operationToken == KtTokens.ELVIS) "" else "?:"
}

fun PsiElement.moveCaretToEnd(project: Project, updater: ModPsiUpdater) {
    val document = containingFile.fileDocument
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
    val endOffset = if (text.endsWith(")")) endOffset - 1 else endOffset
    document.insertString(endOffset, " ")
    updater.moveCaretTo(endOffset + 1)
}
