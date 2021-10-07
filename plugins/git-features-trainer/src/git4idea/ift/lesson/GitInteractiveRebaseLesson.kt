// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.ui.table.JBTable
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.util.findBranch
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle
import git4idea.ift.GitLessonsBundle
import git4idea.ift.GitLessonsUtil.checkoutBranch
import git4idea.ift.GitLessonsUtil.highlightLatestCommitsFromBranch
import git4idea.ift.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import git4idea.ift.GitLessonsUtil.resetGitLogWindow
import git4idea.ift.GitLessonsUtil.showWarningIfGitWindowClosed
import git4idea.ift.GitLessonsUtil.triggerOnNotification
import git4idea.rebase.interactive.dialog.GIT_INTERACTIVE_REBASE_DIALOG_DIMENSION_KEY
import training.dsl.*
import training.dsl.LessonUtil.adjustPopupPosition
import training.dsl.LessonUtil.restorePopupPosition
import training.ui.LearningUiHighlightingManager
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.KeyStroke

class GitInteractiveRebaseLesson : GitLesson("Git.InteractiveRebase", GitLessonsBundle.message("git.interactive.rebase.lesson.name")) {
  override val existedFile = "git/martian_cat.yml"
  private val branchName = "fixes"

  private var backupRebaseDialogLocation: Point? = null

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    checkoutBranch(branchName)

    task("ActivateVersionControlToolWindow") {
      text(GitLessonsBundle.message("git.interactive.rebase.open.git.window", action(it)))
      stateCheck {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible == true
      }
    }

    resetGitLogWindow()

    task {
      text(GitLessonsBundle.message("git.interactive.rebase.introduction"))
      highlightLatestCommitsFromBranch(branchName, sequenceLength = 5, highlightInside = false, usePulsation = true)
      proceedLink()
    }

    task {
      var commitHashToHighlight: Hash? = null
      before {
        LearningUiHighlightingManager.clearHighlights()
        val vcsData = VcsProjectLog.getInstance(project).dataManager
        commitHashToHighlight = vcsData?.findFirstCommitInBranch(branchName)
      }
      highlightSubsequentCommitsInGitLog {
        it.id == commitHashToHighlight
      }
    }

    val interactiveRebaseMenuItemText = GitBundle.message("action.Git.Interactive.Rebase.text")
    lateinit var openRebaseDialogTaskId: TaskContext.TaskId
    task {
      openRebaseDialogTaskId = taskId
      text(GitLessonsBundle.message("git.interactive.rebase.open.context.menu"))
      text(GitLessonsBundle.message("git.interactive.rebase.click.commit.tooltip"),
           LearningBalloonConfig(Balloon.Position.above, 0))
      triggerByUiComponentAndHighlight { ui: ActionMenuItem ->
        ui.text == interactiveRebaseMenuItemText
      }
      showWarningIfGitWindowClosed()
    }

    task {
      text(GitLessonsBundle.message("git.interactive.rebase.choose.interactive.rebase",
                                    strong(interactiveRebaseMenuItemText)))
      val rebasingDialogTitle = GitBundle.message("rebase.interactive.dialog.title")
      triggerByUiComponentAndHighlight(false, false) { ui: JDialog ->
        ui.title?.contains(rebasingDialogTitle) == true
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    lateinit var movingCommitText: String
    task {
      before {
        if (backupRebaseDialogLocation == null) {
          backupRebaseDialogLocation = adjustPopupPosition(GIT_INTERACTIVE_REBASE_DIALOG_DIMENSION_KEY)
        }
      }
      text(GitLessonsBundle.message("git.interactive.rebase.select.one.commit"))
      highlightCommitInRebaseDialog(4)
      triggerByUiComponentAndHighlight(false, false) { ui: JBTable ->
        if (ui !is VcsLogGraphTable) {
          movingCommitText = ui.model.getValueAt(4, 1).toString()
          ui.selectedRow == 4
        }
        else false
      }
      restoreByUi(openRebaseDialogTaskId)
    }

    task {
      val moveUpShortcut = CommonShortcuts.MOVE_UP.shortcuts.first() as KeyboardShortcut
      text(GitLessonsBundle.message("git.interactive.rebase.move.commit", LessonUtil.rawKeyStroke(moveUpShortcut.firstKeyStroke)))
      triggerByPartOfComponent(highlightInside = true, usePulsation = false) { ui: JBTable ->
        if (ui !is VcsLogGraphTable) {
          ui.getCellRect(1, 1, false).apply { height = 1 }
        }
        else null
      }
      triggerByUiComponentAndHighlight(false, false) { ui: JBTable ->
        ui.model.getValueAt(1, 1).toString() == movingCommitText
      }
      restoreState {
        Thread.currentThread().stackTrace.find {
          it.className.contains("GitInteractiveRebaseUsingLog")
        } == null
      }
    }

    task {
      val fixupShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK)
      text(GitLessonsBundle.message("git.interactive.rebase.invoke.fixup", LessonUtil.rawKeyStroke(fixupShortcut),
                                    strong(GitBundle.message("rebase.entry.action.name.fixup"))))
      triggerByUiComponentAndHighlight { _: BasicOptionButtonUI.ArrowButton -> true }
      trigger("git4idea.rebase.interactive.dialog.FixupAction")
    }

    task {
      text(GitLessonsBundle.message("git.interactive.rebase.select.three.commits", LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT)))
      highlightSubsequentCommitsInRebaseDialog(startRowIncl = 2, endRowExcl = 5)
      triggerByUiComponentAndHighlight(false, false) { ui: JBTable ->
        ui.similarCommitsSelected()
      }
    }

