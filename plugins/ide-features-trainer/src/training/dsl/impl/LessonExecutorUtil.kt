// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import training.dsl.LearningBalloonConfig
import training.dsl.TaskContext
import training.dsl.TaskRuntimeContext
import training.learn.ActionsRecorder
import training.ui.LearningUiHighlightingManager
import training.ui.LessonMessagePane
import training.ui.MessageFactory
import training.ui.UISettings
import java.awt.*
import java.awt.event.ActionEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.tree.TreePath

internal data class TaskProperties(var hasDetection: Boolean = false, var messagesNumber: Int = 0)

internal object LessonExecutorUtil {
  /** This task is a real task with some event required and corresponding text. Used for progress indication. */
  fun taskProperties(taskContent: TaskContext.() -> Unit, project: Project): TaskProperties {
    val fakeTaskContext = ExtractTaskPropertiesContext(project)
    taskContent(fakeTaskContext)
    return TaskProperties(fakeTaskContext.hasDetection, fakeTaskContext.textCount)
  }

  fun textMessages(taskContent: TaskContext.() -> Unit, project: Project): List<String> {
    val fakeTaskContext = ExtractTextTaskContext(project)
    taskContent(fakeTaskContext)
    return fakeTaskContext.messages
  }

  fun getTaskCallInfo(): String? {
    return Exception().stackTrace.first { element ->
      element.toString().let { it.startsWith("training.learn.lesson") || !it.startsWith("training.") }
    }?.toString()
  }

  fun showBalloonMessage(text: String,
                         ui: JComponent,
                         balloonConfig: LearningBalloonConfig,
                         actionsRecorder: ActionsRecorder,
                         lessonExecutor: LessonExecutor) {
    if (balloonConfig.delayBeforeShow == 0) {
      showBalloonMessage(text, ui, balloonConfig, actionsRecorder, lessonExecutor, true)
    }
    else {
      val delayed = {
        if (!actionsRecorder.disposed) {
          showBalloonMessage(text, ui, balloonConfig, actionsRecorder, lessonExecutor, true)
        }
      }
      Alarm().addRequest(delayed, balloonConfig.delayBeforeShow)
    }
  }

  private fun showBalloonMessage(text: String,
                                 ui: JComponent,
                                 balloonConfig: LearningBalloonConfig,
                                 actionsRecorder: ActionsRecorder,
                                 lessonExecutor: LessonExecutor,
                                 useAnimationCycle: Boolean) {
    val messages = MessageFactory.convert(text)
    val messagesPane = LessonMessagePane(false)
    messagesPane.border = null
    messagesPane.setBounds(0, 0, balloonConfig.width.takeIf { it != 0 } ?: 500, 1000)
    messagesPane.isOpaque = false
    messagesPane.addMessage(messages, LessonMessagePane.MessageProperties(visualIndex = lessonExecutor.visualIndexNumber))

    val preferredSize = messagesPane.preferredSize

    val balloonPanel = JPanel()
    balloonPanel.isOpaque = false
    balloonPanel.layout = BoxLayout(balloonPanel, BoxLayout.Y_AXIS)
    balloonPanel.border = UISettings.instance.balloonAdditionalBorder
    val insets = UISettings.instance.balloonAdditionalBorder.borderInsets

    var height = preferredSize.height + insets.top + insets.bottom
    val width = (if (balloonConfig.width != 0) balloonConfig.width else (preferredSize.width + insets.left + insets.right + 6))
    balloonPanel.add(messagesPane)
    messagesPane.alignmentX = Component.LEFT_ALIGNMENT
    val gotItCallBack = balloonConfig.gotItCallBack
    val gotItButton = if (gotItCallBack != null) JButton().also {
      balloonPanel.add(it)
      it.alignmentX = Component.LEFT_ALIGNMENT
      it.border = EmptyBorder(JBUI.scale(10), UISettings.instance.balloonIndent, JBUI.scale(2), 0)
      it.background = Color(0, true)
      it.putClientProperty("gotItButton", true)
      it.putClientProperty("JButton.backgroundColor", UISettings.instance.tooltipButtonBackgroundColor)
      it.foreground = UISettings.instance.tooltipButtonForegroundColor
      it.action = object : AbstractAction(IdeBundle.message("got.it.button.name")) {
        override fun actionPerformed(e: ActionEvent?) {
          gotItCallBack()
        }
      }
      height += it.preferredSize.height
    }
    else null

    balloonPanel.preferredSize = Dimension(width, height)

    val balloon = JBPopupFactory.getInstance().createBalloonBuilder(balloonPanel)
      .setCloseButtonEnabled(false)
      .setAnimationCycle(if (useAnimationCycle) balloonConfig.animationCycle else 0)
      .setCornerToPointerDistance(balloonConfig.cornerToPointerDistance)
      .setPointerSize(Dimension(16, 8))
      .setHideOnAction(false)
      .setHideOnClickOutside(false)
      .setBlockClicksThroughBalloon(true)
      .setFillColor(UISettings.instance.tooltipBackgroundColor)
      .setBorderColor(UISettings.instance.tooltipBorderColor)
      .setHideOnCloseClick(false)
      .setDisposable(actionsRecorder)
      .createBalloon()


    balloon.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        val checkStopLesson = {
          lessonExecutor.taskInvokeLater {
            if (!actionsRecorder.disposed)
              showBalloonMessage(text, ui, balloonConfig, actionsRecorder, lessonExecutor, false)
          }
        }
        Alarm().addRequest(checkStopLesson, 500) // it is a hacky a little bit
      }
    })
    balloon.show(getPosition(ui, balloonConfig.side), balloonConfig.side)
    gotItButton?.requestFocus()
  }

  private fun getPosition(component: JComponent, side: Balloon.Position): RelativePoint {
    val visibleRect = LearningUiHighlightingManager.getRectangle(component) ?: component.visibleRect

    val xShift = when (side) {
      Balloon.Position.atLeft -> 0
      Balloon.Position.atRight -> visibleRect.width
      Balloon.Position.above, Balloon.Position.below -> visibleRect.width / 2
      else -> error("Unexpected balloon position")
    }

    val yShift = when (side) {
      Balloon.Position.above -> 0
      Balloon.Position.below -> visibleRect.height
      Balloon.Position.atLeft, Balloon.Position.atRight -> visibleRect.height / 2
      else -> error("Unexpected balloon position")
    }
    val point = Point(visibleRect.x + xShift, visibleRect.y + yShift)
    return RelativePoint(component, point)
  }
}


