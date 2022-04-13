// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl.impl

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import org.assertj.swing.exception.ComponentLookupException
import org.assertj.swing.exception.WaitTimedOutError
import org.intellij.lang.annotations.Language
import training.dsl.*
import training.learn.ActionsRecorder
import training.learn.LearnBundle
import training.learn.lesson.LessonManager
import training.statistic.LearningInternalProblems
import training.statistic.StatisticBase
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiUtil
import java.awt.Component
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTree
import javax.swing.tree.TreePath

internal class TaskContextImpl(private val lessonExecutor: LessonExecutor,
                               private val recorder: ActionsRecorder,
                               val taskIndex: Int,
                               private val data: LessonExecutor.TaskData) : TaskContext() {
  override val project: Project
    get() = lessonExecutor.project

  override val taskId = TaskId(taskIndex)

  override var transparentRestore: Boolean?
    get() = data.transparentRestore
    set(value) {
      data.transparentRestore = value
    }

  override var rehighlightPreviousUi: Boolean?
    get() = data.highlightPreviousUi
    set(value) {
      data.highlightPreviousUi = value
    }

  override var propagateHighlighting: Boolean?
    get() = data.propagateHighlighting
    set(value) {
      data.propagateHighlighting = value
    }

  private val runtimeContext = TaskRuntimeContext(lessonExecutor,
                                                  recorder,
                                                  { lessonExecutor.applyRestore(this) },
                                                  { lessonExecutor.getUserVisibleInfo(taskIndex) })

  val steps: MutableList<CompletableFuture<Boolean>> = mutableListOf()

  val testActions: MutableList<Runnable> = mutableListOf()

  override fun before(preparation: TaskRuntimeContext.() -> Unit) {
    preparation(runtimeContext) // just call it here
  }

  /**
   * To work properly should not be called after [proposeRestore] or [showWarning] calls (only before)
   */
  override fun restoreState(restoreId: TaskId?, delayMillis: Int, checkByTimer: Int?, restoreRequired: TaskRuntimeContext.() -> Boolean) {
    val actualId = restoreId ?: TaskId(lessonExecutor.calculateRestoreIndex())
    addRestoreCheck(delayMillis, checkByTimer, restoreRequired) {
      if (restoreRequired(runtimeContext)) {
        StatisticBase.logRestorePerformed(lessonExecutor.lesson, taskId.idx)
        lessonExecutor.applyRestore(this, actualId)
      }
    }
  }

  override fun restoreByTimer(delayMillis: Int, restoreId: TaskId?) {
    lessonExecutor.restoreByTimer(this, delayMillis, restoreId)
  }

  /**
   * Should not be called more than once in single task (even with [showWarning]
   */
  override fun proposeRestore(restoreCheck: TaskRuntimeContext.() -> RestoreNotification?) {
    checkAndShowNotificationIfNeeded(0, null, restoreCheck) {
      LessonManager.instance.setRestoreNotification(it)
    }
  }

  private fun checkEditor(): RestoreNotification? {
    fun restoreNotification(file: VirtualFile) =
      RestoreNotification(LearnBundle.message("learn.restore.notification.wrong.editor"),
                          LearnBundle.message("learn.restore.get.back.link.text")) {
        lessonExecutor.taskInvokeLater {
          FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)
        }
      }

    fun selectedTextEditor() = FileEditorManager.getInstance(project).selectedTextEditor
    if (lessonExecutor.lesson.lessonType.isSingleEditor) {
      if (selectedTextEditor() != lessonExecutor.predefinedEditor) {
        val file = lessonExecutor.predefinedFile ?: return null
        return restoreNotification(file)
      }
    }
    else {
      val file = runtimeContext.previous.file ?: return null
      val document = selectedTextEditor()?.document ?: return restoreNotification(file)
      val currentFile = FileDocumentManager.getInstance().getFile(document)
      if (file != currentFile) {
        return restoreNotification(file)
      }
    }
    return null
  }

  /**
   * Should not be called more than once in single task (even with [proposeRestore]
   */
  override fun showWarning(text: String,
                           restoreTaskWhenResolved: Boolean,
                           problem: LearningInternalProblems?,
                           warningRequired: TaskRuntimeContext.() -> Boolean
  ) {
    var warningIsLogged = false
    val notificationRequired: TaskRuntimeContext.() -> RestoreNotification? = {
      if (warningRequired()) { // do not spam reports
        if (!warningIsLogged) {
          warningIsLogged = true
          if (problem != null) {
            StatisticBase.logLearningProblem(problem, this@TaskContextImpl.lessonExecutor.lesson)
            thisLogger().error("Detected important problem ($problem): $text")
          }
          else {
            thisLogger().warn("Learning warning: $text")
          }
        }
        RestoreNotification(text) {}
      } else null
    }
    val restoreId = if (restoreTaskWhenResolved) taskId else null
    checkAndShowNotificationIfNeeded(delayMillis = defaultRestoreDelay, restoreId, notificationRequired) {
      LessonManager.instance.setWarningNotification(it)
    }
  }

  private fun checkAndShowNotificationIfNeeded(delayMillis: Int, restoreId: TaskId?,
                                               notificationRequired: TaskRuntimeContext.() -> RestoreNotification?,
                                               setNotification: (RestoreNotification) -> Unit) {
    addRestoreCheck(delayMillis, null, { true }) {
      val notification = checkEditor() ?: notificationRequired(runtimeContext)
      val lessonManager = LessonManager.instance
      val activeNotification = lessonManager.shownRestoreNotification
      if (notification != null && notification.message != activeNotification?.message) {
        setNotification(notification)
        StatisticBase.logRestorePerformed(lessonExecutor.lesson, taskId.idx)
      }
      else if (notification == null && activeNotification != null) {
        lessonManager.clearRestoreMessage()  // clear message if resolved
        if (restoreId != null) lessonExecutor.applyRestore(this, restoreId)  // and restore task if specified
      }
    }
  }

  private fun addRestoreCheck(delayMillis: Int, checkByTimer: Int?, check: TaskRuntimeContext.() -> Boolean, restore: () -> Unit) {
    assert(lessonExecutor.currentTaskIndex == taskIndex)
    data.delayBeforeRestore = delayMillis
    data.checkRestoreByTimer = checkByTimer
    val previous = data.shouldRestore
    data.shouldRestore = { previous?.let { it() } ?: if (check(runtimeContext)) restore else null }
  }

  override fun text(@Language("HTML") text: String, useBalloon: LearningBalloonConfig?) {
    if (useBalloon == null || useBalloon.duplicateMessage)
      lessonExecutor.text(text)

    if (useBalloon != null) {
      val ui = useBalloon.highlightingComponent ?: runtimeContext.previous.ui as? JComponent ?: return
      LessonExecutorUtil.showBalloonMessage(text,
                                            ui,
                                            useBalloon,
                                            runtimeContext.actionsRecorder,
                                            lessonExecutor)
    }
  }


  override fun type(text: String) = before {
    taskInvokeLater(ModalityState.current()) {
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
      lessonExecutor.text(text, runtimeTextContext.removeAfterDone, runtimeTextContext.textProperties)
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
    addStep(recorder.futureActionAndCheckAround(actionId, { state = calculateAction() }) {
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

  override fun timerCheck(delayMillis: Int, checkState: TaskRuntimeContext.() -> Boolean): CompletableFuture<Boolean> {
    val future = recorder.timerCheck(delayMillis) { checkState(runtimeContext) }
    addStep(future)
    return future
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
        val psiFile = invokeAndWaitIfNeeded { PsiDocumentManager.getInstance(project).getPsiFile(runtimeContext.editor.document) }
                      ?: return@Runnable
        var t = 0
        val step = 100
        while (!runReadAction { DaemonCodeAnalyzerEx.getInstanceEx(project).isErrorAnalyzingFinished(psiFile) }) {
          Thread.sleep(step.toLong())
          t += step
          if (t > 3000) return@Runnable
        }
      }

      try {
        TaskTestContext(runtimeContext).action()
      } catch (e: Throwable) {
        thisLogger().error("Test execution error", e)
      }
    })
  }

  override fun triggerUI(parameters: HighlightTriggerParametersContext.() -> Unit): HighlightingTriggerMethods {
    val p = HighlightTriggerParametersContext()
    p.apply(parameters)
    val options = LearningUiHighlightingManager.HighlightingOptions(p.highlightBorder,
                                                                    p.highlightInside,
                                                                    p.usePulsation,
                                                                    p.clearPreviousHighlights)
    return object : HighlightingTriggerMethods() {
      override fun treeItem(checkPath: TaskRuntimeContext.(tree: JTree, path: TreePath) -> Boolean) {
        triggerByFoundPathAndHighlight(options) { tree ->
          TreeUtil.visitVisibleRows(tree, TreeVisitor { path ->
            if (checkPath(tree, path)) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
          })
        }
      }

      override fun listItem(checkList: TaskRuntimeContext.(item: Any) -> Boolean) {
        triggerByFoundListItemAndHighlight(options) { ui: JList<*> ->
          LessonUtil.findItem(ui) { checkList(it) }
        }
      }

      @Suppress("OverridingDeprecatedMember")
      override fun <ComponentType : Component> explicitComponentDetection(
        componentClass: Class<ComponentType>,
        selector: ((candidates: Collection<ComponentType>) -> ComponentType?)?,
        finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean
      ) {
        @Suppress("DEPRECATION")
        triggerByUiComponentAndHighlightImpl(
          componentClass,
          options,
          selector,
          finderFunction
        )
      }

      @Suppress("OverridingDeprecatedMember")
      override fun <ComponentType : Component> explicitComponentPartDetection(componentClass: Class<ComponentType>,
                                                                              rectangle: TaskRuntimeContext.(ComponentType) -> Rectangle?) {
        @Suppress("DEPRECATION")
        triggerByPartOfComponentImpl(
          componentClass,
          options,
          null,
          rectangle
        )
      }
    }
  }

  @Suppress("OverridingDeprecatedMember")
  override fun <ComponentType : Component>
    triggerByUiComponentAndHighlightImpl(componentClass: Class<ComponentType>,
                                         options: LearningUiHighlightingManager.HighlightingOptions,
                                         selector: ((candidates: Collection<ComponentType>) -> ComponentType?)?,
                                         finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean) {
    triggerByUiComponentAndHighlight l@{
      val component = LearningUiUtil.findComponentOrNull(project, componentClass, selector) {
        finderFunction(it)
      }
      if (component != null) {
        taskInvokeLater(ModalityState.any()) {
          LearningUiHighlightingManager.highlightComponent(component, options)
        }
      }
      component
    }
  }

  @Suppress("OverridingDeprecatedMember")
  override fun <T : Component> triggerByPartOfComponentImpl(componentClass: Class<T>,
                                                            options: LearningUiHighlightingManager.HighlightingOptions,
                                                            selector: ((candidates: Collection<T>) -> T?)?,
                                                            rectangle: TaskRuntimeContext.(T) -> Rectangle?) {
    triggerByUiComponentAndHighlight l@{
      val whole = LearningUiUtil.findComponentOrNull(project, componentClass, selector) {
        rectangle(it) != null
      }
      if (whole != null) {
        taskInvokeLater(ModalityState.any()) {
          LearningUiHighlightingManager.highlightPartOfComponent(whole, options) { rectangle(it) }
        }
      }
      whole
    }
  }

  override fun triggerByFoundListItemAndHighlight(options: LearningUiHighlightingManager.HighlightingOptions,
                                                  checkList: TaskRuntimeContext.(list: JList<*>) -> Int?) {
    triggerByUiComponentAndHighlight {
      val list = LearningUiUtil.findComponentOrNull(project, JList::class.java) l@{
        val index = checkList(it) ?: return@l false
        val itemRect = it.getCellBounds(index, index)
        it.visibleRect.intersects(itemRect)
      }
      if (list != null) {
        taskInvokeLater(ModalityState.any()) {
          LearningUiHighlightingManager.highlightJListItem(list, options) {
            checkList(list)
          }
        }
      }
      list
    }
  }

  // This method later can be converted to the public (But I'm not sure it will be ever needed in a such form)
  override fun triggerByFoundPathAndHighlight(options: LearningUiHighlightingManager.HighlightingOptions,
                                              checkTree: TaskRuntimeContext.(tree: JTree) -> TreePath?) {
    triggerByUiComponentAndHighlight l@{
      val tree = LearningUiUtil.findComponentOrNull(project, JTree::class.java) {
        checkTree(it) != null
      }
      if (tree != null) {
        taskInvokeLater(ModalityState.any()) {
          LearningUiHighlightingManager.highlightJTreeItem(tree, options) {
            checkTree(tree)
          }
        }
      }
      tree
    }
  }


  private fun triggerByUiComponentAndHighlight(findAndHighlight: TaskRuntimeContext.() -> Component?) {
    val step = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().executeOnPooledThread {
      while (true) {
        if (lessonExecutor.hasBeenStopped) {
          step.cancel(true)
          break
        }
        try {
          val highlightFunction = { findAndHighlight(runtimeContext) }
          val foundComponent = highlightFunction() ?: continue
          lessonExecutor.taskInvokeLater(ModalityState.any()) {
            lessonExecutor.foundComponent = foundComponent
            lessonExecutor.rehighlightComponent = highlightFunction
            lessonExecutor.rehighlightFoundComponent(foundComponent, highlightFunction)
            step.complete(true)
          }
        }
        catch (e: WaitTimedOutError) {
          continue
        }
        catch (e: ComponentLookupException) {
          continue
        }
        catch (e: Throwable) {
          thisLogger().error(lessonExecutor.getLessonInfoString(), e)
        }
        break
      }
    }
    steps.add(step)
  }
}