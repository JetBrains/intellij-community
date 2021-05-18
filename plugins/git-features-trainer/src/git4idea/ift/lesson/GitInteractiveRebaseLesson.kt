// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.ui.table.JBTable
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.util.findBranch
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle
import git4idea.ift.GitLessonsUtil.checkoutBranch
import git4idea.ift.GitLessonsUtil.findVcsLogData
import git4idea.ift.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import git4idea.ift.GitLessonsUtil.proceedLink
import git4idea.ift.GitLessonsUtil.resetGitLogWindow
import git4idea.ift.GitLessonsUtil.showWarningIfGitWindowClosed
import git4idea.ift.GitLessonsUtil.triggerOnNotification
import training.dsl.*
import training.ui.LearningUiHighlightingManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.KeyStroke

class GitInteractiveRebaseLesson: GitLesson("Git.InteractiveRebase", "Interactive Rebase") {
  override val existedFile = "src/git/martian_cat.yml"
  private val branchName = "fixes"

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    checkoutBranch(branchName)

    task("ActivateVersionControlToolWindow") {
      text("Suppose you have made some fixes to your project. Press ${action(it)} to open Git tool window and overview the project history.")
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
      text("But from the resulting highlighted sequence of commits, it was difficult to understand what has changed in general. It would be more easy if some commits will be reordered or squashed.")
      highlightSubsequentCommitsInGitLog(sequenceLength = 5, highlightInside = false, usePulsation = true) {
        val root = vcsData.roots.single()
        it.id == vcsData.dataPack.findBranch(branchName, root)?.commitHash
      }
      proceedLink()
    }

    val interactiveRebaseMenuItemText = GitBundle.message("action.Git.Interactive.Rebase.text")
    lateinit var openRebaseDialogTaskId: TaskContext.TaskId
    task {
      openRebaseDialogTaskId = taskId
      var commitHashToHighlight: Hash? = null
      before {
        LearningUiHighlightingManager.clearHighlights()
        commitHashToHighlight = vcsData.findFirstCommitInBranch(branchName)
      }
      text("We can use ${strong("Interactive Rebase")} to solve this task easily. Right click the highlighted commit to open context menu.")
      highlightSubsequentCommitsInGitLog {
        it.id == commitHashToHighlight
      }
      triggerByUiComponentAndHighlight { ui: ActionMenuItem ->
        ui.text == interactiveRebaseMenuItemText
      }
      showWarningIfGitWindowClosed()
    }

    task {
      text("Select ${strong(interactiveRebaseMenuItemText)} from the list and press ${LessonUtil.rawEnter()} or click on it.")
      val rebasingDialogTitle = GitBundle.message("rebase.interactive.dialog.title")
      triggerByUiComponentAndHighlight(false, false) { ui: JDialog ->
        ui.title == rebasingDialogTitle
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    lateinit var movingCommitText: String
    task {
      text("Seems that the highlighted commit fixes something in the first commit from the list. It would be great if we combine them in one. Select the highlighted commit.")
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
      text("And now move this commit to the highlighted place. Use mouse or press ${LessonUtil.rawKeyStroke(moveUpShortcut.firstKeyStroke)} three times.")
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
      val fixupShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_MASK)
      text("Press ${LessonUtil.rawKeyStroke(fixupShortcut)} or click highlighted button and select ${strong("Fixup")} from the list to add changes from this commit to the first commit.")
      triggerByUiComponentAndHighlight { _: BasicOptionButtonUI.ArrowButton -> true }
      trigger("git4idea.rebase.interactive.dialog.FixupAction")
    }

    task {
      text("Great! Seems that the three highlighted commits are about the same. So we can combine them in one and edit resulting message. Select highlighted commits using ${LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT)}.")
      highlightSubsequentCommitsInRebaseDialog(startRowIncl = 2, endRowExcl = 5)
      triggerByUiComponentAndHighlight(false, false) { ui: JBTable ->
        ui.similarCommitsSelected()
      }
    }

    task {
      val squashShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_MASK)
      text("And now press ${LessonUtil.rawKeyStroke(squashShortcut)} or click ${strong("Squash")} button to unite the commits.")
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
      text("By default messages of all squashing commits included in the resulting message, but in our case we can replace it with something like ${code("Fix style")}. Edit the message if you want and press ${LessonUtil.rawKeyStroke(applyRewordShortcut.firstKeyStroke)} to apply reword.")
      stateCheck { previous.ui?.isShowing != true }
    }

    task {
      val startRebasingButtonText = GitBundle.message("rebase.interactive.dialog.start.rebase")
      text("Finally click ${strong(startRebasingButtonText)} to perform rebase.")
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JButton ->
        ui.text.contains(startRebasingButtonText)
      }
      triggerOnNotification {
        it.displayId == GitNotificationIdsHolder.REBASE_SUCCESSFUL
      }
    }

    text("Congratulations! You have made history of the project clean again!")
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