// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtilEx
import com.intellij.psi.PsiFile
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.psi.KtFile

internal class KDocSectionEnterHandlerDelegate: EnterHandlerDelegate {
    private fun getHost(file: PsiFile, editor: Editor): KDocSection? {
        if (file !is KtFile || !file.isValid()) return null
        if (editor !is EditorWindow) return null

        val injectedLanguageManager = InjectedLanguageManager.getInstance(file.project)
        return injectedLanguageManager.getInjectionHost(file) as? KDocSection
    }

    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        if (getHost(file, editor) == null) return EnterHandlerDelegate.Result.Continue

        val hostEditor = (editor as EditorWindow).getDelegate()
        val hostDocument = hostEditor.getDocument()

        val caretModelHost = hostEditor.getCaretModel()
        val caretOffsetHost = caretModelHost.offset + 1

        val text = hostDocument.text
        // at this point `\n` is already inserted
        val lineStartOffset = DocumentUtil.getLineStartOffset(caretOffsetHost, hostDocument)
        val lineEndOffset = DocumentUtil.getLineEndOffset(caretOffsetHost, hostDocument)
        val firstNonWsLineOffset = CharArrayUtil.shiftForward(text, lineStartOffset, " \t")

        if (text[firstNonWsLineOffset] != '*') {
            val lineNumber = hostDocument.getLineNumber(caretOffsetHost)
            val lineTextRange = DocumentUtil.getLineTextRange(hostDocument, lineNumber - 1)

            val newLinePrefixWithOffset = calculateNewLinePrefixWithOffset(text, lineTextRange.startOffset)

            hostDocument.replaceString(lineStartOffset, lineEndOffset, newLinePrefixWithOffset)
            caretModelHost.moveToOffset(caretOffsetHost + newLinePrefixWithOffset.length)
            EditorModificationUtilEx.scrollToCaret(editor)
        }

        return EnterHandlerDelegate.Result.Default
    }

    private fun calculateNewLinePrefixWithOffset(text: String, lineStartOffset: Int): String {
        var offset = lineStartOffset
        var endOffset = offset
        while (offset < text.length) {
            val char = text[offset]
            if (char == '\n' || !char.isWhitespace() && char != '*') {
                endOffset = offset
                break
            }
            offset++
        }

        return text.substring(lineStartOffset, endOffset)
    }
}
