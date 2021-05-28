// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import org.fest.swing.timing.Timeout
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.learn.LearnBundle
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiUtil
import java.awt.Component
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.swing.JList
import javax.swing.JTree
import javax.swing.tree.TreePath

@LearningDsl
abstract class TaskContext : LearningDslBase {
  abstract val project: Project

  open val taskId: TaskId = TaskId(0)

  /** Put here some initialization for the task */
  open fun before(preparation: TaskRuntimeContext.() -> Unit) = Unit

  /**
   * @param [restoreId] where to restore, `null` means the previous task
   * @param [delayMillis] the delay before restore actions can be applied.
   *                      Delay may be needed to give pass condition take place.
   *                      It is a hack solution because of possible race conditions.
   * @param [restoreRequired] returns true iff restore is needed
   */
  open fun restoreState(restoreId: TaskId? = null, delayMillis: Int = 0, restoreRequired: TaskRuntimeContext.() -> Boolean) = Unit

  /** Shortcut */
  fun restoreByUi(restoreId: TaskId? = null, delayMillis: Int = 0) {
    restoreState(restoreId, delayMillis) {
      previous.ui?.isShowing?.not() ?: true
    }
  }

  data class RestoreNotification(@Nls val message: String,
                                 @Nls val restoreLinkText: String = LearnBundle.message("learn.restore.default.link.text"),
                                 val callback: () -> Unit)

  open fun proposeRestore(restoreCheck: TaskRuntimeContext.() -> RestoreNotification?) = Unit

  open fun showWarning(@Language("HTML") @Nls text: String,
                       restoreTaskWhenResolved: Boolean = false,
                       warningRequired: TaskRuntimeContext.() -> Boolean) = Unit

  /**
   * Write a text to the learn panel (panel with a learning tasks).
   */
  open fun text(@Language("HTML") @Nls text: String, useBalloon: LearningBalloonConfig? = null) = Unit

  /** Insert text in the current position */
  open fun type(text: String) = Unit
  /** Write a text to the learn panel (panel with a learning tasks). */
  open fun runtimeText(@Nls callback: RuntimeTextContext.() -> String?) = Unit

  /** Simply wait until an user perform particular action */
  open fun trigger(actionId: String) = Unit

  /** Simply wait until an user perform actions */
  open fun trigger(checkId: (String) -> Boolean) = Unit

  /** Trigger on actions start. Needs if you want to split long actions into several tasks. */
  open fun triggerStart(actionId: String, checkState: TaskRuntimeContext.() -> Boolean = { true }) = Unit

  /** [actionIds] these actions required for the current task */
  open fun triggers(vararg actionIds: String) = Unit

  /** An user need to rice an action which leads to necessary state change */
  open fun <T : Any?> trigger(actionId: String, calculateState: TaskRuntimeContext.() -> T, checkState: TaskRuntimeContext.(T, T) -> Boolean) = Unit

