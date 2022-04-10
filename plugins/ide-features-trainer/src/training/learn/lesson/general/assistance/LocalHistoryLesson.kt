// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.assistance

import com.intellij.CommonBundle
import com.intellij.history.integration.ui.actions.LocalHistoryGroup
import com.intellij.history.integration.ui.actions.ShowHistoryAction
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBLoadingPanelListener
import com.intellij.ui.table.JBTable
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.UIUtil
import org.assertj.swing.core.MouseButton
import org.assertj.swing.data.TableCell
import org.assertj.swing.fixture.JTableFixture
import org.jetbrains.annotations.Nls
import training.FeaturesTrainerIcons
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.course.LessonProperties
import training.learn.course.LessonType
import training.learn.lesson.LessonManager
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiHighlightingManager.HighlightingOptions
import training.ui.LearningUiUtil
import training.util.LessonEndInfo
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture
import javax.swing.JFrame

class LocalHistoryLesson : KLesson("CodeAssistance.LocalHistory", LessonsBundle.message("local.history.lesson.name")) {
  override val languageId = "yaml"
  override val lessonType = LessonType.SCRATCH
  override val properties = LessonProperties(availableSince = "212.5284")

  private val lineToDelete = 14
  private val revisionInd = 2

  private val sample = parseLessonSample("""
    cat:
      name: Pelmen
      gender: male
      breed: sphinx
      fur_type: hairless
      fur_pattern: solid
      fur_colors: [ white ]
      tail_length: long
      eyes_colors: [ green ]
    
      favourite_things:
        - three plaids
        - pile of clothes
        - castle of boxes
        - toys scattered all over the place
    
      behavior:
        - play:
            condition: boring
            actions:
              - bring one of the favourite toys to the human
              - run all over the house
        - eat:
            condition: want to eat
            actions:
              - shout to the whole house
              - sharpen claws by the sofa
              - wake up a human in the middle of the night""".trimIndent())

  private val textToDelete = """
    |    - play:
    |        condition: boring
    |        actions:
    |          - bring one of the favourite toys to the human
    |          - run all over the house
  """.trimMargin()

  private val textAfterDelete = """
    |  behavior:
    |
    |    - eat:
  """.trimMargin()

  private val textToAppend = """
    |    - sleep:
    |        condition: want to sleep
    |        action:
    |          - bury himself in a human's blanket
    |          - bury himself in a favourite plaid
  """.trimMargin()

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    caret(textToDelete, select = true)

    prepareRuntimeTask(ModalityState.NON_MODAL) {
      FileDocumentManager.getInstance().saveDocument(editor.document)
    }

    val localHistoryActionText = ActionsBundle.groupText("LocalHistory").dropMnemonic()
    task {
      text(LessonsBundle.message("local.history.remove.code",
                                 strong(localHistoryActionText),
                                 action(IdeActions.ACTION_EDITOR_BACKSPACE)))
      stateCheck {
        editor.document.charsSequence.contains(textAfterDelete)
      }
      restoreIfModifiedOrMoved()
      test { invokeActionViaShortcut("DELETE") }
    }

    setEditorHint(LessonsBundle.message("local.history.editor.hint"))

    waitBeforeContinue(500)

    prepareRuntimeTask {
      if (!TaskTestContext.inTestMode) {
        val userDecision = Messages.showOkCancelDialog(
          LessonsBundle.message("local.history.dialog.message"),
          LessonsBundle.message("recent.files.dialog.title"),
          CommonBundle.message("button.ok"),
          LearnBundle.message("learn.stop.lesson"),
          FeaturesTrainerIcons.Img.PluginIcon
        )
        if (userDecision != Messages.OK) {
          LessonManager.instance.stopLesson()
        }
      }
    }

    modifyFile()

    lateinit var invokeMenuTaskId: TaskContext.TaskId
    task {
      invokeMenuTaskId = taskId
      text(LessonsBundle.message("local.history.imagine.restore", strong(ActionsBundle.message("action.\$Undo.text"))))
      text(LessonsBundle.message("local.history.invoke.context.menu", strong(localHistoryActionText)))
      triggerAndBorderHighlight().component { ui: EditorComponentImpl -> ui.editor == editor }
      triggerAndFullHighlight().component { ui: ActionMenu ->
        isClassEqual(ui.anAction, LocalHistoryGroup::class.java)
      }
      test {
        ideFrame { robot().rightClick(editor.component) }
      }
    }

