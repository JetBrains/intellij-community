// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.dsl

import com.intellij.codeInsight.documentation.DocumentationComponent
import com.intellij.codeInsight.documentation.DocumentationEditorPane
import com.intellij.codeInsight.documentation.QuickDocUtil.isDocumentationV2Enabled
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.ui.ScreenUtil
import com.intellij.ui.content.Content
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.usageView.UsageViewContentManager
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerManager
import org.assertj.swing.timing.Timeout
import org.jetbrains.annotations.Nls
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.ui.*
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.getActionById
import training.util.learningToolWindow
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JList
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
      if (window.id != LearnToolWindowFactory.LEARN_TOOL_WINDOW) {
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
    return " <raw_shortcut>$keyStroke</raw_shortcut> "
  }

  fun rawEnter(): String = rawKeyStroke(KeyEvent.VK_ENTER)

  fun rawCtrlEnter(): String {
    return if (SystemInfo.isMac) {
      rawKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK))
    }
    else {
      rawKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK))
    }
  }

  fun checkToolbarIsShowing(ui: ActionButton): Boolean {
    // Some buttons are duplicated to several tab-panels. It is a way to find an active one.
    val parentOfType = UIUtil.getParentOfType(JBTabsImpl.Toolbar::class.java, ui)
    val location = parentOfType?.location
    val x = location?.x
    return x != 0
  }


  val breakpointXRange: (width: Int) -> IntRange = { IntRange(20, it - 27) }

  fun LessonContext.highlightBreakpointGutter(xRange: (width: Int) -> IntRange = breakpointXRange,
                                              logicalPosition: () -> LogicalPosition

  ) {
    task {
      triggerByPartOfComponent<EditorGutterComponentEx> l@{ ui ->
        if (CommonDataKeys.EDITOR.getData(ui as DataProvider) != editor) return@l null
        val y = editor.visualLineToY(editor.logicalToVisualPosition(logicalPosition()).line)
        val range = xRange(ui.width)
        return@l Rectangle(range.first, y, range.last - range.first + 1, editor.lineHeight)
      }
    }
  }

  /**
   * Should be called after task with detection of UI element inside desired window to adjust
   * @return location of window before adjustment
   */
  fun TaskRuntimeContext.adjustPopupPosition(windowKey: String): Point? {
    val window = UIUtil.getWindow(previous.ui) ?: return null
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

  inline fun<reified T: Component> findUiParent(start: Component, predicate: (Component) -> Boolean): T? {
    if (start is T && predicate(start)) return start
    var ui: Container? = start.parent
    while (ui != null) {
      if (ui is T && predicate(ui)) {
        return ui
      }
      ui = ui.parent
    }
    return null
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

}

fun LessonContext.firstLessonCompletedMessage() {
  text(LessonsBundle.message("goto.action.propose.to.go.next.new.ui", LessonUtil.rawEnter()))
}

fun LessonContext.highlightDebugActionsToolbar() {
  task {
    before {
      LearningUiHighlightingManager.clearHighlights()
    }
    highlightToolbarWithAction(ActionPlaces.DEBUGGER_TOOLBAR, "Resume", clearPreviousHighlights = false)
    if (!Registry.`is`("debugger.new.tool.window.layout")) {
      highlightToolbarWithAction(ActionPlaces.DEBUGGER_TOOLBAR, "ShowExecutionPoint", clearPreviousHighlights = false)
    }
  }
}

private fun TaskContext.highlightToolbarWithAction(place: String, actionId: String, clearPreviousHighlights: Boolean = true) {
  val needAction = getActionById(actionId)
  triggerByUiComponentAndHighlight(usePulsation = true, clearPreviousHighlights = clearPreviousHighlights) { ui: ActionToolbarImpl ->
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
    textProperties = TaskTextProperties(UISettings.instance.taskInternalParagraphAbove + additionalAbove, 12)
    LessonsBundle.message("proceed.to.the.next.step", LearningUiManager.addCallback { gotIt.complete(true) })
  }
  addStep(gotIt)
  test {
    clickLessonMessagePaneLink("Click to proceed")
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

fun LessonContext.highlightButtonById(actionId: String, clearHighlights: Boolean = true): CompletableFuture<Boolean> {
  val feature: CompletableFuture<Boolean> = CompletableFuture()
  val needToFindButton = getActionById(actionId)
  prepareRuntimeTask {
    if (clearHighlights) {
      LearningUiHighlightingManager.clearHighlights()
    }
    invokeInBackground {
      val result = try {
        LearningUiUtil.findAllShowingComponentWithTimeout(project, ActionButton::class.java, seconds01) { ui ->
          ui.action == needToFindButton && LessonUtil.checkToolbarIsShowing(ui)
        }
      }
      catch (e: Throwable) {
        // Just go to the next step if we cannot find needed button (when this method is used as pass trigger)
        feature.complete(false)
        throw IllegalStateException("Cannot find button for $actionId", e)
      }
      taskInvokeLater {
        feature.complete(result.isNotEmpty())
        for (button in result) {
          val options = LearningUiHighlightingManager.HighlightingOptions(usePulsation = true, clearPreviousHighlights = false)
          LearningUiHighlightingManager.highlightComponent(button, options)
        }
      }
    }
  }
  return feature
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
  if (isDocumentationV2Enabled()) {
    triggerByUiComponentAndHighlight(false, false) { _: DocumentationEditorPane -> true }
  }
  else {
    triggerByUiComponentAndHighlight(false, false) { _: DocumentationComponent -> true }
  }
}