  /** An user need to rice an action which leads to appropriate end state */
  fun trigger(actionId: String, checkState: TaskRuntimeContext.() -> Boolean) {
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

  open fun addFutureStep(p: DoneStepContext.() -> Unit) = Unit

  open fun addStep(step: CompletableFuture<Boolean>) = Unit

  /** [action] What should be done to pass the current task */
  open fun test(waitEditorToBeReady: Boolean = true, action: TaskTestContext.() -> Unit) = Unit

  fun triggerByFoundPathAndHighlight(highlightBorder: Boolean = true, highlightInside: Boolean = false, usePulsation: Boolean = false,
                                     checkPath: TaskRuntimeContext.(tree: JTree, path: TreePath) -> Boolean) {
    val options = LearningUiHighlightingManager.HighlightingOptions(highlightBorder, highlightInside, usePulsation)
    triggerByFoundPathAndHighlight(options) { tree ->
      TreeUtil.visitVisibleRows(tree, TreeVisitor { path ->
        if (checkPath(tree, path)) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
      })
    }
  }

  // This method later can be converted to the public (But I'm not sure it will be ever needed in a such form)
  private fun triggerByFoundPathAndHighlight(options: LearningUiHighlightingManager.HighlightingOptions, checkTree: TaskRuntimeContext.(tree: JTree) -> TreePath?) {
    @Suppress("DEPRECATION")
    triggerByUiComponentAndHighlight {
      val delay = Timeout.timeout(500, TimeUnit.MILLISECONDS)
      val tree = LearningUiUtil.findShowingComponentWithTimeout(null, JTree::class.java, delay) {
        checkTree(it) != null
      }
      return@triggerByUiComponentAndHighlight {
        LearningUiHighlightingManager.highlightJTreeItem(tree, options) {
          checkTree(tree)
        }
        tree
      }
    }
  }

  inline fun <reified T: Component> triggerByPartOfComponent(highlightBorder: Boolean = true, highlightInside: Boolean = false,
                                                             usePulsation: Boolean = false,
                                                             noinline selector: ((candidates: Collection<T>) -> T?)? = null,
                                                             crossinline rectangle: TaskRuntimeContext.(T) -> Rectangle?) {
    val options = LearningUiHighlightingManager.HighlightingOptions(highlightBorder, highlightInside, usePulsation)
    @Suppress("DEPRECATION")
    triggerByUiComponentAndHighlight {
      val delay = Timeout.timeout(500, TimeUnit.MILLISECONDS)
      val whole = LearningUiUtil.findShowingComponentWithTimeout(null, T::class.java, delay, selector) {
        rectangle(it) != null
      }
      return@triggerByUiComponentAndHighlight {
        LearningUiHighlightingManager.highlightPartOfComponent(whole, options) { rectangle(it) }
        whole
      }
    }
  }

  fun triggerByListItemAndHighlight(highlightBorder: Boolean = true, highlightInside: Boolean = false, usePulsation: Boolean = false,
                                    checkList: TaskRuntimeContext.(item: Any) -> Boolean) {
    val options = LearningUiHighlightingManager.HighlightingOptions(highlightBorder, highlightInside, usePulsation)
    triggerByFoundListItemAndHighlight(options) { ui: JList<*> ->
      LessonUtil.findItem(ui) { checkList(it) }
    }
  }

  // This method later can be converted to the public (But I'm not sure it will be ever needed in a such form
  private fun triggerByFoundListItemAndHighlight(options: LearningUiHighlightingManager.HighlightingOptions, checkList: TaskRuntimeContext.(list: JList<*>) -> Int?) {
    @Suppress("DEPRECATION")
    triggerByUiComponentAndHighlight {
      val delay = Timeout.timeout(500, TimeUnit.MILLISECONDS)
      val list = LearningUiUtil.findShowingComponentWithTimeout(null, JList::class.java, delay) l@{
        val index = checkList(it)
        index != null && it.visibleRowCount > index
      }
      return@triggerByUiComponentAndHighlight {
        LearningUiHighlightingManager.highlightJListItem(list, options) {
          checkList(list)
        }
        list
      }
    }
  }

  inline fun <reified ComponentType : Component> triggerByUiComponentAndHighlight(
    highlightBorder: Boolean = true, highlightInside: Boolean = true, usePulsation: Boolean = false,
    noinline selector: ((candidates: Collection<ComponentType>) -> ComponentType?)? = null,
    crossinline finderFunction: TaskRuntimeContext.(ComponentType) -> Boolean
  ) {
    @Suppress("DEPRECATION")
    triggerByUiComponentAndHighlight l@{
      val delay = Timeout.timeout(500, TimeUnit.MILLISECONDS)
      val component = LearningUiUtil.findShowingComponentWithTimeout(null, ComponentType::class.java, delay, selector) {
        finderFunction(it)
      }
      return@l {
        val options = LearningUiHighlightingManager.HighlightingOptions(highlightBorder, highlightInside, usePulsation)
        LearningUiHighlightingManager.highlightComponent(component, options)
        component
      }
    }
  }

  @Deprecated("It is auxiliary method with explicit class parameter. Use inlined short form instead")
  open fun triggerByUiComponentAndHighlight(findAndHighlight: TaskRuntimeContext.() -> (() -> Component)) = Unit

  open fun caret(position: LessonSamplePosition) = before {
    caret(position)
  }

  /** NOTE:  [line] and [column] starts from 1 not from zero. So these parameters should be same as in editors. */
  open fun caret(line: Int, column: Int) = before {
    caret(line, column)
  }

  class DoneStepContext(val future: CompletableFuture<Boolean>, rt: TaskRuntimeContext) : TaskRuntimeContext(rt) {
    fun completeStep() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (!future.isDone && !future.isCancelled) {
        future.complete(true)
      }
    }
  }

  data class TaskId(val idx: Int)

  companion object {
    val CaretRestoreProposal: String
      get() = LearnBundle.message("learn.restore.notification.caret.message")
    val ModificationRestoreProposal: String
      get() = LearnBundle.message("learn.restore.notification.modification.message")
  }
}
