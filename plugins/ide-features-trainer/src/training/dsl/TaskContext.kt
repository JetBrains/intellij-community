// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.dsl

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.learn.LearnBundle
import training.statistic.LearningInternalProblems
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiManager
import java.awt.Component
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JTree
import javax.swing.tree.TreePath

@LearningDsl
abstract class TaskContext : LearningDslBase {
  abstract val project: Project

  open val taskId: TaskId = TaskId(0)

  /**
   * By default, the engine requires all steps become completed before move to the next task.
   * The behavior can be changed to pass the task if at least one step becomes completed.
   */
  var passMode: PassMode = PassMode.AllSteps

  /**
   * This property can be set to the true if you want that the next task restore will jump over the current task.
   * Default `null` value is reserved for the future automatic transparent restore calculation.
   */
  open var transparentRestore: Boolean? = null

  /**
   * Can be set to true iff you need to rehighlight the triggered element from the previous task when it will be shown again.
   * Note: that the rehighlighted element can be different from `previous?.ui` (it can become `null` or `!isValid` or `!isShowing`)
   * */
  open var rehighlightPreviousUi: Boolean? = null

  /**
   * Can be set to true iff you need to propagate found highlighting component from the previous task as found from the current task.
   * So it may be used as `previous.ui`. And will be rehighlighted on restore.
   *
   * The default `null` means true now, but it may be changed later.
   */
  open var propagateHighlighting: Boolean? = null

  /** Put here some initialization for the task */
  open fun before(preparation: TaskRuntimeContext.() -> Unit) = Unit

  /**
   * @param [restoreId] where to restore, `null` means the previous task
   * @param [delayMillis] the delay before restore actions can be applied.
   *                      Delay may be needed to give pass condition take place.
   *                      It is a hack solution because of possible race conditions.
   * @param [checkByTimer] Check by timer may be useful in UI detection tasks (in submenus for example).
   * @param [restoreRequired] returns true if restore is needed
   */
  open fun restoreState(restoreId: TaskId? = null, delayMillis: Int = 0, checkByTimer: Int? = null, restoreRequired: TaskRuntimeContext.() -> Boolean) = Unit

  /** Shortcut */
  fun restoreByUi(restoreId: TaskId? = null, delayMillis: Int = 0, checkByTimer: Int? = null) {
    restoreState(restoreId, delayMillis, checkByTimer) {
      previous.ui?.isShowing?.not() ?: true
    }
  }

  /** Restore when timer is out. Is needed for chained tasks. */
  open fun restoreByTimer(delayMillis: Int = 2000, restoreId: TaskId? = null) = Unit

  data class RestoreNotification(@Nls val message: String,
                                 @Nls val restoreLinkText: String = LearnBundle.message("learn.restore.default.link.text"),
                                 val callback: () -> Unit)

  open fun proposeRestore(restoreCheck: TaskRuntimeContext.() -> RestoreNotification?) = Unit

  open fun showWarning(@Language("HTML") @Nls text: String,
                       restoreTaskWhenResolved: Boolean = false,
                       problem: LearningInternalProblems? = null,
                       warningRequired: TaskRuntimeContext.() -> Boolean) = Unit

  /**
   * Write a text to the learn panel (panel with a learning tasks).
   */
  open fun text(@Language("HTML") @Nls text: String, useBalloon: LearningBalloonConfig? = null) = Unit

  /** Add an illustration */
  fun illustration(icon: Icon): Unit = text("<illustration>${LearningUiManager.getIconIndex(icon)}</illustration>")

  /** Insert text in the current position */
  open fun type(text: String) = Unit

  /** Write a text to the learn panel (panel with a learning tasks). */
  open fun runtimeText(@Nls callback: RuntimeTextContext.() -> String?) = Unit

  /** Simply wait until an user perform particular action */
  open fun trigger(@Language("devkit-action-id") actionId: String) = Unit

  /** Simply wait until an user perform actions */
  open fun trigger(checkId: (String) -> Boolean) = Unit

  /** Trigger on actions start. Needs if you want to split long actions into several tasks. */
  open fun triggerStart(@Language("devkit-action-id") actionId: String, checkState: TaskRuntimeContext.() -> Boolean = { true }) = Unit

  /** [actionIds] these actions required for the current task */
  open fun triggers(@Language("devkit-action-id") vararg actionIds: String) = Unit

  /** An user need to rice an action which leads to necessary state change */
  open fun <T : Any?> trigger(@Language("devkit-action-id") actionId: String,
                              calculateState: TaskRuntimeContext.() -> T,
                              checkState: TaskRuntimeContext.(T, T) -> Boolean) = Unit

  /** An user need to rice an action which leads to appropriate end state */
  fun trigger(@Language("devkit-action-id") actionId: String, checkState: TaskRuntimeContext.() -> Boolean) {
    trigger(actionId, { }, { _, _ -> checkState() })
  }

  /**
   * Check that IDE state is as expected
   * In some rare cases DSL could wish to complete a future by itself
   */
  open fun stateCheck(checkState: TaskRuntimeContext.() -> Boolean): CompletableFuture<Boolean> = CompletableFuture()

