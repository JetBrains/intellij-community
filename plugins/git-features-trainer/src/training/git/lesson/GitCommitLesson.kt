// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git.lesson

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsNotificationIdsHolder
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBOptionButton
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.intellij.vcs.commit.CommitActionsPanel
import com.intellij.vcs.commit.restoreState
import com.intellij.vcs.log.ui.frame.VcsLogChangesBrowser
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import git4idea.i18n.GitBundle
import org.assertj.swing.core.MouseButton
import org.assertj.swing.data.TableCell
import org.assertj.swing.fixture.JCheckBoxFixture
import org.assertj.swing.fixture.JTableFixture
import training.dsl.*
import training.git.GitLessonsBundle
import training.git.GitLessonsUtil.clickChangeElement
import training.git.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import training.git.GitLessonsUtil.openCommitWindow
import training.git.GitLessonsUtil.openGitWindow
import training.git.GitLessonsUtil.resetGitLogWindow
import training.git.GitLessonsUtil.restoreCommitWindowStateInformer
import training.git.GitLessonsUtil.showWarningIfCommitWindowClosed
import training.git.GitLessonsUtil.showWarningIfGitWindowClosed
import training.git.GitLessonsUtil.showWarningIfModalCommitEnabled
import training.git.GitLessonsUtil.showWarningIfStagingAreaEnabled
import training.git.GitLessonsUtil.triggerOnChangeCheckboxShown
import training.git.GitLessonsUtil.triggerOnNotification
import training.git.GitLessonsUtil.triggerOnOneChangeIncluded
import training.project.ProjectUtils
import training.ui.LearningUiUtil.findComponentWithTimeout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.tree.TreePath

