// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git.lesson

import com.intellij.CommonBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDifferentiatedDialog
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesAction
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.UnshelveWithDialogAction
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.components.DropDownLink
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import training.dsl.*
import training.dsl.LessonUtil.adjustPopupPosition
import training.dsl.LessonUtil.restorePopupPosition
import training.git.GitLessonsBundle
import training.git.GitLessonsUtil.clickTreeRow
import training.git.GitLessonsUtil.highlightToolWindowStripe
import training.git.GitLessonsUtil.openCommitWindow
import training.git.GitLessonsUtil.restoreByUiAndBackgroundTask
import training.git.GitLessonsUtil.restoreCommitWindowStateInformer
import training.git.GitLessonsUtil.showWarningIfCommitWindowClosed
import training.git.GitLessonsUtil.showWarningIfModalCommitEnabled
import training.git.GitLessonsUtil.showWarningIfStagingAreaEnabled
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.LessonEndInfo
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JButton

class GitChangelistsAndShelveLesson : GitLesson("Git.ChangelistsAndShelf", GitLessonsBundle.message("git.changelists.shelf.lesson.name")) {
  override val sampleFilePath = "git/martian_cat.yml"
  override val branchName = "main"
  private val commentingLineText = "fur_type: long haired"
  private val commentText = "# debug: check another types (short haired, hairless)"

  private var backupShelveDialogLocation: Point? = null
  private var backupUnshelveDialogLocation: Point? = null

  private val fileAddition = """
    |
    |    - eat:
    |        condition: hungry
    |        actions: [ fry self-grown potatoes ]""".trimMargin()

  override val testScriptProperties = TaskTestContext.TestScriptProperties(duration = 40)

