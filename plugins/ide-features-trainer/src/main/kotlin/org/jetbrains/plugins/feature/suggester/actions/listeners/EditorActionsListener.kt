package org.jetbrains.plugins.feature.suggester.actions.listeners

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.BackspaceAction
import com.intellij.openapi.editor.actions.CopyAction
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.plugins.feature.suggester.actions.*
import org.jetbrains.plugins.feature.suggester.suggesters.asString
import java.lang.ref.WeakReference

class EditorActionsListener(val handleAction: (Action) -> Unit) : AnActionListener {
    private val copyPasteManager = CopyPasteManager.getInstance()

    override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
        when (action) {
            is CopyAction -> {
                val copiedText = copyPasteManager.contents?.asString() ?: return
                handleAction(
                    EditorCopyAction(
                        copiedText = copiedText,
                        psiFileRef = WeakReference(psiFile),
                        documentRef = WeakReference(editor.document),
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }
            is PasteAction -> {
                val pastedText = copyPasteManager.contents?.asString() ?: return
                val caretOffset = editor.getCaretOffset()
                handleAction(
                    EditorPasteAction(
                        pastedText = pastedText,
                        caretOffset = caretOffset,
                        psiFileRef = WeakReference(psiFile),
                        documentRef = WeakReference(editor.document),
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }
            is BackspaceAction -> {
                handleAction(
                    EditorBackspaceAction(
                        selection = editor.getSelection(),
                        caretOffset = editor.getCaretOffset(),
                        psiFileRef = WeakReference(psiFile),
                        documentRef = WeakReference(editor.document),
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
        when (action) {
            is CopyAction -> {
                val selectedText = editor.getSelectedText() ?: return
                handleAction(
                    BeforeEditorCopyAction(
                        copiedText = selectedText,
                        psiFileRef = WeakReference(psiFile),
                        documentRef = WeakReference(editor.document),
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }
            is PasteAction -> {
                val pastedText = copyPasteManager.contents?.asString() ?: return
                val caretOffset = editor.getCaretOffset()
                handleAction(
                    BeforeEditorPasteAction(
                        pastedText = pastedText,
                        caretOffset = caretOffset,
                        psiFileRef = WeakReference(psiFile),
                        documentRef = WeakReference(editor.document),
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }
            is BackspaceAction -> {
                handleAction(
                    BeforeEditorBackspaceAction(
                        selection = editor.getSelection(),
                        caretOffset = editor.getCaretOffset(),
                        psiFileRef = WeakReference(psiFile),
                        documentRef = WeakReference(editor.document),
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun Editor.getSelectedText(): String? {
        return selectionModel.selectedText
    }

    private fun Editor.getCaretOffset(): Int {
        return caretModel.offset
    }

    private fun Editor.getSelection(): Selection? {
        with(selectionModel) {
            return if (selectedText != null) {
                Selection(selectionStart, selectionEnd, selectedText!!)
            } else {
                null
            }
        }
    }
}