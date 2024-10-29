// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.dsl

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.documentation.DocumentationEditorPane
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.ui.UIExperiment
import com.intellij.execution.ui.layout.impl.RunnerLayoutSettings
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScreenUtil
import com.intellij.ui.content.Content
import com.intellij.usageView.UsageViewContentManager
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.impl.ui.XDebuggerEmbeddedComboBox
import org.assertj.swing.timing.Timeout
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import training.FeaturesTrainerIcons
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.lang.LangManager
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.ui.*
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.LessonEndInfo
import training.util.getActionById
import training.util.learningToolWindow
import training.util.surroundWithNonBreakSpaces
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JWindow
import javax.swing.KeyStroke

object LessonUtil {
  val productName: String get() {
      return ApplicationNamesInfo.getInstance().fullProductName
    }

  fun getHelpLink(topic: String): String  = getHelpLink(null, topic)

  fun getHelpLink(ide: String?, topic: String): String {
    val helpIdeName: String = ide ?: when (val name = ApplicationNamesInfo.getInstance().productName) {
      "GoLand" -> "go"
      "RubyMine" -> "ruby"
      "AppCode" -> "objc"
      else -> name.lowercase(Locale.ENGLISH)
    }
    return "https://www.jetbrains.com/help/$helpIdeName/$topic"
  }

  fun hideStandardToolwindows(project: Project) {
    val windowManager = ToolWindowManagerEx.getInstanceEx(project)
    for (window in windowManager.toolWindows) {
      if (window.id != LEARN_TOOL_WINDOW_ID) {
        window.hide()
      }
    }
  }

  fun insertIntoSample(sample: LessonSample, inserted: String): String {
    return sample.text.substring(0, sample.startOffset) + inserted + sample.text.substring(sample.startOffset)
  }

  /**
   * Checks that user edited sample text, moved caret to any place of editor or changed selection
   */
  fun TaskContext.restoreIfModifiedOrMoved(sample: LessonSample? = null) {
    proposeRestore {
      checkPositionOfEditor(sample ?: previous.sample)
    }
  }

  /**
   * Checks that user edited sample text, moved caret to any place of editor or changed selection
   */
  fun TaskContext.restoreIfModified(sample: LessonSample? = null) {
    proposeRestore {
      checkExpectedStateOfEditor(sample ?: previous.sample, false)
    }
  }

  /**
   * Checks that user edited sample text or moved caret outside of [possibleCaretArea] text
   */
  fun TaskContext.restoreIfModifiedOrMovedIncorrectly(possibleCaretArea: String, sample: LessonSample? = null) {
    proposeRestore {
      checkPositionOfEditor(sample ?: previous.sample) {
        checkCaretOnText(possibleCaretArea)
      }
    }
  }

  fun TaskRuntimeContext.checkPositionOfEditor(sample: LessonSample,
                                               checkCaret: TaskRuntimeContext.(LessonSample) -> Boolean = { checkCaretValid(it) }
  ): TaskContext.RestoreNotification? {
    return checkExpectedStateOfEditor(sample, false)
           ?: if (!checkCaret(sample)) sampleRestoreNotification(TaskContext.CaretRestoreProposal, sample) else null
  }

  private fun TaskRuntimeContext.checkCaretValid(sample: LessonSample): Boolean {
    val selection = sample.selection
    val currentCaret = editor.caretModel.currentCaret
    return if (selection != null && selection.first != selection.second) {
      currentCaret.selectionStart == selection.first && currentCaret.selectionEnd == selection.second
    }
    else currentCaret.offset == sample.startOffset
  }

  private fun TaskRuntimeContext.checkCaretOnText(text: String): Boolean {
    val caretOffset = editor.caretModel.offset
    val textStartOffset = editor.document.charsSequence.indexOf(text)
    if (textStartOffset == -1) return false
    val textEndOffset = textStartOffset + text.length
    return caretOffset in textStartOffset..textEndOffset
  }

