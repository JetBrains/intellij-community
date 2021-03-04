package org.jetbrains.plugins.feature.suggester.listeners

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.guessProjectForFile
import org.jetbrains.plugins.feature.suggester.TextFragment
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorTextInsertedAction
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorTextRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.EditorTextInsertedAction
import org.jetbrains.plugins.feature.suggester.actions.EditorTextRemovedAction
import org.jetbrains.plugins.feature.suggester.handleAction
import java.lang.ref.WeakReference

object DocumentActionsListener : BulkAwareDocumentListener {

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent) = runInEdt {
        handleDocumentAction(
            event = event,
            textInsertedActionConstructor = ::BeforeEditorTextInsertedAction,
            textRemovedActionConstructor = ::BeforeEditorTextRemovedAction
        )
    }

    override fun documentChangedNonBulk(event: DocumentEvent) = runInEdt {
        handleDocumentAction(
            event = event,
            textInsertedActionConstructor = ::EditorTextInsertedAction,
            textRemovedActionConstructor = ::EditorTextRemovedAction
        )
    }

    private fun <T : Action> handleDocumentAction(
        event: DocumentEvent,
        textInsertedActionConstructor: (String, Int, WeakReference<Editor>, Long) -> T,
        textRemovedActionConstructor: (TextFragment, Int, WeakReference<Editor>, Long) -> T
    ) {
        val document = event.source as? Document ?: return
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val project = guessProjectForFile(virtualFile) ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        if (event.newFragment != "" && event.oldFragment == "") {
            handleAction(
                project,
                textInsertedActionConstructor(
                    event.newFragment.toString(),
                    event.offset,
                    WeakReference(editor),
                    System.currentTimeMillis()
                )
            )
        } else if (event.oldFragment != "" && event.newFragment == "") {
            handleAction(
                project,
                textRemovedActionConstructor(
                    TextFragment(
                        event.offset,
                        event.offset + event.oldLength,
                        event.oldFragment.toString()
                    ),
                    event.offset,
                    WeakReference(editor),
                    System.currentTimeMillis()
                )
            )
        }
    }
}
