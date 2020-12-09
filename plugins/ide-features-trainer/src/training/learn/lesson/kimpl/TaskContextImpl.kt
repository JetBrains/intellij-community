// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.kimpl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorNotifications
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.intellij.lang.annotations.Language
import training.check.Check
import training.commands.kotlin.TaskContext
import training.commands.kotlin.TaskRuntimeContext
import training.commands.kotlin.TaskTestContext
import training.learn.ActionsRecorder
import training.learn.lesson.LessonManager
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
      this@TaskContextImpl.proposeRestoreCheck(restoreCheck)
      return@restoreState false
    }
  }

  private fun proposeRestoreCheck(restoreCheck: TaskRuntimeContext.() -> RestoreNotification?) {
    val file = lessonExecutor.virtualFile
    val proposal = restoreCheck(runtimeContext)
    if (proposal == null) {
      if (LessonManager.instance.shownRestoreNotification != null) {
        LessonManager.instance.clearRestoreMessage()
      }
    }
    else {
      if (proposal.message != LessonManager.instance.shownRestoreNotification?.message) {
        EditorNotifications.getInstance(runtimeContext.project).updateNotifications(file)
        LessonManager.instance.setRestoreNotification(proposal)
      }
    }
  }

  override fun text(@Language("HTML") text: String, useBalloon: LearningBalloonConfig?) {
    lessonExecutor.text(text)
    if (useBalloon != null) {
      val ui = runtimeContext.previous.ui as? JComponent ?: return
      LessonExecutorUtil.showBalloonMessage(text, ui, useBalloon, runtimeContext.taskDisposable)
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

  override fun runtimeText(callback: TaskRuntimeContext.() -> String?) {
    val text = callback(runtimeContext)
    if (text != null) {
      lessonExecutor.text(text)
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
    val check = getCheck(calculateState, checkState)
    addStep(recorder.futureActionAndCheckAround(actionId, check))
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

  private fun <T : Any?> getCheck(calculateState: TaskRuntimeContext.() -> T, checkState: TaskRuntimeContext.(T, T) -> Boolean): Check {
    return object : Check {
      var state: T? = null

      override fun before() {
        state = calculateAction()
      }

      override fun check(): Boolean = state?.let { checkState(runtimeContext, it, calculateAction()) } ?: false

      override fun set(project: Project, editor: Editor) {
        // do nothing
      }

      // Some checks are needed to be performed in EDT thread
      // For example, selection information  could not be got (for some magic reason) from another thread
      // Also we need to commit document
      private fun calculateAction() = WriteAction.computeAndWait<T, RuntimeException> {
        PsiDocumentManager.getInstance(runtimeContext.project).commitDocument(runtimeContext.editor.document)
        calculateState(runtimeContext)
      }
    }
  }

  override fun test(action: TaskTestContext.() -> Unit) {
    testActions.add(Runnable {
      DumbService.getInstance(runtimeContext.project).waitForSmartMode()
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