  fun TaskRuntimeContext.checkExpectedStateOfEditor(sample: LessonSample,
                                                    checkPosition: Boolean = true,
                                                    checkModification: (String) -> Boolean = { it.isEmpty() }): TaskContext.RestoreNotification? {
    val prefix = sample.text.substring(0, sample.startOffset)
    val postfix = sample.text.substring(sample.startOffset)

    val docText = editor.document.charsSequence
    val message = if (docText.startsWith(prefix) && docText.endsWith(postfix)) {
      val middle = docText.subSequence(prefix.length, docText.length - postfix.length).toString()
      if (checkModification(middle)) {
        val offset = editor.caretModel.offset
        if (!checkPosition || (prefix.length <= offset && offset <= prefix.length + middle.length)) {
          null
        }
        else {
          TaskContext.CaretRestoreProposal
        }
      }
      else {
        TaskContext.ModificationRestoreProposal
      }
    }
    else {
      TaskContext.ModificationRestoreProposal
    }

    return if (message != null) sampleRestoreNotification(message, sample) else null
  }

  fun TaskRuntimeContext.sampleRestoreNotification(@Nls message: String, sample: LessonSample) =
    TaskContext.RestoreNotification(message) { setSample(sample) }

  fun TaskRuntimeContext.checkEditorModification(sample: LessonSample,
                                                 modificationPositionId: Int,
                                                 needChange: String): Boolean {
    val startOfChange = sample.getPosition(modificationPositionId).startOffset
    val sampleText = sample.text
    val prefix = sampleText.substring(0, startOfChange)
    val suffix = sampleText.substring(startOfChange, sampleText.length)
    val current = editor.document.text

    if (!current.startsWith(prefix)) return false
    if (!current.endsWith(suffix)) return false

    val indexOfSuffix = current.indexOf(suffix)
    if (indexOfSuffix < startOfChange) return false

    val change = current.substring(startOfChange, indexOfSuffix)

    return change.replace(" ", "") == needChange
  }

  fun findItem(ui: JList<*>, checkList: (item: Any) -> Boolean): Int? {
    for (i in 0 until ui.model.size) {
      val elementAt = ui.model.getElementAt(i)
      if (elementAt != null && checkList(elementAt)) {
        return i
      }
    }
    return null
  }

  fun setEditorReadOnly(editor: Editor) {
    if (editor !is EditorEx) return
    editor.isViewer = true
    EditorModificationUtil.setReadOnlyHint(editor, LearnBundle.message("learn.task.read.only.hint"))
  }

  fun actionName(actionId: String): @NlsActions.ActionText String {
    val name = getActionById(actionId).templatePresentation.text?.replace("...", "")
    return "<strong>${name}</strong>"
  }

  /**
   * Use constants from [java.awt.event.KeyEvent] as keyCode.
   * For example: rawKeyStroke(KeyEvent.VK_SHIFT)
   */
  fun rawKeyStroke(keyCode: Int): String = rawKeyStroke(KeyStroke.getKeyStroke(keyCode, 0))

  fun rawKeyStroke(keyStroke: KeyStroke): String {
    return "<raw_shortcut>$keyStroke</raw_shortcut>".surroundWithNonBreakSpaces()
  }

  fun rawEnter(): String = rawKeyStroke(KeyEvent.VK_ENTER)

