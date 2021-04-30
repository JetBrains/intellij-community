// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.git

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.table.JBTable
import com.intellij.util.DocumentUtil
import com.intellij.vcs.commit.*
import com.intellij.vcs.log.ui.frame.VcsLogChangesBrowser
import training.dsl.*
import training.learn.lesson.general.git.GitLessonsUtil.checkoutBranch
import training.learn.lesson.general.git.GitLessonsUtil.highlightSubsequentCommitsInGitLog
import training.learn.lesson.general.git.GitLessonsUtil.moveLearnToolWindowRight
import training.learn.lesson.general.git.GitLessonsUtil.proceedLink
import training.learn.lesson.general.git.GitLessonsUtil.resetGitLogWindow
import training.ui.LearningUiHighlightingManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.tree.TreePath

class GitCommitLesson : GitLesson("Git.Commit", "Commit") {
  override val existedFile = "src/git/simple_cat.yml"
  private val branchName = "feature"
  private val firstFileName = "simple_cat.yml"
  private val secondFileName = "puss_in_boots.yml"

  override val lessonContent: LessonContext.() -> Unit = {
    checkoutBranch(branchName)

    prepareRuntimeTask {
      modifyFiles()
    }

    task {
      text("Suppose you modified some files in the project and want to commit them to the current branch. Press ${
        action("CheckinProject")
      } to open commit tool window.")
      stateCheck {
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT)?.isVisible == true
      }
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

    moveLearnToolWindowRight()

    highlightVcsChange(secondFileName)

    task {
      text("Commit tool window provides wide customization for your commit. First, let's choose the files that we want to commit.")
      text("Deselect this file to exclude it from commit.", LearningBalloonConfig(Balloon.Position.atRight, 300))
      triggerOnOneChangeIncluded(firstFileName)
    }

    task {
      text("Second, let's configure before commit actions. Press ${icon(AllIcons.General.Gear)} to open options popup.")
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: ActionButton ->
        ActionManager.getInstance().getId(ui.action) == "ChangesView.ShowCommitOptions"
      }
      triggerByUiComponentAndHighlight(false, false) { _: CommitOptionsPanel -> true }
    }

    val reformatCodeButtonText = VcsBundle.message("checkbox.checkin.options.reformat.code").dropMnemonic()
    task {
      triggerByUiComponentAndHighlight { ui: JBCheckBox ->
        if (ui.text == reformatCodeButtonText) {
          ui.isSelected = false
          true
        }
        else false
      }
    }

    task {
      text("${strong("Analyze code")} is very useful when you are working with the code. It will notify if warnings and errors will be found in the committing files. In our case you can stay this option the same.")
      text("Switch on ${strong("Reformat code")} to automatically edit files according to codestyle.")
      triggerByUiComponentAndHighlight(false, false) { ui: JBCheckBox ->
        ui.text == reformatCodeButtonText && ui.isSelected
      }
    }

    task {
      text("When required options selected you can close the commit options popup. Press ${LessonUtil.rawKeyStroke(KeyEvent.VK_ESCAPE)}.")
      stateCheck {
        previous.ui?.isShowing != true
      }
    }

    task {
      text("And now you can edit commit message or leave it as proposed. Then click ${strong("Commit")} button to perform commit.")
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JBOptionButton ->
        ui.text == "Commit"
      }
      triggerOnCommitPerformed()
    }

    task("ActivateVersionControlToolWindow") {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text("Great! Press ${action(it)} to open Git tool window and see your commit in the tree.")
      stateCheck {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible == true
      }
    }

    resetGitLogWindow()

    task {
      text("Select top commit in the tree to see the information about it.")
      triggerOnTopCommitSelected()
    }

    task {
      text("In the right side of the Git tool window you can see one file changed by last commit.")
      triggerByUiComponentAndHighlight(highlightInside = false, usePulsation = true) { _: VcsLogChangesBrowser -> true }
      proceedLink()
    }

    task {
      text("What to do if we forgot to add some changes to the last performed commit? The best way is to use ${
        strong("Amend")
      } feature to edit last commit. Press ${
        LessonUtil.rawKeyStroke(
          KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK))
      } or select ${strong("Amend")} checkbox in the commit tool window.")
      val amendCheckboxText = VcsBundle.message("checkbox.amend").dropMnemonic()
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JBCheckBox -> ui.text == amendCheckboxText }
      triggerByUiComponentAndHighlight(false, false) { ui: JBCheckBox ->
        ui.text == amendCheckboxText && ui.isSelected
      }
    }

    highlightVcsChange(secondFileName)

    task {
      text("Select highlighted file to add it to commit.")
      triggerOnOneChangeIncluded(secondFileName)
    }

    task {
      text("Press ${strong("Amend Commit")} button to edit last commit.")
      triggerByUiComponentAndHighlight { ui: JBOptionButton ->
        ui.text == "Amend Commit"
      }
      triggerOnCommitPerformed()
    }

    task {
      text("Select top commit in the Git tool window again to see the information about amended commit.")
      triggerOnTopCommitSelected()
    }

    text("Now you can see that our commit contain two changed files.")
  }

  private fun LessonContext.highlightVcsChange(changeFileName: String) {
    task {
      triggerByFoundPathAndHighlight { _: JTree, path: TreePath ->
        path.pathCount > 2 && path.getPathComponent(2).toString().contains(changeFileName)
      }
    }
  }

  private fun TaskContext.triggerOnOneChangeIncluded(changeFileName: String) {
    stateCheck l@{
      val ui = previous.ui as? ChangesListView ?: return@l false
      val includedChanges = ui.includedSet
      if (includedChanges.size != 1) return@l false
      val change = includedChanges.first() as? ChangeListChange ?: return@l false
      change.virtualFile?.name == changeFileName
    }
  }

  private fun TaskContext.triggerOnTopCommitSelected() {
    highlightSubsequentCommitsInGitLog(0)
    triggerByUiComponentAndHighlight(false, false) { ui: JBTable ->
      ui.isCellSelected(0, 1)
    }
  }

  private fun TaskContext.triggerOnCommitPerformed() {
    addFutureStep {
      val commitWorkflowHandler: NonModalCommitWorkflowHandler<*, *> = ChangesViewManager.getInstanceEx(project).commitWorkflowHandler
                                                                       ?: error("Changes view not initialized")
      commitWorkflowHandler.workflow.addListener(object : CommitWorkflowListener {
        override fun vcsesChanged() {}

        override fun executionStarted() {}

        override fun executionEnded() {
          completeStep()
        }

        override fun beforeCommitChecksStarted() {}

        override fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult) {}
      }, taskDisposable)
    }
  }

  private fun TaskRuntimeContext.modifyFiles() = invokeLater {
    DocumentUtil.writeInRunUndoTransparentAction {
      val projectRoot = ProjectRootManager.getInstance(project).contentRoots.first()
      appendToFile(projectRoot, firstFileName, """
    - play:
        condition: boring
        actions: [ run after favourite plush mouse ]""")

      appendToFile(projectRoot, secondFileName, """
    - play:
        condition: boring
        actions: [ run after mice or own tail ]""")
    }
  }

  private fun appendToFile(projectRoot: VirtualFile, fileName: String, text: String) {
    val file = VfsUtil.collectChildrenRecursively(projectRoot).find { it.name == fileName }
               ?: error("Failed to find ${fileName} in project root: ${projectRoot}")
    file.refresh(false, false)
    val document = FileDocumentManager.getInstance().getDocument(file)!! // it's not directory or binary file and it isn't large
    document.insertString(document.textLength, text)
  }
}