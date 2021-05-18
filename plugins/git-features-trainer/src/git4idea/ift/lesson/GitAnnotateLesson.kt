// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ift.lesson

import com.intellij.diff.tools.util.DiffSplitter
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.vcs.actions.ActiveAnnotationGutter
import com.intellij.openapi.vcs.actions.AnnotateToggleAction
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import git4idea.ift.GitLessonsUtil.checkoutBranch
import training.dsl.*
import training.learn.LearnBundle
import training.ui.LearningUiHighlightingManager
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture
import javax.swing.JEditorPane

class GitAnnotateLesson : GitLesson("Git.Annotate", "Annotate with Git Blame") {
  override val existedFile = "src/git/martian_cat.yml"
  private val branchName = "main"
  private val firstStateText = "ears_number: 4"
  private val secondStateText = "ear_number:   4"
  private val thirdStateText = "ear_number:   2"
  private val partOfTargetCommitMessage = "Edit ear number of martian cat"

  private var backupDiffLocation: Point? = null

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val lessonContent: LessonContext.() -> Unit = {
    checkoutBranch(branchName)

    val annotateActionName = ActionsBundle.message("action.Annotate.text").dropMnemonic()

    task {
      text("Look at the highlighted property. Seems strange that according to the value of this property, cat has 4 ears. The reason of this change is interesting. We can investigate the history of the file using ${strong("Annotate")} feature.")
      triggerByPartOfComponent l@{ ui: EditorComponentImpl ->
        val startOffset = ui.editor.document.charsSequence.indexOf(firstStateText)
        if (startOffset == -1) return@l null
        val endOffset = startOffset + firstStateText.length
        val startPoint = ui.editor.offsetToXY(startOffset)
        val endPoint = ui.editor.offsetToXY(endOffset)
        Rectangle(startPoint.x - 3, startPoint.y, endPoint.x - startPoint.x + 6, ui.editor.lineHeight)
      }
      proceedLink()
    }

    val annotateMenuItemText = ActionsBundle.message("action.Annotate.with.Blame.text").dropMnemonic()
    if (isAnnotateShortcutSet()) {
      task("Annotate") {
        text("Press ${action(it)} to show history of this file.")
        trigger(it)
      }
    }
    else {
      task {
        before { LearningUiHighlightingManager.clearHighlights() }
        text("Right click somewhere in the highlighted area to open context menu.")
        highlightGutterComponent(null, firstStateText, highlightRight = true)
        highlightAnnotateMenuItem()
      }

      task("Annotate") {
        val addShortcutText = LearnBundle.message("shortcut.balloon.add.shortcut")
        text("Choose ${strong(annotateMenuItemText)} option to show history of this file.")
        text("<strong>Tip</strong>: you can assign a shortcut for ${strong(annotateActionName)} action. Click this link ${action(it)} and choose ${strong(addShortcutText)}.")
        trigger(it)
        restoreByUi()
      }
    }

    val showDiffText = ActionsBundle.message("action.Diff.ShowDiff.text")
    lateinit var openFirstDiffTaskId: TaskContext.TaskId
    task {
      before { LearningUiHighlightingManager.clearHighlights() }
      openFirstDiffTaskId = taskId
      text("Annotate action provides easy access to the last commit that modified any specific line of the file. You can see that <strong>Jonny Catsville</strong> is the last who modified our strange line. Right click the highlighted annotation to open the context menu.")
      highlightAnnotation(null, firstStateText, highlightRight = true)
      highlightShowDiffMenuItem()
    }

    var firstDiffSplitter: DiffSplitter? = null
    task {
      text("Choose ${strong(showDiffText)} option to show what is changed in this commit.")
      trigger("com.intellij.openapi.vcs.actions.ShowDiffFromAnnotation")
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    task {
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { ui: EditorComponentImpl ->
        if (ui.editor.document.charsSequence.contains(secondStateText)) {
          firstDiffSplitter = UIUtil.getParentOfType(DiffSplitter::class.java, ui)
          true
        }
        else false
      }
    }

    prepareRuntimeTask l@{
      val window = UIUtil.getWindow(previous.ui) ?: return@l
      val oldWindowLocation = WindowStateService.getInstance(project).getLocation("DiffContextDialog")
      if (LessonUtil.adjustPopupPosition(project, window)) {
        backupDiffLocation = oldWindowLocation
      }
    }

    if (isAnnotateShortcutSet()) {
      task("Annotate") {
        text("You can see that our strange value of ${code("ears_number")} appeared earlier than this commit. So let's go deeper in the history! Move the caret to the left editor and press ${action(it)} again.")
        triggerOnAnnotationsShown(firstDiffSplitter, secondStateText)
        restoreIfDiffClosed(openFirstDiffTaskId, firstDiffSplitter)
      }
    } else {
      task {
        text("You can see that our strange value of ${code("ears_number")} appeared earlier than this commit. So let's go deeper in the history! Right click somewhere in the highlighted area and choose ${strong(annotateMenuItemText)} option from the opened menu.")
        highlightGutterComponent(firstDiffSplitter, secondStateText, highlightRight = false)
        val annotateItemFuture = highlightAnnotateMenuItem()
        triggerOnAnnotationsShown(firstDiffSplitter, secondStateText)
        restoreIfDiffClosed(openFirstDiffTaskId, firstDiffSplitter)
        restartTaskIfMenuClosed(annotateItemFuture)
      }
    }

    var secondDiffSplitter: DiffSplitter? = null
    lateinit var openSecondDiffTaskId: TaskContext.TaskId
    task {
      openSecondDiffTaskId = taskId
      text("Right click the highlighted annotation to open the context menu and choose ${strong(showDiffText)} option.")
      highlightAnnotation(firstDiffSplitter, secondStateText, highlightRight = false)
      val showDiffItemFuture = highlightShowDiffMenuItem()
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { ui: EditorComponentImpl ->
        if (ui.editor.document.charsSequence.contains(thirdStateText)) {
          secondDiffSplitter = UIUtil.getParentOfType(DiffSplitter::class.java, ui)
          true
        }
        else false
      }
      restoreIfDiffClosed(openFirstDiffTaskId, firstDiffSplitter)
      restartTaskIfMenuClosed(showDiffItemFuture)
    }

    if (isAnnotateShortcutSet()) {
      task("Annotate") {
        text("Great! We found the place where ${code("ear_number")} value was changed. So let's annotate it for the last time to investigate the reason of this change. Move the caret to the right editor and press ${action(it)}.")
        triggerOnAnnotationsShown(secondDiffSplitter, secondStateText)
        restoreIfDiffClosed(openSecondDiffTaskId, secondDiffSplitter)
      }
    } else {
      task {
        text("Great! We found the place where ${code("ear_number")} value was changed. So let's annotate it for the last time to investigate the reason of this change. Right click somewhere in the highlighted area and choose ${strong(annotateMenuItemText)} option from the opened menu.")
        highlightGutterComponent(secondDiffSplitter, secondStateText, highlightRight = true)
        val annotateItemFuture = highlightAnnotateMenuItem()
        triggerOnAnnotationsShown(secondDiffSplitter, secondStateText)
        restoreIfDiffClosed(openSecondDiffTaskId, secondDiffSplitter)
        restartTaskIfMenuClosed(annotateItemFuture)
      }
    }

    task {
      text("Click the highlighted annotation to show information about this commit.")
      highlightAnnotation(secondDiffSplitter, secondStateText, highlightRight = true)
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: JEditorPane ->
        ui.text?.contains(partOfTargetCommitMessage) == true
      }
      restoreIfDiffClosed(openSecondDiffTaskId, secondDiffSplitter)
    }

    task {
      text("As mentioned in the highlighted commit message, this strange value of ${code("ear_number")} is not a mistake. So after the long journey through the history of this file you can close all opened windows to return to the editor.")
      stateCheck {
        previous.ui?.isShowing != true && firstDiffSplitter?.isShowing != true && secondDiffSplitter?.isShowing != true
      }
    }

    if (isAnnotateShortcutSet()) {
      task("Annotate") {
        text("And now you can close the annotations. Press ${action(it)}.")
        stateCheck { !isAnnotationsShown(editor) }
      }
    } else {
      task("Annotate") {
        val closeAnnotationsText = EditorBundle.message("close.editor.annotations.action.name")
        text("And now you can close the annotations. Right click somewhere in the highlighted area and choose ${strong(closeAnnotationsText)} item from the opened menu.")
        triggerByPartOfComponent { ui: EditorGutterComponentEx ->
          Rectangle(ui.x + ui.annotationsAreaOffset, ui.y, ui.annotationsAreaWidth, ui.height)
        }
        val closeAnnotationsItemFuture = CompletableFuture<ActionMenuItem>()
        triggerByUiComponentAndHighlight(highlightInside = false) { ui: ActionMenuItem ->
          (ui.text?.contains(closeAnnotationsText) == true).also {
            if (it) closeAnnotationsItemFuture.complete(ui)
          }
        }
        stateCheck { !isAnnotationsShown(editor) }
        restartTaskIfMenuClosed(closeAnnotationsItemFuture)
      }
    }
  }