  fun rawCtrlEnter(): String {
    return if (ClientSystemInfo.isMac()) {
      rawKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK))
    }
    else {
      rawKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK))
    }
  }

  fun rawShift() = rawKeyStroke(KeyStroke.getKeyStroke("SHIFT"))

  val breakpointXRange: (width: Int) -> IntRange = { IntRange(14, it - 30) }

  fun LessonContext.highlightBreakpointGutter(xRange: (width: Int) -> IntRange = breakpointXRange,
                                              logicalPosition: () -> LogicalPosition

  ) {
    task {
      triggerAndBorderHighlight().componentPart l@{ ui: EditorGutterComponentEx ->
        if (ui.editor != editor) return@l null
        val y = editor.visualLineToY(editor.logicalToVisualPosition(logicalPosition()).line)
        val range = xRange(ui.width)
        return@l Rectangle(range.first, y, range.last - range.first + 1, editor.lineHeight)
      }
    }
  }

  fun LessonContext.highlightInEditor(charsToHighlight: String) {
    task {
      lateinit var rectangle: Rectangle
      before {
        val charsSequence = editor.document.charsSequence

        val startPoint = editor.offsetToXY(charsSequence.indexOf(charsToHighlight))
        val endPoint = editor.offsetToXY(charsToHighlight.let { charsSequence.indexOf(it) + it.length })

        rectangle = Rectangle(startPoint.x - 3, startPoint.y, endPoint.x - startPoint.x + 6,
                              endPoint.y - startPoint.y + editor.lineHeight)
      }
      triggerAndBorderHighlight().componentPart l@{ ui: EditorComponentImpl ->
        if (ui.editor != editor) return@l null
        rectangle
      }
    }
  }

  fun TaskContext.highlightRunGutter(highlightInside: Boolean = false, usePulsation: Boolean = false, singleLineGutter: Boolean = false) {
    triggerAndBorderHighlight {
      this.highlightInside = highlightInside
      this.usePulsation = usePulsation
    }.componentPart l@{ ui: EditorGutterComponentEx ->
      if (ui.editor != editor) return@l null
      val runGutterLines = (0 until editor.document.lineCount).mapNotNull { lineInd ->
        if (ui.getGutterRenderers(lineInd).any { (it as? LineMarkerInfo.LineMarkerGutterIconRenderer<*>)?.featureId == "run" })
          lineInd
        else null
      }
      val startLineY = editor.visualLineToY(runGutterLines.first())
      val endLineY = editor.visualLineToY(runGutterLines.last())
      val startX = if (singleLineGutter) 30 else 25
      Rectangle(startX, startLineY, ui.width - 40, endLineY - startLineY + editor.lineHeight)
    }
  }

  /**
   * Should be called after task with detection of UI element inside desired window to adjust
   * @return location of window before adjustment
   */
  fun TaskRuntimeContext.adjustPopupPosition(windowKey: String): Point? {
    val window = ComponentUtil.getWindow(previous.ui) ?: return null
    val previousWindowLocation = WindowStateService.getInstance(project).getLocation(windowKey)
    return if (adjustPopupPosition(project, window)) previousWindowLocation else null
  }

  fun restorePopupPosition(project: Project, windowKey: String, savedLocation: Point?) {
    if (savedLocation != null) invokeLater {
      WindowStateService.getInstance(project).putLocation(windowKey, savedLocation)
    }
  }

  fun adjustPopupPosition(project: Project, popupWindow: Window): Boolean {
    val learningToolWindow = learningToolWindow(project) ?: return false
    val learningComponent = learningToolWindow.component
    val learningRectangle = Rectangle(learningComponent.locationOnScreen, learningToolWindow.component.size)
    val popupBounds = popupWindow.bounds
    val screenRectangle = ScreenUtil.getScreenRectangle(learningComponent)

    if (!learningRectangle.intersects(popupBounds)) return false // ok, no intersection

    if (!screenRectangle.contains(learningRectangle)) return false // we can make some strange moves in this case

    if (learningRectangle.width + popupBounds.width > screenRectangle.width) return false // some huge sizes

    when (learningToolWindow.anchor) {
      ToolWindowAnchor.LEFT -> {
        val rightScreenBorder = screenRectangle.x + screenRectangle.width
        val expectedRightPopupBorder = learningRectangle.x + learningRectangle.width + popupBounds.width
        if (expectedRightPopupBorder > rightScreenBorder) {
          val mainWindow = UIUtil.getParentOfType(IdeFrameImpl::class.java, learningComponent) ?: return false
          mainWindow.location = Point(mainWindow.location.x - (expectedRightPopupBorder - rightScreenBorder), mainWindow.location.y)
          popupWindow.location = Point(rightScreenBorder - popupBounds.width, popupBounds.y)
        }
        else {
          popupWindow.location = Point(learningRectangle.x + learningRectangle.width, popupBounds.y)
        }
      }
      ToolWindowAnchor.RIGHT -> {
        val learningScreenOffset = learningRectangle.x - screenRectangle.x
        if (popupBounds.width > learningScreenOffset) {
          val mainWindow = UIUtil.getParentOfType(IdeFrameImpl::class.java, learningComponent) ?: return false
          mainWindow.location = Point(mainWindow.location.x + (popupBounds.width - learningScreenOffset), mainWindow.location.y)
          popupWindow.location = Point(screenRectangle.x, popupBounds.y)
        }
        else {
          popupWindow.location = Point(learningRectangle.x - popupBounds.width, popupBounds.y)
        }
      }
      else -> return false
    }
    return true
  }

  fun TaskRuntimeContext.adjustSearchEverywherePosition(popupWindow: JWindow, leftBorderText: String): Boolean {
    val indexOf = 4 + (editor.document.charsSequence.indexOf(leftBorderText).takeIf { it > 0 } ?: return false)
    val endOfEditorText = editor.offsetToXY(indexOf)

    val locationOnScreen = editor.contentComponent.locationOnScreen

    val leftBorder = Point(locationOnScreen.x + endOfEditorText.x, locationOnScreen.y + endOfEditorText.y)
    val screenRectangle = ScreenUtil.getScreenRectangle(leftBorder)


    val learningToolWindow = learningToolWindow(project) ?: return false
    if (learningToolWindow.anchor != ToolWindowAnchor.LEFT) return false

    val popupBounds = popupWindow.bounds

    if (popupBounds.x > leftBorder.x) return false // ok, no intersection

    val rightScreenBorder = screenRectangle.x + screenRectangle.width
    if (leftBorder.x + popupBounds.width > rightScreenBorder) {
      val mainWindow = UIUtil.getParentOfType(IdeFrameImpl::class.java, editor.contentComponent) ?: return false
      val offsetFromBorder = leftBorder.x - mainWindow.x
      val needToShiftWindowX = rightScreenBorder - offsetFromBorder - popupBounds.width
      if (needToShiftWindowX < screenRectangle.x) return false // cannot shift the window back
      mainWindow.location = Point(needToShiftWindowX, mainWindow.location.y)
      popupWindow.location = Point(needToShiftWindowX + offsetFromBorder, popupBounds.y)
    }
    else {
      popupWindow.location = Point(leftBorder.x, popupBounds.y)
    }
    return true
  }

  fun returnToWelcomeScreenRemark(): String {
    val isSingleProject = ProjectManager.getInstance().openProjects.size == 1
    return if (isSingleProject) LessonsBundle.message("onboarding.return.to.welcome.remark") else ""
  }

  fun showFeedbackNotification(lesson: Lesson, project: Project) {
    invokeLater {
      if (project.isDisposed) {
        return@invokeLater
      }
      lesson.module.primaryLanguage?.let { langSupport ->
        // exit link will show notification directly and reset this field to null
        langSupport.onboardingFeedbackData?.let {
          showOnboardingFeedbackNotification(project, it)
        }
        langSupport.onboardingFeedbackData = null
      }
    }
  }

  fun lastHighlightedUi(): JComponent? {
    return LearningUiHighlightingManager.highlightingComponents.getOrNull(0) as? JComponent
  }
}