  override val lessonContent: LessonContext.() -> Unit = {
    val defaultChangelistName = VcsBundle.message("changes.default.changelist.name")
    prepareRuntimeTask(ModalityState.nonModal()) {
      resetChangelistsState(project)
      removeShelvedChangeLists(project)
      modifyFile(virtualFile)
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    showWarningIfModalCommitEnabled()
    showWarningIfStagingAreaEnabled()

    lateinit var highlightLineMarkerTaskId: TaskContext.TaskId
    task {
      highlightLineMarkerTaskId = taskId
      triggerAndBorderHighlight().componentPart l@{ ui: EditorGutterComponentEx ->
        if (ui.editor != editor) return@l null
        ui.getLineMarkerRect(commentText)
      }
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.introduction"))
      text(GitLessonsBundle.message("git.changelists.shelf.click.line.marker.balloon"),
           LearningBalloonConfig(Balloon.Position.below, 0))
      triggerAndBorderHighlight().component { ui: DropDownLink<*> ->
        ui.text?.contains(defaultChangelistName) == true
      }
      test {
        ideFrame {
          val gutter = findComponentWithTimeout(defaultTimeout) { ui: EditorGutterComponentEx ->
            ui.editor == editor
          }
          val rect = gutter.getLineMarkerRect(commentText) ?: error("Failed to find '$commentText' in the editor")
          robot.click(gutter, Point(rect.centerX.toInt(), rect.centerY.toInt()))
        }
      }
    }

    task {
      val newChangelistText = VcsBundle.message("ex.new.changelist")
      text(GitLessonsBundle.message("git.changelists.shelf.choose.new.changelist",
                                    strong(defaultChangelistName), strong(newChangelistText)))
      triggerUI().component { ui: EditorComponentImpl ->
        ui.text.contains(defaultChangelistName)
      }
      restoreByUi(highlightLineMarkerTaskId)
      test {
        ideFrame {
          button(defaultChangelistName).click()
          jList(newChangelistText).clickItem(newChangelistText)
        }
      }
    }

    var newChangeListName = "Comments"
    task {
      text(GitLessonsBundle.message("git.changelists.shelf.create.changelist", code("Comments"),
                                    LessonUtil.rawEnter(), strong(CommonBundle.getOkButtonText())))
      stateCheck {
        val changeListManager = ChangeListManager.getInstance(project)
        if (changeListManager.changeListsNumber == 2) {
          newChangeListName = changeListManager.changeLists.find { it.name != defaultChangelistName }!!.name
          true
        }
        else false
      }
      restoreState(highlightLineMarkerTaskId) {
        (previous.ui?.isShowing != true).also {
          if (it) HintManager.getInstance().hideAllHints()
        }
      }
      test(waitEditorToBeReady = false) {
        type(newChangeListName)
        invokeActionViaShortcut("ENTER")
      }
    }

    prepareRuntimeTask {
      HintManager.getInstance().hideAllHints()  // to close the context menu of line marker
    }

    highlightToolWindowStripe(ToolWindowId.COMMIT)

    task("CheckinProject") {
      openCommitWindow(GitLessonsBundle.message("git.changelists.shelf.open.commit.window"))
      test {
        val stripe = previous.ui ?: error("Not found Commit stripe button")
        ideFrame { jComponent(stripe).click() }
      }
    }

    task {
      triggerUI().component { ui: ChangesListView ->
        ui.expandAll()
        true
      }
    }

    val shelfText = VcsBundle.message("shelf.tab")
    task {
      text(GitLessonsBundle.message("git.changelists.shelf.explanation", strong(shelfText)))
      proceedLink()
      showWarningIfCommitWindowClosed()
    }

    lateinit var letsShelveTaskId: TaskContext.TaskId
    task {
      letsShelveTaskId = taskId
      triggerAndBorderHighlight().treeItem { _, path ->
        path.getPathComponent(path.pathCount - 1).toString().contains(newChangeListName)
      }
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.open.context.menu"))
      text(GitLessonsBundle.message("git.changelists.shelf.click.changelist.tooltip", strong(newChangeListName)),
           LearningBalloonConfig(Balloon.Position.above, 250))
      triggerAndBorderHighlight().component { ui: ActionMenuItem ->
        ui.anAction is ShelveChangesAction
      }
      showWarningIfCommitWindowClosed(restoreTaskWhenResolved = true)
      test {
        ideFrame {
          clickTreeRow(rightClick = true) { item -> item.toString() == newChangeListName }
        }
      }
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.open.shelf.dialog",
                                    strong(ActionsBundle.message("action.ChangesView.Shelve.text")), strong(shelfText)))
      triggerStart("ChangesView.Shelve")
      restoreByUi(letsShelveTaskId, delayMillis = defaultRestoreDelay)
      test {
        ideFrame {
          jMenuItem { item: ActionMenuItem -> item.anAction is ShelveChangesAction }.click()
        }
      }
    }

    val shelveChangesButtonText = VcsBundle.message("shelve.changes.action").dropMnemonic()
    task {
      triggerAndBorderHighlight().component { ui: JButton ->
        ui.text?.contains(shelveChangesButtonText) == true
      }
    }

    task {
      before {
        if (backupShelveDialogLocation == null) {
          backupShelveDialogLocation = adjustPopupPosition(CommitChangeListDialog.DIMENSION_SERVICE_KEY)
        }
      }
      text(GitLessonsBundle.message("git.changelists.shelf.shelve.changelist", strong(shelveChangesButtonText), strong(shelfText)))
      stateCheck {
        ShelveChangesManager.getInstance(project).allLists.size == 1
      }
      restoreByUi(letsShelveTaskId)
      test {
        ideFrame { button(shelveChangesButtonText).click() }
      }
    }

    val removeButtonText = VcsBundle.message("button.remove")
    task {
      triggerAndBorderHighlight().component { ui: JButton ->
        ui.text?.contains(removeButtonText) == true
      }
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.remove.changelist", strong(removeButtonText)))
      stateCheck { previous.ui?.isShowing != true }
      test(waitEditorToBeReady = false) {
        ideFrame { button(removeButtonText).click() }
      }
    }

    task {
      triggerAndBorderHighlight().treeItem { _, path -> path.pathCount == 2 }
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.performed.explanation", strong(shelfText)))
      gotItStep(Balloon.Position.below, width = 0,
                GitLessonsBundle.message("git.changelists.shelf.performed.got.it", strong(shelfText)),
                duplicateMessage = false)
    }

