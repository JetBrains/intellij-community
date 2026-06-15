// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-thread-visibility.spec.md
// @spec community/plugins/agent-workbench/spec/core/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.chat.AgentChatOpenPendingTabsStateService
import com.intellij.agent.workbench.chat.AgentChatPendingEditorLifecycleService
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionCostHintBanner
import com.intellij.agent.workbench.sessions.AgentSessionCostHintStateService
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.jbcentral.JbCentralQuotaCliSupport
import com.intellij.agent.workbench.sessions.jbcentral.JbCentralQuotaHintBanner
import com.intellij.agent.workbench.sessions.jbcentral.JbCentralQuotaHintStateService
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.agent.workbench.sessions.service.AgentArchivedSessionsService
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityListener
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.agent.workbench.sessions.service.AgentSessionsToolWindowVisibilityService
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsListener
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModelDiff
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.ToolTipManager
import javax.swing.tree.TreePath
import org.jetbrains.annotations.TestOnly

internal fun createAgentSessionsNorthComponents(
  project: Project,
  parentDisposable: Disposable,
  refreshSessions: () -> Unit,
): List<JComponent> {
  val providerContributions = AgentSessionProviders.allProvidersById()
    .mapNotNull { provider -> provider.createToolWindowNorthComponent(project) }
  service<JbCentralQuotaHintStateService>().setEligible(JbCentralQuotaCliSupport.isAvailable())
  return buildList {
    add(AgentProviderCliStatusBanner(project, parentDisposable, refreshSessions = refreshSessions))
    add(AgentSessionCostHintBanner())
    add(JbCentralQuotaHintBanner())
    addAll(providerContributions)
  }
}

