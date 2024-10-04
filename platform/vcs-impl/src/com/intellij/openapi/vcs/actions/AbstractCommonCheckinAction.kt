// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.actions.commit.CheckinActionUtil
import com.intellij.openapi.vcs.changes.*
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.commit.cleanActionText
import org.jetbrains.annotations.ApiStatus
import java.util.*

private val LOG = logger<AbstractCommonCheckinAction>()

@ApiStatus.Internal
@ApiStatus.ScheduledForRemoval
@Deprecated("Use [com.intellij.openapi.vcs.actions.commit.CheckinActionUtil] instead")
abstract class AbstractCommonCheckinAction : AbstractVcsAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

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
      val operationName = cleanActionText(actionName)
      VcsBundle.message("error.cant.perform.operation.now", operationName)
    }
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(isFreezedDialogTitle)) {
      LOG.debug("ChangeListManager is freezed. returning.")
      return
    }

    if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning) {
      LOG.debug("Background operation is running. returning.")
      return
    }
    val selectedChanges = context.selectedChanges?.asList().orEmpty()
    val selectedUnversioned = context.selectedUnversionedFilePaths
    val initialChangeList = getInitiallySelectedChangeList(context, project)
    val pathsToCommit = DescindingFilesFilter.filterDescindingFiles(getRoots(context), project).asList()
    val executor = getExecutor(project)
    val forceUpdateCommitStateFromContext = isForceUpdateCommitStateFromContext()

    CheckinActionUtil.performCheckInAfterUpdate(project, selectedChanges, selectedUnversioned, initialChangeList, pathsToCommit,
                                                executor, forceUpdateCommitStateFromContext)
  }

  @Deprecated("getActionName() will be used instead")
  @ApiStatus.ScheduledForRemoval
  protected open fun getMnemonicsFreeActionName(context: VcsContext): String? = null

  protected abstract fun getRoots(dataContext: VcsContext): Array<FilePath>

  protected open fun isForceUpdateCommitStateFromContext(): Boolean = false

  protected open fun getInitiallySelectedChangeList(context: VcsContext, project: Project): LocalChangeList {
    val manager = ChangeListManager.getInstance(project)

    return context.selectedChangeLists?.firstOrNull()?.let { manager.findChangeList(it.name) }
           ?: context.selectedChanges?.firstOrNull()?.let { manager.getChangeList(it) }
           ?: manager.defaultChangeList
  }

  protected open fun getExecutor(project: Project): CommitExecutor? = null
}
