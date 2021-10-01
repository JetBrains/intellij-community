// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.diff.tools.util.SimpleDiffPanel
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
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
import git4idea.ift.GitLessonsUtil.checkoutBranch
import git4idea.ift.GitLessonsUtil.highlightLatestCommitsFromBranch
import git4idea.ift.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import git4idea.ift.GitLessonsUtil.resetGitLogWindow
import git4idea.ift.GitLessonsUtil.showWarningIfGitWindowClosed
import training.dsl.*
import training.ui.LearningUiHighlightingManager
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon

class GitProjectHistoryLesson : GitLesson("Git.ProjectHistory", GitLessonsBundle.message("git.project.history.lesson.name")) {
  override val existedFile = "git/sphinx_cat.yml"
  private val branchName = "feature"
  private val textToFind = "sphinx"

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    checkoutBranch("feature")

    task("ActivateVersionControlToolWindow") {
      text(GitLessonsBundle.message("git.project.history.open.git.window", action(it)))
      stateCheck {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible == true
      }
    }

    resetGitLogWindow()

    task {
      highlightLatestCommitsFromBranch(branchName)
    }

    task {
      text(GitLessonsBundle.message("git.project.history.commits.tree.explanation", icon(commitBackgroundColorIcon)))
      proceedLink()
    }

    task {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(GitLessonsBundle.message("git.project.history.apply.branch.filter"))
      triggerByFoundPathAndHighlight(highlightInside = true) { _, path ->
        path.pathCount > 1 && path.getPathComponent(1).toString() == "HEAD_NODE"
      }
      triggerByUiComponentAndHighlight(false, false) { ui: BranchFilterPopupComponent ->
        ui.currentText?.contains("HEAD") == true
      }
      showWarningIfGitWindowClosed()
    }

    val meFilterText = VcsLogBundle.message("vcs.log.user.filter.me")
    task {
      text(GitLessonsBundle.message("git.project.history.apply.user.filter"))
      triggerByUiComponentAndHighlight { _: UserFilterPopupComponent -> true }
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
        UIUtil.getParentOfType(MainFrame::class.java, ui) != null
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

    // todo Find out why it's hard to collapse highlighted commit details
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

  private val commitBackgroundColorIcon = object: Icon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
      val g2d = g as Graphics2D
      val oldColor = g2d.color
      // todo Add real background colors. Now it is colors with hover.
      g2d.color = JBColor(0xD0E2EE, 0x464A4D)
      g2d.fillRect(x, y, iconWidth, iconHeight)
      g2d.color = oldColor
    }

    override fun getIconWidth(): Int = 16

    override fun getIconHeight(): Int = 16
  }
}