  override fun onLessonEnd(project: Project, lessonPassed: Boolean) {
    if (backupDiffLocation != null) {
      invokeLater {
        WindowStateService.getInstance(project).putLocation("DiffContextDialog", backupDiffLocation)
        backupDiffLocation = null
      }
    }
  }

  private fun TaskContext.highlightGutterComponent(splitter: DiffSplitter?, partOfEditorText: String, highlightRight: Boolean) {
    triggerByPartOfComponent l@{ ui: EditorGutterComponentEx ->
      if (splitter != null && !isInsideSplitter(splitter, ui)) return@l null
      val editor = findEditorForGutter(ui) ?: return@l null
      if (editor.document.charsSequence.contains(partOfEditorText)) {
        if (highlightRight) {
          Rectangle(ui.x, ui.y, ui.width - 5, ui.height)
        }
        else Rectangle(ui.x + 5, ui.y, ui.width, ui.height)
      }
      else null
    }
  }

  private fun TaskContext.highlightAnnotation(splitter: DiffSplitter?, partOfLineText: String, highlightRight: Boolean) {
    triggerByPartOfComponent l@{ ui: EditorGutterComponentEx ->
      if (splitter != null && !isInsideSplitter(splitter, ui)) return@l null
      val editor = findEditorForGutter(ui) ?: return@l null
      val offset = editor.document.charsSequence.indexOf(partOfLineText)
      if (offset == -1) return@l null
      val y = editor.offsetToXY(offset).y
      if (highlightRight) {
        Rectangle(ui.x + ui.annotationsAreaOffset, y, ui.annotationsAreaWidth, editor.lineHeight)
      }
      else Rectangle(ui.x + ui.width - ui.annotationsAreaOffset - ui.annotationsAreaWidth, y, ui.annotationsAreaWidth, editor.lineHeight)
    }
  }