fun LessonContext.firstLessonCompletedMessage() {
  text(LessonsBundle.message("goto.action.propose.to.go.next.new.ui", LessonUtil.rawEnter()))
}

fun LessonContext.highlightRunToolbar(highlightInside: Boolean = true, usePulsation: Boolean = true) {
  task {
    triggerAndBorderHighlight {
      this.highlightInside = highlightInside
      this.usePulsation = usePulsation
    }.component { toolbar: ActionToolbarImpl ->
      toolbar.place == ActionPlaces.NEW_UI_RUN_TOOLBAR
    }
  }
}

fun LessonContext.highlightDebugActionsToolbar(highlightInside: Boolean = false, usePulsation: Boolean = false) {
  task {
    // wait for the treads & variables tab to become selected
    // otherwise the incorrect toolbar can be highlighted in the next task
    triggerUI().component { ui: XDebuggerEmbeddedComboBox<XExpression> -> ui.isEditable }
  }

  waitBeforeContinue(defaultRestoreDelay)

  task {
    highlightToolbarWithAction(ActionPlaces.DEBUGGER_TOOLBAR, "Resume", highlightInside, usePulsation)
  }
}

fun LessonContext.highlightOldDebugActionsToolbar(highlightInside: Boolean = false, usePulsation: Boolean = false) {
  highlightDebugActionsToolbar(highlightInside, usePulsation)
  task {
    if (!ExperimentalUI.isNewUI() && !UIExperiment.isNewDebuggerUIEnabled()) {
      highlightToolbarWithAction(ActionPlaces.DEBUGGER_TOOLBAR, "ShowExecutionPoint",
                                 highlightInside, usePulsation, clearPreviousHighlights = false)
    }
  }
}

