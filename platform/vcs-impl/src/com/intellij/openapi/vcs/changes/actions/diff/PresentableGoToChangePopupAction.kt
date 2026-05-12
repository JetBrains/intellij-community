// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.actions.impl.LinkAction
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent

@ApiStatus.Internal
class PresentableGoToChangePopupAction<T : Any> private constructor(
  private val changesSupplier: () -> ListSelection<out T>,
  private val popupController: GoToChangePopupController<T>,
) : LinkAction() {
  init {
    ActionUtil.copyFrom(this, "GotoChangedFile")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val changes = getChanges()
    e.presentation.apply {
      isVisible = e.getData(DiffDataKeys.DIFF_CONTEXT) != null
      isEnabled = changes.list.size > 1
      icon = null
      text = DiffBundle.message("diff.files.count.progress", changes.list.size, changes.selectedIndex + 1)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: ProjectManager.getInstance().getDefaultProject()
    val popup = GoToChangePopupUtil.createPopup(project, getChanges(), popupController)

    val event = e.inputEvent
    if (event is MouseEvent) {
      popup.showUnderneathOf(event.component)
    }
    else {
      popup.showInBestPositionFor(e.dataContext)
    }
  }

  private fun getChanges() = changesSupplier()

  @Suppress("CompanionObjectInExtension")
  companion object {
    @JvmStatic
    fun <T : Any> create(
      changesSupplier: () -> ListSelection<out T>,
      popupController: GoToChangePopupController<T>,
    ): AnAction = PresentableGoToChangePopupAction(changesSupplier, popupController)
  }
}
