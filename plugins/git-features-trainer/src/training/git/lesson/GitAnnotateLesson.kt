// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git.lesson

import com.intellij.diff.impl.DiffWindowBase
import com.intellij.diff.tools.util.DiffSplitter
import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vcs.actions.ActiveAnnotationGutter
import com.intellij.openapi.vcs.actions.AnnotateToggleAction
import com.intellij.openapi.vcs.actions.ShowDiffFromAnnotation
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel
import org.assertj.swing.core.MouseButton
import org.assertj.swing.timing.Timeout
import training.dsl.*
import training.dsl.LessonUtil.adjustPopupPosition
import training.dsl.LessonUtil.restorePopupPosition
import training.git.GitLessonsBundle
import training.ui.IftTestContainerFixture
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.LessonEndInfo
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle

class GitAnnotateLesson : GitLesson("Git.Annotate", GitLessonsBundle.message("git.annotate.lesson.name")) {
  override val sampleFilePath = "git/martian_cat.yml"
  override val branchName = "main"
  private val propertyName = "ears_number"
  private val editedPropertyName = "ear_number"
  private val firstStateText = "ears_number: 4"
  private val secondStateText = "ear_number:   4"
  private val thirdStateText = "ear_number:   2"
  private val partOfTargetCommitMessage = "Edit ear number of martian cat"

  private var backupDiffLocation: Point? = null
  private var backupRevisionsLocation: Point? = null

  override val testScriptProperties = TaskTestContext.TestScriptProperties(duration = 60)

