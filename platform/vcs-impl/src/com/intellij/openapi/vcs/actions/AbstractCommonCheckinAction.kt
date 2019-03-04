// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.util.containers.ContainerUtil.concat
import com.intellij.util.ui.UIUtil.removeMnemonic

private val LOG = logger<AbstractCommonCheckinAction>()

private fun getChangesIn(project: Project, roots: Array<FilePath>): Set<Change> {
  val manager = ChangeListManager.getInstance(project)
  return roots.flatMap { manager.getChangesIn(it) }.toSet()
}

abstract class AbstractCommonCheckinAction : AbstractVcsAction(), UpdateInBackground {
  override fun update(vcsContext: VcsContext, presentation: Presentation) {
    val project = vcsContext.project

    if (project == null || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) {
      presentation.isEnabledAndVisible = false
    }
    else if (!approximatelyHasRoots(vcsContext)) {
      presentation.isEnabled = false
    }
    else {
      getActionName(vcsContext)?.let { presentation.text = "$it..." }
      presentation.isEnabled = !ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning
      presentation.isVisible = true
    }
  }

  protected abstract fun approximatelyHasRoots(dataContext: VcsContext): Boolean

  protected open fun getActionName(dataContext: VcsContext): String? = null

  public override fun actionPerformed(context: VcsContext) {
    LOG.debug("actionPerformed. ")

    val project = context.project!!
    val actionName = getActionName(context)?.let { removeMnemonic(it) } ?: templatePresentation.text
    val isFreezedDialogTitle = actionName?.let { "Can not $it now" }

    if (ChangeListManager.getInstance(project).isFreezedWithNotification(isFreezedDialogTitle)) {
      LOG.debug("ChangeListManager is freezed. returning.")
    }
    else if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning) {
      LOG.debug("Background operation is running. returning.")
    }
    else {
      val roots = prepareRootsForCommit(getRoots(context), project)
      ChangeListManager.getInstance(project).invokeAfterUpdate(
        { performCheckIn(context, project, roots) }, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE,
        message("waiting.changelists.update.for.show.commit.dialog.message"), ModalityState.current())
    }
  }

  @Deprecated("getActionName() will be used instead")
  protected open fun getMnemonicsFreeActionName(context: VcsContext): String? = null

  protected abstract fun getRoots(dataContext: VcsContext): Array<FilePath>

  protected open fun prepareRootsForCommit(roots: Array<FilePath>, project: Project): Array<FilePath> {
    StoreUtil.saveDocumentsAndProjectSettings(project)

    return DescindingFilesFilter.filterDescindingFiles(roots, project)
  }

  protected open fun performCheckIn(context: VcsContext, project: Project, roots: Array<FilePath>) {
    LOG.debug("invoking commit dialog after update")

    val selectedChanges = context.selectedChanges
    val selectedUnversioned = context.selectedUnversionedFiles
    val changesToCommit: Collection<Change>
    val included: Collection<*>

    if (selectedChanges.isNullOrEmpty() && selectedUnversioned.isEmpty()) {
      changesToCommit = getChangesIn(project, roots)
      included = changesToCommit
    }
    else {
      changesToCommit = selectedChanges.orEmpty().toList()
      included = concat(changesToCommit, selectedUnversioned)
    }

    val initialChangeList = getInitiallySelectedChangeList(context, project)
    CommitChangeListDialog.commitChanges(project, changesToCommit, included, initialChangeList, getExecutor(project), null)
  }

  protected open fun getInitiallySelectedChangeList(context: VcsContext, project: Project): LocalChangeList? {
    val manager = ChangeListManager.getInstance(project)

    context.selectedChangeLists?.firstOrNull()?.let { return manager.findChangeList(it.name) }
    context.selectedChanges?.firstOrNull()?.let { return manager.getChangeList(it) }
    return manager.defaultChangeList
  }

  protected open fun getExecutor(project: Project): CommitExecutor? = null
}