    val unshelveChangesButtonText = VcsBundle.message("unshelve.changes.action")
    task("ShelveChanges.UnshelveWithDialog") {
      text(GitLessonsBundle.message("git.changelists.shelf.open.unshelve.dialog", strong(shelfText), action(it)))
      triggerAndBorderHighlight().component { ui: JButton ->
        ui.text?.contains(unshelveChangesButtonText) == true
      }
      showWarningIfCommitWindowClosed()
      test {
        ideFrame {
          clickTreeRow(rightClick = true) { item -> item.toString() == newChangeListName }
          jMenuItem { item: ActionMenuItem -> item.anAction is UnshelveWithDialogAction }.click()
        }
      }
    }

    task {
      before {
        if (backupUnshelveDialogLocation == null) {
          backupUnshelveDialogLocation = adjustPopupPosition(ApplyPatchDifferentiatedDialog.DIMENSION_SERVICE_KEY)
        }
      }
      text(GitLessonsBundle.message("git.changelists.shelf.unshelve.changelist", strong(unshelveChangesButtonText)))
      stateCheck { editor.document.text.contains(commentText) }
      restoreByUiAndBackgroundTask(VcsBundle.message("vcs.unshelving.changes"), delayMillis = defaultRestoreDelay)
      test(waitEditorToBeReady = false) {
        Thread.sleep(500)
        ideFrame { button(unshelveChangesButtonText).click() }
      }
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.congratulations"))
    }

    restoreCommitWindowStateInformer()
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    restorePopupPosition(project, CommitChangeListDialog.DIMENSION_SERVICE_KEY, backupShelveDialogLocation)
    backupShelveDialogLocation = null
    restorePopupPosition(project, ApplyPatchDifferentiatedDialog.DIMENSION_SERVICE_KEY, backupUnshelveDialogLocation)
    backupUnshelveDialogLocation = null
  }

  private fun removeShelvedChangeLists(project: Project) {
    val shelveChangesManager = ShelveChangesManager.getInstance(project)
    shelveChangesManager.allLists.forEach { it.isRecycled = true }
    shelveChangesManager.cleanUnshelved(System.currentTimeMillis())
  }

  private fun resetChangelistsState(project: Project) {
    val changeListManager = ChangeListManager.getInstance(project)
    val defaultChangelist = changeListManager.defaultChangeList
    val defaultChangelistName = VcsBundle.message("changes.default.changelist.name")
    if (defaultChangelist.name != defaultChangelistName) {
      changeListManager.editName(defaultChangelist.name, defaultChangelistName)
    }
    changeListManager.changeLists.filter { it.id != defaultChangelist.id }.forEach(changeListManager::removeChangeList)
  }

  private fun modifyFile(file: VirtualFile) = invokeLater {
    DocumentUtil.writeInRunUndoTransparentAction {
      val document = FileDocumentManager.getInstance().getDocument(file)!! // it's not directory or binary file and it isn't large
      document.insertString(document.textLength, fileAddition)
      val offset = document.charsSequence.indexOf(commentingLineText)
      if (offset == -1) error("Not found '$commentingLineText' item in text")
      document.insertString(offset + commentingLineText.length, "  $commentText")
      FileDocumentManager.getInstance().saveDocument(document)
    }
  }

  private fun EditorGutterComponentEx.getLineMarkerRect(partOfLine: String): Rectangle? {
    val offset = editor.document.charsSequence.indexOf(partOfLine)
    if (offset == -1) {
      thisLogger().warn("Failed to find '${partOfLine}' in the editor text:\n${editor.document.charsSequence}")
      return null
    }
    val y = invokeAndWaitIfNeeded {
      val line = editor.offsetToVisualLine(offset, true)
      editor.visualLineToY(line)
    }
    return Rectangle(this.x + JBUI.scale(5), y, JBUI.scale(8), editor.lineHeight)
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(GitLessonsBundle.message("git.changelists.shelf.help.link"),
         LessonUtil.getHelpLink("work-on-several-features-simultaneously.html")),
  )
}