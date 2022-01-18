// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift

import com.intellij.dvcs.push.VcsPushAction
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.actions.CommonCheckinProjectAction
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.util.findBranch
import git4idea.config.GitVcsApplicationSettings
import git4idea.i18n.GitBundle
import git4idea.index.enableStagingArea
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.dsl.*
import training.ui.LearningUiManager
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KClass

object GitLessonsUtil {
  // Git tool window must showing to reset it
  fun LessonContext.resetGitLogWindow() {
    prepareRuntimeTask {
      val vcsLogUi = VcsProjectLog.getInstance(project).mainLogUi
      vcsLogUi?.filterUi?.clearFilters()
      PropertiesComponent.getInstance(project).setValue("Vcs.Log.Text.Filter.History", null)

      VcsLogContentUtil.selectMainLog(project)
    }

    // clear Git tool window to return it to default state (needed in case of restarting the lesson)
    task {
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { ui: SearchTextField ->
        if (UIUtil.getParentOfType(MainFrame::class.java, ui) != null) {
          ui.reset()
          true
        }
        else false
      }
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { ui: VcsLogGraphTable ->
        ui.jumpToRow(0, true)
        ui.selectionModel.clearSelection()
        true
      }
    }
  }

  fun TaskContext.highlightSubsequentCommitsInGitLog(startCommitRow: Int,
                                                     sequenceLength: Int = 1,
                                                     highlightInside: Boolean = true,
                                                     usePulsation: Boolean = false) {
    triggerByPartOfComponent(highlightInside = highlightInside, usePulsation = usePulsation) { ui: VcsLogGraphTable ->
      ui.getRectForSubsequentCommits(startCommitRow, sequenceLength)
    }
  }

  fun TaskContext.highlightSubsequentCommitsInGitLog(sequenceLength: Int = 1,
                                                     highlightInside: Boolean = true,
                                                     usePulsation: Boolean = false,
                                                     startCommitPredicate: (VcsCommitMetadata) -> Boolean) {
    triggerByPartOfComponent(highlightInside = highlightInside, usePulsation = usePulsation) { ui: VcsLogGraphTable ->
      val rowIndexes = (0 until ui.rowCount).toList().toIntArray()
      val startCommitRow = ui.model.getCommitMetadata(rowIndexes).indexOfFirst(startCommitPredicate)
      if (startCommitRow >= 0) {
        ui.getRectForSubsequentCommits(startCommitRow, sequenceLength)
      }
      else null
    }
  }

  fun TaskContext.highlightLatestCommitsFromBranch(branchName: String,
                                                   sequenceLength: Int = 1,
                                                   highlightInside: Boolean = true,
                                                   usePulsation: Boolean = false) {
    highlightSubsequentCommitsInGitLog(sequenceLength, highlightInside, usePulsation) l@{ commit ->
      val vcsData = VcsProjectLog.getInstance(project).dataManager ?: return@l false
      val root = vcsData.roots.single()
      commit.id == vcsData.dataPack.findBranch(branchName, root)?.commitHash
    }
  }

  private fun VcsLogGraphTable.getRectForSubsequentCommits(startCommitRow: Int, sequenceLength: Int): Rectangle? {
    val cells = (1..4).map { getCellRect(startCommitRow, it, false) }
    val y = cells[0].y
    val height = cells[0].height * sequenceLength
    val width = cells.fold(0) { acc, rect -> acc + rect.width }
    if (y + height <= visibleRect.y) return null
    val adjustedY = max(y, visibleRect.y)
    val adjustedHeight = min(height, y + height - visibleRect.y)
    return Rectangle(cells[0].x, adjustedY, width, adjustedHeight)
  }

  fun TaskContext.triggerOnNotification(checkState: (Notification) -> Boolean) {
    addFutureStep {
      subscribeForMessageBus(Notifications.TOPIC, object : Notifications {
        override fun notify(notification: Notification) {
          if (checkState(notification)) {
            completeStep()
          }
        }
      })
    }
  }

