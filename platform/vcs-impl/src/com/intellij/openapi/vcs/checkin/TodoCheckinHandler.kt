// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.ide.todo.*
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressSink
import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.progress.progressSink
import com.intellij.openapi.progress.runUnderIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoCancelResult
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.TodoCheckinHandler.Companion.showDialog
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.wm.ToolWindowId.TODO_VIEW
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.search.TodoItem
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.text.DateFormatUtil.formatDateTime
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.getWarningIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

class TodoCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return TodoCheckinHandler(panel.project)
  }
}

class TodoCommitProblem(val worker: TodoCheckinHandlerWorker) : CommitProblemWithDetails {
  override val text: String get() = message("label.todo.items.found", worker.inOneList().size)

  override fun showDetails(project: Project) {
    TodoCheckinHandler.showTodoItems(project, worker.changes, worker.inOneList())
  }

  override fun showModalSolution(project: Project, commitInfo: CommitInfo): CheckinHandler.ReturnResult {
    return showDialog(project, worker, commitInfo.commitActionText)
  }

  override val showDetailsAction: String
    get() = message("todo.in.new.review.button")
}

class TodoCheckinHandler(private val project: Project) : CheckinHandler(), CommitCheck, DumbAware {
  private val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)
  private val todoSettings: TodoPanelSettings get() = settings.myTodoPanelSettings

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.POST_COMMIT

  override fun isEnabled(): Boolean = settings.CHECK_NEW_TODO

  override suspend fun runCheck(commitInfo: CommitInfo): TodoCommitProblem? {
    val sink = coroutineContext.progressSink
    sink?.text(message("progress.text.checking.for.todo"))

    val todoFilter = settings.myTodoPanelSettings.todoFilterName?.let { TodoConfiguration.getInstance().getTodoFilter(it) }
    val changes = commitInfo.committedChanges
    val worker = TodoCheckinHandlerWorker(project, changes, todoFilter)

    withContext(Dispatchers.Default + textToDetailsSinkContext(sink)) {
      runUnderIndicator {
        worker.execute()
      }
    }

    val noTodo = worker.inOneList().isEmpty()
    val noSkipped = worker.skipped.isEmpty()
    if (noTodo && noSkipped) return null

    return TodoCommitProblem(worker)
  }

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    object : BooleanCommitOption(project, "", false, settings::CHECK_NEW_TODO) {
      override fun getComponent(): JComponent {
        val filter = TodoConfiguration.getInstance().getTodoFilter(todoSettings.todoFilterName)
        setFilterText(filter?.name)

        val showFiltersPopup = LinkListener<Any> { sourceLink, _ ->
          val group = SetTodoFilterAction.createPopupActionGroup(project, todoSettings, true) { setFilter(it) }
          JBPopupMenu.showBelow(sourceLink, ActionPlaces.TODO_VIEW_TOOLBAR, group)
        }
        val configureFilterLink = LinkLabel(message("settings.filter.configure.link"), null, showFiltersPopup)

        return simplePanel(4, 0).addToLeft(checkBox).addToCenter(configureFilterLink)
      }

      private fun setFilter(filter: TodoFilter?) {
        todoSettings.todoFilterName = filter?.name
        setFilterText(filter?.name)
      }

      private fun setFilterText(filterName: String?) {
        if (filterName != null) {
          val text = message("checkin.filter.filter.name", filterName)
          checkBox.text = message("before.checkin.new.todo.check", text)
        }
        else {
          checkBox.text = message("before.checkin.new.todo.check.no.filter")
        }
      }
    }

  companion object {
    internal fun showDialog(project: Project,
                            worker: TodoCheckinHandlerWorker,
                            @NlsContexts.Button commitActionText: String): ReturnResult {
      val noTodo = worker.addedOrEditedTodos.isEmpty() && worker.inChangedTodos.isEmpty()
      val noSkipped = worker.skipped.isEmpty()

      return when {
        noTodo && noSkipped -> ReturnResult.COMMIT
        noTodo -> if (confirmCommitWithSkippedFiles(worker, commitActionText)) ReturnResult.COMMIT else ReturnResult.CANCEL
        else -> processFoundTodoItems(project, worker, commitActionText)
      }
    }

    private fun processFoundTodoItems(project: Project,
                                      worker: TodoCheckinHandlerWorker,
                                      @NlsContexts.Button commitActionText: String): ReturnResult =
      when (askReviewCommitCancel(worker, commitActionText)) {
        Messages.YES -> {
          showTodoItems(project, worker.changes, worker.inOneList())
          ReturnResult.CLOSE_WINDOW
        }
        Messages.NO -> ReturnResult.COMMIT
        else -> ReturnResult.CANCEL
      }

    internal fun showTodoItems(project: Project, changes: Collection<Change>, todoItems: Collection<TodoItem>) {
      val todoView = project.service<TodoView>()
      todoView.addCustomTodoView(
        TodoTreeBuilderFactory { tree, project -> CustomChangelistTodosTreeBuilder(tree, project, changes, todoItems) },
        message("checkin.title.for.commit.0", formatDateTime(System.currentTimeMillis())),
        TodoPanelSettings(VcsConfiguration.getInstance(project).myTodoPanelSettings)
      )

      runInEdt(ModalityState.NON_MODAL) {
        if (project.isDisposed) return@runInEdt
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TODO_VIEW) ?: return@runInEdt

        toolWindow.show {
          val lastContent = toolWindow.contentManager.contents.lastOrNull()
          if (lastContent != null) toolWindow.contentManager.setSelectedContent(lastContent, true)
        }
      }
    }
  }
}

internal fun textToDetailsSinkContext(sink: ProgressSink?): CoroutineContext {
  if (sink == null) {
    return EmptyCoroutineContext
  }
  else {
    return TextToDetailsProgressSink(sink).asContextElement()
  }
}

internal class TextToDetailsProgressSink(private val original: ProgressSink) : ProgressSink {

  override fun update(text: @ProgressText String?, details: @ProgressDetails String?, fraction: Double?) {
    original.update(
      text = null,
      details = text, // incoming text will be shown as details in the original sink
      fraction = fraction,
    )
  }
}

private fun confirmCommitWithSkippedFiles(worker: TodoCheckinHandlerWorker, @NlsContexts.Button commitActionText: String) =
  yesNo(message("checkin.dialog.title.todo"), getDescription(worker))
    .icon(getWarningIcon())
    .yesText(commitActionText)
    .noText(getCancelButtonText())
    .ask(worker.project)

@YesNoCancelResult
private fun askReviewCommitCancel(worker: TodoCheckinHandlerWorker, @NlsContexts.Button commitActionText: String): Int =
  yesNoCancel(message("checkin.dialog.title.todo"), getDescription(worker))
    .icon(getWarningIcon())
    .yesText(message("todo.in.new.review.button"))
    .noText(commitActionText)
    .cancelText(getCancelButtonText())
    .show(worker.project)

@DialogMessage
private fun getDescription(worker: TodoCheckinHandlerWorker): String {
  val added = worker.addedOrEditedTodos.size
  val changed = worker.inChangedTodos.size
  val skipped = worker.skipped.size

  return when {
    added == 0 && changed == 0 -> message("todo.handler.only.skipped", skipped)
    changed == 0 -> message("todo.handler.only.added", added, skipped)
    added == 0 -> message("todo.handler.only.in.changed", changed, skipped)
    else -> message("todo.handler.only.both", added, changed, skipped)
  }
}