  private fun TaskContext.highlightAnnotateMenuItem() = highlightMenuItem { it.anAction is AnnotateToggleAction }

  private fun TaskContext.highlightShowDiffMenuItem(): CompletableFuture<ActionMenuItem> {
    val showDiffText = ActionsBundle.message("action.Diff.ShowDiff.text")
    return highlightMenuItem { it.text?.contains(showDiffText) == true }
  }

  private fun TaskContext.highlightMenuItem(predicate: (ActionMenuItem) -> Boolean): CompletableFuture<ActionMenuItem> {
    val future = CompletableFuture<ActionMenuItem>()
    triggerByUiComponentAndHighlight(highlightInside = false) { ui: ActionMenuItem ->
      predicate(ui).also {
        if (it) future.complete(ui)
      }
    }
    return future
  }

  private fun TaskContext.triggerOnAnnotationsShown(splitter: DiffSplitter?, partOfEditorText: String) {
    triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { ui: EditorComponentImpl ->
      ui.editor.document.charsSequence.contains(partOfEditorText)
      && UIUtil.getParentOfType(DiffSplitter::class.java, ui) == splitter
      && isAnnotationsShown(ui.editor)
    }
  }

  private fun TaskContext.restoreIfDiffClosed(restoreId: TaskContext.TaskId, diff: DiffSplitter?) {
    restoreState(restoreId) { diff?.isShowing != true }
  }

  private fun TaskContext.restartTaskIfMenuClosed(menuItemFuture: CompletableFuture<ActionMenuItem>) {
    restoreState(taskId, delayMillis = defaultRestoreDelay) {
      val item = menuItemFuture.getNow(null)
      item != null && !item.isShowing
    }
  }

  private fun isInsideSplitter(splitter: DiffSplitter, component: Component): Boolean {
    return UIUtil.getParentOfType(DiffSplitter::class.java, component) == splitter
  }

  private fun isAnnotationsShown(editor: Editor): Boolean {
    val annotations = editor.gutter.textAnnotations
    return annotations.filterIsInstance<ActiveAnnotationGutter>().isNotEmpty()
  }

  private fun isAnnotateShortcutSet(): Boolean {
    return KeymapManager.getInstance().activeKeymap.getShortcuts("Annotate").isNotEmpty()
  }

  private fun findEditorForGutter(component: EditorGutterComponentEx): Editor? {
    val scrollPane = UIUtil.getParentOfType(JBScrollPane::class.java, component) ?: return null
    return UIUtil.findComponentOfType(scrollPane, EditorComponentImpl::class.java)?.editor
  }
}