    task {
      val squashShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK)
      text(GitLessonsBundle.message("git.interactive.rebase.invoke.squash",
                                    LessonUtil.rawKeyStroke(squashShortcut), strong(GitBundle.message("rebase.entry.action.name.squash"))))
      triggerByUiComponentAndHighlight { _: BasicOptionButtonUI.MainButton -> true }
      trigger("git4idea.rebase.interactive.dialog.SquashAction")
      restoreState {
        val table = previous.ui as? JBTable ?: return@restoreState false
        !table.similarCommitsSelected()
      }
    }

    task {
      triggerByUiComponentAndHighlight(false, false) { _: CommitMessage -> true }
    }

    task {
      val applyRewordShortcut = CommonShortcuts.CTRL_ENTER.shortcuts.first() as KeyboardShortcut
      text(GitLessonsBundle.message("git.interactive.rebase.apply.reword", LessonUtil.rawKeyStroke(applyRewordShortcut.firstKeyStroke)))
      stateCheck { previous.ui?.isShowing != true }
    }

    task {
      val startRebasingButtonText = GitBundle.message("rebase.interactive.dialog.start.rebase")
      text(GitLessonsBundle.message("git.interactive.rebase.start.rebasing", strong(startRebasingButtonText)))
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JButton ->
        ui.text?.contains(startRebasingButtonText) == true
      }
      triggerOnNotification {
        it.displayId == GitNotificationIdsHolder.REBASE_SUCCESSFUL
      }
    }

    text(GitLessonsBundle.message("git.interactive.rebase.congratulations"))
  }

  override fun onLessonEnd(project: Project, lessonPassed: Boolean) {
    restorePopupPosition(project, GIT_INTERACTIVE_REBASE_DIALOG_DIMENSION_KEY, backupRebaseDialogLocation)
    backupRebaseDialogLocation = null
  }

  private fun TaskContext.highlightCommitInRebaseDialog(rowInd: Int) {
    highlightSubsequentCommitsInRebaseDialog(rowInd, rowInd + 1, highlightInside = true)
  }

  private fun TaskContext.highlightSubsequentCommitsInRebaseDialog(startRowIncl: Int,
                                                                   endRowExcl: Int,
                                                                   highlightInside: Boolean = false,
                                                                   usePulsation: Boolean = false) {
    triggerByPartOfComponent(highlightInside = highlightInside, usePulsation = usePulsation) { ui: JBTable ->
      if (ui !is VcsLogGraphTable) {
        val rect = ui.getCellRect(startRowIncl, 1, false)
        rect.height *= endRowExcl - startRowIncl
        rect
      }
      else null
    }
  }

  private fun JBTable.similarCommitsSelected(): Boolean {
    val rows = selectedRows
    return rows.size == 3 && (2..4).all { rows.contains(it) }
  }

  private fun VcsLogData.findFirstCommitInBranch(branchName: String): Hash? {
    val root = roots.single()
    val mainCommitHash = dataPack.findBranch("main", root)?.commitHash
    val lastCommitHash = dataPack.findBranch(branchName, root)?.commitHash
    return if (mainCommitHash != null && lastCommitHash != null) {
      var metadata = getCommitMetadata(lastCommitHash)
      while (metadata.parents.single() != mainCommitHash) {
        metadata = getCommitMetadata(metadata.parents.single())
      }
      metadata.id
    }
    else null
  }

  private fun VcsLogData.getCommitMetadata(hash: Hash): VcsCommitMetadata {
    val index = getCommitIndex(hash, roots.single())
    return topCommitsCache[index] ?: miniDetailsGetter.getCommitData(index, listOf(index))
  }
}