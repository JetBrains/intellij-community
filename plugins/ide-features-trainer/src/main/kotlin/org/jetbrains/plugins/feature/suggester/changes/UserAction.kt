package org.jetbrains.plugins.feature.suggester.changes

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

sealed class UserAction(open val parent: PsiElement?)

data class ChildrenChangedAction(override val parent: PsiElement?) : UserAction(parent)
data class ChildAddedAction(override val parent: PsiElement?, val newChild: PsiElement?) : UserAction(parent)
data class ChildReplacedAction(override val parent: PsiElement?, val newChild: PsiElement?, val oldChild: PsiElement?) :
    UserAction(parent)

data class ChildRemovedAction(override val parent: PsiElement?, val child: PsiElement?) : UserAction(parent)
data class PropertyChangedAction(override val parent: PsiElement?) : UserAction(parent)
data class ChildMovedAction(override val parent: PsiElement?, val child: PsiElement?, val oldParent: PsiElement?) :
    UserAction(parent)

data class BeforeChildrenChangedAction(override val parent: PsiElement?) : UserAction(parent)
data class BeforeChildAddedAction(override val parent: PsiElement?, val newChild: PsiElement?) : UserAction(parent)
data class BeforeChildReplacedAction(override val parent: PsiElement?, val newChild: PsiElement?, val oldChild: PsiElement?) :
    UserAction(parent)

data class BeforeChildRemovedAction(override val parent: PsiElement?, val child: PsiElement?) : UserAction(parent)
data class BeforePropertyChangedAction(override val parent: PsiElement?) : UserAction(parent)
data class BeforeChildMovedAction(override val parent: PsiElement?, val child: PsiElement?, val oldParent: PsiElement?) :
    UserAction(parent)

sealed class UserAnAction(open val timeMillis: Long)

data class EditorBackspaceAction(val selection: Selection?, val caretOffset: Int, val psiFile: PsiFile, override val timeMillis: Long) : UserAnAction(timeMillis)
data class EditorCopyAction(val copiedText: String, override val timeMillis: Long): UserAnAction(timeMillis)
data class EditorPasteAction(val pastedText: String, val caretOffset: Int, override val timeMillis: Long): UserAnAction(timeMillis)
data class BeforeEditorBackspaceAction(val selection: Selection?, val caretOffset: Int, val psiFile: PsiFile, override val timeMillis: Long) : UserAnAction(timeMillis)
data class BeforeEditorCopyAction(val copiedText: String, override val timeMillis: Long): UserAnAction(timeMillis)
data class BeforeEditorPasteAction(val pastedText: String, val caretOffset: Int, override val timeMillis: Long): UserAnAction(timeMillis)

data class Selection(val startOffset: Int, val endOffset: Int, val text: String)
