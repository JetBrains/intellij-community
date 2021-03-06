// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.tasks.TaskBundle
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
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
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiManager
import training.util.KeymapUtil
import training.util.WeakReferenceDelegator
import training.util.invokeActionForFocusContext
import java.awt.Rectangle
import javax.swing.JDialog
import javax.swing.text.JTextComponent

abstract class CommonDebugLesson(id: String) : KLesson(id, LessonsBundle.message("debug.workflow.lesson.name")) {
  protected abstract val sample: LessonSample
  protected abstract var logicalPosition: LogicalPosition
  protected abstract val configurationName: String
  protected abstract val quickEvaluationArgument: String
  protected abstract val expressionToBeEvaluated: String
  protected abstract val confNameForWatches: String
  protected abstract val debuggingMethodName: String
  protected abstract val methodForStepInto: String
  protected abstract val stepIntoDirection: String

  protected val afterFixText: String by lazy { sample.text.replaceFirst("[0]", "[1]") }

  protected var mayBeStopped: Boolean = false
  private var debugSession: XDebugSession? by WeakReferenceDelegator()

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    prepareTask()

    toggleBreakpointTask(sample, { logicalPosition }) {
      LessonsBundle.message("debug.workflow.toggle.breakpoint", action("ToggleLineBreakpoint"))
    }

    startDebugTask()

    returnToEditorTask()

    waitBeforeContinue(500)

    addToWatchTask()

    stepIntoTasks()

    waitBeforeContinue(500)

    quickEvaluateTask()

    fixTheErrorTask()

    applyProgramChangeTasks()

    stepOverTask()

    if (TaskTestContext.inTestMode) waitBeforeContinue(1000)

    resumeTask()

    muteBreakpointsTask()

    waitBeforeContinue(500)

    runToCursorTask()

    if (TaskTestContext.inTestMode) waitBeforeContinue(1000)

    evaluateExpressionTasks()

