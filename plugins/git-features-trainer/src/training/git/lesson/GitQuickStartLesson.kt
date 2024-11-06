// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git.lesson

import com.intellij.CommonBundle
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.COMMIT_FINISHED
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.VcsCloneDialogExtensionList
import com.intellij.vcs.commit.CommitNotification
import git4idea.actions.branch.GitNewBranchAction
import git4idea.i18n.GitBundle
import training.dsl.*
import training.dsl.LessonUtil.adjustPopupPosition
import training.dsl.LessonUtil.sampleRestoreNotification
import training.git.GitLessonsBundle
import training.git.GitLessonsUtil.clickChangeElement
import training.git.GitLessonsUtil.clickTreeRow
import training.git.GitLessonsUtil.highlightToolWindowStripe
import training.git.GitLessonsUtil.openCommitWindow
import training.git.GitLessonsUtil.restoreByUiAndBackgroundTask
import training.git.GitLessonsUtil.restoreCommitWindowStateInformer
import training.git.GitLessonsUtil.showWarningIfCommitWindowClosed
import training.git.GitLessonsUtil.showWarningIfModalCommitEnabled
import training.git.GitLessonsUtil.showWarningIfStagingAreaEnabled
import training.git.GitLessonsUtil.triggerOnChangeCheckboxShown
import training.git.GitLessonsUtil.triggerOnCheckout
import training.git.GitLessonsUtil.triggerOnNotification
import training.git.GitLessonsUtil.triggerOnOneChangeIncluded
import training.git.GitProjectUtil
import training.ui.LearningUiHighlightingManager
import training.util.LessonEndInfo
import training.util.toNullableString
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.tree.TreePath

class GitQuickStartLesson : GitLesson("Git.QuickStart", GitLessonsBundle.message("git.quick.start.lesson.name")) {
  override val sampleFilePath = "git/puss_in_boots.yml"
  override val branchName = "main"
  private val fileToChange = sampleFilePath.substringAfterLast('/')
  private val textToHighlight = "green"

  private var backupSearchEverywhereLocation: Point? = null

  override val testScriptProperties = TaskTestContext.TestScriptProperties(duration = 40)

  override val lessonContent: LessonContext.() -> Unit = {
    val cloneActionText = GitBundle.message("action.Git.Clone.text")

    showWarningIfModalCommitEnabled()
    showWarningIfStagingAreaEnabled()

    task {
      text(GitLessonsBundle.message("git.quick.start.introduction"))
      proceedLink()
    }

    lateinit var findActionTaskId: TaskContext.TaskId
    task("SearchEverywhere") {
      findActionTaskId = taskId
      text(GitLessonsBundle.message("git.quick.start.find.action", strong(StringUtil.removeEllipsisSuffix(cloneActionText)),
                                    LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT), LessonUtil.actionName(it)))
      triggerAndBorderHighlight().component { ui: ExtendableTextField ->
        UIUtil.getParentOfType(SearchEverywhereUI::class.java, ui) != null
      }
      test { actions(it) }
    }

    task("Git.Clone") {
      before {
        if (backupSearchEverywhereLocation == null) {
          backupSearchEverywhereLocation = adjustPopupPosition(SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY)
        }
      }
      text(GitLessonsBundle.message("git.quick.start.type.clone", code(StringUtil.removeEllipsisSuffix(cloneActionText))))
      triggerAndBorderHighlight().listItem { item ->
        item.toNullableString()?.contains(cloneActionText) == true
      }
      triggerStart(it)
      restoreByUi(delayMillis = defaultRestoreDelay)
      test {
        waitComponent(SearchEverywhereUI::class.java)
        type("clone")
        waitAndUsePreviouslyFoundListItem { jListItemFixture ->  jListItemFixture.click() }
      }
    }

    task {
      triggerUI().componentPart { ui: TextFieldWithBrowseButton ->
        val rect = ui.visibleRect
        Rectangle(rect.x, rect.y, 59, rect.height)
      }
    }

    task {
      before {
        adjustPopupPosition("") // Clone dialog is not saving the state now
      }
      gotItStep(Balloon.Position.below, 340, GitLessonsBundle.message("git.quick.start.clone.dialog.got.it.1"),
                cornerToPointerDistance = 50)
      restoreByUi(findActionTaskId)
    }