internal class AgentSessionsToolWindowPanel(
  private val project: Project,
  private val toolWindow: ToolWindow,
) : JPanel(BorderLayout()), Disposable, UiDataProvider {
  private val costHydrationVisibilityToken = "${project.locationHash}:${System.identityHashCode(this)}"
  private var sessionTreeModel: SessionTreeModel = SessionTreeModel.EMPTY
  private var initialRefreshRequested = false
  private val providerAvailabilityService = project.service<AgentSessionProviderAvailabilityService>()

  private val treeStructure = AgentSessionsTreeStructure { sessionTreeModel }
  private val structureTreeModel = StructureTreeModel(treeStructure, this)

  @Suppress("UNNECESSARY_LATEINIT")
  private lateinit var rowActionsOverlay: AgentSessionsTreeRowActionsOverlay

  @Suppress("UNNECESSARY_LATEINIT")
  private lateinit var interactionController: AgentSessionsTreeInteractionController

  @Suppress("UNNECESSARY_LATEINIT")
  private lateinit var dataContextProvider: AgentSessionsTreeDataContextProvider

  private val tree = object : Tree(AsyncTreeModel(structureTreeModel, this)), UiDataProvider {
    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      rowActionsOverlay.paint(g)
    }

    override fun getToolTipText(event: java.awt.event.MouseEvent?): String? {
      val mouseEvent = event ?: return null
      val path = getPathForLocation(mouseEvent.x, mouseEvent.y) ?: return null
      val bounds = getPathBounds(path) ?: return null
      val id = idFromPath(path) ?: return null
      val treeNode = sessionTreeNode(id) as? SessionTreeNode.Thread ?: return null
      val now = System.currentTimeMillis()
      val threadPresentation = buildSessionTreeThreadRowPresentation(treeNode = treeNode, now = now)
      val row = getRowForPath(path)
      val rowActions = if (row >= 0) {
        rowActionsOverlay.rowActionPresentation(
          row = row,
          treeNode = treeNode,
          selected = selectionModel.isRowSelected(row),
        )
      }
      else {
        null
      }
      val actionRightPadding = rowActions?.reservedWidth ?: 0

      val viewportLayout = resolveSessionTreeViewportLayout(this)
      val rowOverflowClipped = isSessionTreeRowClipped(
        pathBoundsX = bounds.x,
        pathBoundsWidth = bounds.width,
        helperX = viewportLayout.x,
        helperWidth = viewportLayout.width,
        helperRightMargin = viewportLayout.rightMargin,
        selectionRightInset = viewportLayout.selectionRightInset,
      )

      val fontMetrics = getFontMetrics(font)
      val sharedTimeColumnWidth = computeSessionTreeSharedTimeColumnWidth(fontMetrics)
      val horizontalLayout = computeSessionTreeThreadHorizontalLayout(
        contentWidth = bounds.width,
        actionRightPadding = actionRightPadding,
        selectionRightInset = viewportLayout.selectionRightInset,
        timeTextWidth = fontMetrics.stringWidth(threadPresentation.timeLabel),
        timeColumnWidth = sharedTimeColumnWidth,
      )
      val titleClipped = isSessionTreeThreadTitleClipped(
        title = threadPresentation.title,
        fontMetrics = fontMetrics,
        titleMaxWidth = horizontalLayout.titleMaxWidth,
      )
      if (!rowOverflowClipped && !titleClipped) return null

      val tooltipWidthPx = resolveSessionTreeThreadTooltipWidth(
        helperWidth = viewportLayout.width,
        helperRightMargin = viewportLayout.rightMargin,
        selectionRightInset = viewportLayout.selectionRightInset,
      )
      return buildSessionTreeThreadTooltipHtml(treeNode, now, maxWidthPx = tooltipWidthPx)
    }

    override fun uiDataSnapshot(sink: DataSink) {
      this@AgentSessionsToolWindowPanel.uiDataSnapshot(sink)
    }
  }

  private val stateController = AgentSessionsTreeStateController(
    sessionsStateFlow = service<AgentSessionReadService>().stateFlow(),
    archivedSessionsStateFlow = service<AgentArchivedSessionsService>().stateFlow(),
    threadViewStateFlow = service<AgentSessionThreadViewStateService>().state,
    selectedChatTabFlow = project.service<AgentChatTabSelectionService>().selectedChatTab,
    pendingChatTabsStateFlow = service<AgentChatOpenPendingTabsStateService>().state,
    markThreadAsRead = { path, provider, threadId, updatedAt ->
      service<AgentSessionRefreshService>().markThreadAsRead(path, provider, threadId, updatedAt)
    },
    ensureArchivedSessionsLoaded = { service<AgentArchivedSessionsService>().ensureLoaded() },
    tree = tree,
    getSessionTreeModel = { sessionTreeModel },
    setSessionTreeModel = {
      sessionTreeModel = it
      if (sessionTreeModelShouldMarkCostHintEligible(it)) {
        service<AgentSessionCostHintStateService>().markEligible()
      }
    },
    onLastUsedProviderChanged = {
      if (isModelUpdateVisible()) {
        tree.repaint()
      }
    },
    onBeforeModelSwap = {
      rowActionsOverlay.clearTransientState()
    },
    invalidateTreeModel = ::invalidateTreeModel,
    expandNode = { id -> structureTreeModel.expand(id, tree) { } },
    selectNodes = ::selectNodes,
  )

  private val northPanel: JPanel = buildNorthPanel()

  init {
    project.service<AgentChatPendingEditorLifecycleService>()
    service<AgentChatOpenPendingTabsStateService>().refreshOpenTabs()
    dataContextProvider = AgentSessionsTreeDataContextProvider(
      project = project,
      tree = tree,
      nodeResolver = ::sessionTreeNode,
      popupActionContextProvider = { interactionController.popupActionContext },
    )

    interactionController = AgentSessionsTreeInteractionController(
      project = project,
      tree = tree,
      rowActionsOverlayProvider = { rowActionsOverlay },
      nodeResolver = ::sessionTreeNode,
      selectedArchiveTargets = { dataContextProvider.selectedArchiveTargets() },
      selectedUnarchiveTargets = { dataContextProvider.selectedUnarchiveTargets() },
      showMoreProjects = ::showMoreProjectsForCurrentView,
      showMoreThreads = ::showMoreThreadsForCurrentView,
    )

    rowActionsOverlay = AgentSessionsTreeRowActionsOverlay(
      project = project,
      tree = tree,
      nodeResolver = ::sessionTreeNode,
    )

    installProviderAvailabilityRefresh()
    configureTree()
    add(northPanel, BorderLayout.NORTH)
    add(createSessionTreeScrollPane(tree), BorderLayout.CENTER)

    interactionController.install()
    installToolWindowVisibilityTracker()
    publishInitialToolWindowVisibility()
    stateController.start()
    requestInitialRefresh()
  }

  private fun installProviderAvailabilityRefresh() {
    providerAvailabilityService.requestRefresh(AgentSessionProviders.allProviders())
    project.messageBus.connect(this)
      .subscribe(AgentSessionProviderAvailabilityListener.TOPIC, object : AgentSessionProviderAvailabilityListener {
        override fun availabilityChanged() {
          tree.repaint()
        }
      })
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(AgentSessionProviderSettingsListener.TOPIC, object : AgentSessionProviderSettingsListener {
        override fun providerSettingsChanged() {
          providerAvailabilityService.requestRefresh(AgentSessionProviders.allProviders(), force = true)
          tree.repaint()
        }
      })
  }

  private fun installToolWindowVisibilityTracker() {
    project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(
        toolWindowManager: ToolWindowManager,
        changedToolWindow: ToolWindow,
        changeType: ToolWindowManagerListener.ToolWindowManagerEventType,
      ) {
        if (changedToolWindow == toolWindow) {
          applyToolWindowVisibility()
        }
      }

      override fun toolWindowShown(shownToolWindow: ToolWindow) {
        if (shownToolWindow == toolWindow) {
          applyToolWindowVisibility()
        }
      }
    })
  }

  private fun publishInitialToolWindowVisibility() {
    // Content creation is the first-paint signal for the tree rows. Cost hydration still uses
    // the actual tool window visibility published below, so hidden cold content does not load costs.
    stateController.setModelUpdatesVisible(true)
    service<AgentSessionsToolWindowVisibilityService>().setVisible(costHydrationVisibilityToken, isModelUpdateVisible())
  }

  private fun applyToolWindowVisibility() {
    val visible = isModelUpdateVisible()
    publishAgentSessionsToolWindowVisibility(
      visible = visible,
      token = costHydrationVisibilityToken,
      setModelUpdatesVisible = stateController::setModelUpdatesVisible,
      visibilityService = service(),
    )
    requestInitialRefresh()
  }

  private fun isModelUpdateVisible(): Boolean {
    return toolWindow.isVisible
  }

  private fun requestInitialRefresh() {
    if (initialRefreshRequested) return
    initialRefreshRequested = true
    service<AgentSessionRefreshService>().refresh()
  }

  private fun buildNorthPanel(): JPanel {
    val contributions = createAgentSessionsNorthComponents(
      project = project,
      parentDisposable = this,
      refreshSessions = {
        service<AgentSessionRefreshService>().refresh()
        service<AgentArchivedSessionsService>().refreshIfLoaded()
      },
    )
    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      contributions.forEach(::add)
    }
  }

  private fun configureTree() {
    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.emptyText.text = AgentSessionsBundle.message("toolwindow.loading")
    tree.selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
    tree.cellRenderer = SessionTreeCellRenderer(
      nowProvider = { System.currentTimeMillis() },
      rowActionsProvider = { row, treeNode, selected -> rowActionsOverlay.rowActionPresentation(row, treeNode, selected) },
      nodeResolver = { treeId -> sessionTreeModel.entriesById[treeId]?.node },
    )
    configureSessionTreeRenderingProperties(tree)
    TreeUIHelper.getInstance().installTreeSpeedSearch(tree)
    installSessionTreeSpeedSearchReveal(
      tree = tree,
      modelProvider = { sessionTreeModel },
      stateProvider = stateController::displayedStateSnapshot,
      ensureProjectVisible = ::ensureProjectVisibleForCurrentView,
      ensureThreadVisible = ::ensureThreadVisibleForCurrentView,
    )
    ToolTipManager.sharedInstance().registerComponent(tree)
    com.intellij.util.ui.tree.ExpandOnDoubleClick.DEFAULT.installOn(tree)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    dataContextProvider.uiDataSnapshot(sink)
  }

  private fun invalidateTreeModel(diff: SessionTreeModelDiff): CompletableFuture<*> {
    if (diff.rootChanged) {
      return structureTreeModel.invalidateAsync().handle { _, _ -> null }
    }

    var future: CompletableFuture<*> = CompletableFuture.completedFuture(null)
    diff.structureChangedIds.forEach { id ->
      future = future
        .handle { _, _ -> null }
        .thenCompose { structureTreeModel.invalidateAsync(id, true) }
    }
    diff.contentChangedIds.forEach { id ->
      future = future
        .handle { _, _ -> null }
        .thenCompose { structureTreeModel.invalidateAsync(id, false) }
    }
    return future.handle { _, _ -> null }
  }

  private fun selectNodes(
    ids: List<SessionTreeId>,
    shouldApply: () -> Boolean,
    scrollToVisible: Boolean,
    onApplied: (List<SessionTreeId>) -> Unit,
  ) {
    if (!shouldApply()) return
    val distinctIds = ids.distinct()
    if (distinctIds.isEmpty()) {
      tree.clearSelection()
      onApplied(emptyList())
      return
    }

    val remaining = AtomicInteger(distinctIds.size)
    val visiblePaths = Collections.synchronizedList(mutableListOf<TreePath>())

    fun applySelectionPaths(selectionPaths: Array<TreePath>) {
      if (!shouldApply()) return
      if (selectionPaths.isEmpty()) {
        tree.clearSelection()
        onApplied(emptyList())
        return
      }
      tree.selectionPaths = selectionPaths
      if (scrollToVisible) {
        TreeUtil.scrollToVisible(tree, selectionPaths.first(), false)
      }
      onApplied(selectionPaths.mapNotNull(::idFromPath).distinct())
    }

    fun finish(path: TreePath?) {
      if (path != null) {
        visiblePaths.add(path)
      }
      if (remaining.decrementAndGet() != 0) return
      val selectionPaths = synchronized(visiblePaths) { visiblePaths.toTypedArray() }
      EdtInvocationManager.invokeLaterIfNeeded { applySelectionPaths(selectionPaths) }
    }

    distinctIds.forEach { id ->
      structureTreeModel.promiseVisitor(id)
        .onSuccess { visitor ->
          if (!shouldApply()) {
            finish(null)
            return@onSuccess
          }
          TreeUtil.promiseMakeVisible(tree, visitor)
            .onSuccess { path -> finish(path) }
            .onError { finish(null) }
        }
        .onError { finish(null) }
    }
  }

  private fun idFromPath(path: TreePath?): SessionTreeId? {
    return path?.lastPathComponent?.let(::extractSessionTreeId)
  }

  private fun sessionTreeNode(id: SessionTreeId): SessionTreeNode? {
    return sessionTreeModel.entriesById[id]?.node
  }

  private fun isArchivedView(): Boolean {
    return service<AgentSessionThreadViewStateService>().state.value.mode == AgentSessionThreadViewMode.ARCHIVED
  }

  private fun showMoreProjectsForCurrentView() {
    if (isArchivedView()) {
      service<AgentArchivedSessionsService>().showMoreProjects()
    }
    else {
      service<AgentSessionsStateStore>().showMoreProjects()
    }
  }

  private fun showMoreThreadsForCurrentView(path: String) {
    if (isArchivedView()) {
      service<AgentArchivedSessionsService>().showMoreThreads(path)
    }
    else {
      service<AgentSessionsStateStore>().showMoreThreads(path)
    }
  }

  private fun ensureProjectVisibleForCurrentView(path: String) {
    if (isArchivedView()) {
      service<AgentArchivedSessionsService>().ensureProjectVisible(path)
    }
    else {
      service<AgentSessionsStateStore>().ensureProjectVisible(path)
    }
  }

  private fun ensureThreadVisibleForCurrentView(path: String, provider: AgentSessionProvider, threadId: String) {
    if (isArchivedView()) {
      service<AgentArchivedSessionsService>().ensureThreadVisible(path, provider, threadId)
    }
    else {
      service<AgentSessionsStateStore>().ensureThreadVisible(path, provider, threadId)
    }
  }

  @TestOnly
  internal fun containsSessionTreeIdForTest(id: SessionTreeId): Boolean {
    return id in sessionTreeModel.entriesById
  }

  override fun dispose() {
    service<AgentSessionsToolWindowVisibilityService>().release(costHydrationVisibilityToken)
    stateController.dispose()
  }
}

internal fun sessionTreeModelShouldMarkCostHintEligible(model: SessionTreeModel): Boolean {
  return model.entriesById.values.any { entry ->
    val threadNode = entry.node as? SessionTreeNode.Thread ?: return@any false
    !isAgentSessionNewSessionId(threadNode.thread.id)
  }
}

internal fun createSessionTreeScrollPane(tree: Tree): JScrollPane {
  return ScrollPaneFactory.createScrollPane(tree, true).apply {
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  }
}

internal fun publishAgentSessionsToolWindowVisibility(
  visible: Boolean,
  token: String,
  setModelUpdatesVisible: (Boolean) -> Unit,
  visibilityService: AgentSessionsToolWindowVisibilityService,
) {
  setModelUpdatesVisible(visible)
  visibilityService.setVisible(token, visible)
}