  /**
   * Check that IDE state is fit
   * @return A feature with value associated with fit state
   */
  open fun <T : Any> stateRequired(requiredState: TaskRuntimeContext.() -> T?): Future<T> = CompletableFuture()

  /**
   * Check that IDE state is as expected and check it by timer.
   * Need to consider merge this method with [stateCheck].
   */
  open fun timerCheck(delayMillis: Int = 200, checkState: TaskRuntimeContext.() -> Boolean): CompletableFuture<Boolean> = CompletableFuture()

  open fun addFutureStep(p: DoneStepContext.() -> Unit) = Unit

  /* The step should be used only inside one task to preserve problems on restore */
  open fun addStep(step: CompletableFuture<Boolean>) = Unit

  /** [action] What should be done to pass the current task */
  open fun test(waitEditorToBeReady: Boolean = true, action: TaskTestContext.() -> Unit) = Unit

  fun triggerAndFullHighlight(parameters: HighlightTriggerParametersContext.() -> Unit = {}): HighlightingTriggerMethods {
    return triggerUI {
      highlightBorder = true
      highlightInside = true
      parameters()
    }
  }

  fun triggerAndBorderHighlight(parameters: HighlightTriggerParametersContext.() -> Unit = {}): HighlightingTriggerMethods {
    return triggerUI {
      highlightBorder = true
      parameters()
    }
  }

  open fun triggerUI(parameters: HighlightTriggerParametersContext.() -> Unit = {}): HighlightingTriggerMethods {
    return object : HighlightingTriggerMethods() {}
  }

  // This method later can be converted to the public (But I'm not sure it will be ever needed in a such form)
  protected open fun triggerByFoundPathAndHighlight(options: LearningUiHighlightingManager.HighlightingOptions,
                                                    checkTree: TaskRuntimeContext.(tree: JTree) -> TreePath?) = Unit

  @Deprecated("Use triggerAndBorderHighlight().componentPart")
  inline fun <reified T : Component> triggerByPartOfComponent(highlightBorder: Boolean = true, highlightInside: Boolean = false,
                                                              usePulsation: Boolean = false, clearPreviousHighlights: Boolean = true,
                                                              noinline selector: ((candidates: Collection<T>) -> T?)? = null,
                                                              crossinline rectangle: TaskRuntimeContext.(T) -> Rectangle?) {
    val options = LearningUiHighlightingManager.HighlightingOptions(highlightBorder, highlightInside, usePulsation, clearPreviousHighlights)
    @Suppress("DEPRECATION")
    triggerByPartOfComponentImpl(T::class.java, options, selector) { rectangle(it) }
  }

  @Deprecated("Use inline version")
  open fun <T : Component> triggerByPartOfComponentImpl(componentClass: Class<T>,
                                                        options: LearningUiHighlightingManager.HighlightingOptions,
                                                        selector: ((candidates: Collection<T>) -> T?)?,
                                                        rectangle: TaskRuntimeContext.(T) -> Rectangle?) = Unit

  // This method later can be converted to the public (But I'm not sure it will be ever needed in a such form
  protected open fun triggerByFoundListItemAndHighlight(options: LearningUiHighlightingManager.HighlightingOptions,
                                                        checkList: TaskRuntimeContext.(list: JList<*>) -> Int?) = Unit

  @Deprecated("Use triggerAndFullHighlight().component")
  inline fun <reified ComponentType : Component> triggerByUiComponentAndHighlight(
    highlightBorder: Boolean = true, highlightInside: Boolean = true,
    usePulsation: Boolean = false, clearPreviousHighlights: Boolean = true,
    noinline selector: ((candidates: Collection<ComponentType>) -> ComponentType?)? = null,
    crossinline finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean
  ) {
    val options = LearningUiHighlightingManager.HighlightingOptions(highlightBorder, highlightInside, usePulsation, clearPreviousHighlights)
    @Suppress("DEPRECATION")
    triggerByUiComponentAndHighlightImpl(ComponentType::class.java, options, selector) { finderFunction(it) }
  }

  @Deprecated("Use inline version")
  open fun <ComponentType : Component>
    triggerByUiComponentAndHighlightImpl(componentClass: Class<ComponentType>,
                                         options: LearningUiHighlightingManager.HighlightingOptions,
                                         selector: ((candidates: Collection<ComponentType>) -> ComponentType?)?,
                                         finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean) = Unit

  open fun caret(position: LessonSamplePosition) = before {
    caret(position)
  }

  /** NOTE:  [line] and [column] starts from 1 not from zero. So these parameters should be same as in editors. */
  open fun caret(line: Int, column: Int) = before {
    caret(line, column)
  }

  class DoneStepContext(private val future: CompletableFuture<Boolean>, rt: TaskRuntimeContext) : TaskRuntimeContext(rt) {
    fun completeStep() {
      ThreadingAssertions.assertEventDispatchThread()
      if (!future.isDone && !future.isCancelled) {
        future.complete(true)
      }
    }
  }

  data class TaskId(val idx: Int)

  enum class PassMode {
    AllSteps,
    AnyStep
  }

  companion object {
    val CaretRestoreProposal: String
      get() = LearnBundle.message("learn.restore.notification.caret.message")
    val ModificationRestoreProposal: String
      get() = LearnBundle.message("learn.restore.notification.modification.message")
  }
}
