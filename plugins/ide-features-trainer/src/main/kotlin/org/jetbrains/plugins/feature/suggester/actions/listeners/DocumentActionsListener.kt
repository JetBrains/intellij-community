package org.jetbrains.plugins.feature.suggester.actions.listeners

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.feature.suggester.actions.*
import java.lang.ref.WeakReference

class DocumentActionsListener(project: Project, val handleAction: (Action) -> Unit) :
    BulkAwareDocumentListener {
    private val psiDocumentManager = PsiDocumentManager.getInstance(project)

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent) {
        val document = event.source as? Document ?: return
        val psiFile = psiDocumentManager.getPsiFile(document) ?: return
        if (event.newFragment != "" && event.oldFragment == "") {
            handleAction(
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
        val psiFile = psiDocumentManager.getPsiFile(document) ?: return
        if (event.newFragment != "" && event.oldFragment == "") {
            handleAction(
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