  override val lessonContent: LessonContext.() -> Unit = {
    val annotateActionName = ActionsBundle.message("action.Annotate.text").dropMnemonic()

    task {
      text(GitLessonsBundle.message("git.annotate.introduction", strong(annotateActionName)))
      triggerAndBorderHighlight().componentPart l@{ ui: EditorComponentImpl ->
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
        text(GitLessonsBundle.message("git.annotate.invoke.shortcut.1", action(it)))
        trigger(it)
        test { actions(it) }
      }
    }
    else {
      task {
        highlightGutterComponent(null, firstStateText, highlightRight = true)
      }

      task {
        text(GitLessonsBundle.message("git.annotate.open.context.menu"))
        text(GitLessonsBundle.message("git.annotate.click.gutter.balloon"), LearningBalloonConfig(Balloon.Position.atRight, 0))
        highlightAnnotateMenuItem(clearPreviousHighlights = true)
        test { clickGutter(null, firstStateText, MouseButton.RIGHT_BUTTON) }
      }

      task("Annotate") {
        val addShortcutText = IdeBundle.message("shortcut.balloon.add.shortcut")
        text(GitLessonsBundle.message("git.annotate.choose.annotate", strong(annotateMenuItemText)))
        text(GitLessonsBundle.message("git.annotate.add.shortcut.tip", strong(annotateActionName), action(it), strong(addShortcutText)))
        trigger(it)
        restoreByUi()
        test { clickAnnotateAction() }
      }
    }

    waitBeforeContinue(500)

    lateinit var openFirstDiffTaskId: TaskContext.TaskId
    task {
      openFirstDiffTaskId = taskId
      highlightAnnotation(null, firstStateText, highlightRight = true)
    }

    val showDiffText = ActionsBundle.message("action.Diff.ShowDiff.text")
    task {
      text(GitLessonsBundle.message("git.annotate.feature.explanation", strong(annotateActionName), strong("Johnny Catsville")))
      text(GitLessonsBundle.message("git.annotate.click.annotation.tooltip"), LearningBalloonConfig(Balloon.Position.above, 0))
      highlightShowDiffMenuItem()
      test {
        clickAnnotation(null, firstStateText, rightOriented = true, MouseButton.RIGHT_BUTTON)
      }
    }

    var firstDiffSplitter: DiffSplitter? = null
    task {
      text(GitLessonsBundle.message("git.annotate.choose.show.diff", strong(showDiffText)))
      trigger("com.intellij.openapi.vcs.actions.ShowDiffFromAnnotation")
      restoreByUi(openFirstDiffTaskId, delayMillis = defaultRestoreDelay)
      test { clickShowDiffAction() }
    }

    task {
      triggerUI().component { ui: EditorComponentImpl ->
        if (ui.editor.document.charsSequence.contains(secondStateText)) {
          firstDiffSplitter = UIUtil.getParentOfType(DiffSplitter::class.java, ui)
          true
        }
        else false
      }
    }

    prepareRuntimeTask l@{
      if (backupDiffLocation == null) {
        backupDiffLocation = adjustPopupPosition(DiffWindowBase.DEFAULT_DIALOG_GROUP_KEY)
      }
    }

    if (isAnnotateShortcutSet()) {
      task("Annotate") {
        text(GitLessonsBundle.message("git.annotate.go.deeper", code(propertyName)) + " "
             + GitLessonsBundle.message("git.annotate.invoke.shortcut.2", action(it)))
        triggerOnAnnotationsShown(firstDiffSplitter, secondStateText)
        restoreIfDiffClosed(openFirstDiffTaskId, firstDiffSplitter)
        test(waitEditorToBeReady = false) {
          clickGutter(firstDiffSplitter, secondStateText, MouseButton.LEFT_BUTTON)
          actions(it)
        }
      }
    } else {
      task {
        highlightGutterComponent(firstDiffSplitter, secondStateText, highlightRight = false)
      }

      task {
        text(GitLessonsBundle.message("git.annotate.go.deeper", code(propertyName)) + " "
             + GitLessonsBundle.message("git.annotate.invoke.manually", strong(annotateMenuItemText)))
        text(GitLessonsBundle.message("git.annotate.click.gutter.balloon"), LearningBalloonConfig(Balloon.Position.atLeft, 0))
        highlightAnnotateMenuItem()
        triggerOnAnnotationsShown(firstDiffSplitter, secondStateText)
        restoreIfDiffClosed(openFirstDiffTaskId, firstDiffSplitter)
        test(waitEditorToBeReady = false) {
          clickGutter(firstDiffSplitter, secondStateText, MouseButton.RIGHT_BUTTON)
          clickAnnotateAction()
        }
      }
    }

    var secondDiffSplitter: DiffSplitter? = null
    lateinit var openSecondDiffTaskId: TaskContext.TaskId
    task {
      openSecondDiffTaskId = taskId
      text(GitLessonsBundle.message("git.annotate.show.diff", strong(showDiffText)))
      highlightAnnotation(firstDiffSplitter, secondStateText, highlightRight = false)
      highlightShowDiffMenuItem()
      triggerUI().component { ui: EditorComponentImpl ->
        if (ui.editor.document.charsSequence.contains(thirdStateText)) {
          secondDiffSplitter = UIUtil.getParentOfType(DiffSplitter::class.java, ui)
          true
        }
        else false
      }
      restoreIfDiffClosed(openFirstDiffTaskId, firstDiffSplitter)
      test(waitEditorToBeReady = false) {
        clickAnnotation(firstDiffSplitter, secondStateText, rightOriented = false, MouseButton.RIGHT_BUTTON)
        clickShowDiffAction()
      }
    }

    if (isAnnotateShortcutSet()) {
      task("Annotate") {
        text(GitLessonsBundle.message("git.annotate.found.needed.commit", code(editedPropertyName)) + " "
             + GitLessonsBundle.message("git.annotate.invoke.shortcut.3", action(it)))
        triggerOnAnnotationsShown(secondDiffSplitter, secondStateText)
        restoreIfDiffClosed(openSecondDiffTaskId, secondDiffSplitter)
        test(waitEditorToBeReady = false) {
          clickGutter(secondDiffSplitter, secondStateText, MouseButton.LEFT_BUTTON)
          actions(it)
        }
      }
    } else {
      task {
        text(GitLessonsBundle.message("git.annotate.found.needed.commit", code(editedPropertyName)) + " "
             + GitLessonsBundle.message("git.annotate.invoke.manually", strong(annotateMenuItemText)))
        highlightGutterComponent(secondDiffSplitter, secondStateText, highlightRight = true)
        highlightAnnotateMenuItem()
        triggerOnAnnotationsShown(secondDiffSplitter, secondStateText)
        restoreIfDiffClosed(openSecondDiffTaskId, secondDiffSplitter)
        test(waitEditorToBeReady = false) {
          clickGutter(secondDiffSplitter, secondStateText, MouseButton.RIGHT_BUTTON)
          clickAnnotateAction()
        }
      }
    }

    task {
      text(GitLessonsBundle.message("git.annotate.click.annotation"))
      highlightAnnotation(secondDiffSplitter, secondStateText, highlightRight = true)
      triggerAndBorderHighlight().component { ui: CommitDetailsListPanel ->
        val textPanel = UIUtil.findComponentOfType(ui, HtmlPanel::class.java)
        textPanel?.text?.contains(partOfTargetCommitMessage) == true
      }
      restoreIfDiffClosed(openSecondDiffTaskId, secondDiffSplitter)
      test(waitEditorToBeReady = false) {
        clickAnnotation(secondDiffSplitter, secondStateText, rightOriented = true, MouseButton.LEFT_BUTTON)
      }
    }

    task("HideActiveWindow") {
      before {
        if (backupRevisionsLocation == null) {
          backupRevisionsLocation = adjustPopupPosition(ChangeListViewerDialog.DIMENSION_SERVICE_KEY)
        }
      }
      text(GitLessonsBundle.message("git.annotate.close.changes", code(editedPropertyName), action(it)))
      stateCheck {
        previous.ui?.isShowing != true
      }
      test(waitEditorToBeReady = false) {
        Thread.sleep(500)
        actions(it)
      }
    }

    task("EditorEscape") {
      text(GitLessonsBundle.message("git.annotate.close.all.windows",
                                    if (VcsEditorTabFilesManager.getInstance().shouldOpenInNewWindow) 0 else 1, action(it)))
      stateCheck {
        firstDiffSplitter?.isShowing != true && secondDiffSplitter?.isShowing != true
      }
      test(waitEditorToBeReady = false) {
        repeat(2) {
          Thread.sleep(300)
          invokeActionViaShortcut("ESCAPE")
        }
      }
    }

    // There can be no selected editor at this moment, because of closing diffs from the previous task
    // and internal recalculation inside FileEditorManager, so wait little bit
    waitBeforeContinue(500)

    if (isAnnotateShortcutSet()) {
      task("Annotate") {
        text(GitLessonsBundle.message("git.annotate.close.annotations") + " "
             + GitLessonsBundle.message("git.annotate.close.by.shortcut", action(it)))
        stateCheck { !isAnnotationsShown(editor) }
        test { actions(it) }
      }
    }
    else {
      task("Annotate") {
        val closeAnnotationsText = EditorBundle.message("close.editor.annotations.action.name")
        text(GitLessonsBundle.message("git.annotate.close.annotations") + " "
             + GitLessonsBundle.message("git.annotate.invoke.manually.2", strong(closeAnnotationsText)))
        triggerAndBorderHighlight().componentPart { ui: EditorGutterComponentEx ->
          if (ui.editor == editor) {
            Rectangle(ui.x + ui.annotationsAreaOffset, ui.y, ui.annotationsAreaWidth, ui.height)
          }
          else null
        }
        highlightMenuItem(clearPreviousHighlights = false) { item -> item.text?.contains(closeAnnotationsText) == true }
        stateCheck { !isAnnotationsShown(editor) }
        test {
          clickGutter(null, firstStateText, MouseButton.RIGHT_BUTTON)
          ideFrame {
            val fixture = jMenuItem { item: ActionMenuItem -> item.text?.contains(closeAnnotationsText) == true }
            fixture.click()
          }
        }
      }
    }
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    restorePopupPosition(project, DiffWindowBase.DEFAULT_DIALOG_GROUP_KEY, backupDiffLocation)
    backupDiffLocation = null
    restorePopupPosition(project, ChangeListViewerDialog.DIMENSION_SERVICE_KEY, backupRevisionsLocation)
    backupRevisionsLocation = null
  }

