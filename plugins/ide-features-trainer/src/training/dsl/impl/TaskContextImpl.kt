// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl.impl

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorNotifications
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.intellij.lang.annotations.Language
import training.dsl.*
import training.learn.ActionsRecorder
import training.learn.LearnBundle
import training.learn.lesson.LessonManager
import training.statistic.StatisticBase
import java.awt.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.JComponent

internal class TaskContextImpl(private val lessonExecutor: LessonExecutor,
                               private val recorder: ActionsRecorder,
                               val taskIndex: Int,
                               private val data: LessonExecutor.TaskData) : TaskContext() {
  override val project: Project
    get() = lessonExecutor.project

  override val taskId = TaskId(taskIndex)

  private val runtimeContext = TaskRuntimeContext(lessonExecutor,
                                                  recorder,
                                                  { lessonExecutor.applyRestore(this) },
                                                  { lessonExecutor.getUserVisibleInfo(taskIndex) })

  val steps: MutableList<CompletableFuture<Boolean>> = mutableListOf()

  val testActions: MutableList<Runnable> = mutableListOf()

  override fun before(preparation: TaskRuntimeContext.() -> Unit) {
    preparation(runtimeContext) // just call it here
  }

  override fun restoreState(restoreId: TaskId?, delayMillis: Int, restoreRequired: TaskRuntimeContext.() -> Boolean) {
    data.delayMillis = delayMillis
    val previous = data.shouldRestoreToTask
    val actualId = restoreId ?: TaskId(taskIndex - 1)
    data.shouldRestoreToTask = { previous?.let { it() } ?: if (restoreRequired(runtimeContext)) actualId else null }
  }

  override fun proposeRestore(restoreCheck: TaskRuntimeContext.() -> RestoreNotification?) {
    restoreState {
      // restoreState is used to trigger by any IDE state change and check restore proposal is needed
      this@TaskContextImpl.checkAndShowNotificationIfNeeded(needToLog = true, restoreCheck) {
        LessonManager.instance.setRestoreNotification(it)
      }
      return@restoreState false
    }
  }

  private fun checkEditor(): RestoreNotification? {
    fun restoreNotification(file: VirtualFile) =
      RestoreNotification(LearnBundle.message("learn.restore.notification.wrong.editor"),
                          LearnBundle.message("learn.restore.get.back.link.text")) {
        invokeLater {
          FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)
        }
      }

    val selectedTextEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
    if (lessonExecutor.lesson.lessonType.isSingleEditor) {
      if (selectedTextEditor != lessonExecutor.predefinedEditor) {
        val file = lessonExecutor.predefinedFile ?: return null
        return restoreNotification(file)
      }
    }
    else {
      val file = runtimeContext.previous.file ?: return null
      val currentFile = FileDocumentManager.getInstance().getFile(selectedTextEditor.document)
      if (file != currentFile) {
        return restoreNotification(file)
      }
    }
    return null
  }

  override fun showWarning(text: String, restoreTaskWhenResolved: Boolean, warningRequired: TaskRuntimeContext.() -> Boolean) {
    restoreState(taskId, delayMillis = defaultRestoreDelay) {
      val notificationRequired: TaskRuntimeContext.() -> RestoreNotification? = {
        if (warningRequired()) RestoreNotification(text) {} else null
      }
      val warningResolved = this@TaskContextImpl.checkAndShowNotificationIfNeeded(needToLog = !restoreTaskWhenResolved,
                                                                                  notificationRequired) {
        LessonManager.instance.setWarningNotification(it)
      }
      warningResolved && restoreTaskWhenResolved
    }
  }

  /**
   * Returns true if already shown notification has been cleared
   */
  private fun checkAndShowNotificationIfNeeded(needToLog: Boolean, notificationRequired: TaskRuntimeContext.() -> RestoreNotification?,
                                               setNotification: (RestoreNotification) -> Unit): Boolean {
    val file = lessonExecutor.virtualFile
    val proposal = checkEditor() ?: notificationRequired(runtimeContext)
    if (proposal == null) {
      if (LessonManager.instance.shownRestoreNotification != null) {
        LessonManager.instance.clearRestoreMessage()
        return true
      }
    }
    else {
      if (proposal.message != LessonManager.instance.shownRestoreNotification?.message) {
        EditorNotifications.getInstance(runtimeContext.project).updateNotifications(file)
        setNotification(proposal)
        if(needToLog) {
          StatisticBase.logRestorePerformed(lessonExecutor.lesson, lessonExecutor.currentTaskIndex)
        }
      }
    }
    return false
  }

  override fun text(@Language("HTML") text: String, useBalloon: LearningBalloonConfig?) {
    if (useBalloon == null || useBalloon.duplicateMessage)
      lessonExecutor.text(text)

    if (useBalloon != null) {
      val ui = useBalloon.highlightingComponent ?: runtimeContext.previous.ui as? JComponent ?: return
      LessonExecutorUtil.showBalloonMessage(text, ui, useBalloon, runtimeContext.actionsRecorder, lessonExecutor.project)
    }
  }


  override fun type(text: String) = before {
    invokeLater(ModalityState.current()) {
      WriteCommandAction.runWriteCommandAction(project) {
        val startOffset = editor.caretModel.offset
        editor.document.insertString(startOffset, text)
        editor.caretModel.moveToOffset(startOffset + text.length)
      }
    }
  }

  override fun runtimeText(callback: RuntimeTextContext.() -> String?) {
    val runtimeTextContext = RuntimeTextContext(runtimeContext)
    val text = callback(runtimeTextContext)
    if (text != null) {
      lessonExecutor.text(text, runtimeTextContext.removeAfterDone)
    }
  }

  override fun trigger(actionId: String) {
    addStep(recorder.futureAction(actionId))
  }

  override fun trigger(checkId: (String) -> Boolean) {
    addStep(recorder.futureAction(checkId))
  }

  override fun triggerStart(actionId: String, checkState: TaskRuntimeContext.() -> Boolean) {
    addStep(recorder.futureActionOnStart(actionId) { checkState(runtimeContext) })
  }

  override fun triggers(vararg actionIds: String) {
    addStep(recorder.futureListActions(actionIds.toList()))
  }

  override fun <T : Any?> trigger(actionId: String,
                                  calculateState: TaskRuntimeContext.() -> T,
                                  checkState: TaskRuntimeContext.(T, T) -> Boolean) {
    // Some checks are needed to be performed in EDT thread
    // For example, selection information  could not be got (for some magic reason) from another thread
    // Also we need to commit document
    fun calculateAction() = WriteAction.computeAndWait<T, RuntimeException> {
      PsiDocumentManager.getInstance(runtimeContext.project).commitDocument(runtimeContext.editor.document)
      calculateState(runtimeContext)
    }
    var state: T? = null
    addStep(recorder.futureActionAndCheckAround(actionId, { state = calculateAction()}) {
      state?.let { checkState(runtimeContext, it, calculateAction()) } ?: false
    })
  }

  override fun stateCheck(checkState: TaskRuntimeContext.() -> Boolean): CompletableFuture<Boolean> {
    val future = recorder.futureCheck { checkState(runtimeContext) }
    addStep(future)
    return future
  }

  override fun <T : Any> stateRequired(requiredState: TaskRuntimeContext.() -> T?): Future<T> {
    val result = CompletableFuture<T>()
    val future = recorder.futureCheck {
      val state = requiredState(runtimeContext)
      if (state != null) {
        result.complete(state)
        true
      }
      else {
        false
      }
    }
    addStep(future)
    return result
  }

  override fun addFutureStep(p: DoneStepContext.() -> Unit) {
    val future: CompletableFuture<Boolean> = CompletableFuture()
    addStep(future)
    p.invoke(DoneStepContext(future, runtimeContext))
  }

  override fun addStep(step: CompletableFuture<Boolean>) {
    steps.add(step)
  }

  override fun test(waitEditorToBeReady: Boolean, action: TaskTestContext.() -> Unit) {
    testActions.add(Runnable {
      DumbService.getInstance(runtimeContext.project).waitForSmartMode()
      // This wait implementation is quite ugly, but it works and it is needed in the test mode only. So should be ok for now.
      if (waitEditorToBeReady) {
        val psiFile = invokeAndWaitIfNeeded { PsiDocumentManager.getInstance(project).getPsiFile(runtimeContext.editor.document) } ?: return@Runnable
        var t = 0
        val step = 100
        while (!runReadAction { DaemonCodeAnalyzerEx.getInstanceEx(project).isErrorAnalyzingFinished(psiFile) }) {
          Thread.sleep(step.toLong())
          t += step
          if (t > 3000) return@Runnable
        }
      }

      TaskTestContext(runtimeContext).action()
    })
  }

  @Suppress("OverridingDeprecatedMember")
  override fun triggerByUiComponentAndHighlight(findAndHighlight: TaskRuntimeContext.() -> (() -> Component)) {
    val step = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().executeOnPooledThread {
      while (true) {
        if (lessonExecutor.hasBeenStopped) {
          step.complete(false)
          break
        }
        try {
          val highlightFunction = findAndHighlight(runtimeContext)
          invokeLater(ModalityState.any()) {
            lessonExecutor.foundComponent = highlightFunction()
            lessonExecutor.rehighlightComponent = highlightFunction
            step.complete(true)
          }
        }
        catch (e: WaitTimedOutError) {
          continue
        }
        catch (e: ComponentLookupException) {
          continue
        }
        break
      }
    }
    steps.add(step)
  }
}