fun TaskContext.highlightToolbarWithAction(place: String,
                                           actionId: String,
                                           highlightInside: Boolean,
                                           usePulsation: Boolean,
                                           clearPreviousHighlights: Boolean = true) {
  val needAction = getActionById(actionId)
  triggerAndBorderHighlight {
    this.highlightInside = highlightInside
    this.usePulsation = usePulsation
    this.clearPreviousHighlights = clearPreviousHighlights
  }.component { ui: ActionToolbarImpl ->
    if (ui.size.let { it.width > 0 && it.height > 0 } && ui.place == place) {
      ui.components.filterIsInstance<ActionButton>().any { it.action == needAction }
    }
    else false
  }
}

fun TaskContext.proceedLink(additionalAbove: Int = 0) {
  val gotIt = CompletableFuture<Boolean>()
  runtimeText {
    removeAfterDone = true
    textProperties = TaskTextProperties(UISettings.getInstance().taskInternalParagraphAbove + additionalAbove, 12)
    LessonsBundle.message("proceed.to.the.next.step", LearningUiManager.addCallback { gotIt.complete(true) })
  }
  addStep(gotIt)
  test {
    clickLessonMessagePaneLink("Click to proceed")
  }
}

fun TaskContext.gotItStep(position: Balloon.Position,
                          width: Int,
                          @Nls text: String,
                          @Nls buttonText: String = IdeBundle.message("got.it.button.name"),
                          cornerToPointerDistance: Int = -1,
                          duplicateMessage: Boolean = true) {
  val gotIt = CompletableFuture<Boolean>()
  text(text, LearningBalloonConfig(position, width, duplicateMessage,
                                   cornerToPointerDistance = cornerToPointerDistance,
                                   buttonText = buttonText) {
    gotIt.complete(true)
  })
  addStep(gotIt)
  test(waitEditorToBeReady = false) {
    ideFrame { button(buttonText).click() }
  }
}

/**
 * Will click on the first occurrence of [linkText] in the [LessonMessagePane]
 */
fun TaskTestContext.clickLessonMessagePaneLink(linkText: String) {
  ideFrame {
    val lessonMessagePane = findComponentWithTimeout(defaultTimeout) { _: LessonMessagePane -> true }
    val offset = lessonMessagePane.text.indexOf(linkText)
    if (offset == -1) error("Not found '$linkText' in the LessonMessagePane")
    val rect = lessonMessagePane.modelToView2D(offset + linkText.length / 2)
    robot.click(lessonMessagePane, Point(rect.centerX.toInt(), rect.centerY.toInt()))
  }
}

