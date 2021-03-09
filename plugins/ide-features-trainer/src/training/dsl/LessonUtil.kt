// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.usageView.UsageViewContentManager
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerManager
import org.fest.swing.timing.Timeout
import org.jetbrains.annotations.Nls
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiUtil
import training.util.KeymapUtil
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.lang.reflect.Modifier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JList
import javax.swing.KeyStroke

object LessonUtil {
  val productName: String
    get() = ApplicationNamesInfo.getInstance().fullProductName

  fun hideStandardToolwindows(project: Project) {
    val windowManager = ToolWindowManager.getInstance(project)
    val declaredFields = ToolWindowId::class.java.declaredFields
    for (field in declaredFields) {
      if (Modifier.isStatic(field.modifiers) && field.type == String::class.java) {
        val id = field.get(null) as String
        windowManager.getToolWindow(id)?.hide(null)
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
    val name = ActionManager.getInstance().getAction(actionId).templatePresentation.text?.replace("...", "") ?: error("No action with ID $actionId")
    return "<strong>${name}</strong>"
  }

  /**
   * Use constants from [java.awt.event.KeyEvent] as keyCode.
   * For example: rawKeyStroke(KeyEvent.VK_SHIFT)
   */
  fun rawKeyStroke(keyCode: Int): String {
    val keyStroke = KeymapUtil.getKeyStrokeText(KeyStroke.getKeyStroke(keyCode, 0))
    return "<raw_action>$keyStroke</raw_action>"
  }

  fun rawKeyStroke(keyStroke: KeyStroke): String {
    return "<raw_action>${KeymapUtil.getKeyStrokeText(keyStroke)}</raw_action>"
  }

  fun rawEnter(): String = rawKeyStroke(KeyEvent.VK_ENTER)

  fun rawCtrlEnter(): String {
    return "<raw_action>${if (SystemInfo.isMacOSMojave) "\u2318\u23CE" else "Ctrl + Enter"}</raw_action>"
  }

  fun checkToolbarIsShowing(ui: ActionButton): Boolean   {
    // Some buttons are duplicated to several tab-panels. It is a way to find an active one.
    val parentOfType = UIUtil.getParentOfType(JBTabsImpl.Toolbar::class.java, ui)
    val location = parentOfType?.location
    val x = location?.x
    return x != 0
  }

  fun LessonContext.highlightBreakpointGutter(logicalPosition: () -> LogicalPosition) {
    task {
      triggerByPartOfComponent<EditorGutterComponentEx> l@{ ui ->
        if (CommonDataKeys.EDITOR.getData(ui as DataProvider) != editor) return@l null
        val y = editor.visualLineToY(editor.logicalToVisualPosition(logicalPosition()).line)
        return@l Rectangle(20, y, ui.width - 26, editor.lineHeight)
      }
    }
  }
}

fun LessonContext.firstLessonCompletedMessage() {
  text(LessonsBundle.message("goto.action.propose.to.go.next.new.ui", LessonUtil.rawEnter()))
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

fun <L> TaskRuntimeContext.subscribeForMessageBus(topic: Topic<L>, handler: L) {
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

fun TaskContext.gotItStep(position: Balloon.Position, width: Int, @Nls text: String) {
  val gotIt = CompletableFuture<Boolean>()
  text(text, LearningBalloonConfig(position, width, false) { gotIt.complete(true) })
  addStep(gotIt)
}

fun String.dropMnemonic(): String {
  return TextWithMnemonic.parse(this).dropMnemonic(true).text
}

val seconds01 = Timeout.timeout(1, TimeUnit.SECONDS)

fun LessonContext.highlightButtonById(actionId: String): CompletableFuture<Boolean> {
  val feature: CompletableFuture<Boolean> = CompletableFuture()
  val needToFindButton = ActionManager.getInstance().getAction(actionId)
  prepareRuntimeTask {
    LearningUiHighlightingManager.clearHighlights()
    ApplicationManager.getApplication().executeOnPooledThread {
      val result =
        LearningUiUtil.findAllShowingComponentWithTimeout(null, ActionButton::class.java, seconds01) { ui ->
        ui.action == needToFindButton && LessonUtil.checkToolbarIsShowing(ui)
      }
      invokeLater {
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
  prepareRuntimeTask {
    if (clearPreviousHighlights) LearningUiHighlightingManager.clearHighlights()
    ApplicationManager.getApplication().executeOnPooledThread {
      val result =
        LearningUiUtil.findAllShowingComponentWithTimeout(null, ComponentType::class.java, seconds01) { ui ->
        finderFunction(ui)
      }

      invokeLater {
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