  private fun TaskContext.highlightGutterComponent(splitter: DiffSplitter?, partOfEditorText: String, highlightRight: Boolean) {
    triggerAndBorderHighlight().componentPart l@{ ui: EditorGutterComponentEx ->
      if (ui.checkInsideSplitterAndRightEditor(splitter, partOfEditorText)) {
        if (highlightRight) {
          Rectangle(ui.x, ui.y, ui.width - 5, ui.height)
        }
        else Rectangle(ui.x + 5, ui.y, ui.width, ui.height)
      }
      else null
    }
  }

  private fun EditorGutterComponentEx.checkInsideSplitterAndRightEditor(splitter: DiffSplitter?, partOfEditorText: String): Boolean {
    if (splitter != null && !isInsideSplitter(splitter, this)) return false
    return editor.document.charsSequence.contains(partOfEditorText)
  }

  private fun TaskContext.highlightAnnotation(splitter: DiffSplitter?, partOfLineText: String, highlightRight: Boolean) {
    triggerAndBorderHighlight().componentPart l@{ ui: EditorGutterComponentEx ->
      if (splitter != null && !isInsideSplitter(splitter, ui)) return@l null
      ui.getAnnotationRect(partOfLineText, highlightRight)
    }
  }

  private fun EditorGutterComponentEx.getAnnotationRect(partOfLineText: String, rightOriented: Boolean): Rectangle? {
    val offset = editor.document.charsSequence.indexOf(partOfLineText)
    if (offset == -1) return null
    return invokeAndWaitIfNeeded {
      val lineY = editor.offsetToXY(offset).y
      if (rightOriented) {
        Rectangle(x + annotationsAreaOffset, lineY, annotationsAreaWidth, editor.lineHeight)
      }
      else Rectangle(x + width - annotationsAreaOffset - annotationsAreaWidth, lineY, annotationsAreaWidth, editor.lineHeight)
    }
  }

