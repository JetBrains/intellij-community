// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.configurationStore.saveDocumentsAndProjectSettings
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.util.ArrayUtil.isEmpty
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil.*
import com.intellij.util.containers.stream
import java.util.Arrays.asList
import java.util.stream.Collectors.toSet

abstract class AbstractCommonCheckinAction : AbstractVcsAction(), UpdateInBackground {
  public override fun actionPerformed(context: VcsContext) {
    LOG.debug("actionPerformed. ")
    val project = ObjectUtils.notNull(context.project)

    if (ChangeListManager.getInstance(project).isFreezedWithNotification("Can not " + getMnemonicsFreeActionName(context) + " now")) {
      LOG.debug("ChangeListManager is freezed. returning.")
    }
    else if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning) {
      LOG.debug("Background operation is running. returning.")
    }
    else {
      val roots = prepareRootsForCommit(getRoots(context), project)
      ChangeListManager.getInstance(project)
        .invokeAfterUpdate({ performCheckIn(context, project, roots) }, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE,
                           VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message"), ModalityState.current())
    }
  }

  protected open fun performCheckIn(context: VcsContext, project: Project, roots: Array<FilePath>) {
    LOG.debug("invoking commit dialog after update")
    val initialSelection = getInitiallySelectedChangeList(context, project)
    val selectedChanges = context.selectedChanges
    val selectedChangesList = if (isEmpty(selectedChanges)) emptyList() else asList(*selectedChanges!!)
    val selectedUnversioned = context.selectedUnversionedFiles
    val changesToCommit: Collection<Change>
    val included: Collection<*>
    if (!isEmpty(selectedChangesList) || !isEmpty(selectedUnversioned)) {
      changesToCommit = selectedChangesList
      included = concat(selectedChangesList, selectedUnversioned)
    }
    else {
      changesToCommit = getChangesIn(project, roots)
      included = changesToCommit
    }

    CommitChangeListDialog.commitChanges(project, changesToCommit, included, initialSelection, getExecutor(project), null)
  }

  protected open fun prepareRootsForCommit(roots: Array<FilePath>, project: Project): Array<FilePath> {
    project.saveDocumentsAndProjectSettings()

    return DescindingFilesFilter.filterDescindingFiles(roots, project)
  }

  protected open fun getMnemonicsFreeActionName(context: VcsContext): String? {
    return getActionName(context)
  }

  protected open fun getExecutor(project: Project): CommitExecutor? {
    return null
  }

  protected open fun getInitiallySelectedChangeList(context: VcsContext, project: Project): LocalChangeList? {
    val result: LocalChangeList?
    val manager = ChangeListManager.getInstance(project)
    val changeLists = context.selectedChangeLists

    if (!isEmpty(changeLists)) {
      // convert copy to real
      result = manager.findChangeList(changeLists!![0].name)
    }
    else {
      val changes = context.selectedChanges
      result = if (!isEmpty(changes)) manager.getChangeList(changes!![0]) else manager.defaultChangeList
    }

    return result
  }

  protected abstract fun getActionName(dataContext: VcsContext): String?

  protected abstract fun getRoots(dataContext: VcsContext): Array<FilePath>

  protected abstract fun approximatelyHasRoots(dataContext: VcsContext): Boolean

  override fun update(vcsContext: VcsContext, presentation: Presentation) {
    val project = vcsContext.project

    if (project == null || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) {
      presentation.isEnabledAndVisible = false
    }
    else if (!approximatelyHasRoots(vcsContext)) {
      presentation.isEnabled = false
    }
    else {
      presentation.text = getActionName(vcsContext) + "..."
      presentation.isEnabled = !ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning
      presentation.isVisible = true
    }
  }

  companion object {

    private val LOG = Logger.getInstance(AbstractCommonCheckinAction::class.java)

    private fun getChangesIn(project: Project, roots: Array<FilePath>): Set<Change> {
      val manager = ChangeListManager.getInstance(project)
      return roots.stream()
        .flatMap { path -> manager.getChangesIn(path).stream() }
        .collect(toSet())
    }
  }
}
