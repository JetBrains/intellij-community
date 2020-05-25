package org.jetbrains.plugins.feature.suggester.changes

import com.intellij.psi.PsiElement

sealed class UserAction(val parent: PsiElement?)

class ChildrenChangedAction(parent: PsiElement?) : UserAction(parent)
class ChildAddedAction(parent: PsiElement?, val newChild: PsiElement?) : UserAction(parent)
class ChildReplacedAction(parent: PsiElement?, val newChild: PsiElement?, val oldChild: PsiElement?) : UserAction(parent)
class ChildRemovedAction(parent: PsiElement?, val child: PsiElement?) : UserAction(parent)
class PropertyChangedAction(parent: PsiElement?) : UserAction(parent)
class ChildMovedAction(parent: PsiElement?, val child: PsiElement?, val oldParent: PsiElement?) : UserAction(parent)


sealed class UserAnAction(val timestamp: Long)

class BackspaceAction(val selectedText: String, timestamp: Long) : UserAnAction(timestamp)
