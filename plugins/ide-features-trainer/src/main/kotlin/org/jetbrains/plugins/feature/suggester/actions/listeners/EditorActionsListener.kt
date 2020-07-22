package org.jetbrains.plugins.feature.suggester.actions.listeners

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.BackspaceAction
import com.intellij.openapi.editor.actions.CopyAction
import com.intellij.openapi.editor.actions.CutAction
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.plugins.feature.suggester.actions.*
import org.jetbrains.plugins.feature.suggester.suggesters.asString
import org.jetbrains.plugins.feature.suggester.suggesters.getSelection
import org.jetbrains.plugins.feature.suggester.suggesters.handleAction
import java.lang.ref.WeakReference

object EditorActionsListener : AnActionListener {
    private val copyPasteManager = CopyPasteManager.getInstance()

    override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        if (!action.isSupportedAction()) return
        val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
        when (action) {
            is CopyAction -> {
                val copiedText = copyPasteManager.contents?.asString() ?: return
                handleAction(
                    project,
                    EditorCopyAction(
                        copiedText = copiedText,
                        psiFileRef = WeakReference(psiFile),
                        documentRef = WeakReference(editor.document),
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }
            is CutAction -> {
                val text = copyPasteManager.contents?.asString() ?: return
                handleAction(
                    project,
                    EditorCutAction(
                        text = text,
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
                    project,
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
                    project,
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
        if (!action.isSupportedAction()) return
        val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
        when (action) {
            is CopyAction -> {
                val selectedText = editor.getSelectedText() ?: return
                handleAction(
                    project,
                    BeforeEditorCopyAction(
                        copiedText = selectedText,
                        psiFileRef = WeakReference(psiFile),
                        documentRef = WeakReference(editor.document),
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }
            is CutAction -> {
                handleAction(
                    project,
                    BeforeEditorCutAction(
                        selection = editor.getSelection(),
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
                    project,
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
                    project,
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

    private fun AnAction.isSupportedAction(): Boolean {
        return this is CopyAction || this is CutAction
                || this is PasteAction || this is BackspaceAction
    }
}