// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandler
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtilEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.formatter.FormatterUtil
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.base.psi.mustHaveNonEmptyPrimaryConstructor
import org.jetbrains.kotlin.idea.formatter.adjustLineIndent
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinTypedHandler : TypedHandlerDelegate() {
    private var kotlinLTTyped = false

    // Global flag for all editors
    private var isGlobalPreviousDollarInString = false

    override fun beforeCharTyped(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
        fileType: FileType
    ): Result {
        if (file !is KtFile) return Result.CONTINUE
        when (c) {
            ')' -> dataClassValParameterInsert(project, editor, file,  /*beforeType = */true)
            '<' -> {
                kotlinLTTyped = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
                        LtGtTypingUtils.shouldAutoCloseAngleBracket(editor.caretModel.offset, editor)

                autoPopupParameterInfo(project, editor)
            }

            '>' -> {
                if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && LtGtTypingUtils.handleKotlinGTInsert(editor)) {
                    return Result.STOP
                }
            }

            '{' -> {
                // Returning Result.CONTINUE will cause inserting "{}" for unmatched '{'
                val offset = editor.caretModel.offset
                if (offset == 0) {
                    return Result.CONTINUE
                }

                val iterator = editor.highlighter.createIterator(offset - 1)
                while (!iterator.atEnd() && iterator.tokenType === TokenType.WHITE_SPACE) {
                    iterator.retreat()
                }

                if (iterator.atEnd() || iterator.tokenType !in KotlinTypedHandlerTokenSets.SUPPRESS_AUTO_INSERT_CLOSE_BRACE_AFTER) {
                    AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null)
                    return Result.CONTINUE
                }

                val tokenBeforeBraceOffset = iterator.start
                val document = editor.document
                PsiDocumentManager.getInstance(project).commitDocument(document)
                val leaf = file.findElementAt(offset)
                if (leaf != null) {
                    val parent = leaf.parent
                    if (parent != null && parent.node.elementType in KotlinTypedHandlerTokenSets.CONTROL_FLOW_EXPRESSIONS) {
                        val nonWhitespaceSibling = FormatterUtil.getPreviousNonWhitespaceSibling(leaf.node)
                        if (nonWhitespaceSibling != null && nonWhitespaceSibling.startOffset == tokenBeforeBraceOffset) {
                            EditorModificationUtilEx.insertStringAtCaret(editor, "{", false, true)
                            TypedHandler.indentBrace(project, editor, '{')
                            return Result.STOP
                        }
                    }

                    if (leaf.text == "}" && parent is KtFunctionLiteral && document.getLineNumber(offset) == document.getLineNumber(parent.getTextRange().startOffset)) {
                        EditorModificationUtilEx.insertStringAtCaret(editor, "{} ", false, false)
                        editor.caretModel.moveToOffset(offset + 1)
                        return Result.STOP
                    }
                }
            }

            '.' -> autoPopupMemberLookup(project, editor)
            ':' -> autoPopupCallableReferenceLookup(project, editor)
            '[' -> autoPopupParameterInfo(project, editor)
            '@' -> autoPopupAt(project, editor)
        }

        return Result.CONTINUE
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is KtFile) return Result.CONTINUE

        var previousDollarInStringOffset: Int? = null
        if (isGlobalPreviousDollarInString) {
            isGlobalPreviousDollarInString = false
            previousDollarInStringOffset = editor.getUserData(PREVIOUS_IN_STRING_DOLLAR_TYPED_OFFSET_KEY)
        }

        editor.putUserData(PREVIOUS_IN_STRING_DOLLAR_TYPED_OFFSET_KEY, null)
        when {
            kotlinLTTyped -> {
                kotlinLTTyped = false
                LtGtTypingUtils.handleKotlinAutoCloseLT(editor)

                return Result.STOP
            }

            c == ',' || c == ')' -> dataClassValParameterInsert(project, editor, file,  /*beforeType = */false)
            c == '{' && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET -> {
                PsiDocumentManager.getInstance(project).commitDocument(editor.document)
                val offset = editor.caretModel.offset
                val previousElement = file.findElementAt(offset - 1)
                if (previousElement is LeafPsiElement && previousElement.elementType === KtTokens.LONG_TEMPLATE_ENTRY_START) {
                    val identifier = file.findElementAt(offset)
                        ?.safeAs<LeafPsiElement>()
                        ?.takeIf { it.elementType == KtTokens.IDENTIFIER }
                        ?: kotlin.run {
                            editor.document.insertString(offset, "}")
                            return Result.STOP
                        }

                    val lastInLongTemplateEntry = previousElement.getParent().lastChild
                    val isSimpleLongTemplateEntry = lastInLongTemplateEntry is LeafPsiElement &&
                            lastInLongTemplateEntry.elementType === KtTokens.LONG_TEMPLATE_ENTRY_END &&
                            lastInLongTemplateEntry.getParent().textLength == identifier.textLength + "\${}".length

                    if (!isSimpleLongTemplateEntry) {
                        val isAfterTypedDollar = previousDollarInStringOffset != null && previousDollarInStringOffset.toInt() == offset - 1
                        if (isAfterTypedDollar) {
                            editor.document.insertString(offset, "}")
                            return Result.STOP
                        }
                    }
                }
            }

            c == ':' -> {
                if (autoIndentCase(editor, project, file, KtClassOrObject::class.java) ||
                    autoIndentCase(editor, project, file, KtOperationReferenceExpression::class.java)
                ) {
                    return Result.STOP
                }
            }

            c == '.' -> {
                if (autoIndentCase(editor, project, file, KtQualifiedExpression::class.java)) return Result.STOP
            }

            c == '|' -> {
                if (autoIndentCase(editor, project, file, KtOperationReferenceExpression::class.java)) return Result.STOP
            }

            c == '&' -> {
                if (autoIndentCase(editor, project, file, KtOperationReferenceExpression::class.java)) return Result.STOP
            }

            c == '$' -> {
                val offset = editor.caretModel.offset
                val element = file.findElementAt(offset)
                if (element is LeafPsiElement && element.elementType === KtTokens.REGULAR_STRING_PART) {
                    editor.putUserData(PREVIOUS_IN_STRING_DOLLAR_TYPED_OFFSET_KEY, offset)
                    isGlobalPreviousDollarInString = true
                }
            }

            c == '(' -> {
                if (autoIndentCase(editor, project, file, KtPropertyAccessor::class.java, forFirstElement = false)) return Result.STOP
            }
        }

        return Result.CONTINUE
    }

    private object KotlinTypedHandlerTokenSets {
        val CONTROL_FLOW_EXPRESSIONS: TokenSet = TokenSet.create(
            KtNodeTypes.IF,
            KtNodeTypes.ELSE,
            KtNodeTypes.FOR,
            KtNodeTypes.WHILE,
            KtNodeTypes.TRY,
        )

        val SUPPRESS_AUTO_INSERT_CLOSE_BRACE_AFTER: TokenSet = TokenSet.create(
            KtTokens.RPAR,
            KtTokens.ELSE_KEYWORD,
            KtTokens.TRY_KEYWORD,
        )
    }

    companion object {
        private val PREVIOUS_IN_STRING_DOLLAR_TYPED_OFFSET_KEY = Key.create<Int>("PREVIOUS_IN_STRING_DOLLAR_TYPED_OFFSET_KEY")
        private fun autoPopupParameterInfo(project: Project, editor: Editor) {
            val offset = editor.caretModel.offset
            if (offset == 0) return
            val iterator = editor.highlighter.createIterator(offset - 1)
            val tokenType = iterator.tokenType
            if (KtTokens.COMMENTS.contains(tokenType) ||
                tokenType === KtTokens.REGULAR_STRING_PART ||
                tokenType === KtTokens.OPEN_QUOTE ||
                tokenType === KtTokens.CHARACTER_LITERAL
            ) {
                return
            }

            AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null)
        }

        private fun autoPopupMemberLookup(project: Project, editor: Editor): Unit =
            AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, fun(file: PsiFile): Boolean {
                val offset = editor.caretModel.offset
                val lastToken = file.findElementAt(offset - 1) ?: return false
                val elementType = lastToken.node.elementType
                if (elementType === KtTokens.DOT || elementType === KtTokens.SAFE_ACCESS) return true
                if (elementType === KtTokens.REGULAR_STRING_PART && lastToken.textRange.startOffset == offset - 1) {
                    val prevSibling = lastToken.parent.prevSibling
                    return prevSibling is KtSimpleNameStringTemplateEntry
                }
                return false
            })

        private fun isLabelCompletion(chars: CharSequence, offset: Int): Boolean {
            return endsWith(chars, offset, "this@")
                    || endsWith(chars, offset, "return@")
                    || endsWith(chars, offset, "break@")
                    || endsWith(chars, offset, "continue@")
        }

        private fun autoPopupAt(project: Project, editor: Editor) {
            AutoPopupController.getInstance(project).autoPopupMemberLookup(editor) { file: PsiFile ->
                val offset = editor.caretModel.offset
                val chars = editor.document.charsSequence
                val lastNodeType = file.findElementAt(offset - 1)?.node?.elementType ?: return@autoPopupMemberLookup false

                lastNodeType === KDocTokens.TEXT || (lastNodeType === KtTokens.AT && isLabelCompletion(chars, offset))
            }
        }

        private fun autoPopupCallableReferenceLookup(project: Project, editor: Editor): Unit =
            AutoPopupController.getInstance(project).autoPopupMemberLookup(editor) { file: PsiFile ->
                val offset = editor.caretModel.offset
                val lastElement = file.findElementAt(offset - 1) ?: return@autoPopupMemberLookup false
                lastElement.node.elementType === KtTokens.COLONCOLON
            }

        private fun endsWith(chars: CharSequence, offset: Int, text: String): Boolean =
            if (offset < text.length) false else chars.subSequence(offset - text.length, offset).toString() == text

        private fun dataClassValParameterInsert(
            project: Project,
            editor: Editor,
            file: PsiFile,
            beforeType: Boolean
        ) {
            if (!KotlinEditorOptions.getInstance().isAutoAddValKeywordToDataClassParameters) return
            val document = editor.document
            PsiDocumentManager.getInstance(project).commitDocument(document)
            var commaOffset = editor.caretModel.offset
            if (!beforeType) commaOffset--
            if (commaOffset < 1) return
            val elementOnCaret = file.findElementAt(commaOffset) ?: return
            var contextMatched = false
            var parentElement = elementOnCaret.parent
            if (parentElement is KtParameterList) {
                parentElement = parentElement.getParent()
                if (parentElement is KtPrimaryConstructor) {
                    parentElement = parentElement.getParent()
                    if (parentElement is KtClass) {
                        val klassElement = parentElement
                        contextMatched = klassElement.mustHaveNonEmptyPrimaryConstructor()
                    }
                }
            }

            if (!contextMatched) return
            val leftElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(elementOnCaret) as? KtParameter ?: return
            val typeReference = leftElement.typeReference ?: return
            if (leftElement.hasValOrVar()) return
            if (typeReference.textLength == 0) return
            document.insertString(leftElement.textOffset, "val ")
        }

        private fun autoIndentCase(
            editor: Editor,
            project: Project,
            file: PsiFile,
            klass: Class<*>,
            forFirstElement: Boolean = true,
        ): Boolean {
            val offset = editor.caretModel.offset
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            val currElement = file.findElementAt(offset - 1)
            if (currElement != null) {
                // Should be applied only if there's nothing but the whitespace in line before the element
                val prevLeaf = PsiTreeUtil.prevLeaf(currElement)
                if (forFirstElement && !(prevLeaf is PsiWhiteSpace && prevLeaf.textContains('\n'))) {
                    return false
                }

                val parent = currElement.parent
                if (klass.isInstance(parent)) {
                    val curElementLength = currElement.text.length
                    if (offset < curElementLength) return false
                    if (forFirstElement) {
                        CodeStyleManager.getInstance(project).adjustLineIndent(file, offset - curElementLength)
                    } else {
                        PsiDocumentManager.getInstance(project).getDocument(file)?.adjustLineIndent(project, offset)
                    }

                    return true
                }
            }

            return false
        }
    }
}