    task("LocalHistory.ShowHistory") {
      val showHistoryActionText = ActionsBundle.actionText(it).dropMnemonic()
      text(LessonsBundle.message("local.history.show.history", strong(localHistoryActionText), strong(showHistoryActionText)))
      triggerAndFullHighlight { clearPreviousHighlights = false }.component { ui: ActionMenuItem ->
        isClassEqual(ui.anAction, ShowHistoryAction::class.java)
      }
      trigger(it)
      restoreByUi()
      test {
        ideFrame {
          jMenuItem { item: ActionMenu -> isClassEqual(item.anAction, LocalHistoryGroup::class.java) }.click()
          jMenuItem { item: ActionMenuItem -> isClassEqual(item.anAction, ShowHistoryAction::class.java) }.click()
        }
      }
    }

    var revisionsTable: JBTable? = null
    task {
      triggerAndBorderHighlight().componentPart { ui: JBTable ->
        if (checkInsideLocalHistoryFrame(ui)) {
          revisionsTable = ui
          ui.getCellRect(revisionInd, 0, false)
        }
        else null
      }
    }

    lateinit var selectRevisionTaskId: TaskContext.TaskId
    task {
      selectRevisionTaskId = taskId
      text(LessonsBundle.message("local.history.select.revision", strong(localHistoryActionText), strong(localHistoryActionText)))
      val step = CompletableFuture<Boolean>()
      addStep(step)
      triggerUI { clearPreviousHighlights = false }.component l@{ ui: JBLoadingPanel ->
        if (!checkInsideLocalHistoryFrame(ui)) return@l false
        ui.addListener(object : JBLoadingPanelListener {
          override fun onLoadingStart() {
            // do nothing
          }

          override fun onLoadingFinish() {
            val revisions = revisionsTable ?: return
            if (revisions.selectionModel.selectedIndices.let { it.size == 1 && it[0] == revisionInd }) {
              ui.removeListener(this)
              step.complete(true)
            }
          }
        })
        true
      }
      restoreByUi(invokeMenuTaskId, delayMillis = defaultRestoreDelay)
      test {
        ideFrame {
          Thread.sleep(1000)
          val table = revisionsTable ?: error("revisionsTable is not initialized")
          JTableFixture(robot(), table).click(TableCell.row(revisionInd).column(0), MouseButton.LEFT_BUTTON)
        }
      }
    }

    task {
      triggerAndBorderHighlight().componentPart { ui: EditorGutterComponentEx -> findDiffGutterRect(ui) }
    }

    task {
      text(LessonsBundle.message("local.history.restore.code", icon(AllIcons.Diff.ArrowRight)))
      text(LessonsBundle.message("local.history.restore.code.balloon"),
        LearningBalloonConfig(Balloon.Position.below, 0, cornerToPointerDistance = 50))
      stateCheck {
        editor.document.charsSequence.contains(textToDelete)
      }
      restoreByUi(invokeMenuTaskId)
      restoreState(selectRevisionTaskId) l@{
        val revisions = revisionsTable ?: return@l false
        revisions.selectionModel.selectedIndices.let { it.size != 1 || it[0] != revisionInd }
      }
      test {
        ideFrame {
          val gutterComponent = previous.ui as? EditorGutterComponentEx ?: error("Failed to find gutter component")
          val gutterRect = findDiffGutterRect(gutterComponent) ?: error("Failed to find required gutter")
          robot().click(gutterComponent, Point(gutterRect.x + gutterRect.width / 2, gutterRect.y + gutterRect.height / 2))
        }
      }
    }

    task {
      before { LearningUiHighlightingManager.clearHighlights() }
      text(LessonsBundle.message("local.history.close.window", action("EditorEscape")))
      stateCheck {
        val focusedEditor = focusOwner as? EditorComponentImpl
        // check that it is editor from main IDE frame
        focusedEditor != null && UIUtil.getParentOfType(SingleHeightTabs::class.java, focusedEditor) != null
      }
      test {
        invokeActionViaShortcut("ESCAPE")
        // sometimes some small popup appears at mouse position so the first Escape may close just that popup
        invokeActionViaShortcut("ESCAPE")
      }
    }

    setEditorHint(null)