  fun TaskContext.triggerOnCheckout(checkBranch: (String) -> Boolean = { true }) {
    addFutureStep {
      subscribeForMessageBus(BranchChangeListener.VCS_BRANCH_CHANGED, object : BranchChangeListener {
        override fun branchWillChange(branchName: String) {}

        override fun branchHasChanged(branchName: String) {
          if (checkBranch(branchName)) completeStep()
        }
      })
    }
  }

  fun TaskContext.gotItStep(position: Balloon.Position,
                            width: Int,
                            @Nls text: String,
                            cornerToPointerDistance: Int = -1,
                            duplicateMessage: Boolean = true) {
    val gotIt = CompletableFuture<Boolean>()
    text(text, LearningBalloonConfig(position, width, duplicateMessage, cornerToPointerDistance = cornerToPointerDistance) {
      gotIt.complete(true)
    })
    addStep(gotIt)
    test(waitEditorToBeReady = false) {
      ideFrame { button(IdeBundle.message("got.it.button.name")).click() }
    }
  }

  /**
   * Restores task if [PreviousTaskInfo.ui] is not showing and background task is not running
   */
  fun TaskContext.restoreByUiAndBackgroundTask(taskTitleRegex: @Nls String, delayMillis: Int = 0, restoreId: TaskContext.TaskId? = null) {
    val regex = Regex(taskTitleRegex)
    restoreState(restoreId, delayMillis) {
      previous.ui?.isShowing != true && !isBackgroundableTaskRunning(regex)
    }
  }

  private fun isBackgroundableTaskRunning(titleRegex: Regex): Boolean {
    val indicators = CoreProgressManager.getCurrentIndicators()
    return indicators.find { it is BackgroundableProcessIndicator && it.isRunning && titleRegex.find(it.title) != null } != null
  }

  fun TaskContext.showWarningIfCommitWindowClosed(restoreTaskWhenResolved: Boolean = false) {
    showWarningIfToolWindowClosed(ToolWindowId.COMMIT,
                                  GitLessonsBundle.message("git.window.closed.warning",
                                                           action("CheckinProject"),
                                                           strong(VcsBundle.message("commit.dialog.configurable"))),
                                  restoreTaskWhenResolved)
  }

  fun TaskContext.showWarningIfGitWindowClosed(restoreTaskWhenResolved: Boolean = false) {
    showWarningIfToolWindowClosed(ToolWindowId.VCS,
                                  GitLessonsBundle.message("git.window.closed.warning",
                                                           action("ActivateVersionControlToolWindow"), strong("Git")),
                                  restoreTaskWhenResolved)
  }

  private fun TaskContext.showWarningIfToolWindowClosed(toolWindowId: String,
                                                        @Language("HTML") @Nls warningMessage: String,
                                                        restoreTaskWhenResolved: Boolean) {
    showWarning(warningMessage, restoreTaskWhenResolved) {
      ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.isVisible != true
    }
  }

  fun LessonContext.showWarningIfModalCommitEnabled() {
    task {
      val step = stateCheck {
        VcsApplicationSettings.getInstance().COMMIT_FROM_LOCAL_CHANGES
      }
      val callbackId = LearningUiManager.addCallback {
        CommitModeManager.setCommitFromLocalChanges(project, true)
        step.complete(true)
      }
      showWarning(GitLessonsBundle.message("git.use.non.modal.commit.ui.warning",
                                           action("ShowSettings"),
                                           strong(VcsBundle.message("version.control.main.configurable.name")),
                                           strong(VcsBundle.message("commit.dialog.configurable")),
                                           strong(VcsBundle.message("settings.commit.without.dialog")))
                  + " " + GitLessonsBundle.message("git.click.to.change.settings", callbackId)) {
        !VcsApplicationSettings.getInstance().COMMIT_FROM_LOCAL_CHANGES
      }
      test {
        if (!VcsApplicationSettings.getInstance().COMMIT_FROM_LOCAL_CHANGES) {
          Thread.sleep(1000)  // need to wait until LessonMessagePane become updated after restart and warning will be showed
          clickLessonMessagePaneLink(" click ")
        }
      }
    }
  }

