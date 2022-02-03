// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsNotificationIdsHolder
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBOptionButton
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.intellij.vcs.commit.CommitActionsPanel
import com.intellij.vcs.commit.CommitOptionsPanel
import com.intellij.vcs.commit.allOptions
import com.intellij.vcs.log.ui.frame.VcsLogChangesBrowser
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import git4idea.i18n.GitBundle
import git4idea.ift.GitLessonsBundle
import git4idea.ift.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import git4idea.ift.GitLessonsUtil.openCommitWindowText
import git4idea.ift.GitLessonsUtil.resetGitLogWindow
import git4idea.ift.GitLessonsUtil.restoreCommitWindowStateInformer
import git4idea.ift.GitLessonsUtil.showWarningIfCommitWindowClosed
import git4idea.ift.GitLessonsUtil.showWarningIfGitWindowClosed
import git4idea.ift.GitLessonsUtil.showWarningIfModalCommitEnabled
import git4idea.ift.GitLessonsUtil.showWarningIfStagingAreaEnabled
import git4idea.ift.GitLessonsUtil.triggerOnNotification
import org.assertj.swing.core.MouseButton
import org.assertj.swing.data.TableCell
import org.assertj.swing.fixture.JCheckBoxFixture
import org.assertj.swing.fixture.JTableFixture
import training.dsl.*
import training.project.ProjectUtils
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiUtil.findComponentWithTimeout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JCheckBox
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.tree.TreePath

class GitCommitLesson : GitLesson("Git.Commit", GitLessonsBundle.message("git.commit.lesson.name")) {
  override val existedFile = "git/puss_in_boots.yml"
  override val branchName = "feature"
  private val firstFileName = "simple_cat.yml"
  private val secondFileName = "puss_in_boots.yml"

  private val firstFileAddition = """
    |
    |    - play:
    |        condition: boring
    |        actions: [ run after favourite plush mouse ]""".trimMargin()

  private val secondFileAddition = """
    |
    |    - play:
    |        condition: boring
    |        actions: [ run after mice or own tail ]""".trimMargin()