    task {
      triggerUI().componentPart { ui: VcsCloneDialogExtensionList ->
        val size = ui.model.size
        val rect = ui.getCellBounds(size - 1, size - 1)
        Rectangle(rect.x, rect.y, 96, rect.height)
      }
    }

    task {
      gotItStep(Balloon.Position.below, 300, GitLessonsBundle.message("git.quick.start.clone.dialog.got.it.2"))
      restoreByUi(findActionTaskId)
    }

    val cancelButtonText = CommonBundle.getCancelButtonText()
    task {
      triggerAndFullHighlight().component { ui: JButton ->
        ui.text?.contains(cancelButtonText) == true
      }
    }

    task {
      text(GitLessonsBundle.message("git.quick.start.close.clone.dialog", strong(cancelButtonText)),
           LearningBalloonConfig(Balloon.Position.above, 300, true))
      stateCheck { previous.ui?.isShowing != true }
      test(waitEditorToBeReady = false) {
        ideFrame { button(cancelButtonText).click() }
      }
    }

    prepareRuntimeTask {
      (editor as? EditorEx)?.let { editor ->
        EditorModificationUtil.setReadOnlyHint(editor, null)  // remove hint about project modification
        editor.isViewer = false
      }
    }

    task {
      triggerAndBorderHighlight().component { ui: ToolbarComboButton ->
        ui.text == branchName
      }
    }