class GitCommitLesson : GitLesson("Git.Commit", GitLessonsBundle.message("git.commit.lesson.name")) {
  override val sampleFilePath = "git/puss_in_boots.yml"
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
      triggerAndBorderHighlight().component { stripe: ActionButton ->
        stripe.action.templateText == IdeBundle.message("toolwindow.stripe.Commit")
      }
    }

    task {
      openCommitWindow(GitLessonsBundle.message("git.commit.open.commit.window"))
      test {
        val stripe = previous.ui ?: error("Not found Commit stripe button")
        ideFrame { jComponent(stripe).click() }
      }
    }

    prepareRuntimeTask {
      val lastCommitMessage = "Add facts about playing cats"
      VcsConfiguration.getInstance(project).apply {
        REFORMAT_BEFORE_PROJECT_COMMIT = false
        LAST_COMMIT_MESSAGE = lastCommitMessage
        setRecentMessages(listOf(lastCommitMessage))
      }

      val commitWorkflowHandler: AbstractCommitWorkflowHandler<*, *> = ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler
                                                                       ?: return@prepareRuntimeTask
      commitWorkflowHandler.workflow.commitOptions.restoreState()
      commitWorkflowHandler.setCommitMessage(lastCommitMessage)
    }

    task {
      triggerOnChangeCheckboxShown(firstFileName)
    }

    val commitWindowName = VcsBundle.message("commit.dialog.configurable")
    task {
      text(GitLessonsBundle.message("git.commit.choose.files", strong(commitWindowName), strong(firstFileName)))
      text(GitLessonsBundle.message("git.commit.choose.files.balloon"),
           LearningBalloonConfig(Balloon.Position.below, width = 0))
      highlightVcsChange(firstFileName)
      triggerOnOneChangeIncluded(firstFileName)
      showWarningIfCommitWindowClosed(restoreTaskWhenResolved = true)
      test {
        clickChangeElement(firstFileName)
      }
    }

    task {
      triggerAndBorderHighlight().component { ui: ActionButton ->
        ActionManager.getInstance().getId(ui.action) == "ChangesView.ShowCommitOptions"
      }
    }

    val reformatCodeButtonText = VcsBundle.message("checkbox.checkin.options.reformat.code").dropMnemonic()
    lateinit var showOptionsTaskId: TaskContext.TaskId
    task {
      showOptionsTaskId = taskId
      text(GitLessonsBundle.message("git.commit.open.before.commit.options", icon(AllIcons.General.Gear)))
      text(GitLessonsBundle.message("git.commit.open.options.tooltip", strong(commitWindowName)),
           LearningBalloonConfig(Balloon.Position.above, 0))
      triggerAndBorderHighlight().component { ui: JBCheckBox ->
        ui.text?.contains(reformatCodeButtonText) == true
      }
      showWarningIfCommitWindowClosed(restoreTaskWhenResolved = true)
      test {
        ideFrame {
          actionButton(ActionsBundle.actionText("ChangesView.ShowCommitOptions")).click()
        }
      }
    }

    task {
      val analyzeOptionText = VcsBundle.message("before.checkin.standard.options.check.smells").dropMnemonic()
      text(GitLessonsBundle.message("git.commit.analyze.code.explanation", strong(analyzeOptionText)))
      text(GitLessonsBundle.message("git.commit.enable.reformat.code", strong(reformatCodeButtonText)))
      triggerUI().component { ui: JBCheckBox ->
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
      triggerAndBorderHighlight().component { ui: JBOptionButton ->
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

    task {
      triggerAndBorderHighlight().component { stripe: ActionButton ->
        stripe.action.templateText == IdeBundle.message("toolwindow.stripe.Version_Control")
      }
    }

    task("ActivateVersionControlToolWindow") {
      openGitWindow(GitLessonsBundle.message("git.commit.open.git.window", action(it),
                                             icon(AllIcons.Toolwindows.ToolWindowChanges),
                                             strong(GitBundle.message("git4idea.vcs.name"))))
    }

    resetGitLogWindow()

    task {
      highlightSubsequentCommitsInGitLog(startCommitRow = 0)
    }

    task {
      text(GitLessonsBundle.message("git.commit.select.top.commit"))
      text(GitLessonsBundle.message("git.commit.select.top.commit.balloon"),
           LearningBalloonConfig(Balloon.Position.below, width = 300))
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
      triggerAndBorderHighlight().component { _: VcsLogChangesBrowser -> true }
    }

    task {
      text(GitLessonsBundle.message("git.commit.committed.file.explanation", strong(GitBundle.message("git4idea.vcs.name"))))
      gotItStep(Balloon.Position.atLeft, width = 0,
                GitLessonsBundle.message("git.commit.committed.file.got.it"),
                cornerToPointerDistance = 20, duplicateMessage = false)
      showWarningIfGitWindowClosed()
    }

    val amendCheckboxText = VcsBundle.message("checkbox.amend").dropMnemonic()
    task {
      triggerAndBorderHighlight().component { ui: JBCheckBox ->
        ui.text?.contains(amendCheckboxText) == true
      }
    }

    task {
      text(GitLessonsBundle.message("git.commit.select.amend.checkbox",
                                    strong(amendCheckboxText),
                                    LessonUtil.rawKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK)),
                                    strong(commitWindowName)))
      text(GitLessonsBundle.message("git.commit.select.amend.checkbox.balloon", strong(amendCheckboxText)),
           LearningBalloonConfig(Balloon.Position.above, width = 0))
      triggerUI().component { ui: JBCheckBox ->
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
      triggerOnChangeCheckboxShown(secondFileName)
    }

    task {
      text(GitLessonsBundle.message("git.commit.select.file"))
      text(GitLessonsBundle.message("git.commit.select.file.balloon"),
           LearningBalloonConfig(Balloon.Position.above, width = 0))
      highlightVcsChange(secondFileName)
      triggerOnOneChangeIncluded(secondFileName)
      showWarningIfCommitWindowClosed()
      test {
        clickChangeElement(secondFileName)
      }
    }

    task {
      triggerAndBorderHighlight().component { ui: JBOptionButton ->
        UIUtil.getParentOfType(CommitActionsPanel::class.java, ui) != null
      }
    }

    task {
      val amendButtonText = VcsBundle.message("amend.action.name", commitButtonText)
      text(GitLessonsBundle.message("git.commit.amend.commit", strong(amendButtonText)))
      text(GitLessonsBundle.message("git.commit.amend.commit.balloon"),
           LearningBalloonConfig(Balloon.Position.above, width = 0))
      triggerOnNotification { it.displayId == VcsNotificationIdsHolder.COMMIT_FINISHED }
      showWarningIfCommitWindowClosed()
      test {
        ideFrame {
          button { b: JBOptionButton -> UIUtil.getParentOfType(CommitActionsPanel::class.java, b) != null }.click()
        }
      }
    }

    task {
      highlightSubsequentCommitsInGitLog(startCommitRow = 0)
    }

    task {
      text(GitLessonsBundle.message("git.commit.select.top.commit.again", GitBundle.message("git4idea.vcs.name")))
      text(GitLessonsBundle.message("git.commit.select.top.commit.again.balloon"),
           LearningBalloonConfig(Balloon.Position.below, width = 0))
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
      triggerAndBorderHighlight().component { _: VcsLogChangesBrowser -> true }
    }

    task {
      gotItStep(Balloon.Position.atLeft, width = 0,
                GitLessonsBundle.message("git.commit.two.committed.files.explanation"),
                cornerToPointerDistance = 20)
    }

    restoreCommitWindowStateInformer()
  }

  private fun TaskContext.highlightVcsChange(changeFileName: String) {
    triggerAndBorderHighlight().treeItem { _: JTree, path: TreePath ->
      path.pathCount > 2 && path.getPathComponent(2).toString().contains(changeFileName)
    }
  }

  private fun TaskContext.triggerOnTopCommitSelected() {
    triggerUI().component { ui: VcsLogGraphTable ->
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

  override val helpLinks: Map<String, String>
    get() = mapOf(
      Pair(GitLessonsBundle.message("git.commit.help.link"),
           LessonUtil.getHelpLink("commit-and-push-changes.html")),
    )
}