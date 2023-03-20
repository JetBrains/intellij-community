// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.dsl.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import training.dsl.*
import training.learn.ActionsRecorder
import training.ui.LearningUiHighlightingManager
import training.ui.MessageFactory
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTree
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
    val textBuilder = MessageFactory.convertToGotItFormat(text)
    val balloonBuilder = GotItComponentBuilder(textBuilder)
    if (balloonConfig.width > 0) {
      balloonBuilder.withMaxWidth(JBUI.scale(balloonConfig.width))
    }
    val balloon: Balloon = balloonBuilder
      .withStepNumber(lessonExecutor.visualIndexNumber)
      .showButton(balloonConfig.gotItCallBack != null)
      .onButtonClick { balloonConfig.gotItCallBack?.invoke() }
      .requestFocus(balloonConfig.gotItCallBack != null)
      .withContrastColors(true)
      .build(actionsRecorder) {
        setCornerToPointerDistance(JBUI.scale(balloonConfig.cornerToPointerDistance))
        setAnimationCycle(if (useAnimationCycle) balloonConfig.animationCycle else 0)
        setCloseButtonEnabled(false)
        setHideOnCloseClick(false)
      }

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
    textCount += text.split("\n").size
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

  override fun triggerUI(parameters: HighlightTriggerParametersContext.() -> Unit): HighlightingTriggerMethods {
    hasDetection = true
    return super.triggerUI(parameters)
  }

  @Suppress("OverridingDeprecatedMember")
  override fun <T : Component> triggerByPartOfComponentImpl(componentClass: Class<T>,
                                                            options: LearningUiHighlightingManager.HighlightingOptions,
                                                            selector: ((candidates: Collection<T>) -> T?)?,
                                                            rectangle: TaskRuntimeContext.(T) -> Rectangle?) {
    hasDetection = true
  }

  @Suppress("OverridingDeprecatedMember")
  override fun <ComponentType : Component> triggerByUiComponentAndHighlightImpl(componentClass: Class<ComponentType>,
                                                                                options: LearningUiHighlightingManager.HighlightingOptions,                                                                                selector: ((candidates: Collection<ComponentType>) -> ComponentType?)?,
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
