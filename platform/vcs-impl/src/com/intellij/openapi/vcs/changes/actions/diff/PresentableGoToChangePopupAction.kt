// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.actions.impl.GoToChangePopupBuilder.BaseGoToChangePopupAction
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup

class PresentableGoToChangePopupAction<T : Any> private constructor(
  private val changesSupplier: () -> ListSelection<out T>,
  private val popupController: GoToChangePopupController<T>,
) : BaseGoToChangePopupAction() {
  private fun getChanges() = changesSupplier()

  override fun canNavigate(): Boolean = getChanges().getList().size > 1

  override fun createPopup(e: AnActionEvent): JBPopup {
    val project = e.project ?: ProjectManager.getInstance().getDefaultProject()
    return GoToChangePopupUtil.createPopup(project, getChanges(), popupController)
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    @JvmStatic
    fun <T : Any> create(
      changesSupplier: () -> ListSelection<out T>,
      popupController: GoToChangePopupController<T>,
    ): AnAction = PresentableGoToChangePopupAction(changesSupplier, popupController)
  }
}