  private fun TaskContext.highlightAnnotateMenuItem(clearPreviousHighlights: Boolean = false) {
    highlightMenuItem(clearPreviousHighlights) { it.anAction is AnnotateToggleAction }
  }

  private fun TaskContext.highlightShowDiffMenuItem(clearPreviousHighlights: Boolean = false) {
    return highlightMenuItem(clearPreviousHighlights) { it.anAction is ShowDiffFromAnnotation }
  }

  private fun TaskContext.highlightMenuItem(clearPreviousHighlights: Boolean, predicate: (ActionMenuItem) -> Boolean) {
    triggerAndBorderHighlight { this.clearPreviousHighlights = clearPreviousHighlights }.component { ui: ActionMenuItem -> predicate(ui) }
  }

  private fun TaskContext.triggerOnAnnotationsShown(splitter: DiffSplitter?, partOfEditorText: String) {
    triggerUI().component { ui: EditorComponentImpl ->
      ui.editor.document.charsSequence.contains(partOfEditorText)
      && UIUtil.getParentOfType(DiffSplitter::class.java, ui) == splitter
      && isAnnotationsShown(ui.editor)
    }
  }

  private fun TaskContext.restoreIfDiffClosed(restoreId: TaskContext.TaskId, diff: DiffSplitter?) {
    restoreState(restoreId, delayMillis = defaultRestoreDelay) { diff?.isShowing != true }
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

  private fun TaskTestContext.clickGutter(splitter: DiffSplitter?, partOfEditorText: String, button: MouseButton) {
    ideFrame {
      val gutter = findGutterComponent(splitter, partOfEditorText, defaultTimeout)
      robot.click(gutter, button)
    }
  }

  private fun TaskTestContext.clickAnnotation(splitter: DiffSplitter?,
                                              partOfLineText: String,
                                              rightOriented: Boolean,
                                              button: MouseButton) {
    ideFrame {
      val gutter = findGutterComponent(splitter, partOfLineText, defaultTimeout)
      val annotationRect = gutter.getAnnotationRect(partOfLineText, rightOriented)
                           ?: error("Failed to find text '$partOfLineText' in editor")
      robot.click(gutter, Point(annotationRect.centerX.toInt(), annotationRect.centerY.toInt()), button, 1)
    }
  }

  private fun IftTestContainerFixture<IdeFrameImpl>.findGutterComponent(splitter: DiffSplitter?,
                                                                        partOfEditorText: String,
                                                                        timeout: Timeout): EditorGutterComponentEx {
    return findComponentWithTimeout(timeout) l@{ ui: EditorGutterComponentEx ->
      ui.checkInsideSplitterAndRightEditor(splitter, partOfEditorText)
    }
  }

  private fun TaskTestContext.clickAnnotateAction() {
    ideFrame { jMenuItem { item: ActionMenuItem -> item.anAction is AnnotateToggleAction }.click() }
  }

  private fun TaskTestContext.clickShowDiffAction() {
    ideFrame {
      val showDiffText = ActionsBundle.message("action.Diff.ShowDiff.text")
      jMenuItem { item: ActionMenuItem -> item.text?.contains(showDiffText) == true }.click()
    }
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(GitLessonsBundle.message("git.annotate.help.link"),
         LessonUtil.getHelpLink("investigate-changes.html#annotate_blame")),
  )}
