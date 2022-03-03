// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.CommonBundle
import com.intellij.dvcs.DvcsUtil
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
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.EngravedLabel
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.VcsCloneDialogExtensionList
import git4idea.i18n.GitBundle
import git4idea.ift.GitLessonsBundle
import git4idea.ift.GitLessonsUtil.gotItStep
import git4idea.ift.GitLessonsUtil.openCommitWindowText
import git4idea.ift.GitLessonsUtil.openPushDialogText
import git4idea.ift.GitLessonsUtil.restoreByUiAndBackgroundTask
import git4idea.ift.GitLessonsUtil.restoreCommitWindowStateInformer
import git4idea.ift.GitLessonsUtil.showWarningIfCommitWindowClosed
import git4idea.ift.GitLessonsUtil.showWarningIfModalCommitEnabled
import git4idea.ift.GitLessonsUtil.showWarningIfStagingAreaEnabled
import git4idea.ift.GitLessonsUtil.triggerOnCheckout
import git4idea.ift.GitLessonsUtil.triggerOnNotification
import git4idea.ift.GitProjectUtil
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchPopupActions
import org.assertj.swing.fixture.JListFixture
import training.dsl.*
import training.dsl.LessonUtil.adjustPopupPosition
import training.dsl.LessonUtil.sampleRestoreNotification
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.LessonEndInfo
import training.util.toNullableString
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.util.regex.Pattern
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JTree
import javax.swing.tree.TreePath

class GitQuickStartLesson : GitLesson("Git.QuickStart", GitLessonsBundle.message("git.quick.start.lesson.name")) {
  override val existedFile = "git/puss_in_boots.yml"
  override val branchName = "main"
  private val fileToChange = existedFile.substringAfterLast('/')
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
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: ExtendableTextField ->
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
      triggerByListItemAndHighlight { item ->
        item.toNullableString()?.contains(cloneActionText) == true
      }
      triggerStart(it)
      restoreByUi(delayMillis = defaultRestoreDelay)
      test {
        waitComponent(SearchEverywhereUI::class.java)
        type("clone")
        ideFrame {
          val list = findComponentWithTimeout(defaultTimeout) { ui: JList<*> ->
            val model = ui.model
            (0 until model.size).any { ind -> model.getElementAt(ind).toString().contains(cloneActionText) }
          }
          JListFixture(robot, list).clickItem(Pattern.compile(""".*$cloneActionText.*"""))
        }
      }
    }

    task {
      triggerByPartOfComponent(false, false) { ui: TextFieldWithBrowseButton ->
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
      triggerByPartOfComponent(false) { ui: VcsCloneDialogExtensionList ->
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
      triggerByUiComponentAndHighlight { ui: JButton ->
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
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: TextPanel.WithIconAndArrows -> ui.text == "main" }
    }

    lateinit var showBranchesTaskId: TaskContext.TaskId
    task("Git.Branches") {
      showBranchesTaskId = taskId
      text(GitLessonsBundle.message("git.quick.start.open.branches", action(it)))
      text(GitLessonsBundle.message("git.feature.branch.open.branches.popup.balloon"), LearningBalloonConfig(Balloon.Position.above, 0))
      triggerByUiComponentAndHighlight(false, false) { ui: EngravedLabel ->
        val repository = GitRepositoryManager.getInstance(project).repositories.first()
        val branchesInRepoText = DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", GitBundle.message("git4idea.vcs.name"),
                                                    DvcsUtil.getShortRepositoryName(repository))
        ui.text?.contains(branchesInRepoText) == true
      }
      test { actions(it) }
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
      test {
        ideFrame { jList(newBranchActionText).clickItem(newBranchActionText) }
      }
    }

    val createButtonText = GitBundle.message("new.branch.dialog.operation.create.name")
    task {
      triggerByUiComponentAndHighlight { ui: JButton ->
        ui.text?.contains(createButtonText) == true
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

    task("CheckinProject") {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      openCommitWindowText(GitLessonsBundle.message("git.quick.start.open.commit.window"))
      stateCheck {
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT)?.isVisible == true
      }
      proposeRestore {
        if (ChangeListManager.getInstance(project).allChanges.isEmpty()) {
          sampleRestoreNotification(TaskContext.ModificationRestoreProposal, previous.sample)
        }
        else null
      }
      test { actions(it) }
    }

    task {
      triggerByFoundPathAndHighlight { _: JTree, path: TreePath ->
        path.getPathComponent(path.pathCount - 1).toString().contains(fileToChange)
      }
    }

    task {
      gotItStep(Balloon.Position.atRight, 0, GitLessonsBundle.message("git.quick.start.commit.window.got.it"))
    }

    task {
      before { LearningUiHighlightingManager.clearHighlights() }
      val commitButtonText = GitBundle.message("commit.action.name").dropMnemonic()
      text(GitLessonsBundle.message("git.quick.start.perform.commit", strong(commitButtonText)))
      triggerByUiComponentAndHighlight(highlightInside = false) { _: CommitMessage -> true }
      triggerOnNotification { it.displayId == COMMIT_FINISHED }
      showWarningIfCommitWindowClosed()
      test {
        type("Edit eyes color of puss in boots")
        ideFrame {
          button { b: JBOptionButton -> b.text == commitButtonText }.click()
        }
      }
    }

    val pushButtonText = DvcsBundle.message("action.push").dropMnemonic()
    task {
      openPushDialogText(GitLessonsBundle.message("git.quick.start.open.push.dialog"))
      triggerByUiComponentAndHighlight { ui: JBOptionButton ->
        ui.text?.contains(pushButtonText) == true
      }
      test { actions("Vcs.Push") }
    }

    task {
      text(GitLessonsBundle.message("git.quick.start.perform.push", strong(pushButtonText)))
      text(GitLessonsBundle.message("git.click.balloon", strong(pushButtonText)),
           LearningBalloonConfig(Balloon.Position.above, 0, cornerToPointerDistance = 117))
      triggerOnNotification { notification ->
        notification.groupId == "Vcs Notifications" && notification.type == NotificationType.INFORMATION
      }
      restoreByUiAndBackgroundTask(DvcsBundle.message("push.process.pushing"), delayMillis = defaultRestoreDelay)
      test(waitEditorToBeReady = false) {
        ideFrame {
          button { b: JBOptionButton -> b.text == pushButtonText }.click()
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

  override val suitableTips = listOf("VCS_general")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(GitLessonsBundle.message("git.quick.start.help.link"),
         LessonUtil.getHelpLink("set-up-a-git-repository.html")),
  )
}