private class ExtractTaskPropertiesContext(override val project: Project) : TaskContext() {
  var textCount = 0
  var hasDetection = false

  override fun text(text: String, useBalloon: LearningBalloonConfig?) {
    if (useBalloon?.duplicateMessage == false) return
    textCount++
  }

  override fun trigger(actionId: String) {
    hasDetection = true
  }

  override fun trigger(checkId: (String) -> Boolean) {
    hasDetection = true
  }

  override fun <T> trigger(actionId: String, calculateState: TaskRuntimeContext.() -> T, checkState: TaskRuntimeContext.(T, T) -> Boolean) {
    hasDetection = true
  }

  override fun triggerStart(actionId: String, checkState: TaskRuntimeContext.() -> Boolean) {
    hasDetection = true
  }

  override fun triggers(vararg actionIds: String) {
    hasDetection = true
  }

  override fun stateCheck(checkState: TaskRuntimeContext.() -> Boolean): CompletableFuture<Boolean> {
    hasDetection = true
    return CompletableFuture<Boolean>()
  }

  override fun <T : Any> stateRequired(requiredState: TaskRuntimeContext.() -> T?): Future<T> {
    hasDetection = true
    return CompletableFuture()
  }

  override fun timerCheck(delayMillis: Int, checkState: TaskRuntimeContext.() -> Boolean): CompletableFuture<Boolean> {
    hasDetection = true
    return CompletableFuture()
  }

  override fun addFutureStep(p: DoneStepContext.() -> Unit) {
    hasDetection = true
  }

  override fun addStep(step: CompletableFuture<Boolean>) {
    hasDetection = true
  }

  @Suppress("OverridingDeprecatedMember")
  override fun <T : Component> triggerByFoundPathAndHighlightImpl(componentClass: Class<T>,
                                                                  highlightBorder: Boolean,
                                                                  highlightInside: Boolean,
                                                                  usePulsation: Boolean,
                                                                  clearPreviousHighlights: Boolean,
                                                                  selector: ((candidates: Collection<T>) -> T?)?,
                                                                  rectangle: TaskRuntimeContext.(T) -> Rectangle?) {
    hasDetection = true
  }

  @Suppress("OverridingDeprecatedMember")
  override fun <ComponentType : Component> triggerByUiComponentAndHighlightImpl(componentClass: Class<ComponentType>,
                                                                                highlightBorder: Boolean,
                                                                                highlightInside: Boolean,
                                                                                usePulsation: Boolean,
                                                                                clearPreviousHighlights: Boolean,
                                                                                selector: ((candidates: Collection<ComponentType>) -> ComponentType?)?,
                                                                                finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean) {
    hasDetection = true
  }

  override fun triggerByFoundListItemAndHighlight(options: LearningUiHighlightingManager.HighlightingOptions,
                                                  checkList: TaskRuntimeContext.(list: JList<*>) -> Int?) {
    hasDetection = true
  }

  override fun triggerByFoundPathAndHighlight(options: LearningUiHighlightingManager.HighlightingOptions,
                                              checkTree: TaskRuntimeContext.(tree: JTree) -> TreePath?) {
    hasDetection = true
  }

  override fun action(actionId: String): String = "" //Doesn't matter what to return

  override fun code(sourceSample: String): String = "" //Doesn't matter what to return

  override fun strong(text: String): String = "" //Doesn't matter what to return

  override fun icon(icon: Icon): String = "" //Doesn't matter what to return
}

private class ExtractTextTaskContext(override val project: Project) : TaskContext() {
  val messages = ArrayList<String>()
  override fun text(text: String, useBalloon: LearningBalloonConfig?) {
    if (useBalloon?.duplicateMessage == false) return
    messages.add(text)
  }
}
