// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.learn.lesson.general.run

import com.intellij.execution.RunManager
import com.intellij.execution.ui.RunConfigurationStartHistory
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.text.ShortcutsRenderingUtil
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.psi.PsiDocumentManager
import com.intellij.tasks.TaskBundle
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.InlayRunToCursorEditorListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog
import com.intellij.xdebugger.impl.ui.XDebuggerEmbeddedComboBox
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNodeImpl
import org.assertj.swing.fixture.JComboBoxFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.dsl.*
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.LessonUtil.checkPositionOfEditor
import training.dsl.LessonUtil.highlightBreakpointGutter
import training.dsl.LessonUtil.sampleRestoreNotification
import training.learn.CourseManager
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.lesson.LessonManager
import training.statistic.LessonStartingWay
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.WeakReferenceDelegator
import java.awt.Rectangle
import java.awt.event.KeyEvent

abstract class CommonDebugLesson(id: String) : KLesson(id, LessonsBundle.message("debug.workflow.lesson.name")) {
  protected abstract val sample: LessonSample
  protected abstract var logicalPosition: LogicalPosition
  protected abstract val configurationName: String
  protected abstract val quickEvaluationArgument: String
  protected abstract val confNameForWatches: String
  protected abstract val debuggingMethodName: String
  protected abstract val methodForStepInto: String
  protected abstract val stepIntoDirectionToRight: Boolean
  protected open val breakpointXRange: (width: Int) -> IntRange = LessonUtil.breakpointXRange

  protected val afterFixText: String by lazy { sample.text.replaceFirst("[0]", "[1]") }

  protected var sessionPaused: Boolean = false
  private var debugSession: XDebugSession? by WeakReferenceDelegator()

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    clearBreakpoints()
    prepareTask()

    highlightButtonById("Run", highlightInside = false, usePulsation = false)

    runSample("Run", LessonsBundle.message("debug.workflow.run.current"))

    toggleBreakpointTask(sample, { logicalPosition }, breakpointXRange = breakpointXRange) {
      text(LessonsBundle.message("debug.workflow.exception.description"))
      text(LessonsBundle.message("debug.workflow.toggle.breakpoint",
                                 action("ToggleLineBreakpoint")))
    }

    startDebugTask()

    waitBeforeContinue(500)

    evaluateExpressionTasks()

    addToWatchTask()

    stepIntoTasks()

    waitBeforeContinue(500)

    evaluateArgumentTask()

    fixTheErrorTask()

    applyProgramChangeTasks()

    stepOverTask()

    if (TaskTestContext.inTestMode) waitBeforeContinue(1000)

    resumeTask()

    muteBreakpointsTask()

    waitBeforeContinue(500)

    runToCursorTask(sample, afterFixText, debuggingMethodName)

    waitBeforeContinue(500)

    evaluateResultTask()

    stopTask()

