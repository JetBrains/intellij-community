// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.util.containers.ContainerUtil.concat
import com.intellij.util.ui.UIUtil.removeMnemonic
import com.intellij.vcs.commit.CommitMode
import com.intellij.vcs.commit.CommitWorkflowHandler
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.commit.removeEllipsisSuffix
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<AbstractCommonCheckinAction>()

private fun getChangesIn(project: Project, roots: Array<FilePath>): Set<Change> {
  val manager = ChangeListManager.getInstance(project)
  return roots.flatMap { manager.getChangesIn(it) }.toSet()
}

internal fun AnActionEvent.getContextCommitWorkflowHandler(): CommitWorkflowHandler? = getData(COMMIT_WORKFLOW_HANDLER)

abstract class AbstractCommonCheckinAction : AbstractVcsAction(), UpdateInBackground {
  override fun update(vcsContext: VcsContext, presentation: Presentation) {
    val project = vcsContext.project

    if (project == null ||
        !ProjectLevelVcsManager.getInstance(project).hasActiveVcss() ||
        CommitModeManager.getInstance(project).getCurrentCommitMode().disableDefaultCommitAction()) {
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

  protected open fun getActionName(dataContext: VcsContext): @NlsActions.ActionText String? = null

  public override fun actionPerformed(context: VcsContext) {
    LOG.debug("actionPerformed. ")

    val project = context.project!!
    val actionName = getActionName(context) ?: templatePresentation.text
    val isFreezedDialogTitle = actionName?.let {
      VcsBundle.message("error.cant.perform.operation.now", removeMnemonic(actionName).removeEllipsisSuffix().toLowerCase())
    }

    if (ChangeListManager.getInstance(project).isFreezedWithNotification(isFreezedDialogTitle)) {
      LOG.debug("ChangeListManager is freezed. returning.")
    }
    else if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning) {
      LOG.debug("Background operation is running. returning.")
    }
    else {
      val roots = prepareRootsForCommit(getRoots(context), project)
      queueCheckin(project, context, roots)
    }
  }

  protected open fun queueCheckin(
    project: Project,
    context: VcsContext,
    roots: Array<FilePath>
  ) {
    ChangeListManager.getInstance(project).invokeAfterUpdateWithModal(
      true, VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message")) {
      performCheckIn(context, project, roots)
    }
  }

  @Deprecated("getActionName() will be used instead")
  @ApiStatus.ScheduledForRemoval
  protected open fun getMnemonicsFreeActionName(context: VcsContext): String? = null

  protected abstract fun getRoots(dataContext: VcsContext): Array<FilePath>

  protected open fun prepareRootsForCommit(roots: Array<FilePath>, project: Project): Array<FilePath> {
    StoreUtil.saveDocumentsAndProjectSettings(project)

    return DescindingFilesFilter.filterDescindingFiles(roots, project)
  }

  protected open fun isForceUpdateCommitStateFromContext(): Boolean = false

  protected open fun performCheckIn(context: VcsContext, project: Project, roots: Array<FilePath>) {
    LOG.debug("invoking commit dialog after update")

    val selectedChanges = context.selectedChanges
    val selectedUnversioned = context.selectedUnversionedFilePaths
    val initialChangeList = getInitiallySelectedChangeList(context, project)
    val changesToCommit: Collection<Change>
    val included: Collection<Any>

    if (selectedChanges.isNullOrEmpty() && selectedUnversioned.isEmpty()) {
      changesToCommit = getChangesIn(project, roots)
      included = initialChangeList.changes.intersect(changesToCommit)
    }
    else {
      changesToCommit = selectedChanges.orEmpty().toList()
      included = concat(changesToCommit, selectedUnversioned)
    }

    val executor = getExecutor(project)
    val workflowHandler = ChangesViewManager.getInstanceEx(project).commitWorkflowHandler
    if (executor == null && workflowHandler != null) {
      workflowHandler.run {
        setCommitState(initialChangeList, included, isForceUpdateCommitStateFromContext())
        activate()
      }
    }
    else {
      CommitChangeListDialog.commitChanges(project, changesToCommit, included, initialChangeList, executor, null)
    }
  }

  protected open fun getInitiallySelectedChangeList(context: VcsContext, project: Project): LocalChangeList {
    val manager = ChangeListManager.getInstance(project)

    return context.selectedChangeLists?.firstOrNull()?.let { manager.findChangeList(it.name) }
           ?: context.selectedChanges?.firstOrNull()?.let { manager.getChangeList(it) }
           ?: manager.defaultChangeList
  }

  protected open fun getExecutor(project: Project): CommitExecutor? = null
}