fun TaskContext.proposeRestoreForInvalidText(needToType: String) {
  proposeRestore {
    checkExpectedStateOfEditor(previous.sample) {
      needToType.contains(it.replace(" ", ""))
    }
  }
}

fun TaskContext.checkToolWindowState(toolWindowId: String, isShowing: Boolean) {
  addFutureStep {
    subscribeForMessageBus(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow(toolWindowId)
        if (toolWindow == null && !isShowing || toolWindow?.isVisible == isShowing) {
          completeStep()
        }
      }
    })
  }
}

fun <L : Any> TaskRuntimeContext.subscribeForMessageBus(topic: Topic<L>, handler: L) {
  project.messageBus.connect(taskDisposable).subscribe(topic, handler)
}

fun TaskRuntimeContext.lineWithBreakpoints(): Set<Int> {
  val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
  return breakpointManager.allBreakpoints.filter {
    val file = FileDocumentManager.getInstance().getFile(editor.document)
    it.sourcePosition?.file == file
  }.mapNotNull {
    it.sourcePosition?.line
  }.toSet()
}

val defaultRestoreDelay: Int
  get() = Registry.intValue("ift.default.restore.delay")

/**
 * @param [restoreId] where to restore, `null` means the previous task
 * @param [restoreRequired] returns true iff restore is needed
 */
fun TaskContext.restoreAfterStateBecomeFalse(restoreId: TaskContext.TaskId? = null,
                                             restoreRequired: TaskRuntimeContext.() -> Boolean) {
  var restoreIsPossible = false
  restoreState(restoreId) {
    val required = restoreRequired()
    (restoreIsPossible && required).also { restoreIsPossible = restoreIsPossible || !required }
  }
}

fun TaskRuntimeContext.closeAllFindTabs() {
  val usageViewManager = UsageViewContentManager.getInstance(project)
  var selectedContent: Content?
  while (usageViewManager.selectedContent.also { selectedContent = it } != null) {
    usageViewManager.closeContent(selectedContent!!)
  }
}

fun @Nls String.dropMnemonic(): @Nls String {
  return TextWithMnemonic.parse(this).dropMnemonic(true).text
}


fun TaskContext.waitSmartModeStep() {
  val future = CompletableFuture<Boolean>()
  addStep(future)
  before {
    DumbService.getInstance(project).runWhenSmart {
      future.complete(true)
    }
  }
}

private val seconds01 = Timeout.timeout(1, TimeUnit.SECONDS)

fun LessonContext.showWarningIfInplaceRefactoringsDisabled() {
  task {
    val step = stateCheck {
      EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled
    }
    val callbackId = LearningUiManager.addCallback {
      EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled = true
      step.complete(true)
    }
    showWarning(LessonsBundle.message("refactorings.change.settings.warning.message", action("ShowSettings"),
                                      strong(OptionsBundle.message("configurable.group.editor.settings.display.name")),
                                      strong(ApplicationBundle.message("title.code.editing")),
                                      strong(ApplicationBundle.message("radiobutton.rename.local.variables.inplace")),
                                      strong(ApplicationBundle.message("radiogroup.rename.local.variables").dropLast(1)),
                                      callbackId)
    ) {
      !EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled
    }
  }
}

fun LessonContext.restoreRefactoringOptionsInformer() {
  if (EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled) return
  restoreChangedSettingsInformer {
    EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled = false
  }
}

fun LessonContext.restoreChangedSettingsInformer(restoreSettings: () -> Unit) {
  task {
    runtimeText {
      val newMessageIndex = LessonManager.instance.messagesNumber()
      val callbackId = LearningUiManager.addCallback {
        restoreSettings()
        LessonManager.instance.removeMessageAndRepaint(newMessageIndex)
      }
      LessonsBundle.message("restore.settings.informer", callbackId)
    }
  }
}

