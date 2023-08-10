// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
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
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JCheckBox
import javax.swing.tree.TreePath
import kotlin.math.max
import kotlin.math.min

object GitLessonsUtil {
  // Git tool window must show to reset it
  fun LessonContext.resetGitLogWindow() {
    prepareRuntimeTask {
      val vcsLogUi = VcsProjectLog.getInstance(project).mainLogUi
      vcsLogUi?.filterUi?.clearFilters()
      PropertiesComponent.getInstance(project).setValue("Vcs.Log.Text.Filter.History", null)

      VcsLogContentUtil.selectMainLog(project)
    }

    // clear Git tool window to return it to the default state (needed in case of restarting the lesson)
    task {
      triggerUI().component { ui: SearchTextField ->
        if (UIUtil.getParentOfType(MainFrame::class.java, ui) != null) {
          ui.reset()
          true
        }
        else false
      }
      triggerUI().component { ui: VcsLogGraphTable ->
        ui.jumpToRow(0, true)
        ui.selectionModel.clearSelection()
        true
      }
    }
  }

  fun LessonContext.refreshGitLogOnOpen() {
    prepareRuntimeTask {
      val connection = project.messageBus.connect(lessonDisposable)
      connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
        override fun toolWindowShown(toolWindow: ToolWindow) {
          if (toolWindow.id == ToolWindowId.VCS) {
            VcsProjectLog.getInstance(project).mainLogUi?.refresher?.setValid(true, false)
            connection.disconnect()
          }
        }
      })
    }
  }

  fun TaskContext.highlightSubsequentCommitsInGitLog(startCommitRow: Int,
                                                     sequenceLength: Int = 1,
                                                     highlightInside: Boolean = false,
                                                     usePulsation: Boolean = false) {
    triggerAndBorderHighlight {
      this.highlightInside = highlightInside
      this.usePulsation = usePulsation
    }.componentPart { ui: VcsLogGraphTable ->
      ui.getRectForSubsequentCommits(startCommitRow, sequenceLength)
    }
  }

  fun TaskContext.highlightSubsequentCommitsInGitLog(sequenceLength: Int = 1,
                                                     highlightInside: Boolean = false,
                                                     usePulsation: Boolean = false,
                                                     startCommitPredicate: (VcsCommitMetadata) -> Boolean) {
    triggerAndBorderHighlight {
      this.highlightInside = highlightInside
      this.usePulsation = usePulsation
    }.componentPart { ui: VcsLogGraphTable ->
      val rowIndexes = (0 until ui.rowCount).toList().toIntArray()
      val startCommitRow = ui.model.createSelection(rowIndexes).cachedMetadata.indexOfFirst(startCommitPredicate)
      if (startCommitRow >= 0) {
        ui.getRectForSubsequentCommits(startCommitRow, sequenceLength)
      }
      else null
    }
  }

  fun TaskContext.highlightLatestCommitsFromBranch(branchName: String,
                                                   sequenceLength: Int = 1,
                                                   highlightInside: Boolean = false,
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

  fun TaskContext.triggerOnChangeCheckboxShown(changeFileName: String) {
    triggerUI().componentPart l@{ ui: ChangesListView ->
      // If commit tool window is opened using shortcut, all the files in the tree will be included
      // so, we need to clear them
      ui.inclusionModel.clearInclusion()

      val path = TreeUtil.treePathTraverser(ui).find { it.getPathComponent(it.pathCount - 1).toString().contains(changeFileName) }
                 ?: return@l null
      val rect = ui.getPathBounds(path) ?: return@l null
      Rectangle(rect.x, rect.y, 20, rect.height)
    }
  }

  fun TaskContext.triggerOnOneChangeIncluded(changeFileName: String) {
    triggerUI().component l@{ ui: ChangesListView ->
      val includedChanges = ui.includedSet
      if (includedChanges.size != 1) return@l false
      val change = includedChanges.first() as? ChangeListChange ?: return@l false
      change.virtualFile?.name == changeFileName
    }
  }

  /**
   * Restores a task if [PreviousTaskInfo.ui] is not showing and a background task is not running
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

  fun TaskContext.openCommitWindow(@Nls introduction: String) {
    val commitWindowName = VcsBundle.message("commit.dialog.configurable")
    val openCommitWindowText = GitLessonsBundle.message("git.open.tool.window",
                                                        action("CheckinProject"),
                                                        icon(AllIcons.Actions.Commit),
                                                        strong(commitWindowName))
    text("$introduction $openCommitWindowText")
    text(GitLessonsBundle.message("git.open.tool.window.balloon", strong(commitWindowName)),
         LearningBalloonConfig(Balloon.Position.atRight, width = 0))
    stateCheck {
      ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT)?.isVisible == true
    }
  }

  fun TaskContext.openGitWindow(@Nls stepText: String) {
    text(stepText)
    text(GitLessonsBundle.message("git.open.tool.window.balloon", strong(GitBundle.message("git4idea.vcs.name"))),
         LearningBalloonConfig(Balloon.Position.atRight, width = 0))
    stateCheck {
      ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS)?.isVisible == true
    }
    test { actions("ActivateVersionControlToolWindow") }
  }

  fun TaskTestContext.clickChangeElement(partOfText: String) {
    val checkPath: (TreePath) -> Boolean = { p -> p.getPathComponent(p.pathCount - 1).toString().contains(partOfText) }
    ideFrame {
      val fixture = jTree(checkPath = checkPath)
      val tree = fixture.target()
      val pathRect = invokeAndWaitIfNeeded {
        val path = TreeUtil.treePathTraverser(tree).find(checkPath)
        tree.getPathBounds(path)
      } ?: error("Failed to find path with text '$partOfText'")
      val offset = JCheckBox().preferredSize.width / 2
      robot.click(tree, Point(pathRect.x + offset, pathRect.y + offset))
    }
  }

  fun TaskTestContext.clickTreeRow(doubleClick: Boolean = false, rightClick: Boolean = false, rowItemPredicate: (Any) -> Boolean) {
    ideFrame {
      val tree = jTree { path -> rowItemPredicate(path.lastPathComponent) }
      val rowToClick = invokeAndWaitIfNeeded {
        val path = TreeUtil.treePathTraverser(tree.target()).find { path -> rowItemPredicate(path.lastPathComponent) }
        tree.target().getRowForPath(path)
      }
      when {
        doubleClick -> tree.doubleClickRow(rowToClick)
        rightClick -> tree.rightClickRow(rowToClick)
        else -> tree.clickRow(rowToClick)
      }
    }
  }
}