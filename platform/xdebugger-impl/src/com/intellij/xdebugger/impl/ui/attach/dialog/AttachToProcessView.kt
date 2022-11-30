package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addPreferredFocusedComponent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.application
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.impl.ui.attach.dialog.diagnostics.ProcessesFetchingProblemAction
import com.intellij.xdebugger.impl.ui.attach.dialog.diagnostics.ProcessesFetchingProblemException
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessItemsListBase
import com.intellij.xdebugger.impl.ui.attach.dialog.items.tree.buildTree
import com.intellij.xdebugger.impl.util.SequentialDisposables
import com.intellij.xdebugger.impl.util.isNotAlive
import com.intellij.xdebugger.impl.util.onTermination
import kotlinx.coroutines.*
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

internal abstract class AttachToProcessView(
  private val project: Project,
  protected val state: AttachDialogState,
  private val attachDebuggerProviders: List<XAttachDebuggerProvider>) {

  companion object {
    private val logger = Logger.getInstance(AttachToProcessView::class.java)
  }

  private val currentComponentDisposables: SequentialDisposables = SequentialDisposables(state.dialogDisposable)
  private var updateJob: Job? = null

  protected val centerPanel = JPanel(MigLayout("ins 0, fill, gapy 0")).apply {
    border = JBUI.Borders.empty(0, 0, 0, 0)
  }

  private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    if (throwable is CancellationException) {
      return@CoroutineExceptionHandler
    }
    logger.error(throwable)
  }
  protected val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)

  init {
    state.searchFieldValue.afterChange { updateSearchFilter(it) }
    state.dialogDisposable.onTermination { coroutineScope.cancel() }
  }

  fun getMainComponent() = centerPanel

  fun updateProcesses() {
    application.assertIsDispatchThread()
    if (state.dialogDisposable.isNotAlive) return
    showLoadingPanel()
    updateJob?.cancel()
    updateJob = coroutineScope.launch(coroutineExceptionHandler) {
      doUpdateProcesses()
    }
  }

  fun getFocusedComponent(): Component? = state.currentList.get()?.getFocusedComponent()

  abstract fun getViewActions(): List<AnAction>

  fun getName(): String = getHostType().displayText

  abstract fun getHostType(): AttachDialogHostType

  protected abstract suspend fun doUpdateProcesses()

  private fun showList(list: AttachToProcessItemsListBase, installDoubleClickHandler: Boolean) {
    val pane = JBScrollPane(list.getFocusedComponent())
    pane.border = JBUI.Borders.empty(0, 0, 0, 0)
    pane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    pane.verticalScrollBar.background = JBUI.CurrentTheme.List.BACKGROUND
    val listLifetimeDisposable = loadComponent(pane)
    installSelectionListener(list, listLifetimeDisposable, installDoubleClickHandler)
    list.updateFilter(state.searchFieldValue.get())
    state.currentList.set(list)
  }

  protected fun loadComponent(component: JComponent): Disposable {
    val disposable = currentComponentDisposables.next()
    centerPanel.removeAll()
    centerPanel.add(component, "push, growx, growy")
    centerPanel.addPreferredFocusedComponent(component)
    centerPanel.revalidate()
    centerPanel.repaint()
    state.selectedDebuggerItem.set(null)
    return disposable
  }

  protected suspend fun collectAndShowItems(host: XAttachHost) {
    if (state.selectedViewType.get() == AttachViewType.TREE) {
      processAsTree(host)
    }
    else {
      processAsPlainList(host)
    }
  }

  private suspend fun processAsTree(host: XAttachHost) {
    try {
      val attachInfo = try {
        collectAttachProcessItemsGroupByProcessInfo(project, host, attachDebuggerProviders)
      }
      catch (processesFetchingException: ProcessesFetchingProblemException) {
        processDiagnosticInfo(processesFetchingException, host)
        return
      }

      processTreeItems(attachInfo)
    }
    catch (executionException: ExecutionException) {
      processPlainListItems(AttachItemsInfo.EMPTY)
    }
    catch (cancellationException: CancellationException) {
      throw cancellationException
    }
    catch (t: Throwable) {
      logger.error(t)
      processPlainListItems(AttachItemsInfo.EMPTY)
    }
  }

  private suspend fun processAsPlainList(host: XAttachHost) {
    val itemsInfo = try {
      collectAttachProcessItemsGroupByProcessInfo(project, host, attachDebuggerProviders)
    }
    catch (processesFetchingException: ProcessesFetchingProblemException) {
      processDiagnosticInfo(processesFetchingException, host)
      return
    }

    processPlainListItems(itemsInfo)
  }

  private suspend fun processDiagnosticInfo(problem: ProcessesFetchingProblemException, host: XAttachHost) {
    val panel = getActionablePane(problem) { selectedAction ->
      try {
        coroutineScope.launch {
          withUiContextAnyModality {

            val progressIndicator = showLoadingPanel()
            selectedAction.action(project, host, progressIndicator)
            updateProcesses()
          }
        }
      }
      catch (t: Throwable) {
        logger.error(t)
      }
    }

    withUiContextAnyModality {
      loadComponent(panel)
    }
  }

  private suspend fun processPlainListItems(items: AttachItemsInfo) {
    val list = com.intellij.xdebugger.impl.ui.attach.dialog.items.list.buildList(items, state)

    withUiContextAnyModality {
      showList(list, true)
    }
  }

  private suspend fun processTreeItems(attachItemsInfo: AttachItemsInfo) {
    val tree = buildTree(attachItemsInfo, state)

    withUiContextAnyModality {
      showList(tree, false)
    }
  }

  private fun showLoadingPanel(): ProgressIndicator {
    val jbLoadingPanel = JBLoadingPanel(BorderLayout(), state.dialogDisposable)
    jbLoadingPanel.background = JBUI.CurrentTheme.List.BACKGROUND

    val progressIndicator = object : ProgressIndicatorBase() {
      override fun setText(text: String?) {
        jbLoadingPanel.setLoadingText(text)
      }
    }

    jbLoadingPanel.startLoading()
    loadComponent(jbLoadingPanel)

    return progressIndicator
  }

  protected suspend fun withUiContextAnyModality(action: suspend () -> Unit) {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      action()
    }
  }

  private fun updateSearchFilter(searchFilter: String) {
    coroutineScope.launch {
      withUiContextAnyModality {
        val attachList = state.currentList.get()
        attachList?.updateFilter(searchFilter)
      }
    }
  }

  private fun getActionablePane(problem: ProcessesFetchingProblemException, onClick: (ProcessesFetchingProblemAction) -> Unit): JComponent {
    val component = JPanel(MigLayout("ins 0, gap 0, fill"))
    component.add(JLabel(problem.descriptionDisplayText), "alignx center, aligny bottom, wrap")

    val action = problem.action
    if (action != null) {
      component.add(ActionLink(action.actionName) { onClick(action) }, "alignx center, aligny top, wrap")
    }
    component.background = JBUI.CurrentTheme.List.BACKGROUND
    return component
  }

  private fun installSelectionListener(
    list: AttachToProcessItemsListBase,
    disposable: Disposable,
    addDoubleClickHandler: Boolean) {
    list.addSelectionListener(disposable) {
      state.selectedDebuggerItem.set(it?.getProcessItem())
    }
    state.selectedDebuggerItem.set(list.getSelectedItem()?.getProcessItem())

    if (addDoubleClickHandler) {
      val mouseListener = object : MouseListener {
        override fun mouseClicked(e: MouseEvent?) {
          if ((e?.clickCount ?: 0) == 2) {
            state.itemWasDoubleClicked.set(true)
          }
        }

        override fun mousePressed(e: MouseEvent?) {
        }

        override fun mouseReleased(e: MouseEvent?) {
        }

        override fun mouseEntered(e: MouseEvent?) {
        }

        override fun mouseExited(e: MouseEvent?) {
        }
      }
      list.getFocusedComponent().addMouseListener(mouseListener)
      Disposer.register(disposable) { list.getFocusedComponent().removeMouseListener(mouseListener) }
    }
  }
}