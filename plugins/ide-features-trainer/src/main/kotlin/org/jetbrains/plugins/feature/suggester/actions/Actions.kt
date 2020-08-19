package org.jetbrains.plugins.feature.suggester.actions

import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.feature.suggester.suggesters.Selection
import java.lang.ref.WeakReference

sealed class Action(open val timeMillis: Long) {
    abstract val language: Language?
    abstract val project: Project?
}

//--------------------------------------PSI ACTIONS----------------------------------------------------------------------------------------
sealed class PsiAction(open val parent: PsiElement?, override val timeMillis: Long) : Action(timeMillis) {
    override val language: Language?
        get() = parent?.language
    override val project: Project?
        get() = parent?.project
}

//-------------------------------------AFTER PSI ACTIONS-----------------------------------------------------------------------------------
data class ChildrenChangedAction(override val parent: PsiElement?, override val timeMillis: Long) :
    PsiAction(parent, timeMillis)

data class ChildAddedAction(
    override val parent: PsiElement?,
    val newChild: PsiElement?,
    override val timeMillis: Long
) : PsiAction(parent, timeMillis)

data class ChildReplacedAction(
    override val parent: PsiElement?,
    val newChild: PsiElement?,
    val oldChild: PsiElement?,
    override val timeMillis: Long
) :
    PsiAction(parent, timeMillis)

data class ChildRemovedAction(override val parent: PsiElement?, val child: PsiElement?, override val timeMillis: Long) :
    PsiAction(parent, timeMillis)

data class PropertyChangedAction(override val parent: PsiElement?, override val timeMillis: Long) :
    PsiAction(parent, timeMillis)

data class ChildMovedAction(
    override val parent: PsiElement?,
    val child: PsiElement?,
    val oldParent: PsiElement?,
    override val timeMillis: Long
) :
    PsiAction(parent, timeMillis)


//-------------------------------------BEFORE PSI ACTIONS----------------------------------------------------------------------------------
data class BeforeChildrenChangedAction(override val parent: PsiElement?, override val timeMillis: Long) :
    PsiAction(parent, timeMillis)

data class BeforeChildAddedAction(
    override val parent: PsiElement?,
    val newChild: PsiElement?,
    override val timeMillis: Long
) : PsiAction(parent, timeMillis)

data class BeforeChildReplacedAction(
    override val parent: PsiElement?,
    val newChild: PsiElement?,
    val oldChild: PsiElement?,
    override val timeMillis: Long
) :
    PsiAction(parent, timeMillis)

data class BeforeChildRemovedAction(
    override val parent: PsiElement?,
    val child: PsiElement?,
    override val timeMillis: Long
) : PsiAction(parent, timeMillis)

data class BeforePropertyChangedAction(override val parent: PsiElement?, override val timeMillis: Long) :
    PsiAction(parent, timeMillis)

data class BeforeChildMovedAction(
    override val parent: PsiElement?,
    val child: PsiElement?,
    val oldParent: PsiElement?,
    override val timeMillis: Long
) :
    PsiAction(parent, timeMillis)


//-------------------------------------EDITOR ACTIONS--------------------------------------------------------------------------------------
sealed class EditorAction(
    open val psiFileRef: WeakReference<PsiFile>,
    open val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : Action(timeMillis) {
    override val language: Language?
        get() = psiFileRef.get()?.language
    override val project: Project?
        get() = psiFileRef.get()?.project
}

//-------------------------------------AFTER EDITOR ACTIONS--------------------------------------------------------------------------------
data class EditorBackspaceAction(
    val selection: Selection?,
    val caretOffset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class EditorCopyAction(
    val copiedText: String,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class EditorCutAction(
    val text: String,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class EditorPasteAction(
    val pastedText: String,
    val caretOffset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class EditorTextInsertedAction(
    val text: String,
    val offset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class EditorTextRemovedAction(
    val text: String,
    val offset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class EditorFindAction(
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class EditorCodeCompletionAction(
    val offset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class CompletionChooseItemAction(
    val offset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class EditorEscapeAction(
    val caretOffset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

//-------------------------------------BEFORE EDITOR ACTIONS-------------------------------------------------------------------------------
data class BeforeEditorBackspaceAction(
    val selection: Selection?,
    val caretOffset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class BeforeEditorCopyAction(
    val copiedText: String,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class BeforeEditorCutAction(
    val selection: Selection?,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class BeforeEditorPasteAction(
    val pastedText: String,
    val caretOffset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class BeforeEditorTextInsertedAction(
    val text: String,
    val offset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class BeforeEditorTextRemovedAction(
    val text: String,
    val offset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class BeforeEditorFindAction(
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class BeforeEditorCodeCompletionAction(
    val offset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class BeforeCompletionChooseItemAction(
    val offset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)

data class BeforeEditorEscapeAction(
    val caretOffset: Int,
    override val psiFileRef: WeakReference<PsiFile>,
    override val documentRef: WeakReference<Document>,
    override val timeMillis: Long
) : EditorAction(psiFileRef, documentRef, timeMillis)


//-------------------------------------OTHER ACTIONS---------------------------------------------------------------------------------------

data class EditorFocusGainedAction(
    val editor: Editor,
    override val timeMillis: Long
) : Action(timeMillis) {
    override val language: Language?
        get() = psiFile?.language

    override val project: Project?
        get() = editor.project

    val document: Document
        get() = editor.document

    val psiFile: PsiFile?
        get() {
            val project = project ?: return null
            return PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        }
}