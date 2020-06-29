package org.jetbrains.plugins.feature.suggester.changes

import com.intellij.psi.PsiElement

sealed class UserAction(open val parent: PsiElement?)

data class ChildrenChangedAction(override val parent: PsiElement?) : UserAction(parent)
data class ChildAddedAction(override val parent: PsiElement?, val newChild: PsiElement?) : UserAction(parent)
data class ChildReplacedAction(override val parent: PsiElement?, val newChild: PsiElement?, val oldChild: PsiElement?) :
    UserAction(parent)

data class ChildRemovedAction(override val parent: PsiElement?, val child: PsiElement?) : UserAction(parent)
data class PropertyChangedAction(override val parent: PsiElement?) : UserAction(parent)
data class ChildMovedAction(override val parent: PsiElement?, val child: PsiElement?, val oldParent: PsiElement?) :
    UserAction(parent)


sealed class UserAnAction(open val timestamp: Long)

data class BackspaceAction(val selectedText: String, override val timestamp: Long) : UserAnAction(timestamp)