    lateinit var showBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      showBranchesTaskId = taskId
      val vcsWidgetName = GitBundle.message("action.main.toolbar.git.Branches.text")
      text(GitLessonsBundle.message("git.quick.start.workflow.explanation"))
      text(GitLessonsBundle.message("git.quick.start.open.vcs.widget", action(it), strong(vcsWidgetName)))
      text(GitLessonsBundle.message("git.click.to.open", strong(vcsWidgetName)),
           LearningBalloonConfig(Balloon.Position.below, width = 0))
      triggerAndBorderHighlight().treeItem { _, path ->
        val action = (path.lastPathComponent as? PopupFactoryImpl.ActionItem)?.action
        action is GitNewBranchAction
      }
      test {
        val widget = previous.ui ?: error("Not found VCS widget")
        ideFrame { jComponent(widget).click() }
      }
    }

    val createButtonText = GitBundle.message("new.branch.dialog.operation.create.name")
    task {
      val newBranchActionText = DvcsBundle.message("new.branch.action.text.with.ellipsis")
      text(GitLessonsBundle.message("git.quick.start.choose.new.branch.item", strong(newBranchActionText)))
      triggerAndBorderHighlight().component { ui: JButton ->
        ui.text?.contains(createButtonText) == true
      }
      restoreByUi(showBranchesTaskId, delayMillis = defaultRestoreDelay)
      test {
        clickTreeRow { item -> (item as? PopupFactoryImpl.ActionItem)?.action is GitNewBranchAction }
      }
    }

    task {
      text(GitLessonsBundle.message("git.quick.start.name.new.branch", LessonUtil.rawEnter(), strong(createButtonText)))
      triggerOnCheckout()
      restoreByUiAndBackgroundTask(GitBundle.message("branch.checking.out.branch.from.process", "\\w+", "HEAD"),
                                   delayMillis = 2 * defaultRestoreDelay, showBranchesTaskId)
      test(waitEditorToBeReady = false) {
        type("newBranch")
        ideFrame { button(createButtonText).click() }
      }
    }

    prepareRuntimeTask {
      VcsConfiguration.getInstance(project).apply {
        setRecentMessages(emptyList())
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
      triggerAndBorderHighlight().componentPart l@{ ui: EditorComponentImpl ->
        if (ui.editor != editor) return@l null
        val endOffset = ui.editor.caretModel.offset
        if (endOffset < startOffset || ui.editor.document.getLineNumber(endOffset) != line) return@l null
        val startPoint = ui.editor.offsetToXY(startOffset)
        val endPoint = ui.editor.offsetToXY(endOffset)
        Rectangle(startPoint.x - 3, startPoint.y, endPoint.x - startPoint.x + 6, ui.editor.lineHeight)
      }
      stateCheck {
        val lineEndOffset = editor.document.getLineEndOffset(line)
        val color = editor.document.charsSequence.subSequence(startOffset, lineEndOffset).removeSuffix("]").trim()
        !textToHighlight.startsWith(color) && color.length >= 3 && !ChangeListManager.getInstance(project).allChanges.isEmpty()
      }
      proposeRestore {
        val caretOffset = editor.caretModel.offset
        if (editor.document.getLineNumber(caretOffset) != line) {
          sampleRestoreNotification(TaskContext.CaretRestoreProposal, previous.sample)
        }
        else null
      }
      test {
        for (ch in "yellow") type(ch.toString())
      }
    }

    highlightToolWindowStripe(ToolWindowId.COMMIT)

    task("CheckinProject") {
      openCommitWindow(GitLessonsBundle.message("git.quick.start.open.commit.window"))
      proposeRestore {
        if (ChangeListManager.getInstance(project).allChanges.isEmpty()) {
          sampleRestoreNotification(TaskContext.ModificationRestoreProposal, previous.sample)
        }
        else null
      }
      test {
        val stripe = previous.ui ?: error("Not found Commit stripe button")
        ideFrame { jComponent(stripe).click() }
      }
    }

    task {
      triggerOnChangeCheckboxShown(fileToChange)
    }

    task {
      text(GitLessonsBundle.message("git.quick.start.commit.window.select.file"),
           LearningBalloonConfig(Balloon.Position.below, width = 0, duplicateMessage = true))
      triggerAndBorderHighlight().treeItem { _: JTree, path: TreePath ->
        path.getPathComponent(path.pathCount - 1).toString().contains(fileToChange)
      }
      triggerOnOneChangeIncluded(fileToChange)
      showWarningIfCommitWindowClosed()
      test {
        clickChangeElement(fileToChange)
      }
    }

    task {
      before { LearningUiHighlightingManager.clearHighlights() }
      val commitButtonText = GitBundle.message("commit.action.name").dropMnemonic()
      text(GitLessonsBundle.message("git.quick.start.perform.commit", code("Edit eyes color of puss in boots"), strong(commitButtonText)))
      triggerAndBorderHighlight().component { ui: CommitMessage ->
        ui.focus()
        true
      }
      triggerOnNotification { it is CommitNotification }
      showWarningIfCommitWindowClosed()
      test {
        type("Edit eyes color of puss in boots")
        ideFrame {
          button { b: JBOptionButton -> b.text == commitButtonText }.click()
        }
      }
    }

    val pushButtonText = DvcsBundle.message("action.complex.push").dropMnemonic()
    task {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(GitLessonsBundle.message("git.quick.start.open.push.dialog", action("Vcs.Push"),
                                    strong(DvcsBundle.message("action.push").dropMnemonic())))
      triggerAndBorderHighlight().component { ui: JBOptionButton ->
        ui.text?.contains(pushButtonText) == true
      }
      test { actions("Vcs.Push") }
    }

    task {
      text(GitLessonsBundle.message("git.quick.start.perform.push", strong(pushButtonText)))
      text(GitLessonsBundle.message("git.click.balloon", strong(pushButtonText)),
           LearningBalloonConfig(Balloon.Position.above, width = 0))
      triggerOnNotification { notification ->
        notification.groupId == "Vcs Notifications" && notification.type == NotificationType.INFORMATION
      }
      restoreByUiAndBackgroundTask(DvcsBundle.message("push.process.pushing"), delayMillis = defaultRestoreDelay)
      test(waitEditorToBeReady = false) {
        ideFrame {
          val pushAnywayText = DvcsBundle.message("action.push.anyway").dropMnemonic()
          button { b: JBOptionButton -> b.text == pushButtonText || b.text == pushAnywayText }.click()
        }
      }
    }

    restoreCommitWindowStateInformer()
  }

  override fun prepare(project: Project) {
    super.prepare(project)
    GitProjectUtil.createRemoteProject("origin", project)
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    LessonUtil.restorePopupPosition(project, SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY, backupSearchEverywhereLocation)
    backupSearchEverywhereLocation = null
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(GitLessonsBundle.message("git.quick.start.help.link"),
         LessonUtil.getHelpLink("set-up-a-git-repository.html")),
  )
}