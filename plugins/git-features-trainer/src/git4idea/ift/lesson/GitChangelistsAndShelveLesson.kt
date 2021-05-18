// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.CommonBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesAction
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.DropDownLink
import com.intellij.util.DocumentUtil
import git4idea.ift.GitLessonsUtil.checkoutBranch
import git4idea.ift.GitLessonsUtil.moveLearnToolWindowRight
import git4idea.ift.GitLessonsUtil.showWarningIfCommitWindowClosed
import training.dsl.*
import java.awt.Rectangle
import javax.swing.JButton

class GitChangelistsAndShelveLesson : GitLesson("Git.ChangelistsAndShelf", "Changelists and Shelf") {
  override val existedFile = "src/git/martian_cat.yml"
  private val branchName = "main"
  private val commentingLineText = "fur_type: long haired"
  private val commentText = "# debug: check another types (short haired, hairless)"

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    checkoutBranch(branchName)

    val defaultChangelistName = VcsBundle.message("changes.default.changelist.name")
    prepareRuntimeTask {
      resetChangelistsState(project)
      removeShelvedChangeLists(project)
      modifyFile(virtualFile)
    }

    lateinit var clickLineMarkerTaskId: TaskContext.TaskId
    task {
      clickLineMarkerTaskId = taskId
      text("Suppose you don't want to commit added comment to the repository, because this change needed only locally. In the common case it can be some personal settings. You can extract the comment to the new changelist, to not commit it accidentally with other changes. Click the highlighted line marker to open the context menu.")
      triggerByPartOfComponent(highlightInside = true, usePulsation = true) l@{ ui: EditorGutterComponentEx ->
        val offset = editor.document.charsSequence.indexOf(commentText)
        if (offset == -1) return@l null
        val line = editor.offsetToVisualLine(offset, true)
        val y = editor.visualLineToY(line)
        return@l Rectangle(ui.x + ui.width - 15, y, 10, editor.lineHeight)
      }
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: DropDownLink<*> ->
        ui.text?.contains(defaultChangelistName) == true
      }
    }

    task {
      val newChangelistText = VcsBundle.message("ex.new.changelist")
      text("Click the ${strong(defaultChangelistName)} link and choose the ${strong(newChangelistText)} item.")
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { ui: EditorComponentImpl ->
        ui.text.contains(defaultChangelistName)
      }
      restoreByUi()
    }

    var newChangeListName = "Comments"
    task {
      text("Name the new changelist something like ${code("Comments")}. Press ${LessonUtil.rawEnter()} or click ${strong(CommonBundle.getOkButtonText())} button to create new changelist.")
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
      text("Now please press ${action(it)} to open commit tool window and see created changelist.")
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

    moveLearnToolWindowRight()

    task {
      text("In addition you can use ${strong("Shelf")} feature to save this changes in the file on your computer. Changes stored in the ${strong("Shelf")} can be applied later in any branch. So it will protect you from losing this changes.")
      triggerByFoundPathAndHighlight(highlightInside = true) { _, path ->
        path.getPathComponent(path.pathCount - 1).toString().contains(newChangeListName)
      }
      proceedLink()
    }

    lateinit var letsShelveTaskId: TaskContext.TaskId
    task {
      letsShelveTaskId = taskId
      text("Let's shelve our changes! Right click the highlighted changelist to open context menu.")
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: ActionMenuItem ->
        ui.anAction is ShelveChangesAction
      }
      showWarningIfCommitWindowClosed()
    }

    val shelveChangesButtonText = VcsBundle.message("shelve.changes.action").dropMnemonic()
    task {
      val shelveChangesText = ActionsBundle.message("action.ChangesView.Shelve.text")
      text("Select ${strong(shelveChangesText)} to open ${strong("Shelf")} dialog.")
      triggerStart("ChangesView.Shelve")
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    task {
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JButton ->
        ui.text?.contains(shelveChangesButtonText) == true
      }
    }

    task {
      text("Now you can edit the shelf message or leave it as the IDE proposed. Click ${strong(shelveChangesButtonText)} button to store the changes in the shelf.")
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
      text("We don't need this changelist anymore, so click the ${strong(removeButtonText)} button.")
      stateCheck { previous.ui?.isShowing != true }
    }

    task {
      text("You can see that our changelist successfully saved to the ${strong("Shelf")} and comment is disappeared from the open file.")
      triggerByFoundPathAndHighlight(highlightInside = true, usePulsation = true) { _, path ->
        path.pathCount == 2
      }
      proceedLink()
    }

    val unshelveChangesButtonText = VcsBundle.message("unshelve.changes.action")
    task("ShelveChanges.UnshelveWithDialog") {
      text("When your changes stored in the ${strong("Shelf")} you can apply it again. To perform it, select the highlighted changelist and press ${action(it)} to open the ${strong("Unshelve")} dialog.")
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: JButton ->
        ui.text?.contains(unshelveChangesButtonText) == true
      }
      showWarningIfCommitWindowClosed()
    }

    task {
      text("Now you can edit the name of the changelist to put the unshelving changes or leave it as the IDE proposed. Click ${strong(unshelveChangesButtonText)} button to apply the changes.")
      stateCheck { editor.document.text.contains(commentText) }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }
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
      document.insertString(document.textLength, """
    - eat:
        condition: hungry
        actions: [ fry self-grown potatoes ]""")

      val offset = document.charsSequence.indexOf(commentingLineText)
      if (offset == -1) error("Not found '$commentingLineText' item in text")
      document.insertString(offset + commentingLineText.length, "  $commentText")
    }
  }
}