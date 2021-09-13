// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.CommonBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
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
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.DropDownLink
import com.intellij.util.DocumentUtil
import git4idea.ift.GitLessonsBundle
import git4idea.ift.GitLessonsUtil.checkoutBranch
import git4idea.ift.GitLessonsUtil.openCommitWindowText
import git4idea.ift.GitLessonsUtil.showWarningIfCommitWindowClosed
import git4idea.ift.GitLessonsUtil.showWarningIfModalCommitEnabled
import training.dsl.*
import training.dsl.LessonUtil.adjustPopupPosition
import training.dsl.LessonUtil.restorePopupPosition
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JButton

class GitChangelistsAndShelveLesson : GitLesson("Git.ChangelistsAndShelf", GitLessonsBundle.message("git.changelists.shelf.lesson.name")) {
  override val existedFile = "git/martian_cat.yml"
  private val branchName = "main"
  private val commentingLineText = "fur_type: long haired"
  private val commentText = "# debug: check another types (short haired, hairless)"

  private var backupShelveDialogLocation: Point? = null
  private var backupUnshelveDialogLocation: Point? = null

  private val fileAddition = """
    |
    |    - eat:
    |        condition: hungry
    |        actions: [ fry self-grown potatoes ]""".trimMargin()

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    checkoutBranch(branchName)

    val defaultChangelistName = VcsBundle.message("changes.default.changelist.name")
    prepareRuntimeTask {
      resetChangelistsState(project)
      removeShelvedChangeLists(project)
      modifyFile(virtualFile)
    }

    showWarningIfModalCommitEnabled()

    task {
      triggerByPartOfComponent(highlightInside = true, usePulsation = true) l@{ ui: EditorGutterComponentEx ->
        if (CommonDataKeys.EDITOR.getData(ui as DataProvider) != editor) return@l null
        val offset = editor.document.charsSequence.indexOf(commentText)
        if (offset == -1) {
          thisLogger().warn("Failed to find '${commentText}' in the editor text:\n${editor.document.charsSequence}")
          return@l null
        }
        val line = editor.offsetToVisualLine(offset, true)
        val y = editor.visualLineToY(line)
        return@l Rectangle(ui.x + ui.width - 15, y, 10, editor.lineHeight)
      }
    }

    lateinit var clickLineMarkerTaskId: TaskContext.TaskId
    task {
      clickLineMarkerTaskId = taskId
      text(GitLessonsBundle.message("git.changelists.shelf.introduction"))
      text(GitLessonsBundle.message("git.changelists.shelf.click.line.marker.balloon"),
           LearningBalloonConfig(Balloon.Position.below, 0))
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: DropDownLink<*> ->
        ui.text?.contains(defaultChangelistName) == true
      }
    }

    task {
      val newChangelistText = VcsBundle.message("ex.new.changelist")
      text(GitLessonsBundle.message("git.changelists.shelf.choose.new.changelist",
                                    strong(defaultChangelistName), strong(newChangelistText)))
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { ui: EditorComponentImpl ->
        ui.text.contains(defaultChangelistName)
      }
      restoreByUi()
    }

    var newChangeListName = "Comments"
    task {
      text(GitLessonsBundle.message("git.changelists.shelf.create.changelist",
                                    LessonUtil.rawEnter(), strong(CommonBundle.getOkButtonText())))
      stateCheck {
        val changeListManager = ChangeListManager.getInstance(project)
        if (changeListManager.changeListsNumber == 2) {
          newChangeListName = changeListManager.changeLists.find { it.name != defaultChangelistName }!!.name
          true
        }
        else false
      }
      restoreState(clickLineMarkerTaskId) {
        (previous.ui?.isShowing != true).also {
          if (it) HintManager.getInstance().hideAllHints()
        }
      }
    }

    prepareRuntimeTask {
      HintManager.getInstance().hideAllHints()  // to close the context menu of line marker
    }

    task("CheckinProject") {
      openCommitWindowText(GitLessonsBundle.message("git.changelists.shelf.open.commit.window"))
      stateCheck {
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT)?.isVisible == true
      }
    }

    task {
      triggerByUiComponentAndHighlight(false, false) { ui: ChangesListView ->
        ui.expandAll()
        true
      }
    }

    val shelfText = VcsBundle.message("shelf.tab")
    task {
      text(GitLessonsBundle.message("git.changelists.shelf.explanation", strong(shelfText)))
      proceedLink()
    }

    task {
      triggerByFoundPathAndHighlight(highlightInside = true) { _, path ->
        path.getPathComponent(path.pathCount - 1).toString().contains(newChangeListName)
      }
    }

    lateinit var letsShelveTaskId: TaskContext.TaskId
    task {
      letsShelveTaskId = taskId
      text(GitLessonsBundle.message("git.changelists.shelf.open.context.menu"))
      text(GitLessonsBundle.message("git.changelists.shelf.click.changelist.tooltip", strong(newChangeListName)),
           LearningBalloonConfig(Balloon.Position.above, 250))
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: ActionMenuItem ->
        ui.anAction is ShelveChangesAction
      }
      showWarningIfCommitWindowClosed()
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.open.shelf.dialog",
                                    strong(ActionsBundle.message("action.ChangesView.Shelve.text")), strong(shelfText)))
      triggerStart("ChangesView.Shelve")
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    val shelveChangesButtonText = VcsBundle.message("shelve.changes.action").dropMnemonic()
    task {
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JButton ->
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
    }

    val removeButtonText = VcsBundle.message("button.remove")
    task {
      triggerByUiComponentAndHighlight { ui: JButton ->
        ui.text?.contains(removeButtonText) == true
      }
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.remove.changelist", strong(removeButtonText)))
      stateCheck { previous.ui?.isShowing != true }
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.performed.explanation", strong(shelfText)))
      triggerByFoundPathAndHighlight(highlightInside = true, usePulsation = true) { _, path ->
        path.pathCount == 2
      }
      proceedLink()
    }

    val unshelveChangesButtonText = VcsBundle.message("unshelve.changes.action")
    task("ShelveChanges.UnshelveWithDialog") {
      text(GitLessonsBundle.message("git.changelists.shelf.open.unshelve.dialog", strong(shelfText), action(it)))
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JButton ->
        ui.text?.contains(unshelveChangesButtonText) == true
      }
      showWarningIfCommitWindowClosed()
    }

    task {
      before {
        if (backupUnshelveDialogLocation == null) {
          backupUnshelveDialogLocation = adjustPopupPosition(ApplyPatchDifferentiatedDialog.DIMENSION_SERVICE_KEY)
        }
      }
      text(GitLessonsBundle.message("git.changelists.shelf.unshelve.changelist", strong(unshelveChangesButtonText)))
      stateCheck { editor.document.text.contains(commentText) }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    task {
      text(GitLessonsBundle.message("git.changelists.shelf.congratulations"))
    }
  }

  override fun onLessonEnd(project: Project, lessonPassed: Boolean) {
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
    }
  }
}