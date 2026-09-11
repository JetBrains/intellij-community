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
        if (getHost(file, editor) == null)
            return EnterHandlerDelegate.Result.Continue

        val hostEditor = (editor as EditorWindow).getDelegate()
        val hostDocument = hostEditor.getDocument()

        val caretModelHost = hostEditor.getCaretModel()
        val caretOffsetHost = caretModelHost.offset + 1

        val text = hostDocument.text
        // at this point `\n` is already inserted
        var lineStartOffset = DocumentUtil.getLineStartOffset(caretOffsetHost, hostDocument)
        val firstNonWsLineOffset = CharArrayUtil.shiftForward(text, lineStartOffset, " \t")

        val charAt = text[firstNonWsLineOffset]
        if (charAt != '}' && charAt != ')') {
            val lineNumber = hostDocument.getLineNumber(caretOffsetHost)
            val lineTextRange = DocumentUtil.getLineTextRange(hostDocument, lineNumber - 1)

            var newLinePrefixWithOffset = calculateNewLinePrefixWithOffset(text, lineTextRange.startOffset)
            if (newLinePrefixWithOffset.isEmpty()) {
                newLinePrefixWithOffset = " *"
                lineStartOffset--
            }

            if (charAt == '\n') {
                newLinePrefixWithOffset = "$newLinePrefixWithOffset "
            }

            hostDocument.insertString(lineStartOffset, newLinePrefixWithOffset)
            val newOffset = caretOffsetHost + newLinePrefixWithOffset.length - 1
            caretModelHost.moveToOffset(newOffset)
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
        if (text[offset] == '\n') {
            while (offset > lineStartOffset) {
                val char = text[offset]
                if (!char.isWhitespace()) {
                    endOffset = offset + 1
                    break
                }
                offset--
            }
        }

        val substring = text.substring(lineStartOffset, endOffset)
        return substring
    }
}
