package org.jetbrains.plugins.feature.suggester.actions.listeners

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorTextInsertedAction
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorTextRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.EditorTextInsertedAction
import org.jetbrains.plugins.feature.suggester.actions.EditorTextRemovedAction
import org.jetbrains.plugins.feature.suggester.suggesters.handleAction
import java.lang.ref.WeakReference

object DocumentActionsListener : BulkAwareDocumentListener {

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent) {
        val document = event.source as? Document ?: return
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val project = guessProjectForFile(virtualFile) ?: return
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return
        if (event.newFragment != "" && event.oldFragment == "") {
            handleAction(
                project,
                BeforeEditorTextInsertedAction(
                    text = event.newFragment.toString(),
                    offset = event.offset,
                    psiFileRef = WeakReference(psiFile),
                    documentRef = WeakReference(document),
                    timeMillis = System.currentTimeMillis()
                )
            )
        } else if (event.oldFragment != "" && event.newFragment == "") {
            handleAction(
                project,
                BeforeEditorTextRemovedAction(
                    text = event.oldFragment.toString(),
                    offset = event.offset,
                    psiFileRef = WeakReference(psiFile),
                    documentRef = WeakReference(document),
                    timeMillis = System.currentTimeMillis()
                )
            )
        }
    }

    override fun documentChangedNonBulk(event: DocumentEvent) {
        val document = event.source as? Document ?: return
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val project = guessProjectForFile(virtualFile) ?: return
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return
        if (event.newFragment != "" && event.oldFragment == "") {
            handleAction(
                project,
                EditorTextInsertedAction(
                    text = event.newFragment.toString(),
                    offset = event.offset,
                    psiFileRef = WeakReference(psiFile),
                    documentRef = WeakReference(document),
                    timeMillis = System.currentTimeMillis()
                )
            )
        } else if (event.oldFragment != "" && event.newFragment == "") {
            handleAction(
                project,
                EditorTextRemovedAction(
                    text = event.oldFragment.toString(),
                    offset = event.offset,
                    psiFileRef = WeakReference(psiFile),
                    documentRef = WeakReference(document),
                    timeMillis = System.currentTimeMillis()
                )
            )
        }
    }
}