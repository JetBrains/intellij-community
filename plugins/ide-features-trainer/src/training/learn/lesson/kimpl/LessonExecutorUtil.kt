// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.kimpl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import training.commands.kotlin.TaskContext
import training.commands.kotlin.TaskRuntimeContext
import training.commands.kotlin.TaskTestContext
import training.learn.LearnBundle
import training.learn.lesson.LessonManager
import training.ui.LearningUiHighlightingManager
import training.ui.LessonMessagePane
import training.ui.MessageFactory
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.event.ActionEvent
import java.lang.reflect.Modifier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.*

data class TaskProperties(var hasDetection: Boolean = false, var messagesNumber: Int = 0)

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

  fun showBalloonMessage(text: String, ui: JComponent, balloonConfig: LearningBalloonConfig, taskDisposable: Disposable) {
    val messages = MessageFactory.convert(text)
    val messagesPane = LessonMessagePane()
    messagesPane.addMessage(messages)
    val balloonPanel = JPanel()
    balloonPanel.layout = BoxLayout(balloonPanel, BoxLayout.Y_AXIS)
    //balloonPanel.layout = FlowLayout()
    balloonPanel.preferredSize = balloonConfig.dimension
    //balloonPanel.maximumSize = Dimension(500, 1000)
    balloonPanel.add(messagesPane)
    val stopButton = JButton()
    balloonPanel.add(stopButton)

    val balloon = JBPopupFactory.getInstance().createBalloonBuilder(balloonPanel)
      .setCloseButtonEnabled(false)
      .setAnimationCycle(0)
      .setHideOnClickOutside(false)
      .setBlockClicksThroughBalloon(true)
      .setBorderColor(JBColor(Color.BLACK, Color.WHITE))
      .setHideOnCloseClick(false)
      .setDisposable(taskDisposable)
      .createBalloon()
    val gotItCallBack = balloonConfig.gotItCallBack
    stopButton.action = if (gotItCallBack != null) {
      object : AbstractAction(UIBundle.message("got.it")) {
        override fun actionPerformed(e: ActionEvent?) {
          gotItCallBack()
        }
      }
    }
    else {
      object : AbstractAction(LearnBundle.message("learn.ui.button.stop.lesson")) {
        override fun actionPerformed(e: ActionEvent?) {
          LessonManager.instance.stopLesson()
        }
      }
    }
    balloon.show(getPosition(ui, balloonConfig.side), balloonConfig.side)
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

  override fun test(action: TaskTestContext.() -> Unit) = Unit // do nothing

  override fun action(actionId: String): String = "" //Doesn't matter what to return

  override fun code(sourceSample: String): String = "" //Doesn't matter what to return

  override fun strong(text: String): String = "" //Doesn't matter what to return

  override fun icon(icon: Icon): String = "" //Doesn't matter what to return
}

private class ExtractTextTaskContext(override val project: Project) : TaskContext() {
  val messages = ArrayList<String>()
  override fun text(text: String, useBalloon: LearningBalloonConfig?) {
    messages.add(text)
  }
}
