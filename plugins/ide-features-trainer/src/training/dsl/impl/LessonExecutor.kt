// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.ActionUpdateEdtExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import org.intellij.lang.annotations.Language
import training.dsl.*
import training.learn.ActionsRecorder
import training.learn.course.KLesson
import training.learn.exceptons.NoTextEditor
import training.learn.lesson.LessonManager
import training.statistic.StatisticBase
import training.ui.LearningUiUtil
import training.util.WeakReferenceDelegator
import training.util.getLearnToolWindowForProject
import java.awt.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class LessonExecutor(val lesson: KLesson,
                              val project: Project,
                              initialEditor: Editor?,
                              val predefinedFile: VirtualFile?) : Disposable {
  private data class TaskInfo(val content: () -> Unit,
                              var restoreIndex: Int,
                              var taskProperties: TaskProperties?,
                              val taskContent: (TaskContext.() -> Unit)?,
                              val taskVisualIndex: Int?,
                              var messagesNumberBeforeStart: Int = 0,
                              var rehighlightComponent: (() -> Component?)? = null,
                              var userVisibleInfo: PreviousTaskInfo? = null,
                              var transparentRestore: Boolean? = null,
                              var highlightPreviousUi: Boolean? = null,
                              val removeAfterDoneMessages: MutableList<Int> = mutableListOf())

  var predefinedEditor: Editor? by WeakReferenceDelegator(initialEditor)
    private set

  private val selectedEditor: Editor?
    get() {
      val result = if (lesson.lessonType.isSingleEditor) predefinedEditor
      else FileEditorManager.getInstance(project).selectedTextEditor?.also {
        // We may need predefined editor in the multi-editor lesson in the start of the lesson.
        // It seems, there is a platform bug with no selected editor when the needed editor is actually opened.
        // But better do not use it later to avoid possible bugs.
        predefinedEditor = null
      } ?: predefinedEditor // It may be needed in the start of the lesson.
      return result?.takeIf { !it.isDisposed }
    }

  val editor: Editor
    get() = selectedEditor ?: throw NoTextEditor()

  /**
   * @property [shouldRestore] - function that should invoke some check for restore
   * and return the function that will apply restore if restore required, null otherwise.
   */
  data class TaskData(var shouldRestore: (() -> (() -> Unit)?)? = null,
                      var transparentRestore: Boolean? = null,
                      var highlightPreviousUi: Boolean? = null,
                      var propagateHighlighting: Boolean? = null,
                      var checkRestoreByTimer: Int? = null,
                      var delayBeforeRestore: Int = 0)

  private val taskActions: MutableList<TaskInfo> = ArrayList()

  var foundComponent: Component? = null
  var rehighlightComponent: (() -> Component?)? = null

  private var currentRecorder: ActionsRecorder? = null
  private var currentRestoreRecorder: ActionsRecorder? = null
  private var currentRestoreFuture: CompletableFuture<Boolean>? = null
  internal var currentTaskIndex = 0
    private set
  private var currentVisualIndex = 1

  private val parentDisposable: Disposable = getLearnToolWindowForProject(project)?.parentDisposable ?: project

  internal val visualIndexNumber: Int get() = taskActions[currentTaskIndex].taskVisualIndex ?: 0

  private var continueHighlighting: Ref<Boolean> = Ref(true)

  // Is used from ui detection pooled thread
  @Volatile
  var hasBeenStopped = false
    private set

  init {
    Disposer.register(parentDisposable, this)
  }

  private fun addTaskAction(taskProperties: TaskProperties? = null, taskContent: (TaskContext.() -> Unit)? = null, content: () -> Unit) {
    val previousIndex = max(taskActions.size - 1, 0)


    val taskVisualIndex = if (taskProperties != null && taskProperties.hasDetection && taskProperties.messagesNumber > 0) {
      currentVisualIndex++
    }
    else null
    taskActions.add(TaskInfo(content, previousIndex, taskProperties, taskContent, taskVisualIndex))
  }

  fun getUserVisibleInfo(index: Int): PreviousTaskInfo {
    return taskActions[index].userVisibleInfo ?: throw IllegalArgumentException("No information available for task $index")
  }

  fun waitBeforeContinue(delayMillis: Int) {
    addTaskAction {
      val action = {
        foundComponent = taskActions[currentTaskIndex].userVisibleInfo?.ui
        rehighlightComponent = taskActions[currentTaskIndex].rehighlightComponent
        processNextTask(currentTaskIndex + 1)
      }
      Alarm().addRequest(action, delayMillis)
    }
  }

  fun task(taskContent: TaskContext.() -> Unit) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val taskProperties = LessonExecutorUtil.taskProperties(taskContent, project)
    addTaskAction(taskProperties, taskContent) {
      val taskInfo = taskActions[currentTaskIndex]
      taskInfo.taskProperties?.messagesNumber?.let {
        LessonManager.instance.removeInactiveMessages(it)
        taskInfo.taskProperties?.messagesNumber = 0 // Here could be runtime messages
      }
      processTask(taskContent)
    }
  }

  override fun dispose() {
    if (hasBeenStopped) return
    ApplicationManager.getApplication().assertIsDispatchThread()
    val lessonPassed = currentTaskIndex == taskActions.size
    val visualIndex = if(lessonPassed) currentVisualIndex else (taskActions[currentTaskIndex].taskVisualIndex ?: 0)
    lesson.onStop(project, lessonPassed, currentTaskIndex, visualIndex)
    continueHighlighting.set(false)
    clearRestore()
    disposeRecorders()
    hasBeenStopped = true
    taskActions.clear()
  }

  fun stopLesson() {
    Disposer.dispose(this)
  }

  private fun disposeRecorders() {
    currentRecorder?.let { Disposer.dispose(it) }
    currentRecorder = null
    currentRestoreRecorder?.let { Disposer.dispose(it) }
    currentRestoreRecorder = null
    currentRestoreFuture = null
  }

  val virtualFile: VirtualFile
    get() = FileDocumentManager.getInstance().getFile(editor.document) ?: error("No Virtual File")

  fun startLesson() {
    addAllInactiveMessages()
    if (lesson.properties.canStartInDumbMode) {
      processNextTask(0)
    }
    else {
      DumbService.getInstance(project).runWhenSmart {
        if (!hasBeenStopped)
          processNextTask(0)
      }
    }
  }

  inline fun invokeInBackground(crossinline runnable: () -> Unit) {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        runnable()
      }
      catch (e: Throwable) {
        thisLogger().error(getLessonInfoString(), e)
      }
    }
  }

  inline fun taskInvokeLater(modalityState: ModalityState? = null, crossinline runnable: () -> Unit) {
    invokeLater(modalityState) {
      try {
        runnable()
      }
      catch (e: Throwable) {
        thisLogger().error(getLessonInfoString(), e)
      }
    }
  }

  private fun processNextTask(taskIndex: Int) {
    // ModalityState.current() or without argument - cannot be used: dialog steps can stop to work.
    // Good example: track of rename refactoring
    taskInvokeLater(ModalityState.any()) {
      disposeRecorders()
      continueHighlighting.set(false)
      continueHighlighting = Ref(true)
      currentTaskIndex = taskIndex
      processNextTask2()
    }
  }

  private fun processNextTask2() {
    LessonManager.instance.clearRestoreMessage()
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (currentTaskIndex == taskActions.size) {
      LessonManager.instance.passLesson(lesson)
      return
    }
    val taskInfo = taskActions[currentTaskIndex]
    taskInfo.messagesNumberBeforeStart = LessonManager.instance.messagesNumber()
    setUserVisibleInfo()
    taskInfo.content()
  }

  private fun setUserVisibleInfo() {
    val taskInfo = taskActions[currentTaskIndex]
    // do not reset information from the previous tasks if it is available already
    if (taskInfo.userVisibleInfo == null) {
      taskInfo.userVisibleInfo = object : PreviousTaskInfo {
        override val text: String = selectedEditor?.document?.text ?: ""
        override val position: LogicalPosition = selectedEditor?.caretModel?.currentCaret?.logicalPosition ?: LogicalPosition(0, 0)
        override val sample: LessonSample = selectedEditor?.let { prepareSampleFromCurrentState(it) } ?: parseLessonSample("")
        override val ui: Component? = foundComponent
        override val file: VirtualFile? = selectedEditor?.let { FileDocumentManager.getInstance().getFile(it.document) }
      }
      taskInfo.rehighlightComponent = rehighlightComponent
    }
    //Clear user visible information for later tasks
    for (i in currentTaskIndex + 1 until taskActions.size) {
      taskActions[i].userVisibleInfo = null
      taskActions[i].rehighlightComponent = null
    }
    foundComponent = null
    rehighlightComponent = null
  }

  private fun processTask(taskContent: TaskContext.() -> Unit) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val recorder = ActionsRecorder(project, selectedEditor?.document, this)
    currentRecorder = recorder
    val taskCallbackData = TaskData()
    val taskContext = TaskContextImpl(this, recorder, currentTaskIndex, taskCallbackData)
    taskContext.apply(taskContent)
    if (taskCallbackData.highlightPreviousUi == true) {
      val taskInfo = taskActions[currentTaskIndex]
      rehighlightFoundComponent(taskInfo.userVisibleInfo?.ui, taskInfo.rehighlightComponent)
    }

    if (taskContext.steps.isEmpty()) {
      processNextTask(currentTaskIndex + 1)
      return
    }

    chainNextTask(taskContext, recorder, taskCallbackData)

    processTestActions(taskContext)
  }

  /**
   * Will update the highlighting implemented in [highlightingFunction] if provided [component] is null or not showing
   * Rehighlighting will be stopped at the start of the next task (or after lesson end)
   */
  internal fun rehighlightFoundComponent(component: Component?, highlightingFunction: (() -> Component?)?) {
    if (highlightingFunction == null) return
    val condition = continueHighlighting
    ApplicationManager.getApplication().executeOnPooledThread {
      var ui = component
      while (ActionUpdateEdtExecutor.computeOnEdt { condition.get() } == true) {
        if (ui == null || !ui.isShowing) {
          ui = highlightingFunction()
        }
        Thread.sleep(300)
      }
    }
  }

  internal fun restoreByTimer(taskContext: TaskContextImpl, delayMillis: Int, restoreId: TaskContext.TaskId?) {
    val restore = {
      if (currentTaskIndex == taskContext.taskIndex) {
        applyRestore(taskContext, restoreId)
      }
    }
    Alarm().addRequest(restore, delayMillis)
  }

  internal fun applyRestore(taskContext: TaskContextImpl, restoreId: TaskContext.TaskId? = null) {
    clearRestore()
    disposeRecorders()
    taskContext.steps.forEach { it.cancel(true) }
    val restoreIndex = restoreId?.idx ?: taskActions[taskContext.taskIndex].restoreIndex
    for (info in taskActions.subList(restoreIndex + 1, taskActions.size)) {
      info.rehighlightComponent = null
      info.userVisibleInfo = null
      info.highlightPreviousUi = null
      info.removeAfterDoneMessages.clear()
    }
    val restoreInfo = taskActions[restoreIndex]
    val rehighlightComponentFn = restoreInfo.rehighlightComponent
    if (rehighlightComponentFn != null) {
      val feature = CompletableFuture<Boolean>()
      feature.whenCompleteAsync { _, _ -> invokeLater { finishRestore(restoreInfo, restoreIndex) } }
      feature.completeOnTimeout(true, LearningUiUtil.defaultComponentSearchShortTimeout.duration(), TimeUnit.MILLISECONDS)
      ApplicationManager.getApplication().executeOnPooledThread {
        rehighlightComponentFn()
        feature.complete(true)
      }
    } else {
      finishRestore(restoreInfo, restoreIndex)
    }
  }

  private fun finishRestore(restoreInfo: TaskInfo, restoreIndex: Int) {
    LessonManager.instance.resetMessagesNumber(restoreInfo.messagesNumberBeforeStart)

    StatisticBase.logRestorePerformed(lesson, currentTaskIndex)
    processNextTask(restoreIndex)
  }

  fun calculateRestoreIndex(): Int {
    var i = currentTaskIndex - 1
    while (i > 0 && taskActions[i].transparentRestore == true) i--
    return i
  }

  private fun checkForRestore(taskContext: TaskContextImpl, taskData: TaskData) {
    val shouldRestore = taskData.shouldRestore ?: return
    val restoreRecorder = ActionsRecorder(project, selectedEditor?.document, this)
    currentRestoreRecorder = restoreRecorder

    fun checkFunction() {
      if (hasBeenStopped) {
        // Strange situation
        clearRestore()
        return
      }
      val restoreFunction = shouldRestore() ?: return
      val restoreIfNeeded = {
        taskInvokeLater(ModalityState.any()) {
          if (canBeRestored(taskContext)) {
            restoreFunction()
          }
        }
      }
      if (taskData.delayBeforeRestore == 0) {
        restoreIfNeeded()
      }
      else {
        Alarm().addRequest(restoreIfNeeded, taskData.delayBeforeRestore)
      }
    }
    currentRestoreFuture = restoreRecorder.futureCheck { checkFunction(); false }
    taskData.checkRestoreByTimer?.let {
      restoreRecorder.timerCheck(it) { checkFunction(); false }
    }
    ?: checkFunction() // In case of regular restore check we need to check that restore should be performed just after another restore
  }

  private fun clearRestore() {
    val future = currentRestoreFuture ?: return
    if (!future.isDone) {
      future.cancel(true)
    }
    LessonManager.instance.clearRestoreMessage()
  }

  private fun chainNextTask(taskContext: TaskContextImpl,
                            recorder: ActionsRecorder,
                            taskData: TaskData) {
    val taskInfo = taskActions[currentTaskIndex]
    taskInfo.transparentRestore = taskData.transparentRestore
    taskInfo.highlightPreviousUi = taskData.highlightPreviousUi

    checkForRestore(taskContext, taskData)

    recorder.tryToCheckCallback()

    taskContext.steps.forEach { step ->
      step.thenAccept {
        try {
          stepHasBeenCompleted(taskContext, taskInfo)
        }
        catch (e: Throwable) {
          thisLogger().error("Step exception: ", e)
        }
      }
    }
  }

  private fun stepHasBeenCompleted(taskContext: TaskContextImpl, taskInfo: TaskInfo) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    // do not process the next step if step is not fully completed
    // or lesson has been stopped during task completion (dialogs in Recent Files and Restore removed code lessons)
    if (!isTaskCompleted(taskContext) || hasBeenStopped) return

    clearRestore()
    LessonManager.instance.passExercise()
    if (taskContext.propagateHighlighting != false) {
      if (foundComponent == null) foundComponent = taskInfo.userVisibleInfo?.ui
      if (rehighlightComponent == null) rehighlightComponent = taskInfo.rehighlightComponent
    }
    for (index in taskInfo.removeAfterDoneMessages) {
      LessonManager.instance.removeMessage(index)
    }
    taskInfo.taskProperties?.let { it.messagesNumber -= taskInfo.removeAfterDoneMessages.size }
    processNextTask(currentTaskIndex + 1)
  }

  private fun isTaskCompleted(taskContext: TaskContextImpl) = taskContext.steps.all { it.isDone && it.get() }

  private fun canBeRestored(taskContext: TaskContextImpl): Boolean {
    return !hasBeenStopped && taskContext.steps.any { !it.isCancelled && !it.isCompletedExceptionally && (!it.isDone || !it.get()) }
  }

  private fun processTestActions(taskContext: TaskContextImpl) {
    if (TaskTestContext.inTestMode && taskContext.testActions.isNotEmpty()) {
      LessonManager.instance.testActionsExecutor.execute {
        taskContext.testActions.forEach { it.run() }
      }
    }
  }

  fun text(@Language("HTML") text: String, removeAfterDone: Boolean = false, textProperties: TaskTextProperties? = null) {
    val taskInfo = taskActions[currentTaskIndex]

    if (removeAfterDone) taskInfo.removeAfterDoneMessages.add(LessonManager.instance.messagesNumber())

    // Here could be runtime messages
    taskInfo.taskProperties?.let {
      it.messagesNumber++
    }

    var hasDetection = false
    for (i in currentTaskIndex until taskActions.size) {
      if (taskInfo.taskProperties?.hasDetection == true) {
        hasDetection = true
        break
      }
    }
    // A little bit hacky here: visual index should be shown only for the first paragraph.
    // But it is passed here for all paragraphs.
    // But... LessonMessagePane will draw number only for the first active paragraph :)
    LessonManager.instance.addMessage(text, !hasDetection, taskInfo.taskVisualIndex, useInternalParagraphStyle = removeAfterDone,
                                      textProperties = textProperties)
  }

  private fun addAllInactiveMessages() {
    for (taskInfo in taskActions) {
      if (taskInfo.taskContent != null) {
        val textMessages = LessonExecutorUtil.textMessages(taskInfo.taskContent, project)
        for ((index, message) in textMessages.withIndex()) {
          LessonManager.instance.addInactiveMessage(message, taskInfo.taskVisualIndex?.takeIf { index == 0 })
        }
      }
    }
  }

  fun getLessonInfoString() = """lesson ID = ${lesson.id}, language ID = ${lesson.languageId}, taskId = $currentTaskIndex"""
}