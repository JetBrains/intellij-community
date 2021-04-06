// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.git

import com.intellij.diff.tools.util.SimpleDiffPanel
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.filter.BranchFilterPopupComponent
import com.intellij.vcs.log.ui.filter.UserFilterPopupComponent
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.ui.frame.VcsLogCommitDetailsListPanel
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.util.findBranch
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.learn.course.KLesson
import training.learn.lesson.general.git.GitLessonsUtil.checkoutBranch
import training.learn.lesson.general.git.GitLessonsUtil.findVcsLogData
import training.learn.lesson.general.git.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import training.learn.lesson.general.git.GitLessonsUtil.proceedLink
import training.learn.lesson.general.git.GitLessonsUtil.resetGitLogWindow
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon

class GitProjectHistoryLesson() : KLesson("Git.ProjectHistory", "Project history") {
  override val existedFile = "src/git/sphinx_cat.yml"
  private val branchName = "feature"
  private val textToFind = "sphinx"

  override val lessonContent: LessonContext.() -> Unit = {
    checkoutBranch("feature")

    task("ActivateVersionControlToolWindow") {
      text("Press ${action(it)} to open Git tool window and overview the project history.")
      stateCheck {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible == true
      }
    }

    resetGitLogWindow()
    lateinit var vcsData: VcsLogData
    task {
      val future = findVcsLogData()
      stateCheck {
        val data = future.getNow(null)
        if (data != null) {
          vcsData = data
          true
        }
        else false
      }
    }

    task {
      highlightSubsequentCommitsInGitLog {
        val root = vcsData.roots.single()
        it.id == vcsData.dataPack.findBranch(branchName, root)?.commitHash
      }
    }

    task {
      text("Commits tree located in the center of the tool window. The last commit of your active branch is highlighted. All commits with ${icon(commitBackgroundColorIcon)} background contained in this branch. The rest of the commits are in the other branches.")
      proceedLink()
    }

    task {
      text("In the left side of the tool window listed all branches from your repository. Double click the highlighted ${
        strong("HEAD")
      } branch to show commits only from active branch.")
      triggerByFoundPathAndHighlight(highlightInside = true) { _, path ->
        path.pathCount > 1 && path.getPathComponent(1).toString() == "HEAD_NODE"
      }
      triggerByUiComponentAndHighlight(false, false) { ui: BranchFilterPopupComponent ->
        ui.currentText == "HEAD"
      }
    }

    val meFilterText = VcsLogBundle.message("vcs.log.user.filter.me")
    task {
      text("You can use a lot of filters that can help to find needed commits. For example you can show the commits from a specific author. Click the highlighted filter to open users popup.")
      triggerByUiComponentAndHighlight { _: UserFilterPopupComponent -> true }
      triggerByListItemAndHighlight { item ->
        item.toString() == meFilterText
      }
    }

    task {
      text("Select ${strong(meFilterText)} from the list to show only your commits.")
      triggerByUiComponentAndHighlight(false, false) { ui: UserFilterPopupComponent ->
        ui.currentText == meFilterText
      }
    }

    task {
      text("Highlighted search field can help you to find a commit by message or hash. Suppose you want to find a commit by part of the message. For example, type ${code(textToFind)} in the highlighted field and press ${LessonUtil.rawEnter()}.")
      triggerByUiComponentAndHighlight { ui: SearchTextField ->
        UIUtil.getParentOfType(MainFrame::class.java, ui) != null
      }
      triggerByUiComponentAndHighlight(false, false) l@{ ui: VcsLogGraphTable ->
        val model = ui.model as? GraphTableModel ?: return@l false
        model.rowCount > 0 && model.getCommitMetadata(0).fullMessage.contains(textToFind)
      }
    }

    task {
      text("Select found commit to show information about it.")
      highlightSubsequentCommitsInGitLog(0)
      triggerByUiComponentAndHighlight(false, false) { ui: VcsLogGraphTable ->
        ui.selectedRow == 0
      }
    }

    // todo Find out why it's hard to collapse highlighted commit details
    task {
      text("In the right side of the tool window showed information about selected commit. You can find here some metadata and branches that contain this commit.")
      proceedLink()
      triggerByUiComponentAndHighlight(highlightInside = false, usePulsation = true) { _: VcsLogCommitDetailsListPanel -> true }
    }

    task {
      triggerByFoundPathAndHighlight(highlightInside = true) { _, path ->
        path.getPathComponent(path.pathCount - 1).toString().contains("sphinx_cat.yml")
      }
    }

    task {
      text("There are also tree with all files changed in this commit. Click the highlighted file twice to show it's changes.")
      triggerByUiComponentAndHighlight(false, false) { _: SimpleDiffPanel -> true }
    }

    text("Great! Let's discover how to create commit in the next lesson.")
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