fun LessonContext.highlightButtonById(actionId: String,
                                      highlightInside: Boolean = true,
                                      usePulsation: Boolean = true,
                                      clearHighlights: Boolean = true,
                                      additionalContent: (TaskContext.() -> Unit)? = null) {
  val needToFindButton = getActionById(actionId)

  task {
    val feature: CompletableFuture<Boolean> = CompletableFuture()
    transparentRestore = true
    before {
      if (clearHighlights) {
        LearningUiHighlightingManager.clearHighlights()
      }
      invokeInBackground {
        val result = try {
          LearningUiUtil.findAllShowingComponentWithTimeout(project, ActionButton::class.java, seconds01) { ui ->
            ui.action == needToFindButton
          }
        }
        catch (e: Throwable) {
          // Just go to the next step if we cannot find the needed button (when this method is used as pass trigger)
          taskInvokeLater { feature.complete(false) }
          throw IllegalStateException("Cannot find button for $actionId", e)
        }
        taskInvokeLater {
          feature.complete(result.isNotEmpty())
          for (button in result) {
            val options = LearningUiHighlightingManager.HighlightingOptions(highlightInside = highlightInside,
                                                                            usePulsation = usePulsation,
                                                                            clearPreviousHighlights = false)
            LearningUiHighlightingManager.highlightComponent(button, options)
          }
        }
      }
    }
    addStep(feature)
    additionalContent?.invoke(this)
  }
}

inline fun <reified ComponentType : Component> LessonContext.highlightAllFoundUi(
  clearPreviousHighlights: Boolean = true,
  highlightInside: Boolean = true,
  usePulsation: Boolean = false,
  crossinline finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean
) {
  val componentClass = ComponentType::class.java
  @Suppress("DEPRECATION")
  highlightAllFoundUiWithClass(componentClass, clearPreviousHighlights, highlightInside, usePulsation) {
    finderFunction(it)
  }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use inline form instead")
fun <ComponentType : Component> LessonContext.highlightAllFoundUiWithClass(componentClass: Class<ComponentType>,
                                                                           clearPreviousHighlights: Boolean,
                                                                           highlightInside: Boolean,
                                                                           usePulsation: Boolean,
                                                                           finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean) {
  prepareRuntimeTask {
    if (clearPreviousHighlights) LearningUiHighlightingManager.clearHighlights()
    invokeInBackground {
      val result =
        LearningUiUtil.findAllShowingComponentWithTimeout(project, componentClass, seconds01) { ui ->
          finderFunction(ui)
        }

      taskInvokeLater {
        for (ui in result) {
          val options = LearningUiHighlightingManager.HighlightingOptions(clearPreviousHighlights = false,
                                                                          highlightInside = highlightInside,
                                                                          usePulsation = usePulsation)
          LearningUiHighlightingManager.highlightComponent(ui, options)
        }
      }
    }
  }
}

fun TaskContext.triggerOnQuickDocumentationPopup() {
  triggerUI().component { _: DocumentationEditorPane -> true }
}

fun TaskContext.triggerOnEditorText(text: String, centerOffset: Int? = null, highlightBorder: Boolean = false) {
  triggerUI { this.highlightBorder = highlightBorder }.componentPart l@{ ui: EditorComponentImpl ->
    if (ui.editor != editor) return@l null
    val offset = editor.document.charsSequence.indexOf(text)
    if (offset < 0) return@l null
    if (centerOffset == null) {
      val point = editor.offsetToPoint2D(offset)
      val width = (editor as EditorImpl).charHeight * text.length
      Rectangle(point.x.toInt(), point.y.toInt(), width, editor.lineHeight)
    }
    else {
      val point = editor.offsetToPoint2D(offset + centerOffset)
      Rectangle(point.x.toInt() - 1, point.y.toInt(), 2, editor.lineHeight)
    }
  }
}

fun TaskContext.showBalloonOnHighlightingComponent(@Language("HTML") @Nls message: String,
                                                   position: Balloon.Position = Balloon.Position.below,
                                                   cornerToPointerDistance: Int = -1,
                                                   chooser: (List<JComponent>) -> JComponent? = { it.firstOrNull() }) {
  val highlightingComponent = chooser(LearningUiHighlightingManager.highlightingComponents.filterIsInstance<JComponent>())
  val useBalloon = LearningBalloonConfig(
    side = position,
    width = 0,
    highlightingComponent = highlightingComponent,
    duplicateMessage = false,
    cornerToPointerDistance = cornerToPointerDistance)
  text(message, useBalloon)
}

fun LessonContext.showInvalidDebugLayoutWarning() = task {
  val step = stateCheck {
    val viewImpl = getDebugFramesView()
    !(viewImpl?.isMinimizedInGrid ?: false)
  }
  val callbackId = LearningUiManager.addCallback {
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG)?.contentManager?.removeAllContents(true)
    val viewImpl = getDebugFramesView()
    viewImpl?.let { it.isMinimizedInGrid = false }
    step.complete(true)
  }
  val framesOptionName = strong(XDebuggerBundle.message("debugger.session.tab.frames.title"))
  showWarning(LessonsBundle.message("debug.workflow.frames.disabled.warning", callbackId, framesOptionName)) {
    val viewImpl = getDebugFramesView()
    viewImpl?.isMinimizedInGrid ?: false
  }
}

