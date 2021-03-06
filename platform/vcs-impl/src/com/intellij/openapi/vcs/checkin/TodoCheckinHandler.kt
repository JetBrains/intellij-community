// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.ide.IdeBundle
import com.intellij.ide.todo.*
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoCancelResult
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.text.StringUtil.removeEllipsisSuffix
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.wm.ToolWindowId.TODO_VIEW
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.search.TodoItem
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.PairConsumer
import com.intellij.util.text.DateFormatUtil.formatDateTime
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.getWarningIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JComponent

class TodoCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler = TodoCheckinHandler(panel)
}

class TodoCommitProblem(val changes: Collection<Change>, val todoItems: Collection<TodoItem>) : CommitProblem {
  override val text: String get() = message("label.todo.items.found", todoItems.size)
}

class TodoCheckinHandler(private val commitPanel: CheckinProjectPanel) : CheckinHandler(), CommitCheck<TodoCommitProblem> {
  private val project: Project get() = commitPanel.project
  private val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)
  private val todoSettings: TodoPanelSettings get() = settings.myTodoPanelSettings

  private var todoFilter: TodoFilter? = null

  override fun isEnabled(): Boolean = settings.CHECK_NEW_TODO

  override suspend fun runCheck(): TodoCommitProblem? {
    val changes = commitPanel.selectedChanges
    val worker = TodoCheckinHandlerWorker(project, changes, todoFilter)

    withContext(Dispatchers.Default) { worker.execute() }

    val todoItems = worker.inOneList()
    return if (todoItems.isNotEmpty()) TodoCommitProblem(changes, todoItems) else null
  }

  override fun showDetails(problem: TodoCommitProblem) = showTodoItems(problem.changes, problem.todoItems)

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    object : BooleanCommitOption(commitPanel, "", true, settings::CHECK_NEW_TODO) {
      override fun getComponent(): JComponent {
        setFilterText(todoSettings.todoFilterName)
        todoSettings.todoFilterName?.let { todoFilter = TodoConfiguration.getInstance().getTodoFilter(it) }

        val showFiltersPopup = LinkListener<Any> { sourceLink, _ ->
          val group = SetTodoFilterAction.createPopupActionGroup(project, todoSettings) { setFilter(it) }
          JBPopupMenu.showBelow(sourceLink, ActionPlaces.TODO_VIEW_TOOLBAR, group)
        }
        val configureFilterLink = LinkLabel(message("settings.filter.configure.link"), null, showFiltersPopup)

        return simplePanel(4, 0).addToLeft(checkBox).addToCenter(configureFilterLink)
      }

      private fun setFilter(filter: TodoFilter?) {
        todoFilter = filter
        todoSettings.todoFilterName = filter?.name
        setFilterText(filter?.name)
      }

      private fun setFilterText(filterName: String?) {
        val text = if (filterName != null) message("checkin.filter.filter.name", filterName) else IdeBundle.message("action.todo.show.all")
        checkBox.text = message("before.checkin.new.todo.check", text)
      }
    }

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {
    if (!isEnabled()) return ReturnResult.COMMIT
    if (isDumb(project)) return if (confirmCommitInDumbMode(project)) ReturnResult.COMMIT else ReturnResult.CANCEL

    val worker = FindTodoItemsTask(project, commitPanel.selectedChanges, todoFilter).find() ?: return ReturnResult.CANCEL
    val commitActionText = removeEllipsisSuffix(executor?.actionText ?: commitPanel.commitActionName)
    val noTodo = worker.addedOrEditedTodos.isEmpty() && worker.inChangedTodos.isEmpty()
    val noSkipped = worker.skipped.isEmpty()

    return when {
      noTodo && noSkipped -> ReturnResult.COMMIT
      noTodo -> if (confirmCommitWithSkippedFiles(worker, commitActionText)) ReturnResult.COMMIT else ReturnResult.CANCEL
      else -> processFoundTodoItems(worker, commitActionText)
    }
  }

  private fun processFoundTodoItems(worker: TodoCheckinHandlerWorker, @NlsContexts.Button commitActionText: String): ReturnResult =
    when (askReviewCommitCancel(worker, commitActionText)) {
      Messages.YES -> {
        showTodoItems(worker.changes, worker.inOneList())
        ReturnResult.CLOSE_WINDOW
      }
      Messages.NO -> ReturnResult.COMMIT
      else -> ReturnResult.CANCEL
    }

  private fun showTodoItems(changes: Collection<Change>, todoItems: Collection<TodoItem>) {
    project.service<TodoView>().addCustomTodoView(
      TodoTreeBuilderFactory { tree, project -> CustomChangelistTodosTreeBuilder(tree, project, changes, todoItems) },
      message("checkin.title.for.commit.0", formatDateTime(System.currentTimeMillis())),
      TodoPanelSettings(todoSettings)
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

private class FindTodoItemsTask(project: Project, changes: Collection<Change>, todoFilter: TodoFilter?) :
  Task.Modal(project, message("checkin.dialog.title.looking.for.new.edited.todo.items"), true) {

  private val worker = TodoCheckinHandlerWorker(myProject, changes, todoFilter)
  private var result: TodoCheckinHandlerWorker? = null

  fun find(): TodoCheckinHandlerWorker? {
    queue()
    return result
  }

  override fun run(indicator: ProgressIndicator) {
    indicator.isIndeterminate = true
    worker.execute()
  }

  override fun onSuccess() {
    result = worker
  }
}

private fun confirmCommitInDumbMode(project: Project): Boolean =
  !yesNo(message("checkin.dialog.title.not.possible.right.now"),
         message("checkin.dialog.message.cant.be.performed", ApplicationNamesInfo.getInstance().fullProductName))
    .icon(null)
    .yesText(message("checkin.wait"))
    .noText(message("checkin.commit"))
    .ask(project)

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