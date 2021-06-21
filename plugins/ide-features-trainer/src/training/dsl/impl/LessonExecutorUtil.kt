// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl.impl

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import training.dsl.LearningBalloonConfig
import training.dsl.TaskContext
import training.dsl.TaskRuntimeContext
import training.learn.ActionsRecorder
import training.ui.LearningUiHighlightingManager
import training.ui.LessonMessagePane
import training.ui.MessageFactory
import training.ui.UISettings
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.*

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

  fun showBalloonMessage(text: String,
                         ui: JComponent,
                         balloonConfig: LearningBalloonConfig,
                         actionsRecorder: ActionsRecorder,
                         project: Project,
                         visualIndexNumber: Int) {
    if (balloonConfig.delayBeforeShow == 0) {
      showBalloonMessage(text, ui, balloonConfig, actionsRecorder, project, visualIndexNumber, true)
    }
    else {
      val delayed = {
        if (!actionsRecorder.disposed) {
          showBalloonMessage(text, ui, balloonConfig, actionsRecorder, project, visualIndexNumber, true)
        }
      }
      Alarm().addRequest(delayed, balloonConfig.delayBeforeShow)
    }
  }

  private fun showBalloonMessage(text: String,
                                 ui: JComponent,
                                 balloonConfig: LearningBalloonConfig,
                                 actionsRecorder: ActionsRecorder,
                                 project: Project,
                                 visualIndexNumber: Int,
                                 useAnimationCycle: Boolean) {
    val messages = MessageFactory.convert(text)
    val messagesPane = LessonMessagePane(false)
    messagesPane.setBounds(0, 0, balloonConfig.width.takeIf { it != 0 } ?: 500, 1000)
    messagesPane.isOpaque = false
    messagesPane.addMessage(messages, LessonMessagePane.MessageProperties(visualIndex = visualIndexNumber))

    val preferredSize = messagesPane.preferredSize

    val balloonPanel = JPanel()
    balloonPanel.isOpaque = false
    balloonPanel.layout = BoxLayout(balloonPanel, BoxLayout.Y_AXIS)
    var height = preferredSize.height
    val width = (if (balloonConfig.width != 0) balloonConfig.width else (preferredSize.width + 6))
    balloonPanel.add(messagesPane)
    val gotItCallBack = balloonConfig.gotItCallBack
    val gotItButton = if (gotItCallBack != null) JButton().also {
      balloonPanel.add(it)
      it.action = object : AbstractAction(UIBundle.message("got.it")) {
        override fun actionPerformed(e: ActionEvent?) {
          gotItCallBack()
        }
      }
      it.isSelected = true
      it.isFocusable = true
      height += it.preferredSize.height
    }
    else null
    gotItButton?.isSelected = true

    balloonPanel.preferredSize = Dimension(width, height)

    val balloon = JBPopupFactory.getInstance().createBalloonBuilder(balloonPanel)
      .setCloseButtonEnabled(false)
      .setAnimationCycle(if (useAnimationCycle) balloonConfig.animationCycle else 0)
      .setHideOnAction(false)
      .setHideOnClickOutside(false)
      .setBlockClicksThroughBalloon(true)
      .setFillColor(UISettings.instance.tooltipBackgroundColor)
      .setBorderColor(UISettings.instance.tooltipBackgroundColor)
      .setHideOnCloseClick(false)
      .setDisposable(actionsRecorder)
      .createBalloon()


    balloon.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        val checkStopLesson = {
          invokeLater {
            if (!actionsRecorder.disposed)
              showBalloonMessage(text, ui, balloonConfig, actionsRecorder, project, visualIndexNumber, false)
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

  override fun addFutureStep(p: DoneStepContext.() -> Unit) {
    hasDetection = true
  }

  override fun addStep(step: CompletableFuture<Boolean>) {
    hasDetection = true
  }

  @Suppress("OverridingDeprecatedMember")
  override fun triggerByUiComponentAndHighlight(findAndHighlight: TaskRuntimeContext.() -> () -> Component)  {
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
