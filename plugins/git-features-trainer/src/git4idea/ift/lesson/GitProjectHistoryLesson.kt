// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.diff.tools.util.SimpleDiffPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.filter.BranchFilterPopupComponent
import com.intellij.vcs.log.ui.filter.UserFilterPopupComponent
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.ui.frame.VcsLogCommitDetailsListPanel
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import git4idea.ift.GitLessonsBundle
import git4idea.ift.GitLessonsUtil.highlightLatestCommitsFromBranch
import git4idea.ift.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import git4idea.ift.GitLessonsUtil.resetGitLogWindow
import git4idea.ift.GitLessonsUtil.showWarningIfGitWindowClosed
import git4idea.ui.branch.dashboard.CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY
import git4idea.ui.branch.dashboard.SHOW_GIT_BRANCHES_LOG_PROPERTY
import training.dsl.*
import training.ui.LearningUiHighlightingManager

class GitProjectHistoryLesson : GitLesson("Git.ProjectHistory", GitLessonsBundle.message("git.project.history.lesson.name")) {
  override val existedFile = "git/sphinx_cat.yml"
  override val branchName = "feature"
  private val textToFind = "sphinx"

  private var showGitBranchesBackup: Boolean? = null

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    task("ActivateVersionControlToolWindow") {
      text(GitLessonsBundle.message("git.project.history.open.git.window", action(it)))
      stateCheck {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible == true
      }
    }

    resetGitLogWindow()

    prepareRuntimeTask l@{
      val property = SHOW_GIT_BRANCHES_LOG_PROPERTY
      val logUiProperties = VcsProjectLog.getInstance(project).mainLogUi?.properties ?: return@l
      showGitBranchesBackup = logUiProperties[property]
      logUiProperties[property] = true
    }

    task {
      highlightLatestCommitsFromBranch(branchName)
    }

    task {
      text(GitLessonsBundle.message("git.project.history.commits.tree.explanation"))
      proceedLink()
    }

    task {
      var selectionCleared = false
      triggerByFoundPathAndHighlight(highlightInside = true) { tree, path ->
        (path.pathCount > 1 && path.getPathComponent(1).toString() == "HEAD_NODE").also {
          if (!selectionCleared) {
            tree.clearSelection()
            selectionCleared = true
          }
        }
      }
    }

    task {
      val logUiProperties = VcsProjectLog.getInstance(project).mainLogUi?.properties
      val choice = if (logUiProperties == null || !logUiProperties[CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY]) 1 else 0
      text(GitLessonsBundle.message("git.project.history.apply.branch.filter", choice))
      text(GitLessonsBundle.message("git.project.history.click.head.tooltip", choice),
           LearningBalloonConfig(Balloon.Position.above, 250))
      triggerByUiComponentAndHighlight(false, false) { ui: BranchFilterPopupComponent ->
        ui.currentText?.contains("HEAD") == true
      }
      showWarningIfGitWindowClosed()
    }

    task {
      triggerByUiComponentAndHighlight { _: UserFilterPopupComponent -> true }
    }

    val meFilterText = VcsLogBundle.message("vcs.log.user.filter.me")
    task {
      text(GitLessonsBundle.message("git.project.history.apply.user.filter"))
      text(GitLessonsBundle.message("git.project.history.click.filter.tooltip"),
           LearningBalloonConfig(Balloon.Position.above, 0))
      triggerByListItemAndHighlight { item ->
        item.toString().contains(meFilterText)
      }
      showWarningIfGitWindowClosed()
    }

    task {
      text(GitLessonsBundle.message("git.project.history.select.me", strong(meFilterText)))
      triggerByUiComponentAndHighlight(false, false) { ui: UserFilterPopupComponent ->
        ui.currentText?.contains(meFilterText) == true
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    task {
      text(GitLessonsBundle.message("git.project.history.apply.message.filter", code(textToFind), LessonUtil.rawEnter()))
      triggerByUiComponentAndHighlight { ui: SearchTextField ->
        (UIUtil.getParentOfType(MainFrame::class.java, ui) != null).also {
          if (it) IdeFocusManager.getInstance(project).requestFocus(ui, true)
        }
      }
      triggerByUiComponentAndHighlight(false, false) l@{ ui: VcsLogGraphTable ->
        val model = ui.model as? GraphTableModel ?: return@l false
        model.rowCount > 0 && model.getCommitMetadata(0).fullMessage.contains(textToFind)
      }
      showWarningIfGitWindowClosed()
    }

    task {
      text(GitLessonsBundle.message("git.project.history.select.commit"))
      highlightSubsequentCommitsInGitLog(0)
      triggerByUiComponentAndHighlight(false, false) { ui: VcsLogGraphTable ->
        ui.selectedRow == 0
      }
      restoreState {
        val vcsLogUi = VcsProjectLog.getInstance(project).mainLogUi ?: return@restoreState false
        vcsLogUi.filterUi.textFilterComponent.text == ""
      }
      showWarningIfGitWindowClosed()
    }

    task {
      text(GitLessonsBundle.message("git.project.history.commit.details.explanation"))
      proceedLink()
      triggerByUiComponentAndHighlight(highlightInside = false, usePulsation = true) { _: VcsLogCommitDetailsListPanel -> true }
    }

    task {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(GitLessonsBundle.message("git.project.history.click.changed.file"))
      triggerByFoundPathAndHighlight(highlightInside = true) { _, path ->
        path.getPathComponent(path.pathCount - 1).toString().contains(".yml")
      }
      triggerByUiComponentAndHighlight(false, false) { _: SimpleDiffPanel -> true }
      showWarningIfGitWindowClosed()
    }

    if (VcsEditorTabFilesManager.getInstance().shouldOpenInNewWindow) {
      task("EditorEscape") {
        text(GitLessonsBundle.message("git.project.history.close.diff", action(it)))
        stateCheck { previous.ui?.isShowing != true }
      }
    }

    text(GitLessonsBundle.message("git.project.history.invitation.to.commit.lesson"))
  }

  override fun onLessonEnd(project: Project, lessonPassed: Boolean) {
    if (showGitBranchesBackup != null) {
      val logUiProperties = VcsProjectLog.getInstance(project).mainLogUi?.properties ?: error("Failed to get MainVcsLogUiProperties")
      logUiProperties[SHOW_GIT_BRANCHES_LOG_PROPERTY] = showGitBranchesBackup!!
    }
  }
}