    restoreHotSwapStateInformer()
  }

  private fun LessonContext.prepareTask() {
    showInvalidDebugLayoutWarning()

    prepareRuntimeTask {
      runWriteAction {
        val instance = RunManager.getInstance(project)
        for (it in instance.allSettings) {
          instance.removeConfiguration(it)
        }
        RunManager.getInstance(project).selectedConfiguration = null
        RunConfigurationStartHistory.getInstance(project).loadState(RunConfigurationStartHistory.State())
      }
    }
  }

  private fun LessonContext.startDebugTask() {
    highlightButtonById("Debug")

    var watchesRemoved = false
    task("Debug") {
      text(LessonsBundle.message("debug.workflow.start.debug", icon(AllIcons.Actions.StartDebugger), action(it)))
      addFutureStep {
        project.messageBus.connect(lessonDisposable).subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
          override fun processStarted(debugProcess: XDebugProcess) {
            val debugSession = debugProcess.session
            (this@CommonDebugLesson).debugSession = debugSession

            invokeLater { debugSession.setBreakpointMuted(false) }  // session is not initialized at this moment
            if (!watchesRemoved) {
              (debugSession as XDebugSessionImpl).sessionData.watchExpressions = emptyList()
              watchesRemoved = true
            }
            debugSession.addSessionListener(object : XDebugSessionListener {
              override fun sessionPaused() {
                sessionPaused = true
                taskInvokeLater { completeStep() }
              }
            }, lessonDisposable)
          }
        })
      }
      proposeModificationRestore(sample.text, checkDebugSession = false)
      test { actions(it) }
    }
  }

  private fun LessonContext.evaluateExpressionTasks() {
    task {
      triggerAndBorderHighlight { usePulsation = true }.component { ui: XDebuggerEmbeddedComboBox<XExpression> -> ui.isEditable }
    }

    val position = sample.getPosition(1)
    val needToEvaluate = position.selection?.let { pair -> sample.text.substring(pair.first, pair.second) }
                         ?: error("Invalid sample data")
    caret(position)

    task {
      text(LessonsBundle.message("debug.workflow.evaluate.expression"))
      triggerUI().component { ui: EditorComponentImpl ->
        ui.editor.document.text == needToEvaluate
      }
      proposeSelectionChangeRestore(position)
      test {
        invokeActionViaShortcut("CTRL C")
        ideFrame {
          val evaluateExpressionField =
            findComponentWithTimeout(defaultTimeout) { ui: XDebuggerEmbeddedComboBox<XExpression> -> ui.isEditable }
          JComboBoxFixture(robot(), evaluateExpressionField).click()
          invokeActionViaShortcut("CTRL V")
        }
      }
    }

    task {
      text(LessonsBundle.message("debug.workflow.evaluate.it", LessonUtil.rawEnter()))
      triggerUI().component l@{ ui: XDebuggerTree ->
        if (ui.root.childCount == 0) return@l false
        val resultNode = ui.root.getChildAt(0) as? WatchNodeImpl ?: return@l false
        resultNode.expression.expression == needToEvaluate
      }
      proposeSelectionChangeRestore(position)
      test {
        invokeActionViaShortcut("ENTER")
      }
    }
  }

  private fun LessonContext.addToWatchTask() {
    task("Debugger.AddToWatch") {
      val position = sample.getPosition(1)
      val needAddToWatch = position.selection?.let { pair -> sample.text.substring(pair.first, pair.second) }
                           ?: error("Invalid sample data")
      val hasShortcut = ShortcutsRenderingUtil.getShortcutByActionId(it) != null
      val shortcut = if (hasShortcut) "" else " " + LessonsBundle.message("debug.workflow.consider.to.add.a.shortcut")

      text(LessonsBundle.message("debug.workflow.use.watches",
                                 strong(TaskBundle.message("debugger.watches")),
                                 LessonUtil.rawKeyStroke(XDebuggerEvaluationDialog.getAddWatchKeystroke()),
                                 icon(AllIcons.Debugger.AddToWatch)))
      text(LessonsBundle.message("debug.workflow.use.watches.shortcut", action(it),
                                 strong(TaskBundle.message("debugger.watches")), shortcut))
      val addToWatchActionText = ActionsBundle.actionText(it)
      triggerAndFullHighlight { usePulsation = true }.component { ui: ActionButton ->
        ui.action.templatePresentation.text == addToWatchActionText
      }
      stateCheck {
        val watches = (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).watchesManager.getWatches(confNameForWatches)
        watches.any { watch -> watch.expression == needAddToWatch }
      }
      proposeSelectionChangeRestore(position)
      test { invokeActionViaShortcut("CTRL SHIFT ENTER") }
    }
  }

  private fun LessonContext.stepIntoTasks() {
    highlightButtonById("StepInto")

    actionTask("StepInto") {
      proposeModificationRestore(sample.text)
      LessonsBundle.message("debug.workflow.step.into", action(it), icon(AllIcons.Actions.TraceInto))
    }

    task {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(LessonsBundle.message("debug.workflow.choose.method.to.step.in",
                                 code(methodForStepInto),
                                 LessonUtil.rawKeyStroke(if (stepIntoDirectionToRight) KeyEvent.VK_RIGHT else KeyEvent.VK_LEFT),
                                 action("EditorEnter")))
      stateCheck {
        val debugLine = debugSession?.currentStackFrame?.sourcePosition?.line
        val sampleLine = editor.offsetToLogicalPosition(sample.getPosition(2).startOffset).line
        debugLine == sampleLine
      }
      proposeModificationRestore(sample.text)
      test {
        Thread.sleep(500)
        invokeActionViaShortcut(if (stepIntoDirectionToRight) "RIGHT" else "LEFT")
        invokeActionViaShortcut("ENTER")
      }
    }
  }

  private fun LessonContext.evaluateArgumentTask() {
    quickEvaluateTask(positionId = 2) { position ->
      text(LessonsBundle.message("debug.workflow.quick.evaluate", code(quickEvaluationArgument), action("QuickEvaluateExpression")))
      proposeSelectionChangeRestore(position)
    }
  }

  private fun LessonContext.fixTheErrorTask() {
    task {
      text(LessonsBundle.message("debug.workflow.fix.error", action("EditorEscape")))
      val intermediate = sample.text.replaceFirst("[0]", "[]")
      val restorePosition = sample.text.indexOf("[0]") + 2
      stateCheck {
        editor.document.text == afterFixText
      }
      proposeRestore {
        val editorText = editor.document.text
        if (editorText != afterFixText && editorText != intermediate && editorText != sample.text)
          sampleRestoreNotification(TaskContext.ModificationRestoreProposal, LessonSample(sample.text, restorePosition))
        else checkForBreakpoints()
      }
      test {
        invokeActionViaShortcut("ESCAPE")
        taskInvokeLater {
          DocumentUtil.writeInRunUndoTransparentAction {
            val offset = sample.text.indexOf("[0]")
            editor.selectionModel.removeSelection()
            editor.document.replaceString(offset + 1, offset + 2, "1")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
          }
        }
      }
    }
  }

  private fun LessonContext.stepOverTask() {
    highlightButtonById("StepOver")

    actionTask("StepOver") {
      proposeModificationRestore(afterFixText)
      LessonsBundle.message("debug.workflow.step.over", code("extract_number"), action(it), icon(AllIcons.Actions.TraceOver))
    }
  }

  private fun LessonContext.resumeTask() {
    highlightButtonById("Resume")

    actionTask("Resume") {
      proposeModificationRestore(afterFixText)
      LessonsBundle.message("debug.workflow.resume", action(it), icon(AllIcons.Actions.Resume))
    }
  }

  private fun LessonContext.muteBreakpointsTask() {
    highlightButtonById("XDebugger.MuteBreakpoints")

    actionTask("XDebugger.MuteBreakpoints") {
      proposeModificationRestore(afterFixText)
      LessonsBundle.message("debug.workflow.mute.breakpoints", icon(AllIcons.Debugger.MuteBreakpoints))
    }
  }

  private fun LessonContext.evaluateResultTask() {
    quickEvaluateTask(positionId = 4) { position ->
      text(LessonsBundle.message("debug.workflow.check.result", action("QuickEvaluateExpression")))
      proposeRestore {
        checkPositionOfEditor(LessonSample(afterFixText, position))
      }
    }
  }

  private fun TaskRuntimeContext.checkForBreakpoints(): TaskContext.RestoreNotification? {
    return if (lineWithBreakpoints() != setOf(logicalPosition.line)) {
      TaskContext.RestoreNotification(incorrectBreakPointsMessage) {
        runWriteAction {
          LessonManager.instance.clearRestoreMessage()
          XDebuggerUtilImpl.removeAllBreakpoints(project)
          FileDocumentManager.getInstance().getFile(editor.document)
          val line = logicalPosition.line
          val createPosition = XDebuggerUtil.getInstance().createPosition(virtualFile, line)
                               ?: error("Can't create source position: $line at $virtualFile")
          XBreakpointUtil.toggleLineBreakpoint(project, createPosition, false, editor, false, false, true)
          //breakpointManager.addLineBreakpoint()
        }
      }
    }
    else null
  }

  private fun TaskRuntimeContext.checkDebugIsRunning(): TaskContext.RestoreNotification? {
    return if (XDebuggerManager.getInstance(project).currentSession == null) {
      TaskContext.RestoreNotification(LessonsBundle.message("debug.workflow.need.restart.lesson")) {
        CourseManager.instance.openLesson(project, this@CommonDebugLesson, LessonStartingWay.RESTORE_LINK)
      }
    }
    else null
  }

  protected abstract fun LessonContext.applyProgramChangeTasks()

  protected open fun LessonContext.restoreHotSwapStateInformer() = Unit

  private fun LessonContext.quickEvaluateTask(positionId: Int, textAndRestore: TaskContext.(LessonSamplePosition) -> Unit) {
    task("QuickEvaluateExpression") {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      val position = sample.getPosition(positionId)
      caret(position)
      trigger(it)
      textAndRestore(position)
      test { actions(it) }
    }
  }

  protected fun TaskContext.proposeModificationRestore(restoreText: String, checkDebugSession: Boolean = true) = proposeRestore {
    val caretOffset = editor.caretModel.offset
    val textLength = editor.document.textLength
    val restoreLength = restoreText.length
    val offset = caretOffset - (if (restoreLength <= textLength) 0 else restoreLength - textLength)

    checkExpectedStateOfEditor(LessonSample(restoreText, offset), false)
    ?: checkForBreakpoints()
    ?: if (checkDebugSession) checkDebugIsRunning() else null
  }

  private fun TaskContext.proposeSelectionChangeRestore(position: LessonSamplePosition) = proposeRestore {
    checkPositionOfEditor(LessonSample(sample.text, position))
    ?: checkForBreakpoints()
    ?: checkDebugIsRunning()
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("debug.workflow.help.link"),
         LessonUtil.getHelpLink("debugging-code.html")),
  )

  companion object {
    fun LessonContext.runToCursorTask(sample: LessonSample, afterFixText: String, debuggingMethodName: String) {
      val position = sample.getPosition(3)
      caret(position)

      task {
        if (InlayRunToCursorEditorListener.isInlayRunToCursorEnabled) triggerAndBorderHighlight {
          limitByVisibleRect = false
        }.componentPart l@{ ui: EditorComponentImpl ->
          if (ui.editor != editor) return@l null
          val line = editor.offsetToVisualLine(position.startOffset, true)
          val actionButtonSize = InlayRunToCursorEditorListener.ACTION_BUTTON_SIZE
          val y = editor.visualLineToY(line)
          return@l Rectangle(JBUI.scale(InlayRunToCursorEditorListener.NEGATIVE_INLAY_PANEL_SHIFT - 1), y - JBUI.scale(1),
                             JBUI.scale(actionButtonSize + 2), JBUI.scale(actionButtonSize + 2))
        }
      }

      actionTask("RunToCursor") {
        proposeRestore {
          checkPositionOfEditor(LessonSample(afterFixText, position))
        }
        val intro = LessonsBundle.message("debug.workflow.run.to.cursor.intro", code(debuggingMethodName), code("return"))
        val actionPart = LessonsBundle.message("debug.workflow.run.to.cursor.press", action(it))
        val alternative = if (InlayRunToCursorEditorListener.isInlayRunToCursorEnabled)
          " " + LessonsBundle.message("debug.workflow.run.to.cursor.alternative", LessonUtil.actionName(it))
        else ""
        val notePart = LessonsBundle.message("debug.workflow.run.to.cursor.note", LessonUtil.actionName(it))
        "$intro $actionPart$alternative $notePart"
      }
    }

    fun LessonContext.stopTask() {
      highlightButtonById("Stop")

      task("Stop") {
        text(LessonsBundle.message("debug.workflow.stop.debug",
                                   action(it), icon(AllIcons.Actions.Suspend)))
        stateCheck {
          XDebuggerManager.getInstance(project).currentSession == null
        }
        test { actions(it) }
      }
    }
  }
}


