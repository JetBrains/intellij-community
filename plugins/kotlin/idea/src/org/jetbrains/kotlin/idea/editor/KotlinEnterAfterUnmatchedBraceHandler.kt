// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinEnterAfterUnmatchedBraceHandler : EnterAfterUnmatchedBraceHandler() {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffsetRef: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        val caretOffset = caretOffsetRef.get() - 1
        val element = file.findElementAt(caretOffset)
        if (element?.node?.elementType == KtTokens.LBRACE) {
            return super.preprocessEnter(file, editor, caretOffsetRef, caretAdvance, dataContext, originalHandler)
        }
        if (element !is PsiWhiteSpace) {
            return EnterHandlerDelegate.Result.Continue
        }
        val prevElement = CodeInsightUtils.getElementAtOffsetIgnoreWhitespaceAfter(file, caretOffset)
        if (prevElement != null && prevElement.node.elementType == KtTokens.LBRACE) {
            return super.preprocessEnter(file, editor, Ref(prevElement.startOffset + 1), caretAdvance, dataContext, originalHandler)
        }
        return EnterHandlerDelegate.Result.Continue
    }

    override fun getRBraceOffset(file: PsiFile, editor: Editor, caretOffset: Int): Int {
        val element = file.findElementAt(caretOffset - 1)
        val nextSibling = element?.nextSibling
        if (nextSibling is PsiWhiteSpace && nextSibling.textContains('\n')) return super.getRBraceOffset(file, editor, caretOffset)
        val endOffset = when (val parent = element?.parent) {
            is KtFunctionLiteral -> {
                getRBraceForLambda(parent)
            }
            is KtWhenExpression -> {
                if (parent.isDeclarationInitializer()) {
                    (parent.entries.firstOrNull()?.conditions?.firstOrNull() as? KtWhenConditionWithExpression)?.endOffset
                } else {
                    null
                }
            }
            else -> null
        }
        return endOffset ?: super.getRBraceOffset(file, editor, caretOffset)
    }

    private fun getRBraceForLambda(functionLiteral: KtFunctionLiteral): Int? {
        val bodyExpression = (functionLiteral.parent as? KtLambdaExpression)?.bodyExpression ?: return null
        val firstVisibleChild = bodyExpression.firstChild?.getNextSiblingIgnoringWhitespace(withItself = true)
        return firstVisibleChild?.endOffset
    }

    private fun KtExpression.isDeclarationInitializer(): Boolean {
        return (parent as? KtDeclarationWithInitializer)?.initializer == this
    }
}