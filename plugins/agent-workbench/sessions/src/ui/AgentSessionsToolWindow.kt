// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.ui

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-thread-visibility.spec.md

import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaStatusBarWidgetSettings
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.agent.workbench.sessions.state.AgentSessionTreeUiStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.tree.SessionTreeModelDiff
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.concurrent.CompletableFuture
import javax.swing.JPanel
import javax.swing.ToolTipManager
import javax.swing.tree.TreePath

internal class AgentSessionsToolWindowPanel(
  private val project: Project,
) : JPanel(BorderLayout()), Disposable, UiDataProvider {
  private val launchService = service<AgentSessionLaunchService>()
  private val readService = service<AgentSessionReadService>()
  private val stateStore = service<AgentSessionsStateStore>()
  private val syncService = service<AgentSessionRefreshService>()
  private val chatSelectionService = project.service<AgentChatTabSelectionService>()
  private val treeUiStateService = service<AgentSessionTreeUiStateService>()
  private val uiPreferencesStateService = service<AgentSessionUiPreferencesStateService>()

  private var sessionTreeModel: SessionTreeModel = SessionTreeModel.EMPTY
  private var lastUsedProvider: AgentSessionProvider? = null

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
      val actionRightPadding = sessionTreeRowActionRightPadding(rowActions?.actionSlots ?: 0)

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

  private val quotaHintPanel = ClaudeQuotaHintPanel(
    onEnable = {
      ClaudeQuotaStatusBarWidgetSettings.setEnabled(true)
      uiPreferencesStateService.acknowledgeClaudeQuotaHint()
    },
    onDismiss = {
      uiPreferencesStateService.acknowledgeClaudeQuotaHint()
    },
  )

  private val stateController = AgentSessionsTreeStateController(
    sessionsStateFlow = readService.stateFlow(),
    chatSelectionService = chatSelectionService,
    treeUiStateService = treeUiStateService,
    uiPreferencesStateService = uiPreferencesStateService,
    tree = tree,
    getSessionTreeModel = { sessionTreeModel },
    setSessionTreeModel = { sessionTreeModel = it },
    onLastUsedProviderChanged = { provider ->
      lastUsedProvider = provider
      tree.repaint()
    },
    onBeforeModelSwap = {
      rowActionsOverlay.clearTransientState()
    },
    invalidateTreeModel = ::invalidateTreeModel,
    expandNode = { id -> structureTreeModel.expand(id, tree) { } },
    selectNode = { id -> structureTreeModel.select(id, tree) { } },
  )

  private val quotaHintController = ClaudeQuotaHintController(
    uiPreferencesStateService = uiPreferencesStateService,
    quotaHintPanel = quotaHintPanel,
  )

  init {
    dataContextProvider = AgentSessionsTreeDataContextProvider(
      project = project,
      tree = tree,
      nodeResolver = ::sessionTreeNode,
      popupActionContextProvider = { interactionController.popupActionContext },
    )

    interactionController = AgentSessionsTreeInteractionController(
      project = project,
      tree = tree,
      launchService = launchService,
      syncService = syncService,
      stateStore = stateStore,
      treeUiStateService = treeUiStateService,
      rowActionsOverlayProvider = { rowActionsOverlay },
      nodeResolver = ::sessionTreeNode,
      selectedArchiveTargets = { dataContextProvider.selectedArchiveTargets() },
    )

    rowActionsOverlay = AgentSessionsTreeRowActionsOverlay(
      tree = tree,
      nodeResolver = ::sessionTreeNode,
      lastUsedProvider = { lastUsedProvider },
      onQuickCreate = { path, provider ->
        launchService.createNewSession(
          path = path,
          provider = provider,
          mode = AgentSessionLaunchMode.STANDARD,
          currentProject = project,
        )
      },
      onShowPopup = { nodeId, node, anchorRect, row ->
        interactionController.showNewSessionActionPopup(nodeId, node, anchorRect, row)
      },
    )

    configureTree()
    add(quotaHintPanel, BorderLayout.NORTH)
    add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
    quotaHintPanel.isVisible = false

    interactionController.install()
    stateController.start()
    quotaHintController.start()
    syncService.refresh()
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
      duplicateProjectNamesProvider = { sessionTreeModel.duplicateProjectNames },
    )
    configureSessionTreeRenderingProperties(tree)
    ToolTipManager.sharedInstance().registerComponent(tree)
    com.intellij.util.ui.tree.ExpandOnDoubleClick.DEFAULT.installOn(tree)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    dataContextProvider.uiDataSnapshot(sink)
  }

  private fun invalidateTreeModel(diff: SessionTreeModelDiff): CompletableFuture<*> {
    if (diff.rootChanged) {
      return structureTreeModel.invalidateAsync()
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
    return future
  }

  private fun idFromPath(path: TreePath?): SessionTreeId? {
    return path?.lastPathComponent?.let(::extractSessionTreeId)
  }

  private fun sessionTreeNode(id: SessionTreeId): SessionTreeNode? {
    return sessionTreeModel.entriesById[id]?.node
  }

  override fun dispose() {
    stateController.dispose()
    quotaHintController.dispose()
  }
}
