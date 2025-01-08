// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.committed.ClearCommittedAction.Companion.getSelectedChangesViewContent
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings
import com.intellij.ui.BadgeIconSupplier
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

private fun <S : ChangeBrowserSettings> RepositoryLocationCommittedChangesPanel<S>.setCommittedChangesFilter() {
  val dialog = CommittedChangesFilterDialog(project, provider.createFilterUI(true), settings)
  if (!dialog.showAndGet()) return

  @Suppress("UNCHECKED_CAST")
  settings = dialog.settings as S
  refreshChanges()
}

@ApiStatus.Internal
class FilterCommittedAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val changesPanel = e.getSelectedChangesViewContent<RepositoryLocationCommittedChangesPanel<*>>()
    e.presentation.isEnabledAndVisible = changesPanel != null

    if (changesPanel != null) {
      e.presentation.icon = getFilterIcon(changesPanel.settings)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getSelectedChangesViewContent<RepositoryLocationCommittedChangesPanel<*>>()!!.setCommittedChangesFilter()
  }

  companion object {
    private val FILTER_ICON = BadgeIconSupplier(AllIcons.General.Filter)

    @JvmStatic
    fun getFilterIcon(settings: ChangeBrowserSettings): Icon {
      return if (settings.isAnyFilterSpecified) {
        FILTER_ICON.liveIndicatorIcon
      }
      else {
        FILTER_ICON.originalIcon
      }
    }
  }
}
