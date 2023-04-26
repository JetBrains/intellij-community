// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git.lesson

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.util.findBranch
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle
import git4idea.rebase.interactive.dialog.GIT_INTERACTIVE_REBASE_DIALOG_DIMENSION_KEY
import org.assertj.swing.core.MouseButton
import org.assertj.swing.data.TableCell
import org.assertj.swing.fixture.JTableFixture
import training.dsl.*
import training.dsl.LessonUtil.adjustPopupPosition
import training.dsl.LessonUtil.restorePopupPosition
import training.git.GitLessonsBundle
import training.git.GitLessonsUtil.highlightLatestCommitsFromBranch
import training.git.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import training.git.GitLessonsUtil.openGitWindow
import training.git.GitLessonsUtil.resetGitLogWindow
import training.git.GitLessonsUtil.showWarningIfGitWindowClosed
import training.git.GitLessonsUtil.triggerOnNotification
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.LessonEndInfo
import java.awt.Component
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.KeyStroke

class GitInteractiveRebaseLesson : GitLesson("Git.InteractiveRebase", GitLessonsBundle.message("git.interactive.rebase.lesson.name")) {
  override val sampleFilePath = "git/martian_cat.yml"
  override val branchName = "fixes"

  private var backupRebaseDialogLocation: Point? = null

  override val testScriptProperties = TaskTestContext.TestScriptProperties(duration = 30)

  override val lessonContent: LessonContext.() -> Unit = {
    task {
      triggerAndBorderHighlight().component { stripe: ActionButton ->
        stripe.action.templateText == IdeBundle.message("toolwindow.stripe.Version_Control")
      }
    }

    task("ActivateVersionControlToolWindow") {
      openGitWindow(GitLessonsBundle.message("git.interactive.rebase.open.git.window", action(it),
                                             strong(GitBundle.message("git4idea.vcs.name"))))
    }

    resetGitLogWindow()

    task {
      text(GitLessonsBundle.message("git.interactive.rebase.introduction"))
      highlightLatestCommitsFromBranch(branchName, sequenceLength = 5)
      proceedLink()
      showWarningIfGitWindowClosed()
    }

    var commitHashToHighlight: Hash? = null
    lateinit var clickCommitTaskId: TaskContext.TaskId
    task {
      clickCommitTaskId = taskId
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
      triggerAndBorderHighlight().component { ui: ActionMenuItem ->
        ui.text == interactiveRebaseMenuItemText
      }
      showWarningIfGitWindowClosed(restoreTaskWhenResolved = true)
      test {
        ideFrame {
          val table: VcsLogGraphTable = findComponentWithTimeout(defaultTimeout)
          val row = invokeAndWaitIfNeeded {
            (0 until table.rowCount).find { table.model.getCommitMetadata(it).id == commitHashToHighlight }
          } ?: error("Failed to find commit with hash: $commitHashToHighlight")
          JTableFixture(robot, table).click(TableCell.row(row).column(1), MouseButton.RIGHT_BUTTON)
        }
      }
    }

    task("Git.Interactive.Rebase") {
      text(GitLessonsBundle.message("git.interactive.rebase.choose.interactive.rebase",
                                    strong(interactiveRebaseMenuItemText)))
      trigger(it)
      restoreByUi(clickCommitTaskId, delayMillis = defaultRestoreDelay)
      test {
        ideFrame {
          jMenuItem { item: ActionMenuItem -> item.text == interactiveRebaseMenuItemText }.click()
        }
      }
    }

    task {
      highlightCommitInRebaseDialog(4)
    }

    lateinit var movingCommitText: String
    task {
      before {
        if (backupRebaseDialogLocation == null) {
          backupRebaseDialogLocation = adjustPopupPosition(GIT_INTERACTIVE_REBASE_DIALOG_DIMENSION_KEY)
        }
      }
      text(GitLessonsBundle.message("git.interactive.rebase.select.one.commit"))
      triggerUI().component { ui: JBTable ->
        if (isInsideRebaseDialog(ui)) {
          movingCommitText = ui.model.getValueAt(4, 1).toString()
          ui.selectedRow == 4
        }
        else false
      }
      restoreByUi(openRebaseDialogTaskId)
      test(waitEditorToBeReady = false) {
        ideFrame {
          val table = findComponentWithTimeout(defaultTimeout) { ui: JBTable -> isInsideRebaseDialog(ui) }
          JTableFixture(robot(), table).click(TableCell.row(4).column(1), MouseButton.LEFT_BUTTON)
        }
      }
    }

    task {
      val moveUpShortcut = CommonShortcuts.MOVE_UP.shortcuts.first() as KeyboardShortcut
      text(GitLessonsBundle.message("git.interactive.rebase.move.commit", LessonUtil.rawKeyStroke(moveUpShortcut.firstKeyStroke)))
      triggerAndBorderHighlight().componentPart { ui: JBTable ->
        if (isInsideRebaseDialog(ui)) {
          ui.getCellRect(1, 1, false).apply { height = 1 }
        }
        else null
      }
      triggerUI().component { ui: JBTable ->
        isInsideRebaseDialog(ui) && ui.model.getValueAt(1, 1).toString() == movingCommitText
      }
      restoreByUi(openRebaseDialogTaskId)
      test(waitEditorToBeReady = false) {
        repeat(3) { invokeActionViaShortcut("ALT UP") }
      }
    }

    task {
      val fixupShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK)
      text(GitLessonsBundle.message("git.interactive.rebase.invoke.fixup", LessonUtil.rawKeyStroke(fixupShortcut),
                                    strong(GitBundle.message("rebase.entry.action.name.fixup"))))
      triggerAndBorderHighlight().component { ui: BasicOptionButtonUI.ArrowButton -> isInsideRebaseDialog(ui) }
      trigger("git4idea.rebase.interactive.dialog.FixupAction")
      test(waitEditorToBeReady = false) {
        invokeActionViaShortcut("ALT F")
      }
    }