    text(LessonsBundle.message("local.history.congratulations"))
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    if (!lessonEndInfo.lessonPassed) return
    ApplicationManager.getApplication().executeOnPooledThread {
      val editorComponent = LearningUiUtil.findComponentOrNull(project, EditorComponentImpl::class.java) { editor ->
        UIUtil.getParentOfType(SingleHeightTabs::class.java, editor) != null
      } ?: error("Failed to find editor component")
      invokeLater {
        val lines = textToDelete.lines()
        val rightColumn = lines.maxOf { it.length }
        LearningUiHighlightingManager.highlightPartOfComponent(editorComponent, HighlightingOptions(highlightInside = false)) {
          val editor = editorComponent.editor
          val textToFind = lines[0].trim()
          val offset = editor.document.charsSequence.indexOf(textToFind)
          if (offset == -1) error("Failed to find '$textToFind' in the editor")
          val leftPosition = editor.offsetToLogicalPosition(offset)
          val leftPoint = editor.logicalPositionToXY(leftPosition)
          val rightPoint = editor.logicalPositionToXY(LogicalPosition(leftPosition.line, rightColumn))
          Rectangle(leftPoint.x - 3, leftPoint.y, rightPoint.x - leftPoint.x + 6, editor.lineHeight * lines.size)
        }
      }
    }
  }

  private fun isClassEqual(value: Any, expectedClass: Class<*>): Boolean {
    return value.javaClass.name == expectedClass.name
  }

  private fun findDiffGutterRect(ui: EditorGutterComponentEx): Rectangle? {
    val editor = CommonDataKeys.EDITOR.getData(ui as DataProvider) ?: return null
    val offset = editor.document.charsSequence.indexOf(textToDelete)
    return if (offset != -1) {
      val lineIndex = editor.document.getLineNumber(offset)
      invokeAndWaitIfNeeded {
        val y = editor.visualLineToY(lineIndex)
        Rectangle(ui.width - ui.whitespaceSeparatorOffset, y, ui.width - 26, editor.lineHeight)
      }
    }
    else null
  }

  private fun TaskRuntimeContext.checkInsideLocalHistoryFrame(component: Component): Boolean {
    val frame = UIUtil.getParentOfType(JFrame::class.java, component)
    return frame?.title == FileUtil.toSystemDependentName(virtualFile.path)
  }

  // If message is null it will remove the existing hint and allow file modification
  private fun LessonContext.setEditorHint(@Nls message: String?) {
    prepareRuntimeTask {
      EditorModificationUtil.setReadOnlyHint(editor, message)
      (editor as EditorEx).isViewer = message != null
    }
  }

  private fun LessonContext.modifyFile() {
    task {
      addFutureStep {
        val editor = this.editor
        runBackgroundableTask(LessonsBundle.message("local.history.file.modification.progress"), project, cancellable = false) {
          val document = editor.document
          invokeAndWaitIfNeeded { FileDocumentManager.getInstance().saveDocument(document) }
          removeLineWithAnimation(editor)
          invokeAndWaitIfNeeded { FileDocumentManager.getInstance().saveDocument(document) }
          Thread.sleep(50)
          insertStringWithAnimation(editor, textToAppend, editor.document.textLength)
          taskInvokeLater {
            editor.caretModel.moveToOffset(document.textLength)
            FileDocumentManager.getInstance().saveDocument(document)
            completeStep()
          }
        }
      }
    }
  }

  @RequiresBackgroundThread
  private fun removeLineWithAnimation(editor: Editor) {
    val document = editor.document
    val startOffset = document.getLineStartOffset(lineToDelete)
    val endOffset = document.getLineEndOffset(lineToDelete)
    for (ind in endOffset downTo startOffset) {
      invokeAndWaitIfNeeded {
        DocumentUtil.writeInRunUndoTransparentAction {
          editor.caretModel.moveToOffset(ind)
          document.deleteString(ind - 1, ind)
        }
      }
      Thread.sleep(10)
    }
  }

  @RequiresBackgroundThread
  private fun insertStringWithAnimation(editor: Editor, text: String, offset: Int) {
    val document = editor.document
    for (ind in text.indices) {
      invokeAndWaitIfNeeded {
        DocumentUtil.writeInRunUndoTransparentAction {
          document.insertString(offset + ind, text[ind].toString())
          editor.caretModel.moveToOffset(offset + ind)
        }
      }
      Thread.sleep(10)
    }
  }

  override val suitableTips = listOf("local_history")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("local.history.help.link"),
         LessonUtil.getHelpLink("local-history.html")),
  )
}