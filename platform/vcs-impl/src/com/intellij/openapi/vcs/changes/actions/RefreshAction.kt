// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt

class RefreshAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    doRefresh(project)
  }

  companion object {
    private val LOG = logger<RefreshAction>()

    @JvmStatic
    @RequiresEdt
    fun doRefresh(project: Project) {
      if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return

      LOG.debug("Performing changes refresh")

      val changeListManager = ChangeListManagerEx.getInstanceEx(project)
      val changesBeforeUpdate = changeListManager.getAllChanges()
      val unversionedBefore = changeListManager.getUnversionedFilesPaths()
      val wasUpdatingBefore = changeListManager.isInUpdate()

      saveAllAndInvokeCustomRefreshers(project)

      VirtualFileManager.getInstance().asyncRefresh {
        performRefreshAndTrackChanges(project, changesBeforeUpdate, unversionedBefore, wasUpdatingBefore)
      }
    }

    @RequiresEdt
    internal fun saveAllAndInvokeCustomRefreshers(project: Project) {
      LOG.info("Saving all documents and project settings")

      FileDocumentManager.getInstance().saveAllDocuments()
      runWithModalProgressBlocking(project, IdeBundle.message("progress.saving.project", project.name)) {
        saveSettings(project)
      }
      invokeCustomRefreshes(project)
    }

    private fun invokeCustomRefreshes(project: Project) {
      for (refresher in ChangesViewRefresher.EP_NAME.getExtensionList(project)) {
        refresher.refresh(project)
      }
    }

    private fun performRefreshAndTrackChanges(
      project: Project,
      changesBeforeUpdate: Collection<Change>,
      unversionedBefore: Collection<FilePath>,
      wasUpdatingBefore: Boolean,
    ) {
      if (project.isDisposed()) return
      val changeListManager = ChangeListManagerEx.getInstanceEx(project)

      VcsDirtyScopeManager.getInstance(project).markEverythingDirty()

      changeListManager.invokeAfterUpdate(false) {
        val changesAfterUpdate = changeListManager.getAllChanges()
        val unversionedAfter = changeListManager.getUnversionedFilesPaths()
        VcsStatisticsCollector
          .logRefreshActionPerformed(project, changesBeforeUpdate, changesAfterUpdate, unversionedBefore, unversionedAfter,
                                     wasUpdatingBefore)
      }
    }
  }
}
