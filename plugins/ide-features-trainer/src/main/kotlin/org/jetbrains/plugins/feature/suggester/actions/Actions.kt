package org.jetbrains.plugins.feature.suggester.actions

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.feature.suggester.suggesters.Selection
import java.lang.ref.WeakReference

sealed class Action(open val timeMillis: Long)

//--------------------------------------PSI ACTIONS----------------------------------------------------------------------------------------
sealed class PsiAction(open val parent: PsiElement?, override val timeMillis: Long) : Action(timeMillis)

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
) : Action(timeMillis)

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

data class Selection(val startOffset: Int, val endOffset: Int, val text: String)