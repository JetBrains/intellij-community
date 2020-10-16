// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.ide.IdeBundle
import com.intellij.ide.todo.*
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YesNoCancelResult
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.Consumer
import com.intellij.util.PairConsumer
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.UIUtil.getWarningIcon
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class TodoCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler = TodoCheckinHandler(panel)
}

class TodoCheckinHandler(checkinProjectPanel: CheckinProjectPanel) : CheckinHandler(), CommitCheck {
  private val myProject: Project = checkinProjectPanel.project
  private val myCheckinProjectPanel: CheckinProjectPanel = checkinProjectPanel
  private val myConfiguration: VcsConfiguration = VcsConfiguration.getInstance(myProject)

  private var myTodoFilter: TodoFilter? = null

  override fun isEnabled(): Boolean = myConfiguration.CHECK_NEW_TODO

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
    return object : BooleanCommitOption(myCheckinProjectPanel, message("before.checkin.new.todo.check", ""), true,
                                        myConfiguration::CHECK_NEW_TODO) {
      override fun getComponent(): JComponent {
        val panel = JPanel(BorderLayout(4, 0))
        panel.add(checkBox, BorderLayout.WEST)
        setFilterText(myConfiguration.myTodoPanelSettings.todoFilterName)
        if (myConfiguration.myTodoPanelSettings.todoFilterName != null) {
          myTodoFilter = TodoConfiguration.getInstance().getTodoFilter(myConfiguration.myTodoPanelSettings.todoFilterName)
        }
        val consumer = Consumer { todoFilter: TodoFilter? ->
          myTodoFilter = todoFilter
          val name = todoFilter?.name
          myConfiguration.myTodoPanelSettings.todoFilterName = name
          setFilterText(name)
        }
        val linkLabel = LinkLabel<Any>(message("settings.filter.configure.link"), null)
        linkLabel.setListener(LinkListener { _, _ ->
          val group = SetTodoFilterAction.createPopupActionGroup(myProject, myConfiguration.myTodoPanelSettings, consumer)
          JBPopupMenu.showBelow(linkLabel, ActionPlaces.TODO_VIEW_TOOLBAR, group)
        }, null)
        panel.add(linkLabel, BorderLayout.CENTER)
        return panel
      }

      private fun setFilterText(filterName: String?) {
        if (filterName == null) {
          checkBox.text = message("before.checkin.new.todo.check", IdeBundle.message("action.todo.show.all"))
        }
        else {
          checkBox.text = message("before.checkin.new.todo.check",
                                  message("checkin.filter.filter.name", filterName))
        }
      }
    }
  }

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {
    if (!isEnabled()) return ReturnResult.COMMIT
    if (DumbService.getInstance(myProject).isDumb) {
      return if (confirmCommitInDumbMode(myProject)) ReturnResult.COMMIT else ReturnResult.CANCEL
    }
    val changes = myCheckinProjectPanel.selectedChanges
    val worker = TodoCheckinHandlerWorker(myProject, changes, myTodoFilter)
    val completed = Ref.create(false)
    ProgressManager.getInstance().run(object : Task.Modal(myProject,
                                                          message("checkin.dialog.title.looking.for.new.edited.todo.items"),
                                                          true) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        worker.execute()
      }

      override fun onSuccess() = completed.set(true)
    })
    if (completed.get() && ((worker.addedOrEditedTodos.isEmpty() && worker.inChangedTodos.isEmpty() &&
                             worker.skipped.isEmpty()))) return ReturnResult.COMMIT
    return if (!completed.get()) ReturnResult.CANCEL else showResults(worker, executor)
  }

  private fun showResults(worker: TodoCheckinHandlerWorker, executor: CommitExecutor?): ReturnResult {
    var commitButtonText = executor?.actionText ?: myCheckinProjectPanel.commitActionName
    commitButtonText = StringUtil.trimEnd(commitButtonText!!, "...")
    val thereAreTodoFound = worker.addedOrEditedTodos.size + worker.inChangedTodos.size > 0
    if (thereAreTodoFound) {
      return askReviewOrCommit(worker, commitButtonText)
    }
    return if (confirmCommitWithSkippedFiles(worker, commitButtonText)) ReturnResult.COMMIT else ReturnResult.CANCEL
  }

  private fun askReviewOrCommit(worker: TodoCheckinHandlerWorker, @NlsContexts.Button commitButton: String): ReturnResult {
    return when (askReviewCommitCancel(worker, commitButton)) {
      Messages.YES -> {
        showTodo(worker)
        ReturnResult.CLOSE_WINDOW
      }
      Messages.NO -> ReturnResult.COMMIT
      else -> ReturnResult.CANCEL
    }
  }

  private fun showTodo(worker: TodoCheckinHandlerWorker) {
    val title = message("checkin.title.for.commit.0", DateFormatUtil.formatDateTime(System.currentTimeMillis()))
    ServiceManager.getService(myProject, TodoView::class.java).addCustomTodoView(
      TodoTreeBuilderFactory { tree, _ -> CustomChangelistTodosTreeBuilder(tree, myProject, worker.changes, worker.inOneList()) },
      title, TodoPanelSettings(myConfiguration.myTodoPanelSettings)
    )
    ApplicationManager.getApplication().invokeLater(
      {
        val manager = ToolWindowManager.getInstance(myProject)
        val window = manager.getToolWindow("TODO")

        window?.show(Runnable {
          val cm = window.contentManager
          val contents = cm.contents
          if (contents.isNotEmpty()) {
            cm.setSelectedContent(contents.last(), true)
          }
        })
      },
      ModalityState.NON_MODAL, myProject.disposed
    )
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