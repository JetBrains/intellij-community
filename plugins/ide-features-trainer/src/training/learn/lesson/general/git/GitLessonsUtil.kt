// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.git

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import git4idea.commands.Git
import git4idea.index.actions.runProcess
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.Nls
import training.dsl.LearningBalloonConfig
import training.dsl.LessonContext
import training.dsl.TaskContext
import training.dsl.subscribeForMessageBus
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

  fun TaskContext.highlightCommitInGitLog(row: Int, highlightInside: Boolean = true, usePulsation: Boolean = false) {
    highlightSubsequentCommitsInGitLog(row, row + 1, highlightInside, usePulsation)
  }

  fun TaskContext.highlightSubsequentCommitsInGitLog(startRowIncl: Int,
                                                     endRowExcl: Int,
                                                     highlightInside: Boolean = false,
                                                     usePulsation: Boolean = false) {
    triggerByPartOfComponent(highlightInside = highlightInside, usePulsation = usePulsation) { ui: VcsLogGraphTable ->
      val cells = (1..4).map { ui.getCellRect(startRowIncl, it, false) }
      val width = cells.fold(0) { acc, rect -> acc + rect.width }
      Rectangle(cells[0].x, cells[0].y, width, cells[0].height * (endRowExcl - startRowIncl))
    }
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

  // copy pasted logic from PythonOnboardingTour.kt
  fun TaskContext.proceedLink() {
    val gotIt = CompletableFuture<Boolean>()
    runtimeText {
      removeAfterDone = true
      "<callback id=\"${LearningUiManager.addCallback { gotIt.complete(true) }}\">Click to proceed</callback>"
    }
    addStep(gotIt)
  }
}