@Nls
private val incorrectBreakPointsMessage = LessonsBundle.message("debug.workflow.incorrect.breakpoints")

fun LessonContext.clearBreakpoints() {
  prepareRuntimeTask {
    XDebuggerUtilImpl.removeAllBreakpoints(project)
  }
}

fun LessonContext.runSample(@Language("devkit-action-id") actionId: String, @Nls message: String) {
  task {
    text(message, LearningBalloonConfig(Balloon.Position.below, 0, duplicateMessage = true))
    checkToolWindowState(actionId, true)
    test {
      ideFrame {
        highlightedArea.click()
      }
    }
  }
}

fun LessonContext.toggleBreakpointTask(sample: LessonSample?,
                                       logicalPosition: () -> LogicalPosition,
                                       checkLine: Boolean = true,
                                       breakpointXRange: (width: Int) -> IntRange = LessonUtil.breakpointXRange,
                                       useCheckByTimerInsteadOfStateCheck: Boolean = false,
                                       textContent: TaskContext.() -> Unit) {
  highlightBreakpointGutter(breakpointXRange, logicalPosition)

  task {
    transparentRestore = true
    textContent()

    val checkLambda: TaskRuntimeContext.() -> Boolean = {
      lineWithBreakpoints() == setOf(logicalPosition().line)
    }
    if (useCheckByTimerInsteadOfStateCheck) {
      timerCheck(checkState = checkLambda)
    }
    else {
      stateCheck(checkLambda)
    }

    proposeRestore {
      val breakpoints = lineWithBreakpoints()
      checkExpectedStateOfEditor(sample ?: previous.sample, checkPosition = checkLine)
      ?: if (breakpoints.isNotEmpty() && (breakpoints != setOf(logicalPosition().line))) {
        TaskContext.RestoreNotification(incorrectBreakPointsMessage, callback = restorePreviousTaskCallback)
      }
      else null
    }
    test { actions("ToggleLineBreakpoint") }
  }
}