private fun getDebugFramesView() = RunnerLayoutSettings.getInstance().getLayout("Debug").getViewById("FrameContent")

fun LessonContext.sdkConfigurationTasks() {
  val langSupport = LangManager.getInstance().getLangSupport()
  if (langSupport != null && lesson.languageId == langSupport.primaryLanguage) {
    langSupport.sdkConfigurationTasks.invoke(this, lesson)
  }
}

fun TaskRuntimeContext.addNewRunConfigurationFromContext(editConfiguration: (RunConfiguration) -> Unit = {}) {
  val runManager = RunManager.getInstance(project) as RunManagerImpl
  val dataContext = DataManagerImpl.getInstance().getDataContext(editor.component)
  val configurationsFromContext = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN).configurationsFromContext
  val configurationSettings = configurationsFromContext?.singleOrNull() ?.configurationSettings ?: return
  val runConfiguration = configurationSettings.configuration.clone()
  editConfiguration(runConfiguration)
  val newSettings = RunnerAndConfigurationSettingsImpl(runManager, runConfiguration)
  runManager.addConfiguration(newSettings)
}

fun showEndOfLessonDialogAndFeedbackForm(onboardingLesson: Lesson, lessonEndInfo: LessonEndInfo, project: Project) {
  if (!lessonEndInfo.lessonPassed) {
    LessonUtil.showFeedbackNotification(onboardingLesson, project)
    return
  }
  val dataContextPromise = DataManager.getInstance().dataContextFromFocusAsync
  invokeLater {
    val result = MessageDialogBuilder.yesNoCancel(LessonsBundle.message("onboarding.finish.title"),
                                                  LessonsBundle.message("onboarding.finish.text",
                                                                            LessonUtil.returnToWelcomeScreenRemark()))
      .yesText(LessonsBundle.message("onboarding.finish.exit"))
      .noText(LessonsBundle.message("onboarding.finish.modules"))
      .icon(FeaturesTrainerIcons.PluginIcon)
      .show(project)

    when (result) {
      Messages.YES -> invokeLater {
        LessonManager.instance.stopLesson()
        val closeAction = getActionById("CloseProject")
        dataContextPromise.onSuccess { context ->
          invokeLater {
            val event = AnActionEvent.createFromAnAction(closeAction, null, ActionPlaces.LEARN_TOOLWINDOW, context)
            ActionUtil.performActionDumbAwareWithCallbacks(closeAction, event)
          }
        }
      }
      Messages.NO -> invokeLater {
        LearningUiManager.resetModulesView()
      }
    }
    if (result != Messages.YES) {
      LessonUtil.showFeedbackNotification(onboardingLesson, project)
    }
  }
}

