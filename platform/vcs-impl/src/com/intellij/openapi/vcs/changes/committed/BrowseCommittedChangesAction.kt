// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.getQuestionIcon
import com.intellij.openapi.ui.Messages.showYesNoCancelDialog
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.AbstractVcs.fileInVcsByFileStatus
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangesUtil.getVcsForFile
import com.intellij.openapi.vcs.changes.committed.CommittedChangesViewManager.Companion.isCommittedChangesAvailable
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverVersionControlView
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil.getFilePath

class BrowseCommittedChangesAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false

    val project = e.project ?: return
    val file = e.getData(VIRTUAL_FILE) ?: return
    val vcs = getVcsForFile(file, project) ?: return
    if (!isCommittedChangesAvailable(vcs)) return

    e.presentation.isVisible = true
    e.presentation.isEnabled = vcs.allowsRemoteCalls(file) && fileInVcsByFileStatus(vcs.project, file)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val file = e.getData(VIRTUAL_FILE)!!
    val vcs = getVcsForFile(file, project)!!
    val settings = getChangeBrowserSettings(vcs)

    if (CommittedChangesFilterDialog(project, vcs.committedChangesProvider!!.createFilterUI(true), settings).showAndGet()) {
      showCommittedChanges(vcs, file, settings)
    }
  }
}

private fun getChangeBrowserSettings(vcs: AbstractVcs): ChangeBrowserSettings =
  vcs.configuration.changeBrowserSettings.computeIfAbsent(vcs.name) { vcsName ->
    vcs.committedChangesProvider!!.createDefaultSettings().also {
      vcs.project.stateStore.initPersistencePlainComponent(it, "VcsManager.ChangeBrowser.$vcsName")
    }
  }

private fun showCommittedChanges(vcs: AbstractVcs, file: VirtualFile, settings: ChangeBrowserSettings) {
  val maxCount = if (!settings.isAnyFilterSpecified) askMaxCount(vcs.project) else 0
  if (maxCount < 0) return

  val repositoryLocation = CommittedChangesCache.getInstance(vcs.project).locationCache.getLocation(vcs, getFilePath(file), false)
  if (repositoryLocation == null) {
    showOverVersionControlView(vcs.project, message("changes.notification.content.repository.location.not.found.for", file.presentableUrl), MessageType.ERROR)
    return
  }

  AbstractVcsHelper.getInstance(vcs.project).openCommittedChangesTab(
    vcs.committedChangesProvider!!, repositoryLocation, settings, maxCount, null)
}

private fun askMaxCount(project: Project): Int =
  when (
    showYesNoCancelDialog(
      project, message("browse.changes.no.filter.prompt"), message("browse.changes.title"), message("browse.changes.show.recent.button"),
      message("browse.changes.show.all.button"), getCancelButtonText(), getQuestionIcon())
    ) {
    Messages.CANCEL -> -1
    Messages.YES -> 50
    else -> 0
  }