  override val testScriptProperties = TaskTestContext.TestScriptProperties(duration = 60)

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      modifyFiles()
    }

    showWarningIfModalCommitEnabled()
    showWarningIfStagingAreaEnabled()

    task {
      openCommitWindowText(GitLessonsBundle.message("git.commit.open.commit.window"))
      stateCheck {
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT)?.isVisible == true
      }
      test { actions("CheckinProject") }
    }

    prepareRuntimeTask {
      val lastCommitMessage = "Add facts about playing cats"
      VcsConfiguration.getInstance(project).apply {
        REFORMAT_BEFORE_PROJECT_COMMIT = false
        LAST_COMMIT_MESSAGE = lastCommitMessage
        myLastCommitMessages = mutableListOf(lastCommitMessage)
      }

      val commitWorkflowHandler: AbstractCommitWorkflowHandler<*, *> = ChangesViewManager.getInstanceEx(project).commitWorkflowHandler
                                                                       ?: return@prepareRuntimeTask
      commitWorkflowHandler.workflow.commitOptions.allOptions.forEach(RefreshableOnComponent::restoreState)
      commitWorkflowHandler.setCommitMessage(lastCommitMessage)
    }

    task {
      triggerByPartOfComponent(false) l@{ ui: ChangesListView ->
        val path = TreeUtil.treePathTraverser(ui).find { it.getPathComponent(it.pathCount - 1).toString().contains(firstFileName) }
                   ?: return@l null
        val rect = ui.getPathBounds(path) ?: return@l null
        Rectangle(rect.x, rect.y, 20, rect.height)
      }
    }

    val commitWindowName = VcsBundle.message("commit.dialog.configurable")
    task {
      text(GitLessonsBundle.message("git.commit.choose.files", strong(commitWindowName), strong(firstFileName)))
      text(GitLessonsBundle.message("git.commit.choose.files.balloon"),
           LearningBalloonConfig(Balloon.Position.below, 300, cornerToPointerDistance = 55))
      highlightVcsChange(firstFileName)
      triggerOnOneChangeIncluded(secondFileName)
      showWarningIfCommitWindowClosed(restoreTaskWhenResolved = true)
      test {
        clickChangeElement(firstFileName)
      }
    }

    task {
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: ActionButton ->
        ActionManager.getInstance().getId(ui.action) == "ChangesView.ShowCommitOptions"
      }
    }

    lateinit var showOptionsTaskId: TaskContext.TaskId
    task {
      showOptionsTaskId = taskId
      text(GitLessonsBundle.message("git.commit.open.before.commit.options", icon(AllIcons.General.Gear)))
      text(GitLessonsBundle.message("git.commit.open.options.tooltip", strong(commitWindowName)),
           LearningBalloonConfig(Balloon.Position.above, 0))
      triggerByUiComponentAndHighlight(false, false) { _: CommitOptionsPanel -> true }
      showWarningIfCommitWindowClosed(restoreTaskWhenResolved = true)
      test {
        ideFrame {
          actionButton(ActionsBundle.actionText("ChangesView.ShowCommitOptions")).click()
        }
      }
    }

    val reformatCodeButtonText = VcsBundle.message("checkbox.checkin.options.reformat.code").dropMnemonic()
    task {
      triggerByUiComponentAndHighlight { ui: JBCheckBox ->
        ui.text?.contains(reformatCodeButtonText) == true
      }
    }

    task {
      val analyzeOptionText = VcsBundle.message("before.checkin.standard.options.check.smells").dropMnemonic()
      text(GitLessonsBundle.message("git.commit.analyze.code.explanation", strong(analyzeOptionText)))
      text(GitLessonsBundle.message("git.commit.enable.reformat.code", strong(reformatCodeButtonText)))
      triggerByUiComponentAndHighlight(false, false) { ui: JBCheckBox ->
        ui.text?.contains(reformatCodeButtonText) == true && ui.isSelected
      }
      restoreByUi(showOptionsTaskId)
      test {
        ideFrame {
          val checkBox = findComponentWithTimeout(defaultTimeout) { ui: JBCheckBox -> ui.text?.contains(reformatCodeButtonText) == true }
          JCheckBoxFixture(robot, checkBox).check()
        }
      }
    }

    task {
      text(GitLessonsBundle.message("git.commit.close.commit.options", LessonUtil.rawKeyStroke(KeyEvent.VK_ESCAPE)))
      stateCheck {
        previous.ui?.isShowing != true
      }
      test { invokeActionViaShortcut("ESCAPE") }
    }

    val commitButtonText = GitBundle.message("commit.action.name").dropMnemonic()
    task {
      text(GitLessonsBundle.message("git.commit.perform.commit", strong(commitButtonText)))
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JBOptionButton ->
        ui.text?.contains(commitButtonText) == true
      }
      triggerOnNotification { it.displayId == VcsNotificationIdsHolder.COMMIT_FINISHED }
      showWarningIfCommitWindowClosed()
      test {
        ideFrame {
          button { b: JBOptionButton -> b.text == commitButtonText }.click()
        }
      }
    }

    task("ActivateVersionControlToolWindow") {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(GitLessonsBundle.message("git.commit.open.git.window", action(it)))
      stateCheck {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible == true
      }
      test { actions(it) }
    }

    resetGitLogWindow()

    task {
      text(GitLessonsBundle.message("git.commit.select.top.commit"))
      triggerOnTopCommitSelected()
      showWarningIfGitWindowClosed()
      test {
        ideFrame {
          val table: VcsLogGraphTable = findComponentWithTimeout(defaultTimeout)
          JTableFixture(robot, table).click(TableCell.row(0).column(1), MouseButton.LEFT_BUTTON)
        }
      }
    }

    task {
      text(GitLessonsBundle.message("git.commit.committed.file.explanation"))
      triggerByUiComponentAndHighlight(highlightInside = false, usePulsation = true) { _: VcsLogChangesBrowser -> true }
      proceedLink()
      showWarningIfGitWindowClosed()
    }

    task {
      before { LearningUiHighlightingManager.clearHighlights() }
      val amendCheckboxText = VcsBundle.message("checkbox.amend").dropMnemonic()
      text(GitLessonsBundle.message("git.commit.select.amend.checkbox",
                                    strong(amendCheckboxText),
                                    LessonUtil.rawKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK)),
                                    strong(commitWindowName)))
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JBCheckBox ->
        ui.text?.contains(amendCheckboxText) == true
      }
      triggerByUiComponentAndHighlight(false, false) { ui: JBCheckBox ->
        ui.text?.contains(amendCheckboxText) == true && ui.isSelected
      }
      showWarningIfCommitWindowClosed()
      test {
        ideFrame {
          val checkBox = findComponentWithTimeout(defaultTimeout) { ui: JBCheckBox -> ui.text?.contains(amendCheckboxText) == true }
          JCheckBoxFixture(robot, checkBox).check()
        }
      }
    }

    task {
      text(GitLessonsBundle.message("git.commit.select.file"))
      highlightVcsChange(firstFileName)
      triggerOnOneChangeIncluded(firstFileName)
      showWarningIfCommitWindowClosed()
      test {
        clickChangeElement(firstFileName)
      }
    }

    task {
      val amendButtonText = VcsBundle.message("amend.action.name", commitButtonText)
      text(GitLessonsBundle.message("git.commit.amend.commit", strong(amendButtonText)))
      triggerByUiComponentAndHighlight { ui: JBOptionButton ->
        UIUtil.getParentOfType(CommitActionsPanel::class.java, ui) != null
      }
      triggerOnNotification { it.displayId == VcsNotificationIdsHolder.COMMIT_FINISHED }
      showWarningIfCommitWindowClosed()
      test {
        ideFrame {
          button { b: JBOptionButton -> UIUtil.getParentOfType(CommitActionsPanel::class.java, b) != null }.click()
        }
      }
    }

    task {
      text(GitLessonsBundle.message("git.commit.select.top.commit.again"))
      triggerOnTopCommitSelected()
      showWarningIfGitWindowClosed()
      test {
        ideFrame {
          val table: VcsLogGraphTable = findComponentWithTimeout(defaultTimeout)
          JTableFixture(robot, table).click(TableCell.row(0).column(1), MouseButton.LEFT_BUTTON)
        }
      }
    }

    text(GitLessonsBundle.message("git.commit.two.committed.files.explanation"))

    restoreCommitWindowStateInformer()
  }

  private fun TaskContext.highlightVcsChange(changeFileName: String, highlightBorder: Boolean = true) {
    triggerByFoundPathAndHighlight(highlightBorder) { _: JTree, path: TreePath ->
      path.pathCount > 2 && path.getPathComponent(2).toString().contains(changeFileName)
    }
  }

  private fun TaskContext.triggerOnOneChangeIncluded(changeFileName: String) {
    triggerByUiComponentAndHighlight(false, false) l@{ ui: ChangesListView ->
      val includedChanges = ui.includedSet
      if (includedChanges.size != 1) return@l false
      val change = includedChanges.first() as? ChangeListChange ?: return@l false
      change.virtualFile?.name == changeFileName
    }
  }

  private fun TaskContext.triggerOnTopCommitSelected() {
    highlightSubsequentCommitsInGitLog(0)
    triggerByUiComponentAndHighlight(false, false) { ui: VcsLogGraphTable ->
      ui.isCellSelected(0, 1)
    }
  }

  private fun TaskRuntimeContext.modifyFiles() = invokeLater {
    DocumentUtil.writeInRunUndoTransparentAction {
      val projectRoot = ProjectUtils.getCurrentLearningProjectRoot()
      appendToFile(projectRoot, firstFileName, firstFileAddition)
      appendToFile(projectRoot, secondFileName, secondFileAddition)
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
  }

  private fun appendToFile(projectRoot: VirtualFile, fileName: String, text: String) {
    val file = VfsUtil.collectChildrenRecursively(projectRoot).find { it.name == fileName }
               ?: error("Failed to find ${fileName} in project root: ${projectRoot}")
    file.refresh(false, false)
    val document = FileDocumentManager.getInstance().getDocument(file)!! // it's not directory or binary file and it isn't large
    document.insertString(document.textLength, text)
    FileDocumentManager.getInstance().saveDocument(document)
  }

  private fun TaskTestContext.clickChangeElement(partOfText: String) {
    val checkPath: (TreePath) -> Boolean = { p -> p.getPathComponent(p.pathCount - 1).toString().contains(partOfText) }
    ideFrame {
      val fixture = jTree(checkPath = checkPath)
      val tree = fixture.target()
      val pathRect = invokeAndWaitIfNeeded {
        val path = TreeUtil.treePathTraverser(tree).find(checkPath)
        tree.getPathBounds(path)
      } ?: error("Failed to find path with text '$partOfText'")
      val offset = JCheckBox().preferredSize.width / 2
      robot.click(tree, Point(pathRect.x + offset, pathRect.y + offset))
    }
  }

  override val suitableTips = listOf("partial_git_commit")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(GitLessonsBundle.message("git.commit.help.link"),
         LessonUtil.getHelpLink("commit-and-push-changes.html")),
  )
}