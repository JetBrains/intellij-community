// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git.lesson

import com.intellij.diff.tools.util.SimpleDiffPanel
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel
import com.intellij.vcs.log.ui.filter.BranchFilterPopupComponent
import com.intellij.vcs.log.ui.filter.UserFilterPopupComponent
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import git4idea.i18n.GitBundle
import git4idea.ui.branch.dashboard.CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY
import git4idea.ui.branch.dashboard.SHOW_GIT_BRANCHES_LOG_PROPERTY
import org.assertj.swing.fixture.JPanelFixture
import org.assertj.swing.fixture.JTableFixture
import training.dsl.*
import training.git.GitLessonsBundle
import training.git.GitLessonsUtil.clickTreeRow
import training.git.GitLessonsUtil.highlightLatestCommitsFromBranch
import training.git.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import training.git.GitLessonsUtil.openGitWindow
import training.git.GitLessonsUtil.resetGitLogWindow
import training.git.GitLessonsUtil.showWarningIfGitWindowClosed
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.LessonEndInfo
import java.util.regex.Pattern

class GitProjectHistoryLesson : GitLesson("Git.ProjectHistory", GitLessonsBundle.message("git.project.history.lesson.name")) {
  override val sampleFilePath = "git/sphinx_cat.yml"
  override val branchName = "feature"
  private val textToFind = "sphinx"

  private var showGitBranchesBackup: Boolean? = null

  override val testScriptProperties = TaskTestContext.TestScriptProperties(40)

  override val lessonContent: LessonContext.() -> Unit = {
    task {
      triggerAndBorderHighlight().component { stripe: ActionButton ->
        stripe.action.templateText == IdeBundle.message("toolwindow.stripe.Version_Control")
      }
    }

    task("ActivateVersionControlToolWindow") {
      openGitWindow(GitLessonsBundle.message("git.project.history.open.git.window", action(it),
                                             icon(AllIcons.Toolwindows.ToolWindowChanges),
                                             strong(GitBundle.message("git4idea.vcs.name"))))
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
      gotItStep(Balloon.Position.above, width = 0,
                GitLessonsBundle.message("git.project.history.commits.tree.got.it"),
                duplicateMessage = false)
      showWarningIfGitWindowClosed()
    }

    task {
      var selectionCleared = false
      triggerAndBorderHighlight().treeItem { tree, path ->
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
      triggerUI().component { ui: BranchFilterPopupComponent ->
        ui.currentText.contains("HEAD")
      }
      showWarningIfGitWindowClosed(restoreTaskWhenResolved = true)
      test {
        ideFrame {
          clickTreeRow(doubleClick = true) { item -> item.toString() == "HEAD_NODE" }
        }
      }
    }

    task {
      triggerAndBorderHighlight().component { _: UserFilterPopupComponent -> true }
    }

    val meFilterText = VcsLogBundle.message("vcs.log.user.filter.me")
    task {
      text(GitLessonsBundle.message("git.project.history.apply.user.filter"))
      text(GitLessonsBundle.message("git.project.history.click.filter.tooltip"),
           LearningBalloonConfig(Balloon.Position.above, 0))
      triggerAndBorderHighlight().listItem { item ->
        item.toString().contains(meFilterText)
      }
      showWarningIfGitWindowClosed(restoreTaskWhenResolved = true)
      test {
        ideFrame {
          val panel: UserFilterPopupComponent = findComponentWithTimeout(defaultTimeout)
          JPanelFixture(robot, panel).click()
        }
      }
    }

    task {
      text(GitLessonsBundle.message("git.project.history.select.me", strong(meFilterText)))
      triggerUI().component { ui: UserFilterPopupComponent ->
        ui.currentText.contains(meFilterText)
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
      test {
        ideFrame {
          jList(meFilterText).clickItem(meFilterText)
        }
      }
    }

    task {
      text(GitLessonsBundle.message("git.project.history.apply.message.filter", code(textToFind), LessonUtil.rawEnter()))
      triggerAndBorderHighlight().component { ui: SearchFieldWithExtension ->
        (UIUtil.getParentOfType(MainFrame::class.java, ui) != null).also {
          if (it) IdeFocusManager.getInstance(project).requestFocus(ui, true)
        }
      }
      triggerUI().component l@{ ui: VcsLogGraphTable ->
        val model = ui.model as? GraphTableModel ?: return@l false
        model.rowCount > 0 && model.getCommitMetadata(0).fullMessage.contains(textToFind)
      }
      showWarningIfGitWindowClosed()
      test {
        Thread.sleep(500)
        type(textToFind)
        invokeActionViaShortcut("ENTER")
      }
    }

    task {
      text(GitLessonsBundle.message("git.project.history.select.commit"))
      highlightSubsequentCommitsInGitLog(startCommitRow = 0)
      triggerUI().component { ui: VcsLogGraphTable ->
        ui.selectedRow == 0
      }
      restoreState {
        val vcsLogUi = VcsProjectLog.getInstance(project).mainLogUi ?: return@restoreState false
        vcsLogUi.filterUi.textFilterComponent.textField.text == ""
      }
      showWarningIfGitWindowClosed()
      test {
        ideFrame {
          val table: VcsLogGraphTable = findComponentWithTimeout(defaultTimeout)
          JTableFixture(robot, table).cell(Pattern.compile(""".*$textToFind.*""")).click()
        }
      }
    }

    task {
      triggerAndBorderHighlight().component { _: CommitDetailsListPanel -> true }
    }

    task {
      text(GitLessonsBundle.message("git.project.history.commit.details.explanation"))
      gotItStep(Balloon.Position.atLeft, width = 0,
                GitLessonsBundle.message("git.project.history.commit.details.got.it"),
                duplicateMessage = false)
      showWarningIfGitWindowClosed()
    }

    task {
      triggerAndBorderHighlight().treeItem { _, path ->
        path.getPathComponent(path.pathCount - 1).toString().contains(".yml")
      }
    }

    task {
      text(GitLessonsBundle.message("git.project.history.click.changed.file"))
      text(GitLessonsBundle.message("git.project.history.click.changed.file.popup"),
           LearningBalloonConfig(Balloon.Position.below, width = 0))
      triggerUI().component { _: SimpleDiffPanel -> true }
      showWarningIfGitWindowClosed()
      test {
        clickTreeRow(doubleClick = true) { item -> item.toString().contains(sampleFilePath) }
      }
    }

    if (VcsEditorTabFilesManager.getInstance().shouldOpenInNewWindow) {
      task("EditorEscape") {
        text(GitLessonsBundle.message("git.project.history.close.diff", action(it)))
        stateCheck { previous.ui?.isShowing != true }
        test { invokeActionViaShortcut("ESCAPE") }
      }
    }

    text(GitLessonsBundle.message("git.project.history.invitation.to.commit.lesson"))
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    if (showGitBranchesBackup != null) {
      val logUiProperties = VcsProjectLog.getInstance(project).mainLogUi?.properties ?: error("Failed to get MainVcsLogUiProperties")
      logUiProperties[SHOW_GIT_BRANCHES_LOG_PROPERTY] = showGitBranchesBackup!!
    }
  }
}