// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.tree.TokenSet
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal fun getElementAtOffsetIgnoreWhitespaceBefore(file: PsiFile, offset: Int): PsiElement? {
    val element = file.findElementAt(offset)
    if (element is PsiWhiteSpace) {
        return file.findElementAt(element.getTextRange().endOffset)
    }
    return element
}

class KotlinEnterHandler : EnterHandlerDelegate {
    companion object {
        private val LOG = Logger.getInstance(KotlinEnterHandler::class.java)
        private val FORCE_INDENT_IN_LAMBDA_AFTER = TokenSet.create(KtTokens.ARROW, KtTokens.LBRACE)
        private const val KDOC_PREFIX = "/**"
        private const val KDOC_SUFFIX = "*/"
    }

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffsetRef: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        if (file.fileType != KotlinFileType.INSTANCE) return EnterHandlerDelegate.Result.Continue

        if (preprocessEnterInStringLiteral(file, editor, caretOffsetRef, caretAdvance)) {
            return EnterHandlerDelegate.Result.DefaultForceIndent
        }

        if (!CodeInsightSettings.getInstance()!!.SMART_INDENT_ON_ENTER) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        val text = document.charsSequence
        val caretOffset = caretOffsetRef.get()!!.toInt()

        if (caretOffset !in 0..text.length) return EnterHandlerDelegate.Result.Continue

        prepareKDocEndOnSeparateLine(file, editor, caretOffset)?.let { return it }

        val elementAt = file.findElementAt(caretOffset)
        if (elementAt is PsiWhiteSpace && elementAt.textContains('\n')) return EnterHandlerDelegate.Result.Continue

        // Indent for LBRACE can be removed after fixing IDEA-124917
        val elementBefore = getElementAtOffsetIgnoreWhitespaceAfter(file, caretOffset)
        val elementAfter = getElementAtOffsetIgnoreWhitespaceBefore(file, caretOffset)

        val isAfterLBraceOrArrow = elementBefore != null && elementBefore.node!!.elementType in FORCE_INDENT_IN_LAMBDA_AFTER
        val isBeforeRBrace = elementAfter != null && elementAfter.node!!.elementType == KtTokens.RBRACE

        if (isAfterLBraceOrArrow && isBeforeRBrace && (elementBefore!!.parent is KtFunctionLiteral)) {
            originalHandler?.execute(editor, editor.caretModel.currentCaret, dataContext)
            PsiDocumentManager.getInstance(file.getProject()).commitDocument(document)

            try {
                CodeStyleManager.getInstance(file.getProject())!!.adjustLineIndent(file, editor.caretModel.offset)
            } catch (e: IncorrectOperationException) {
                LOG.error(e)
            }

            return EnterHandlerDelegate.Result.DefaultForceIndent
        }

