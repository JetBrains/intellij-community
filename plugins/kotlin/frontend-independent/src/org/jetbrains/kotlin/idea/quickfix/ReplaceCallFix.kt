// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

abstract class ReplaceCallFix(
    expression: KtQualifiedExpression,
    private val operation: String,
    private val notNullNeeded: Boolean = false
) : KotlinPsiOnlyQuickFixAction<KtQualifiedExpression>(expression) {

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val element = element ?: return false
        return element.selectorExpression != null
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        replace(element, project, editor)
    }

    protected fun replace(element: KtQualifiedExpression?, project: Project, editor: Editor?): KtExpression? {
        val selectorExpression = element?.selectorExpression ?: return null
        val elvis = element.elvisOrEmpty(notNullNeeded)
        val betweenReceiverAndOperation = element.elementsBetweenReceiverAndOperation().joinToString(separator = "") { it.text }
        val newExpression = KtPsiFactory(element).createExpressionByPattern(
            "$0$betweenReceiverAndOperation$operation$1$elvis",
            element.receiverExpression,
            selectorExpression,
        )

        val replacement = element.replace(newExpression)
        if (elvis.isNotEmpty()) {
            replacement.moveCaretToEnd(editor, project)
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
    expression: KtExpression,
    private val notNullNeeded: Boolean
) : KotlinPsiOnlyQuickFixAction<KtExpression>(expression) {
    override fun getFamilyName() = text

    override fun getText() = KotlinBundle.message("replace.with.safe.this.call")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val elvis = element.elvisOrEmpty(notNullNeeded)
        val newExpression = KtPsiFactory(element).createExpressionByPattern("this?.$0$elvis", element)
        val replacement = element.replace(newExpression)
        if (elvis.isNotEmpty()) {
            replacement.moveCaretToEnd(editor, project)
        }
    }
}

class ReplaceWithSafeCallFix(
    expression: KtDotQualifiedExpression,
    notNullNeeded: Boolean
) : ReplaceCallFix(expression, "?.", notNullNeeded) {
    override fun getText() = KotlinBundle.message("replace.with.safe.call")
}

class ReplaceWithSafeCallForScopeFunctionFix(
    expression: KtDotQualifiedExpression,
    notNullNeeded: Boolean
) : ReplaceCallFix(expression, "?.", notNullNeeded) {
    override fun getText() = KotlinBundle.message("replace.scope.function.with.safe.call")
}

class ReplaceWithDotCallFix(
    expression: KtSafeQualifiedExpression,
    private val callChainCount: Int = 0
) : ReplaceCallFix(expression, "."), CleanupFix {
    override fun getText() = KotlinBundle.message("replace.with.dot.call")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        var replaced = replace(element, project, editor) ?: return
        repeat(callChainCount) {
            val parent = replaced.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression ?: return
            replaced = replace(parent, project, editor) ?: return
        }
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val qualifiedExpression = psiElement.getParentOfType<KtSafeQualifiedExpression>(strict = false)
                ?: return emptyList()

            var parent = qualifiedExpression.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression
            var callChainCount = 0
            if (parent != null) {
                val bindingContext = qualifiedExpression.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
                while (parent is KtQualifiedExpression) {
                    val compilerReports = bindingContext.diagnostics.forElement(parent.operationTokenNode as PsiElement)
                    if (compilerReports.none { it.factory == Errors.UNNECESSARY_SAFE_CALL }) break
                    callChainCount++
                    parent = parent.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression
                }
            }

            return listOf(ReplaceWithDotCallFix(qualifiedExpression, callChainCount))
        }
    }
}

fun KtExpression.elvisOrEmpty(notNullNeeded: Boolean): String {
    if (!notNullNeeded) return ""
    val binaryExpression = getStrictParentOfType<KtBinaryExpression>()
    return if (binaryExpression?.left == this && binaryExpression.operationToken == KtTokens.ELVIS) "" else "?:"
}

fun PsiElement.moveCaretToEnd(editor: Editor?, project: Project) {
    editor?.run {
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        val endOffset = if (text.endsWith(")")) endOffset - 1 else endOffset
        document.insertString(endOffset, " ")
        caretModel.moveToOffset(endOffset + 1)
    }
}