  fun LessonContext.showWarningIfStagingAreaEnabled() {
    task {
      val step = stateCheck {
        !GitVcsApplicationSettings.getInstance().isStagingAreaEnabled
      }
      val callbackId = LearningUiManager.addCallback {
        enableStagingArea(false)
        step.complete(true)
      }
      showWarning(GitLessonsBundle.message("git.not.use.staging.area.warning",
                                           action("ShowSettings"),
                                           strong(VcsBundle.message("version.control.main.configurable.name")),
                                           strong(GitBundle.message("settings.git.option.group")),
                                           strong(GitBundle.message("settings.enable.staging.area")))
                  + " " + GitLessonsBundle.message("git.click.to.change.settings", callbackId)) {
        GitVcsApplicationSettings.getInstance().isStagingAreaEnabled
      }
    }
  }

  fun LessonContext.restoreCommitWindowStateInformer() {
    val enabledModalInterface = !VcsApplicationSettings.getInstance().COMMIT_FROM_LOCAL_CHANGES
    val enabledStagingArea = GitVcsApplicationSettings.getInstance().isStagingAreaEnabled
    if (!enabledModalInterface && !enabledStagingArea) return
    restoreChangedSettingsInformer {
      if (enabledModalInterface) CommitModeManager.setCommitFromLocalChanges(null, false)
      if (enabledStagingArea) enableStagingArea(true)
    }
  }

  fun TaskContext.openPushDialogText(@Nls introduction: String) {
    openGitDialogText(introduction, DvcsBundle.message("action.push").dropMnemonic(),
                      "Vcs.Push", AllIcons.Vcs.Push, VcsPushAction::class)
  }

  fun TaskContext.openUpdateDialogText(@Nls introduction: String) {
    openGitDialogText(introduction, VcsBundle.message("action.display.name.update.scope", VcsBundle.message("update.project.scope.name")),
                      "Vcs.UpdateProject", AllIcons.Actions.CheckOut, CommonUpdateProjectAction::class)
  }

  fun TaskContext.openCommitWindowText(@Nls introduction: String) {
    val commitText = VcsBundle.message("commit.dialog.configurable")
    openGitWindowText(introduction,
                      GitLessonsBundle.message("git.open.tool.window", 0, action("CheckinProject"),
                                               icon(AllIcons.Actions.Commit), strong(commitText)),
                      GitLessonsBundle.message("git.open.tool.window", 1, action("CheckinProject"),
                                               "", strong(commitText)),
                      CommonCheckinProjectAction::class)
  }

  private fun <T : AnAction> TaskContext.openGitDialogText(@Nls introduction: String,
                                                           @Nls dialogName: String,
                                                           actionId: String,
                                                           icon: Icon,
                                                           actionClass: KClass<T>) {
    openGitWindowText(introduction,
                      GitLessonsBundle.message("git.open.dialog", 0, action(actionId), icon(icon), strong(dialogName)),
                      GitLessonsBundle.message("git.open.dialog", 1, action(actionId), "", strong(dialogName)),
                      actionClass)
  }

  private fun <T : AnAction> TaskContext.openGitWindowText(@Nls introduction: String,
                                                           @Nls suggestionWithIcon: String,
                                                           @Nls suggestionWithoutIcon: String,
                                                           actionClass: KClass<T>) {
    if (UISettings.instance.run { showNavigationBar || showMainToolbar }) {
      text("$introduction $suggestionWithIcon")
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: ActionButton ->
        actionClass.isInstance(ui.action)
      }
    }
    else text("$introduction $suggestionWithoutIcon")
  }

  fun loadIllustration(illustrationName: String): Icon {
    return IconLoader.getIcon("illustrations/$illustrationName", GitLessonsUtil::class.java)
  }
}