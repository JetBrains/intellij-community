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
import com.intellij.ui.UIBundle
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.util.findBranch
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
      text(GitLessonsBundle.message("git.balloon.press.to.proceed", strong(UIBundle.message("got.it"))))
      gotItStep(Balloon.Position.atLeft, 300, GitLessonsBundle.message("git.move.learn.window.balloon.text"))
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
    showWarningIfToolWindowClosed(ToolWindowId.COMMIT, GitLessonsBundle.message("git.window.closed.warning", action("CheckinProject")),
                                  restoreTaskWhenResolved)
  }

  fun TaskContext.showWarningIfGitWindowClosed(restoreTaskWhenResolved: Boolean = true) {
    showWarningIfToolWindowClosed(ToolWindowId.VCS,
                                  GitLessonsBundle.message("git.commit.window.closed.warning", action("ActivateVersionControlToolWindow")),
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