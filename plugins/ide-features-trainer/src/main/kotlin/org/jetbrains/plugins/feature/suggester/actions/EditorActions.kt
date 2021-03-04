package org.jetbrains.plugins.feature.suggester.actions

import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.feature.suggester.TextFragment
import java.lang.ref.WeakReference

sealed class EditorAction : Action() {
    protected abstract val editorRef: WeakReference<Editor>

    override val language: Language?
        get() = psiFile?.language

    override val project: Project?
        get() = editorRef.get()?.project

    val editor: Editor?
        get() = editorRef.get()

    val psiFile: PsiFile?
        get() {
            val editor = editorRef.get() ?: return null
            val project = editor.project ?: return null
            return PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        }

    val document: Document?
        get() = editorRef.get()?.document
}

// -------------------------------------EDITOR AFTER ACTIONS--------------------------------------------------------------------------------
data class EditorBackspaceAction(
    val textFragment: TextFragment?,
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class EditorCopyAction(
    val text: String,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class EditorCutAction(
    val text: String,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class EditorPasteAction(
    val text: String,
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class EditorTextInsertedAction(
    val text: String,
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class EditorTextRemovedAction(
    val textFragment: TextFragment,
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class EditorFindAction(
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class EditorCodeCompletionAction(
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class CompletionChooseItemAction(
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class EditorEscapeAction(
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class EditorFocusGainedAction(
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

// -------------------------------------EDITOR BEFORE ACTIONS-------------------------------------------------------------------------------
data class BeforeEditorBackspaceAction(
    val textFragment: TextFragment?,
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class BeforeEditorCopyAction(
    val text: String,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class BeforeEditorCutAction(
    val textFragment: TextFragment?,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class BeforeEditorPasteAction(
    val text: String,
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class BeforeEditorTextInsertedAction(
    val text: String,
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class BeforeEditorTextRemovedAction(
    val textFragment: TextFragment,
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class BeforeEditorFindAction(
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class BeforeEditorCodeCompletionAction(
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class BeforeCompletionChooseItemAction(
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()

data class BeforeEditorEscapeAction(
    val caretOffset: Int,
    override val editorRef: WeakReference<Editor>,
    override val timeMillis: Long
) : EditorAction()
