// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.actions.impl.GoToChangePopupBuilder.BaseGoToChangePopupAction
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.vcs.changes.ui.PresentableChange

abstract class PresentableGoToChangePopupAction<T> : BaseGoToChangePopupAction() {
  abstract class Default<T : PresentableChange> : PresentableGoToChangePopupAction<T>() {
    override fun getPresentation(change: T): PresentableChange? = change
  }

  protected abstract fun getChanges(): ListSelection<out T>

  override fun canNavigate(): Boolean = getChanges().getList().size > 1

  protected abstract fun getPresentation(change: T): PresentableChange?

  protected open fun createToolbarActions(): List<AnAction> = listOf()

  protected open fun createPopupMenuActions(): List<AnAction> = listOf()

  protected abstract fun onSelected(change: T)

  final override fun createPopup(e: AnActionEvent): JBPopup {
    val project = e.project ?: ProjectManager.getInstance().getDefaultProject()
    return GoToChangePopupUtil.createPopup(project, getChanges(), PopupController())
  }

  private inner class PopupController : GoToChangePopupController<T> {
    override fun getPresentation(change: T): PresentableChange? = this@PresentableGoToChangePopupAction.getPresentation(change)
    override fun createToolbarActions(): List<AnAction> = this@PresentableGoToChangePopupAction.createToolbarActions()
    override fun createPopupMenuActions(): List<AnAction> = this@PresentableGoToChangePopupAction.createPopupMenuActions()
    override fun onSelected(change: T) = this@PresentableGoToChangePopupAction.onSelected(change)
  }
}