    stopTask()
  }

  private fun LessonContext.prepareTask() {
    var needToRun = false
    prepareRuntimeTask {
      val stopAction = ActionManager.getInstance().getAction("Stop")
      invokeActionForFocusContext(stopAction)
      runWriteAction {
        needToRun = !selectedNeedConfiguration() && !configureDebugConfiguration()
      }
    }

    if (needToRun) {
      // Normally this step should not be shown!
      task {
        text(LessonsBundle.message("debug.workflow.run.program", action("RunClass")))
        addFutureStep {
          subscribeForMessageBus(RunManagerListener.TOPIC, object : RunManagerListener {
            override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
              if (selectedNeedConfiguration()) {
                completeStep()
              }
            }
          })
        }
        proposeRestore {
          checkExpectedStateOfEditor(sample, false)
        }
      }
    }
  }

  private fun LessonContext.startDebugTask() {
    highlightButtonById("Debug")

    mayBeStopped = false
    task("Debug") {
      text(LessonsBundle.message("debug.workflow.start.debug", icon(AllIcons.Actions.StartDebugger), action(it)))
      addFutureStep {
        project.messageBus.connect(lessonDisposable).subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
          override fun processStarted(debugProcess: XDebugProcess) {
            mayBeStopped = false
            val debugSession = debugProcess.session
            (this@CommonDebugLesson).debugSession = debugSession

            debugSession.setBreakpointMuted(false)
            (debugSession as XDebugSessionImpl).setWatchExpressions(emptyList())
            debugSession.addSessionListener(object : XDebugSessionListener {
              override fun sessionPaused() {
                invokeLater { completeStep() }
              }
            }, lessonDisposable)
            debugSession.addSessionListener(object : XDebugSessionListener {
              override fun sessionStopped() {
                val activeToolWindow = LearningUiManager.activeToolWindow
                if (activeToolWindow != null && !mayBeStopped && LessonManager.instance.currentLesson == this@CommonDebugLesson) {
                  val notification = TaskContext.RestoreNotification(LessonsBundle.message("debug.workflow.need.restart.lesson")) {
                    CourseManager.instance.openLesson(activeToolWindow.project, this@CommonDebugLesson)
                  }
                  LessonManager.instance.setRestoreNotification(notification)
                }
              }
            }, lessonDisposable)
          }
        })
      }
      proposeModificationRestore(sample.text)
      test { actions(it) }
    }

    task {
      stateCheck {
        focusOwner is XDebuggerFramesList
      }
    }
  }

  private fun LessonContext.returnToEditorTask() {
    task("EditorEscape") {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(LessonsBundle.message("debug.workflow.return.to.editor", action(it)))
      stateCheck {
        focusOwner is EditorComponentImpl
      }
      proposeModificationRestore(sample.text)
      test {
        Thread.sleep(500)
        invokeActionViaShortcut("ESCAPE")
      }
    }
  }

  private fun LessonContext.addToWatchTask() {
    highlightButtonById("XDebugger.NewWatch")

    task("Debugger.AddToWatch") {
      val position = sample.getPosition(1)
      val needAddToWatch = position.selection?.let { pair -> sample.text.substring(pair.first, pair.second) }
                           ?: error("Invalid sample data")
      caret(position)
      val hasShortcut = KeymapUtil.getShortcutByActionId(it) != null
      val shortcut = if (hasShortcut) "" else " " + LessonsBundle.message("debug.workflow.consider.to.add.a.shortcut")

      text(LessonsBundle.message("debug.workflow.use.watches",
                                 strong(TaskBundle.message("debugger.watches")), icon(AllIcons.General.Add), action(it), shortcut))
      stateCheck {
        val watches = (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).watchesManager.getWatches(confNameForWatches)
        watches.any { watch -> watch.expression == needAddToWatch }
      }
      proposeRestore {
        checkPositionOfEditor(LessonSample(sample.text, position)) ?: checkForBreakpoints()
      }
      test { actions(it) }
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
                                 "<raw_action>$stepIntoDirection</raw_action>",
                                 action("EditorEnter")))
      stateCheck {
        val debugLine = debugSession?.currentStackFrame?.sourcePosition?.line
        val sampleLine = editor.offsetToLogicalPosition(sample.getPosition(2).startOffset).line
        debugLine == sampleLine
      }
      proposeModificationRestore(sample.text)
      test {
        Thread.sleep(500)
        invokeActionViaShortcut(if (stepIntoDirection == "→") "RIGHT" else "LEFT")
        invokeActionViaShortcut("ENTER")
      }
    }
  }

  private fun LessonContext.quickEvaluateTask() {
    task("QuickEvaluateExpression") {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      val position = sample.getPosition(2)
      caret(position)
      text(LessonsBundle.message("debug.workflow.quick.evaluate", code(quickEvaluationArgument), action(it)))
      trigger(it)
      proposeRestore {
        checkPositionOfEditor(LessonSample(sample.text, position)) ?: checkForBreakpoints()
      }
      test { actions(it) }
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
        invokeLater {
          WriteCommandAction.runWriteCommandAction(project) {
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
      LessonsBundle.message("debug.workflow.mute.breakpoints", icon(AllIcons.Debugger.MuteBreakpoints), action(it))
    }
  }

  private fun LessonContext.runToCursorTask() {
    val position = sample.getPosition(3)
    caret(position)

    highlightButtonById("RunToCursor")
    highlightLineNumberByOffset(position.startOffset)

    actionTask("RunToCursor") {
      proposeRestore {
        checkPositionOfEditor(LessonSample(afterFixText, position))
      }
      LessonsBundle.message("debug.workflow.run.to.cursor",
                            code(debuggingMethodName),
                            code("return"),
                            action(it),
                            icon(AllIcons.Actions.RunToCursor),
                            LessonUtil.actionName(it))
    }
  }

  private fun LessonContext.evaluateExpressionTasks() {
    highlightButtonById("EvaluateExpression")

    actionTask("EvaluateExpression") {
      proposeModificationRestore(afterFixText)
      LessonsBundle.message("debug.workflow.evaluate.expression", code("result"), code(expressionToBeEvaluated), action(it),
                            icon(AllIcons.Debugger.EvaluateExpression))
    }

    task(expressionToBeEvaluated) {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(LessonsBundle.message("debug.workflow.type.result", code(it),
                                 strong(XDebuggerBundle.message("xdebugger.evaluate.label.expression"))))
      stateCheck { checkWordInTextField(it) }
      proposeModificationRestore(afterFixText)
      test {
        type(it)
      }
    }

    task {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(LessonsBundle.message("debug.workflow.evaluate.it", LessonUtil.rawEnter(),
                                 strong(XDebuggerBundle.message("xdebugger.button.evaluate").dropMnemonic())))
      triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { debugTree: XDebuggerTree ->
        val dialog = UIUtil.getParentOfType(JDialog::class.java, debugTree)
        val root = debugTree.root
        dialog?.title == XDebuggerBundle.message("xdebugger.evaluate.dialog.title") && root?.children?.size == 1
      }
      proposeModificationRestore(afterFixText)
      test(waitEditorToBeReady = false) {
        invokeActionViaShortcut("ENTER")
        invokeActionViaShortcut("ESCAPE")
      }
    }
  }

  private fun LessonContext.stopTask() {
    highlightButtonById("Stop")

    actionTask("Stop") {
      before { mayBeStopped = true }
      LessonsBundle.message("debug.workflow.stop.debug", action(it), icon(AllIcons.Actions.Suspend))
    }
  }

  private fun TaskRuntimeContext.checkForBreakpoints(): TaskContext.RestoreNotification? {
    return if (lineWithBreakpoints() != setOf(logicalPosition.line)) {
      TaskContext.RestoreNotification(incorrectBreakPointsMessage) {
        runWriteAction {
          LessonManager.instance.clearRestoreMessage()
          val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
          breakpointManager.allBreakpoints.forEach { breakpointManager.removeBreakpoint(it) }
          FileDocumentManager.getInstance().getFile(editor.document)
          val line = logicalPosition.line
          val createPosition = XDebuggerUtil.getInstance().createPosition(virtualFile, line)
                               ?: error("Can't create source position: $line at $virtualFile")
          XBreakpointUtil.toggleLineBreakpoint(project, createPosition, editor, false, false, true)
          //breakpointManager.addLineBreakpoint()
        }
      }
    }
    else null
  }

  protected abstract fun LessonContext.applyProgramChangeTasks()

  private fun LessonContext.highlightLineNumberByOffset(offset: Int) {
    task {
      triggerByPartOfComponent<EditorGutterComponentEx> l@{ ui ->
        if (CommonDataKeys.EDITOR.getData(ui as DataProvider) != editor) return@l null
        val line = editor.offsetToVisualLine(offset, true)
        val y = editor.visualLineToY(line)
        return@l Rectangle(2, y, ui.iconsAreaWidth + 6, editor.lineHeight)
      }
    }
  }

  private fun TaskRuntimeContext.selectedNeedConfiguration(): Boolean {
    val runManager = RunManager.getInstance(project)
    val selectedConfiguration = runManager.selectedConfiguration
    return selectedConfiguration?.name == configurationName
  }

  private fun TaskRuntimeContext.configureDebugConfiguration(): Boolean {
    val runManager = RunManager.getInstance(project)
    val dataContext = DataManagerImpl.getInstance().getDataContext(editor.component)
    val configurationsFromContext = ConfigurationContext.getFromContext(dataContext).configurationsFromContext

    val configuration = configurationsFromContext?.singleOrNull() ?: return false
    runManager.addConfiguration(configuration.configurationSettings)
    runManager.selectedConfiguration = configuration.configurationSettings
    return true
  }

  private fun TaskRuntimeContext.checkWordInTextField(expected: String): Boolean =
    (focusOwner as? JTextComponent)?.text?.replace(" ", "")?.toLowerCase() == expected.toLowerCase().replace(" ", "")

  protected fun TaskContext.proposeModificationRestore(restoreText: String) = proposeRestore {
    val caretOffset = editor.caretModel.offset
    val textLength = editor.document.textLength
    val restoreLength = restoreText.length
    val offset = caretOffset - (if (restoreLength <= textLength) 0 else restoreLength - textLength)

    checkExpectedStateOfEditor(LessonSample(restoreText, offset), false) ?: checkForBreakpoints()
  }
}


@Nls
private val incorrectBreakPointsMessage = LessonsBundle.message("debug.workflow.incorrect.breakpoints")

fun LessonContext.toggleBreakpointTask(sample: LessonSample,
                                       logicalPosition: () -> LogicalPosition,
                                       checkLine: Boolean = true,
                                       @Nls message: TaskContext.() -> String) {
  highlightBreakpointGutter(logicalPosition)

  prepareRuntimeTask {
    runWriteAction {
      val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
      breakpointManager.allBreakpoints.forEach { breakpointManager.removeBreakpoint(it) }
    }
  }

  task {
    text(message())
    stateCheck {
      lineWithBreakpoints() == setOf(logicalPosition().line)
    }
    proposeRestore {
      val breakpoints = lineWithBreakpoints()
      checkExpectedStateOfEditor(sample)
      ?: if (breakpoints.isNotEmpty() && (checkLine && breakpoints != setOf(logicalPosition().line))) {
        TaskContext.RestoreNotification(incorrectBreakPointsMessage, callback = restorePreviousTaskCallback)
      }
      else null
    }
    test { actions("ToggleLineBreakpoint") }
  }
}
