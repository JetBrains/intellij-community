package org.jetbrains.plugins.feature.suggester.changes

import com.intellij.psi.PsiElement

/**
 * @author Alefas
 * @since 23.05.13
 */
sealed trait UserAction {
  def parent: PsiElement
}

case class ChildrenChangedAction(parent: PsiElement) extends UserAction
case class ChildAddedAction(parent: PsiElement, newChild: PsiElement) extends UserAction
case class ChildReplacedAction(parent: PsiElement, newChild: PsiElement, oldChild: PsiElement) extends UserAction
case class ChildRemovedAction(parent: PsiElement, child: PsiElement) extends UserAction
case class PropertyChangedAction(parent: PsiElement) extends UserAction
case class ChildMovedAction(parent: PsiElement, child: PsiElement, oldParent: PsiElement) extends UserAction

sealed trait UserAnAction {
  def timestamp: Long
}

case class BackspaceAction(selectedText: String, timestamp: Long) extends UserAnAction