        return EnterHandlerDelegate.Result.Continue
    }

    private fun prepareKDocEndOnSeparateLine(file: PsiFile, editor: Editor, caretOffset: Int): EnterHandlerDelegate.Result? {
        val document = editor.document
        PsiDocumentManager.getInstance(file.project).commitDocument(document)

        val text = document.charsSequence
        val lineNumber = document.getLineNumber(caretOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        val kDocStartOffset = findKDocStartOffset(file, text, lineStartOffset, caretOffset) ?: return null
        val kDocEndOffset = findKDocEndOffset(file, text, caretOffset, lineEndOffset) ?: return null
        val lineIndentEndOffset = getLineIndentEndOffset(text, lineStartOffset, kDocStartOffset)
        val lineIndent = text.subSequence(lineStartOffset, lineIndentEndOffset).toString()
        if (lineIndentEndOffset < kDocStartOffset && isAtKDocContentStart(text, kDocStartOffset, caretOffset)) {
            splitInlineKDocAtContentStart(editor, caretOffset, kDocStartOffset, kDocEndOffset, lineIndent)
            return EnterHandlerDelegate.Result.Stop
        }

        val prefix = "$lineIndent "
        val insertionOffset = deleteSeparatorBeforeKDocEnd(document, caretOffset, kDocEndOffset)

        document.insertString(insertionOffset, "\n$prefix")
        return EnterHandlerDelegate.Result.Continue
    }

    private fun splitInlineKDocAtContentStart(
        editor: Editor,
        caretOffset: Int,
        kDocStartOffset: Int,
        kDocEndOffset: Int,
        lineIndent: String,
    ) {
        val document = editor.document
        val contentStartOffset = kDocStartOffset + KDOC_PREFIX.length
        val separatorLength = caretOffset - contentStartOffset
        document.deleteString(contentStartOffset, caretOffset)
        val insertionOffset = deleteSeparatorBeforeKDocEnd(document, contentStartOffset, kDocEndOffset - separatorLength)
        document.insertString(insertionOffset, "\n$lineIndent ")

        val contentPrefix = "\n$lineIndent * "
        document.insertString(contentStartOffset, contentPrefix)
        editor.caretModel.moveToOffset(contentStartOffset + contentPrefix.length)
    }

    private fun isAtKDocContentStart(text: CharSequence, kDocStartOffset: Int, caretOffset: Int): Boolean {
        var offset = kDocStartOffset + KDOC_PREFIX.length
        if (caretOffset < offset) return false
        while (offset < caretOffset) {
            if (text[offset].let { it != ' ' && it != '\t' }) return false
            offset++
        }
        return true
    }

    private fun getLineIndentEndOffset(text: CharSequence, lineStartOffset: Int, maxOffset: Int): Int {
        var offset = lineStartOffset
        while (offset < maxOffset && text[offset].let { it == ' ' || it == '\t' }) {
            offset++
        }
        return offset
    }

    private fun findKDocStartOffset(file: PsiFile, text: CharSequence, startOffset: Int, endOffset: Int): Int? {
        var offset = endOffset - 1
        while (offset >= startOffset) {
            val element = file.findElementAt(offset)
            if (offset <= text.length - KDOC_PREFIX.length &&
                text.startsWith(KDOC_PREFIX, offset, false) &&
                element?.node?.elementType == KDocTokens.START &&
                element.parent.textRange.contains(endOffset)
            ) {
                return offset
            }
            offset--
        }
        return null
    }

    private fun findKDocEndOffset(file: PsiFile, text: CharSequence, startOffset: Int, endOffset: Int): Int? {
        var offset = startOffset
        while (offset < endOffset - 1) {
            if (text.startsWith(KDOC_SUFFIX, offset, false) && file.findElementAt(offset)?.node?.elementType == KDocTokens.END) {
                return offset
            }
            offset++
        }
        return null
    }

    private fun deleteSeparatorBeforeKDocEnd(document: Document, minOffset: Int, kDocEndOffset: Int): Int {
        var offset = kDocEndOffset
        val text = document.charsSequence
        while (offset > minOffset && text[offset - 1].let { it == ' ' || it == '\t' }) {
            offset--
        }
        if (offset < kDocEndOffset) {
            document.deleteString(offset, kDocEndOffset)
            return offset
        }
        return kDocEndOffset
    }

    // We can't use the core platform logic (EnterInStringLiteralHandler) because it assumes that the string
    // is a single token and the first character of the token is an opening quote. In the case of Kotlin,
    // the opening quote is a separate token and the first character of the string token is just a random letter.
    private fun preprocessEnterInStringLiteral(
        psiFile: PsiFile,
        editor: Editor,
        caretOffsetRef: Ref<Int>,
        caretAdvanceRef: Ref<Int>
    ): Boolean {
        var caretOffset = caretOffsetRef.get()
        val doc = editor.document
        // We commit document, so that psiFile.findElementAt returns proper value in case
        // of multi-caret insertions
        PsiDocumentManager.getInstance(psiFile.project).commitDocument(doc)
        val psiAtOffset = psiFile.findElementAt(caretOffset) ?: return false
        val stringTemplate = psiAtOffset.getStrictParentOfType<KtStringTemplateExpression>() ?: return false
        if (!stringTemplate.isSingleQuoted()) return false
        when (psiAtOffset.node.elementType) {
            KtTokens.CLOSING_QUOTE, KtTokens.REGULAR_STRING_PART, KtTokens.ESCAPE_SEQUENCE,
            KtTokens.SHORT_TEMPLATE_ENTRY_START, KtTokens.LONG_TEMPLATE_ENTRY_START -> {
                var caretAdvance = 1
                if (stringTemplate.parent is KtDotQualifiedExpression) {
                    doc.insertString(stringTemplate.endOffset, ")")
                    doc.insertString(stringTemplate.startOffset, "(")
                    caretOffset++
                    caretAdvance++
                }
                doc.insertString(caretOffset, "\" + \"")
                caretOffsetRef.set(caretOffset + 3)
                caretAdvanceRef.set(caretAdvance)
                return true
            }
        }
        return false
    }
}
