// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.ide.todo.*
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoCancelResult
import com.intellij.openapi.util.NlsContexts.Button
import com.intellij.openapi.util.NlsContexts.DialogMessage
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
import com.intellij.platform.util.progress.withProgressText
import com.intellij.psi.search.TodoItem
import com.intellij.util.text.DateFormatUtil.formatDateTime
import com.intellij.util.ui.UIUtil.getWarningIcon
import com.intellij.vcs.commit.isPostCommitCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class TodoCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return TodoCheckinHandler(panel.project)
  }
}

@ApiStatus.Internal
class TodoCommitProblem(private val worker: TodoCheckinHandlerWorker,
                        private val isPostCommit: Boolean) : CommitProblemWithDetails {
  override val text: String get() = message("label.todo.items.found", worker.inOneList().size)

  override fun showDetails(project: Project) {
    TodoCheckinHandler.showTodoItems(project, worker.changes, worker.inOneList(), isPostCommit)
  }

  override fun showModalSolution(project: Project, commitInfo: CommitInfo): CheckinHandler.ReturnResult {
    return showDialog(project, worker, commitInfo.commitActionText)
  }

  override val showDetailsAction: String
    get() = message("todo.in.new.review.button")
}

@ApiStatus.Internal
class TodoCheckinHandler(private val project: Project) : CheckinHandler(), CommitCheck, DumbAware {
  private val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)
  private val todoSettings: TodoPanelSettings get() = settings.myTodoPanelSettings

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.POST_COMMIT

  override fun isEnabled(): Boolean = settings.CHECK_NEW_TODO

  override suspend fun runCheck(commitInfo: CommitInfo): TodoCommitProblem? {
    val isPostCommit = commitInfo.isPostCommitCheck
    val todoFilter = settings.myTodoPanelSettings.todoFilterName?.let { TodoConfiguration.getInstance().getTodoFilter(it) }
    val changes = commitInfo.committedChanges // must be on EDT
    val worker = TodoCheckinHandlerWorker(project, changes, todoFilter)

    withContext(Dispatchers.Default) {
      withProgressText(message("progress.text.checking.for.todo")) {
        coroutineToIndicator {
          worker.execute()
        }
      }
    }
    val noTodo = worker.inOneList().isEmpty()
    val noSkipped = worker.skipped.isEmpty()
    if (noTodo && noSkipped) return null

    return TodoCommitProblem(worker, isPostCommit)
  }

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
    val initialText = getFilterText(todoSettings.todoFilterName)
    return BooleanCommitOption.createLink(project, this, false, initialText, settings::CHECK_NEW_TODO,
                                          message("settings.filter.configure.link")) { sourceLink, linkData ->
      val group = SetTodoFilterAction.createPopupActionGroup(project, todoSettings, true) { filter ->
        todoSettings.todoFilterName = filter?.name
        linkData.setCheckboxText(getFilterText(filter?.name))
      }
      JBPopupMenu.showBelow(sourceLink, ActionPlaces.TODO_VIEW_TOOLBAR, group)
    }
  }

  private fun getFilterText(filterName: String?): @Nls String {
    if (filterName != null) {
      val text = message("checkin.filter.filter.name", filterName)
      return message("before.checkin.new.todo.check", text)
    }
    else {
      return message("before.checkin.new.todo.check.no.filter")
    }
  }

  companion object {
    internal fun showDialog(project: Project,
                            worker: TodoCheckinHandlerWorker,
                            @Button commitActionText: String): ReturnResult {
      val noTodo = worker.addedOrEditedTodos.isEmpty() && worker.inChangedTodos.isEmpty()
      val noSkipped = worker.skipped.isEmpty()

      if (noTodo && noSkipped) return ReturnResult.COMMIT
      if (noTodo) {
        val commit = confirmCommitWithSkippedFiles(worker, commitActionText)
        if (commit) {
          return ReturnResult.COMMIT
        }
        else {
          return ReturnResult.CANCEL
        }
      }

      when (askReviewCommitCancel(worker, commitActionText)) {
        Messages.YES -> {
          showTodoItems(project, worker.changes, worker.inOneList(), isPostCommit = false)
          return ReturnResult.CLOSE_WINDOW
        }
        Messages.NO -> return ReturnResult.COMMIT
        else -> return ReturnResult.CANCEL
      }
    }

    internal fun showTodoItems(project: Project, changes: Collection<Change>, todoItems: Collection<TodoItem>, isPostCommit: Boolean) {
      val todoView = project.service<TodoView>()
      val content = todoView.addCustomTodoView(
        { tree, _ ->
          if (isPostCommit) {
            PostCommitChecksTodosTreeBuilder(tree, project, changes, todoItems)
          }
          else {
            CommitChecksTodosTreeBuilder(tree, project, changes, todoItems)
          }
        },
        message("checkin.title.for.commit.0", formatDateTime(System.currentTimeMillis())),
        TodoPanelSettings(VcsConfiguration.getInstance(project).myTodoPanelSettings)
      )
      if (content == null) return

      runInEdt(ModalityState.nonModal()) {
        if (project.isDisposed) return@runInEdt
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TODO_VIEW) ?: return@runInEdt

        toolWindow.show {
          toolWindow.contentManager.setSelectedContent(content, true)
        }
      }
    }
  }
}

private fun confirmCommitWithSkippedFiles(worker: TodoCheckinHandlerWorker, @Button commitActionText: String) =
  yesNo(message("checkin.dialog.title.todo"), getDescription(worker))
    .icon(getWarningIcon())
    .yesText(commitActionText)
    .noText(getCancelButtonText())
    .ask(worker.project)

@YesNoCancelResult
private fun askReviewCommitCancel(worker: TodoCheckinHandlerWorker, @Button commitActionText: String): Int =
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