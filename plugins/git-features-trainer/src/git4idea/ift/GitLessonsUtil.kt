// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import git4idea.commands.Git
import git4idea.index.actions.runProcess
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.dsl.LearningBalloonConfig
import training.dsl.LessonContext
import training.dsl.TaskContext
import training.dsl.subscribeForMessageBus
import training.ui.LearnToolWindow
import training.ui.LearningUiManager
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture

object GitLessonsUtil {
  fun LessonContext.checkoutBranch(branchName: String) {
    task {
      addFutureStep {
        val changeListManager = ChangeListManager.getInstance(project)
        changeListManager.scheduleUpdate()
        changeListManager.invokeAfterUpdate(true) {
          val repository = GitRepositoryManager.getInstance(project).repositories.first()
          if (repository.currentBranch?.name == branchName) {
            completeStep()
          }
          else {
            runProcess(project, "", false) {
              Git.getInstance().checkout(repository, branchName, null, true, false).throwOnError()
            }
            completeStep()
          }
        }
      }
    }

    prepareRuntimeTask {
      val vcsLogData = VcsProjectLog.getInstance(project).mainLogUi?.logData ?: return@prepareRuntimeTask
      val roots = GitRepositoryManager.getInstance(project).repositories.map(GitRepository::getRoot)
      vcsLogData.refresh(roots)
    }
  }

  // Git tool window must showing to reset it
  fun LessonContext.resetGitLogWindow() {
    prepareRuntimeTask {
      val vcsLogUi = VcsProjectLog.getInstance(project).mainLogUi
      // todo: find out how to open branches if it is hidden (git4idea.ui.branch.dashboard.SHOW_GIT_BRANCHES_LOG_PROPERTY is internal and can't be accessed)
      vcsLogUi?.filterUi?.clearFilters()

      PropertiesComponent.getInstance(project).setValue("Vcs.Log.Text.Filter.History", null)
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

  fun LessonContext.moveLearnToolWindowRight() {
    prepareRuntimeTask {
      val learnToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Learn") ?: error("Not found Learn toolwindow")
      learnToolWindow.setAnchor(ToolWindowAnchor.RIGHT, null)
      learnToolWindow.show()
    }

    task {
      triggerByUiComponentAndHighlight(false, false) { _: LearnToolWindow -> true }
    }

    task {
      text("Press ${strong("Got it!")} to proceed.")
      gotItStep(Balloon.Position.atLeft, 300, "We moved the Learn panel to the right because it is covered by the Commit tool window.")
    }
  }

  fun TaskContext.findVcsLogData(): CompletableFuture<VcsLogData> {
    val future = CompletableFuture<VcsLogData>()
    val data = VcsProjectLog.getInstance(project).dataManager
    if (data != null) {
      future.complete(data)
    }
    else {
      before {
        subscribeForMessageBus(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, object : VcsProjectLog.ProjectLogListener {
          override fun logCreated(manager: VcsLogManager) {
            future.complete(data)
          }

          override fun logDisposed(manager: VcsLogManager) {}
        })
      }
    }
    return future
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

  private fun VcsLogGraphTable.getRectForSubsequentCommits(startCommitRow: Int, sequenceLength: Int): Rectangle {
    val cells = (1..4).map { getCellRect(startCommitRow, it, false) }
    val width = cells.fold(0) { acc, rect -> acc + rect.width }
    return Rectangle(cells[0].x, cells[0].y, width, cells[0].height * sequenceLength)
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

  fun TaskContext.gotItStep(position: Balloon.Position, width: Int, @Nls text: String) {
    val gotIt = CompletableFuture<Boolean>()
    text(text, LearningBalloonConfig(position, width, false) { gotIt.complete(true) })
    addStep(gotIt)
  }

  fun TaskContext.showWarningIfCommitWindowClosed(restoreTaskWhenResolved: Boolean = true) {
    showWarningIfToolWindowClosed(ToolWindowId.COMMIT, "Press ${action("CheckinProject")} to open the commit tool window again.",
                                  restoreTaskWhenResolved)
  }

  fun TaskContext.showWarningIfGitWindowClosed(restoreTaskWhenResolved: Boolean = true) {
    showWarningIfToolWindowClosed(ToolWindowId.VCS,
                                  "Press ${action("ActivateVersionControlToolWindow")} to open the Git tool window again.",
                                  restoreTaskWhenResolved)
  }

  private fun TaskContext.showWarningIfToolWindowClosed(toolWindowId: String,
                                                        @Language("HTML") @Nls warningMessage: String,
                                                        restoreTaskWhenResolved: Boolean) {
    showWarning(warningMessage, restoreTaskWhenResolved) {
      ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.isVisible != true
    }
  }
}