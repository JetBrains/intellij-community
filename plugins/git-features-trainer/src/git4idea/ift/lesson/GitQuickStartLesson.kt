// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.CommonBundle
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.dvcs.ui.SelectChildTextFieldWithBrowseButton
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_FINISHED
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.EngravedLabel
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.VcsCloneDialogExtensionList
import git4idea.GitNotificationIdsHolder.Companion.BRANCH_OPERATION_SUCCESS
import git4idea.i18n.GitBundle
import git4idea.ift.GitLessonsBundle
import git4idea.ift.GitLessonsUtil.gotItStep
import git4idea.ift.GitLessonsUtil.showWarningIfCommitWindowClosed
import git4idea.ift.GitLessonsUtil.triggerOnNotification
import git4idea.ift.GitProjectUtil
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchPopupActions
import training.dsl.*
import training.dsl.LessonUtil.adjustPopupPosition
import training.dsl.LessonUtil.sampleRestoreNotification
import training.ui.LearningUiHighlightingManager
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.tree.TreePath

class GitQuickStartLesson : GitLesson("Git.QuickStart", GitLessonsBundle.message("git.quick.start.lesson.name")) {
  override val existedFile = "git/puss_in_boots.yml"
  private val fileToChange = existedFile.substringAfterLast('/')
  private val textToHighlight = "green"

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    val cloneActionText = GitBundle.message("action.Git.Clone.text")
    lateinit var findActionTaskId: TaskContext.TaskId
    task("GotoAction") {
      findActionTaskId = taskId
      text(GitLessonsBundle.message("git.quick.start.find.action",
                                    strong(StringUtil.removeEllipsisSuffix(cloneActionText)), action(it), LessonUtil.actionName(it)))
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: ExtendableTextField ->
        UIUtil.getParentOfType(SearchEverywhereUI::class.java, ui) != null
      }
    }

    task("Git.Clone") {
      text(GitLessonsBundle.message("git.quick.start.type.clone", code(StringUtil.removeEllipsisSuffix(cloneActionText))))
      triggerByListItemAndHighlight { item ->
        item.toString().contains(cloneActionText)
      }
      triggerStart(it)
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    task {
      triggerByUiComponentAndHighlight(false, false) { _: SelectChildTextFieldWithBrowseButton -> true }
    }

    task {
      before {
        adjustPopupPosition("") // Clone dialog is not saving the state now
      }
      gotItStep(Balloon.Position.below, 300, GitLessonsBundle.message("git.quick.start.clone.dialog.got.it.1"))
      restoreByUi(findActionTaskId)
    }

    task {
      triggerByPartOfComponent(false) { ui: VcsCloneDialogExtensionList ->
        if (ui.model.size > 3) {
          val rect = ui.getCellBounds(2, 2)
          Rectangle(rect.x, rect.y + rect.height / 2, rect.width, rect.height)  // middle of the third and fourth item
        }
        else ui.visibleRect
      }
    }

    task {
      gotItStep(Balloon.Position.atRight, 400, GitLessonsBundle.message("git.quick.start.clone.dialog.got.it.2"))
      restoreByUi(findActionTaskId)
    }

    val cancelButtonText = CommonBundle.getCancelButtonText()
    task {
      triggerByUiComponentAndHighlight { ui: JButton ->
        ui.text?.contains(cancelButtonText) == true
      }
    }

    task {
      text(GitLessonsBundle.message("git.quick.start.close.clone.dialog", strong(cancelButtonText)),
           LearningBalloonConfig(Balloon.Position.above, 400, true))
      stateCheck { previous.ui?.isShowing != true }
    }

    prepareRuntimeTask {
      (editor as? EditorEx)?.let { editor ->
        EditorModificationUtil.setReadOnlyHint(editor, null)  // remove hint about project modification
        editor.isViewer = false
      }
    }

    task {
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: TextPanel.WithIconAndArrows -> ui.text == "main" }
    }

    lateinit var showBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      showBranchesTaskId = taskId
      text(GitLessonsBundle.message("git.quick.start.open.branches", action(it)))
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.balloon"), LearningBalloonConfig(Balloon.Position.above, 200))
      triggerByUiComponentAndHighlight(false, false) { ui: EngravedLabel ->
        val repository = GitRepositoryManager.getInstance(project).repositories.first()
        val branchesInRepoText = DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", GitBundle.message("git4idea.vcs.name"),
                                                    DvcsUtil.getShortRepositoryName(repository))
        ui.text?.contains(branchesInRepoText) == true
      }
    }

    val newBranchActionText = DvcsBundle.message("new.branch.action.text")
    task {
      triggerByListItemAndHighlight { item ->
        item.toString().contains(newBranchActionText)
      }
    }

    task {
      text(GitLessonsBundle.message("git.quick.start.choose.new.branch.item", strong(newBranchActionText)))
      triggerStart(GitBranchPopupActions.GitNewBranchAction::class.java.name)
      restoreByUi(showBranchesTaskId)
    }

    val createButtonText = GitBundle.message("new.branch.dialog.operation.create.name")
    task {
      triggerByUiComponentAndHighlight { ui: JButton ->
        ui.text?.contains(createButtonText) == true
      }
    }

    task {
      text(GitLessonsBundle.message("git.quick.start.name.new.branch", LessonUtil.rawEnter(), strong(createButtonText)))
      triggerOnNotification { it.displayId == BRANCH_OPERATION_SUCCESS }
      restoreByUi(showBranchesTaskId, delayMillis = defaultRestoreDelay)
    }

    prepareRuntimeTask {
      VcsConfiguration.getInstance(project).apply {
        myLastCommitMessages = mutableListOf()
      }
    }

    caret(textToHighlight, select = true)

    task {
      var startOffset = -1
      var line = -1
      before {
        startOffset = editor.caretModel.offset
        editor.caretModel.moveToOffset(startOffset + textToHighlight.length)
        line = editor.document.getLineNumber(startOffset)
      }
      text(GitLessonsBundle.message("git.quick.start.modify.file", code("green")))
      triggerByPartOfComponent l@{ ui: EditorComponentImpl ->
        val endOffset = ui.editor.caretModel.offset
        if (endOffset < startOffset || ui.editor.document.getLineNumber(endOffset) != line) return@l null
        val startPoint = ui.editor.offsetToXY(startOffset)
        val endPoint = ui.editor.offsetToXY(endOffset)
        Rectangle(startPoint.x - 3, startPoint.y, endPoint.x - startPoint.x + 6, ui.editor.lineHeight)
      }
      stateCheck {
        val lineEndOffset = editor.document.getLineEndOffset(line)
        val color = editor.document.charsSequence.subSequence(startOffset, lineEndOffset).removeSuffix("]").trim()
        !textToHighlight.startsWith(color) && color.length >= 3
      }
      proposeRestore {
        val caretOffset = editor.caretModel.offset
        if (editor.document.getLineNumber(caretOffset) != line) {
          sampleRestoreNotification(TaskContext.CaretRestoreProposal, previous.sample)
        }
        else null
      }
    }

    task("CheckinProject") {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(GitLessonsBundle.message("git.quick.start.open.commit.window", action(it), VcsBundle.message("commit.dialog.configurable")))
      stateCheck {
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT)?.isVisible == true
      }
      proposeRestore {
        if (ChangeListManager.getInstance(project).allChanges.isEmpty()) {
          sampleRestoreNotification(TaskContext.ModificationRestoreProposal, previous.sample)
        }
        else null
      }
    }

    task {
      triggerByFoundPathAndHighlight { _: JTree, path: TreePath ->
        path.getPathComponent(path.pathCount - 1).toString().contains(fileToChange)
      }
    }

    task {
      gotItStep(Balloon.Position.atRight, 400, GitLessonsBundle.message("git.quick.start.commit.window.got.it"))
    }

    task {
      val commitButtonText = GitBundle.message("commit.action.name").dropMnemonic()
      text(GitLessonsBundle.message("git.quick.start.perform.commit", strong(commitButtonText)))
      triggerByUiComponentAndHighlight(highlightInside = false) { _: CommitMessage -> true }
      triggerOnNotification { it.displayId == COMMIT_FINISHED }
      showWarningIfCommitWindowClosed()
    }

    val pushButtonText = DvcsBundle.message("action.push").dropMnemonic()
    task("Vcs.Push") {
      text(GitLessonsBundle.message("git.quick.start.open.push.dialog", action(it)))
      triggerByUiComponentAndHighlight { ui: JBOptionButton ->
        ui.text?.contains(pushButtonText) == true
      }
    }

    task {
      text(GitLessonsBundle.message("git.quick.start.perform.push", strong(pushButtonText)))
      triggerOnNotification { notification ->
        notification.groupId == "Vcs Notifications" && notification.type == NotificationType.INFORMATION
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }
  }

  override fun prepare(project: Project) {
    super.prepare(project)
    GitProjectUtil.createRemoteProject("origin", project)
  }
}