// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import org.intellij.lang.annotations.Language
import training.dsl.*
import training.learn.ActionsRecorder
import training.learn.course.KLesson
import training.learn.exceptons.NoTextEditor
import training.learn.lesson.LessonManager
import training.statistic.StatisticBase
import training.ui.LearnToolWindowFactory
import training.util.WeakReferenceDelegator
import java.awt.Component
import kotlin.math.max

class LessonExecutor(val lesson: KLesson, val project: Project, initialEditor: Editor?, val predefinedFile: VirtualFile?) : Disposable {
  private data class TaskInfo(val content: () -> Unit,
                              var restoreIndex: Int,
                              var taskProperties: TaskProperties?,
                              val taskContent: (TaskContext.() -> Unit)?,
                              var messagesNumberBeforeStart: Int = 0,
                              var rehighlightComponent: (() -> Component)? = null,
                              var userVisibleInfo: PreviousTaskInfo? = null,
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

  data class TaskData(var shouldRestoreToTask: (() -> TaskContext.TaskId?)? = null,
                      var delayMillis: Int = 0)

  private val taskActions: MutableList<TaskInfo> = ArrayList()

  var foundComponent: Component? = null
  var rehighlightComponent: (() -> Component)? = null

  private var currentRecorder: ActionsRecorder? = null
  private var currentRestoreRecorder: ActionsRecorder? = null
  internal var currentTaskIndex = 0
    private set

  private val parentDisposable: Disposable = LearnToolWindowFactory.learnWindowPerProject[project]?.parentDisposable ?: project

  // Is used from ui detection pooled thread
  @Volatile
  var hasBeenStopped = false
    private set

  init {
    Disposer.register(parentDisposable, this)
  }

  private fun addTaskAction(taskProperties: TaskProperties? = null, taskContent: (TaskContext.() -> Unit)? = null, content: () -> Unit) {
    val previousIndex = max(taskActions.size - 1, 0)
    taskActions.add(TaskInfo(content, previousIndex, taskProperties, taskContent))
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
    if (!hasBeenStopped) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      disposeRecorders()
      hasBeenStopped = true
      taskActions.clear()
    }
  }

  fun stopLesson() {
    Disposer.dispose(this)
  }

  private fun disposeRecorders() {
    currentRecorder?.let { Disposer.dispose(it) }
    currentRecorder = null
    currentRestoreRecorder?.let { Disposer.dispose(it) }
    currentRestoreRecorder = null
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

  private fun processNextTask(taskIndex: Int) {
    // ModalityState.current() or without argument - cannot be used: dialog steps can stop to work.
    // Good example: track of rename refactoring
    invokeLater(ModalityState.any()) {
      disposeRecorders()
      currentTaskIndex = taskIndex
      processNextTask2()
    }
  }

  private fun processNextTask2() {
    LessonManager.instance.clearRestoreMessage()
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (currentTaskIndex == taskActions.size) {
      LessonManager.instance.passLesson(lesson)
      disposeRecorders()
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

    if (taskContext.steps.isEmpty()) {
      processNextTask(currentTaskIndex + 1)
      return
    }

    chainNextTask(taskContext, recorder, taskCallbackData)

    processTestActions(taskContext)
  }

  internal fun applyRestore(taskContext: TaskContextImpl, restoreId: TaskContext.TaskId? = null) {
    taskContext.steps.forEach { it.cancel(true) }
    val restoreIndex = restoreId?.idx ?: taskActions[taskContext.taskIndex].restoreIndex
    val restoreInfo = taskActions[restoreIndex]
    restoreInfo.rehighlightComponent?.let { it() }
    LessonManager.instance.resetMessagesNumber(restoreInfo.messagesNumberBeforeStart)

    StatisticBase.logRestorePerformed(lesson, currentTaskIndex)
    processNextTask(restoreIndex)
  }

  /** @return a callback to clear resources used to track restore */
  private fun checkForRestore(taskContext: TaskContextImpl,
                              taskData: TaskData): () -> Unit {
    var clearRestore: () -> Unit = {}

    fun restore(restoreId: TaskContext.TaskId) {
      clearRestore()
      invokeLater(ModalityState.any()) { // restore check must be done after pass conditions (and they will be done during current event processing)
        if (canBeRestored(taskContext)) {
          applyRestore(taskContext, restoreId)
        }
      }
    }

    val shouldRestoreToTask = taskData.shouldRestoreToTask ?: return {}

    fun checkFunction(): Boolean {
      if (hasBeenStopped) {
        // Strange situation
        clearRestore()
        return false
      }

      val checkAndRestoreIfNeeded = {
        if (canBeRestored(taskContext)) {
          val restoreId = shouldRestoreToTask()
          if (restoreId != null) {
            restore(restoreId)
          }
        }
      }
      if (taskData.delayMillis == 0) {
        checkAndRestoreIfNeeded()
      }
      else {
        Alarm().addRequest(checkAndRestoreIfNeeded, taskData.delayMillis)
      }
      return false
    }

    // Not sure about use-case when we need to check restore at the start of current task
    // But it theoretically can be needed in case of several restores of dependent steps
    if (checkFunction()) return {}

    val restoreRecorder = ActionsRecorder(project, selectedEditor?.document, this)
    currentRestoreRecorder = restoreRecorder
    val restoreFuture = restoreRecorder.futureCheck { checkFunction() }
    clearRestore = {
      if (!restoreFuture.isDone) {
        restoreFuture.cancel(true)
      }
    }
    return clearRestore
  }

  private fun chainNextTask(taskContext: TaskContextImpl,
                            recorder: ActionsRecorder,
                            taskData: TaskData) {
    val clearRestore = checkForRestore(taskContext, taskData)

    recorder.tryToCheckCallback()

    taskContext.steps.forEach { step ->
      step.thenAccept {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val taskHasBeenDone = isTaskCompleted(taskContext)
        if (taskHasBeenDone) {
          clearRestore()
          LessonManager.instance.passExercise()
          val taskInfo = taskActions[currentTaskIndex]
          if (foundComponent == null) foundComponent = taskInfo.userVisibleInfo?.ui
          if (rehighlightComponent == null) rehighlightComponent = taskInfo.rehighlightComponent
          for (index in taskInfo.removeAfterDoneMessages) {
            LessonManager.instance.removeMessage(index)
          }
          taskInfo.taskProperties?.let { it.messagesNumber -= taskInfo.removeAfterDoneMessages.size }
          processNextTask(currentTaskIndex + 1)
        }
      }
    }
  }

  private fun isTaskCompleted(taskContext: TaskContextImpl) = taskContext.steps.all { it.isDone && it.get() }

  internal val taskCount: Int
    get() = taskActions.size

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

  fun text(@Language("HTML") text: String, removeAfterDone: Boolean = false) {
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
    LessonManager.instance.addMessage(text, !hasDetection)
  }

  private fun addAllInactiveMessages() {
    val tasksWithContent = taskActions.mapNotNull { it.taskContent }
    val messages = tasksWithContent.map { LessonExecutorUtil.textMessages(it, project) }.flatten()
    LessonManager.instance.addInactiveMessages(messages)
  }
}