    task {
      text(GitLessonsBundle.message("git.interactive.rebase.select.three.commits", LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT)))
      highlightSubsequentCommitsInRebaseDialog(startRowIncl = 2, endRowExcl = 5)
      triggerUI().component { ui: JBTable ->
        isInsideRebaseDialog(ui) && ui.similarCommitsSelected()
      }
      test(waitEditorToBeReady = false) {
        ideFrame {
          val table = findComponentWithTimeout(defaultTimeout) { ui: JBTable -> isInsideRebaseDialog(ui) }
          JTableFixture(robot(), table).click(TableCell.row(2).column(1), MouseButton.LEFT_BUTTON)
        }
        invokeActionViaShortcut("SHIFT DOWN")
        invokeActionViaShortcut("SHIFT DOWN")
      }
    }

    task {
      val squashShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK)
      text(GitLessonsBundle.message("git.interactive.rebase.invoke.squash",
                                    LessonUtil.rawKeyStroke(squashShortcut), strong(GitBundle.message("rebase.entry.action.name.squash"))))
      triggerAndBorderHighlight().component { ui: BasicOptionButtonUI.MainButton -> isInsideRebaseDialog(ui) }
      trigger("git4idea.rebase.interactive.dialog.SquashAction")
      restoreState {
        val table = previous.ui as? JBTable ?: return@restoreState false
        !table.similarCommitsSelected()
      }
      test(waitEditorToBeReady = false) {
        invokeActionViaShortcut("ALT S")
      }
    }

    task {
      triggerUI().component { ui: CommitMessage -> isInsideRebaseDialog(ui) }
    }

    task {
      val applyRewordShortcut = CommonShortcuts.CTRL_ENTER.shortcuts.first() as KeyboardShortcut
      text(GitLessonsBundle.message("git.interactive.rebase.apply.reword", code("Fix style"),
                                    LessonUtil.rawKeyStroke(applyRewordShortcut.firstKeyStroke)))
      stateCheck { previous.ui?.isShowing != true }
      test(waitEditorToBeReady = false) {
        invokeActionViaShortcut("CTRL ENTER")
      }
    }

    task {
      val startRebasingButtonText = GitBundle.message("rebase.interactive.dialog.start.rebase")
      text(GitLessonsBundle.message("git.interactive.rebase.start.rebasing", strong(startRebasingButtonText)))
      triggerAndBorderHighlight().component { ui: JButton ->
        ui.text?.contains(startRebasingButtonText) == true
      }
      triggerOnNotification {
        it.displayId == GitNotificationIdsHolder.REBASE_SUCCESSFUL
      }
      test(waitEditorToBeReady = false) {
        ideFrame { button(startRebasingButtonText).click() }
      }
    }

    text(GitLessonsBundle.message("git.interactive.rebase.congratulations"))
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    restorePopupPosition(project, GIT_INTERACTIVE_REBASE_DIALOG_DIMENSION_KEY, backupRebaseDialogLocation)
    backupRebaseDialogLocation = null
  }

  private fun isInsideRebaseDialog(component: Component): Boolean {
    val dialog = UIUtil.getParentOfType(JDialog::class.java, component)
    return dialog?.title?.contains(GitBundle.message("rebase.interactive.dialog.title")) == true
  }

  private fun TaskContext.highlightCommitInRebaseDialog(rowInd: Int) {
    highlightSubsequentCommitsInRebaseDialog(rowInd, rowInd + 1)
  }

  private fun TaskContext.highlightSubsequentCommitsInRebaseDialog(startRowIncl: Int,
                                                                   endRowExcl: Int,
                                                                   highlightInside: Boolean = false,
                                                                   usePulsation: Boolean = false) {
    triggerAndBorderHighlight {
      this.highlightInside = highlightInside
      this.usePulsation = usePulsation
    }.componentPart { ui: JBTable ->
      if (isInsideRebaseDialog(ui)) {
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
    return topCommitsCache[index] ?: miniDetailsGetter.getCommitData(index)
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(GitLessonsBundle.message("git.interactive.rebase.help.link"),
         LessonUtil.getHelpLink("edit-project-history.html#interactive-rebase")),
  )
}