package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-thread-visibility.spec.md

import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaStatusBarWidgetSettings
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.ProductIcons
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.IconManager
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleColoredComponent.FragmentTextClipper
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.LeafState
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.ui.PlainSelectionTree
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import kotlin.time.Duration.Companion.seconds

internal class AgentSessionsToolWindowPanel(
  private val project: Project,
) : JPanel(BorderLayout()), Disposable, UiDataProvider {
  private val service = service<AgentSessionsService>()
  private val chatSelectionService = project.service<AgentChatTabSelectionService>()
  private val treeUiStateService = service<AgentSessionsTreeUiStateService>()
  @Suppress("RAW_SCOPE_CREATION")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)

  private var sessionTreeModel: SessionTreeModel = SessionTreeModel.EMPTY
  private val treeStructure = AgentSessionsTreeStructure { sessionTreeModel }
  private val structureTreeModel = StructureTreeModel(treeStructure, this)
  private val tree = object : Tree(AsyncTreeModel(structureTreeModel, this)), UiDataProvider {
    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      paintNewSessionRowActions(g)
    }

    override fun getToolTipText(event: MouseEvent?): String? {
      val mouseEvent = event ?: return null
      val path = getPathForLocation(mouseEvent.x, mouseEvent.y) ?: return null
      val id = idFromPath(path) ?: return null
      val treeNode = sessionTreeNode(id) as? SessionTreeNode.Thread ?: return null
      return buildSessionTreeThreadTooltipHtml(treeNode, System.currentTimeMillis())
    }

    override fun uiDataSnapshot(sink: DataSink) {
      this@AgentSessionsToolWindowPanel.uiDataSnapshot(sink)
    }
  }
  private val quotaHintPanel = ClaudeQuotaHintPanel(
    onEnable = {
      ClaudeQuotaStatusBarWidgetSettings.setEnabled(true)
      treeUiStateService.acknowledgeClaudeQuotaHint()
    },
    onDismiss = {
      treeUiStateService.acknowledgeClaudeQuotaHint()
    },
  )

  private var sessionsState: AgentSessionsState = AgentSessionsState()
  private var selectedChatTab: AgentChatTabSelection? = null
  private var lastUsedProvider: AgentSessionProvider? = null
  private var claudeQuotaHintEligible: Boolean = false
  private var claudeQuotaHintAcknowledged: Boolean = false
  private var isClaudeQuotaWidgetEnabled: Boolean = false
  private var treeUpdateSequence: Long = 0
  private var hoveredRowAction: RowActionHit? = null
  private var popupPinnedRow: Int? = null
  private var popupActionContext: AgentSessionsTreePopupActionContext? = null
  private var rebuildJob: Job? = null

  init {
    configureTree()
    add(quotaHintPanel, BorderLayout.NORTH)
    add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
    quotaHintPanel.isVisible = false

    collectState()
    service.refresh()
  }

  private fun configureTree() {
    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.emptyText.text = AgentSessionsBundle.message("toolwindow.loading")
    tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
    tree.cellRenderer = SessionTreeCellRenderer(
      nowProvider = { System.currentTimeMillis() },
      rowActionsProvider = { row, treeNode, selected -> rowActionPresentation(row, treeNode, selected) },
      nodeResolver = { treeId -> sessionTreeModel.entriesById[treeId]?.node },
    )
    tree.setExpandableItemsEnabled(false)
    tree.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    ToolTipManager.sharedInstance().registerComponent(tree)

    TreeUtil.installActions(tree)
    TreeUIHelper.getInstance().installTreeSpeedSearch(tree)
    TreeHoverListener.DEFAULT.addTo(tree)
    EditSourceOnDoubleClickHandler.install(tree, Runnable { activateSelectedNode() })
    installEnterKeyActivation()

    val mouseHandler = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (!SwingUtilities.isLeftMouseButton(e) || e.clickCount != 1) return
        if (handleRowActionClick(e)) {
          e.consume()
          return
        }
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        val id = idFromPath(path) ?: return
        val treeNode = sessionTreeNode(id) ?: return
        if (!shouldHandleSingleClick(treeNode)) return
        if (runNodeAction(id = id, treeNode = treeNode, includeOpenActions = false)) {
          e.consume()
        }
      }

      override fun mouseMoved(e: MouseEvent) {
        updateRowActionHover(e.point)
      }

      override fun mouseExited(e: MouseEvent) {
        clearRowActionHover()
      }

      override fun mousePressed(e: MouseEvent) {
        maybeShowPopup(e)
      }

      override fun mouseReleased(e: MouseEvent) {
        maybeShowPopup(e)
      }
    }
    tree.addMouseListener(mouseHandler)
    tree.addMouseMotionListener(mouseHandler)

    tree.addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        when (val id = idFromPath(event.path) ?: return) {
          is SessionTreeId.Project -> {
            treeUiStateService.setProjectCollapsed(id.path, collapsed = false)
            val projectNode = sessionTreeNode(id) as? SessionTreeNode.Project ?: return
            if (!projectNode.project.hasLoaded && !projectNode.project.isLoading) {
              service.loadProjectThreadsOnDemand(id.path)
            }
          }

          is SessionTreeId.Worktree -> {
            val worktreeNode = sessionTreeNode(id) as? SessionTreeNode.Worktree ?: return
            if (!worktreeNode.worktree.hasLoaded && !worktreeNode.worktree.isLoading) {
              service.loadWorktreeThreadsOnDemand(id.projectPath, id.worktreePath)
            }
          }

          else -> Unit
        }
      }

      override fun treeCollapsed(event: TreeExpansionEvent) {
        val id = idFromPath(event.path) as? SessionTreeId.Project ?: return
        treeUiStateService.setProjectCollapsed(id.path, collapsed = true)
      }
    })
  }

  private fun installEnterKeyActivation() {
    val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
    val fallbackListener = tree.getActionForKeyStroke(enter)
    tree.registerKeyboardAction({ event ->
      val handled = activateSelectedNode()
      if (!handled) {
        fallbackListener?.actionPerformed(event)
      }
    }, enter, 0)
  }

  private fun rowActionPresentation(
    row: Int,
    treeNode: SessionTreeNode,
    selected: Boolean,
  ): SessionTreeRowActionPresentation? {
    val rowActions = resolveNewSessionRowActions(treeNode, lastUsedProvider) ?: return null
    val isHovered = TreeHoverListener.getHoveredRow(tree) == row
    val isPinned = popupPinnedRow == row
    val showInteractiveActions = selected || isHovered || isPinned
    val showLoadingAction = when (treeNode) {
      is SessionTreeNode.Project -> treeNode.project.isLoading
      is SessionTreeNode.Worktree -> treeNode.worktree.isLoading
      else -> false
    }
    if (!showInteractiveActions && !showLoadingAction) return null

    val quickIcon = rowActions.quickProvider?.let { provider ->
      providerIcon(provider) ?: AllIcons.General.Add
    }
    val hoveredKind = hoveredRowAction?.takeIf { it.row == row }?.kind
    return SessionTreeRowActionPresentation(
      showLoadingAction = showLoadingAction,
      quickIcon = quickIcon,
      showQuickAction = showInteractiveActions && rowActions.quickProvider != null,
      showPopupAction = showInteractiveActions,
      hoveredKind = hoveredKind,
    )
  }

  private fun rowActionRects(row: Int, presentation: SessionTreeRowActionPresentation): RowActionRects? {
    val bounds = tree.getRowBounds(row) ?: return null
    if (!tree.visibleRect.intersects(bounds)) return null

    val helper = RenderingHelper(tree)
    val slot = JBUI.scale(SESSION_TREE_ACTION_SLOT_SIZE)
    val rightGap = JBUI.scale(SESSION_TREE_ACTION_RIGHT_GAP)
    val gap = JBUI.scale(SESSION_TREE_ACTION_GAP)
    val y = bounds.y + (bounds.height - slot) / 2

    var right = sessionTreeRowActionsRightBoundary(
      helperWidth = helper.width,
      helperRightMargin = helper.rightMargin,
      rightGap = rightGap,
      selectionRightInset = sessionTreeThreadSelectionRightInset(tree),
    )

    fun consumeSlot(show: Boolean): Rectangle? {
      if (!show) return null
      val rect = Rectangle(right - slot, y, slot, slot)
      right = rect.x - gap
      return rect
    }

    val popupRect = consumeSlot(show = presentation.showPopupAction)
    val quickRect = consumeSlot(show = presentation.showQuickAction)
    val loadingRect = consumeSlot(show = presentation.showLoadingAction)
    return RowActionRects(
      loadingRect = loadingRect,
      quickRect = quickRect,
      popupRect = popupRect,
    )
  }

  private fun rowActionAtPoint(point: Point): RowActionHit? {
    val row = TreeUtil.getRowForLocation(tree, point.x, point.y)
    if (row < 0) return null
    val canShowActions = tree.selectionModel.isRowSelected(row) || TreeHoverListener.getHoveredRow(tree) == row || popupPinnedRow == row
    if (!canShowActions) return null

    val path = tree.getPathForRow(row) ?: return null
    val treeId = idFromPath(path) ?: return null
    val treeNode = sessionTreeNode(treeId) ?: return null
    val rowActions = resolveNewSessionRowActions(treeNode, lastUsedProvider) ?: return null
    val presentation = rowActionPresentation(
      row = row,
      treeNode = treeNode,
      selected = tree.selectionModel.isRowSelected(row),
    ) ?: return null
    val rects = rowActionRects(row = row, presentation = presentation) ?: return null
    val quickRect = rects.quickRect
    if (quickRect != null && quickRect.contains(point)) {
      return RowActionHit(row = row, nodeId = treeId, node = treeNode, kind = RowActionKind.QuickCreate, actions = rowActions, rects = rects)
    }
    val popupRect = rects.popupRect
    if (popupRect != null && popupRect.contains(point)) {
      return RowActionHit(row = row, nodeId = treeId, node = treeNode, kind = RowActionKind.ShowPopup, actions = rowActions, rects = rects)
    }
    return null
  }

  private fun handleRowActionClick(event: MouseEvent): Boolean {
    val hit = rowActionAtPoint(event.point) ?: return false
    if (!tree.selectionModel.isRowSelected(hit.row)) {
      tree.setSelectionRow(hit.row)
    }

    when (hit.kind) {
      RowActionKind.QuickCreate -> {
        val provider = hit.actions.quickProvider ?: return false
        service.createNewSession(
          path = hit.actions.path,
          provider = provider,
          mode = AgentSessionLaunchMode.STANDARD,
          currentProject = project,
        )
      }

      RowActionKind.ShowPopup -> {
        val popupRect = hit.rects.popupRect ?: return false
        showNewSessionActionPopup(
          nodeId = hit.nodeId,
          node = hit.node,
          anchorRect = popupRect,
          row = hit.row,
        )
      }
    }
    return true
  }

  private fun updateRowActionHover(point: Point) {
    val previous = hoveredRowAction
    val next = rowActionAtPoint(point)
    if (previous == next) return

    hoveredRowAction = next
    previous?.let { TreeUtil.repaintRow(tree, it.row) }
    next?.let { TreeUtil.repaintRow(tree, it.row) }
    tree.cursor = if (next != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
  }

  private fun clearRowActionHover() {
    val previous = hoveredRowAction
    hoveredRowAction = null
    previous?.let { TreeUtil.repaintRow(tree, it.row) }
    tree.cursor = Cursor.getDefaultCursor()
  }

  private fun showNewSessionActionPopup(
    nodeId: SessionTreeId,
    node: SessionTreeNode,
    anchorRect: Rectangle,
    row: Int,
  ) {
    val actionGroup = ActionManager.getInstance().getAction(AGENT_SESSIONS_TREE_POPUP_NEW_THREAD_GROUP_ID) as? ActionGroup
                      ?: return
    popupActionContext = AgentSessionsTreePopupActionContext(
      project = project,
      nodeId = nodeId,
      node = node,
      archiveTargets = selectedArchiveTargets(),
    )
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, actionGroup)
    popupMenu.setTargetComponent(tree)
    popupPinnedRow = row
    TreeUtil.repaintRow(tree, row)
    popupMenu.component.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
      override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) = Unit

      override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {
        clearPopupPinnedRow(row)
        clearPopupActionContext()
      }

      override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {
        clearPopupPinnedRow(row)
        clearPopupActionContext()
      }
    })
    popupMenu.component.show(tree, anchorRect.x, anchorRect.y + anchorRect.height)
  }

  private fun clearPopupPinnedRow(row: Int) {
    if (popupPinnedRow != row) return
    popupPinnedRow = null
    TreeUtil.repaintRow(tree, row)
  }

  private fun maybeShowPopup(event: MouseEvent) {
    if (!event.isPopupTrigger) return
    val path = tree.getPathForLocation(event.x, event.y) ?: return
    if (shouldRetargetSelectionForContextMenu(tree.selectionModel.isPathSelected(path))) {
      tree.selectionPath = path
    }
    val id = idFromPath(path) ?: return
    val treeNode = sessionTreeNode(id) ?: return
    val actionGroup = ActionManager.getInstance().getAction(AGENT_SESSIONS_TREE_POPUP_ACTION_GROUP_ID) as? ActionGroup
                      ?: return
    popupActionContext = AgentSessionsTreePopupActionContext(
      project = project,
      nodeId = id,
      node = treeNode,
      archiveTargets = selectedArchiveTargets(),
    )
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, actionGroup)
    popupMenu.setTargetComponent(tree)
    popupMenu.component.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
      override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) = Unit

      override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {
        clearPopupActionContext()
      }

      override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {
        clearPopupActionContext()
      }
    })
    popupMenu.component.show(tree, event.x, event.y)
  }

  private fun clearPopupActionContext() {
    popupActionContext = null
  }

  override fun uiDataSnapshot(sink: DataSink) {
    val selectedTreeId = selectedTreeId()
    val selectedTreeNode = selectedTreeId?.let(::sessionTreeNode)
    sink[AgentSessionsTreePopupDataKeys.CONTEXT] = resolveArchiveActionContext(
      popupActionContext = popupActionContext,
      project = project,
      selectedTreeId = selectedTreeId,
      selectedTreeNode = selectedTreeNode,
      selectedArchiveTargets = selectedArchiveTargets(),
    )
  }

  private fun selectedArchiveTargets(): List<ArchiveThreadTarget> {
    val targetsByKey = LinkedHashMap<String, ArchiveThreadTarget>()
    selectedTreeIds().forEach { id ->
      val threadNode = sessionTreeNode(id) as? SessionTreeNode.Thread ?: return@forEach
      val target = archiveTargetFromThreadNode(id, threadNode)
      val key = "${target.path}:${target.provider}:${target.threadId}"
      targetsByKey.putIfAbsent(key, target)
    }
    return targetsByKey.values.toList()
  }

  private fun collectState() {
    scope.launch {
      service.state.collect { newState ->
        sessionsState = newState
        rebuildTree()
      }
    }

    scope.launch {
      chatSelectionService.selectedChatTab.collect { selection ->
        selectedChatTab = selection
        rebuildTree()
      }
    }

    scope.launch {
      treeUiStateService.lastUsedProviderFlow.collect { provider ->
        lastUsedProvider = provider
        tree.repaint()
      }
    }

    scope.launch {
      treeUiStateService.claudeQuotaHintEligibleFlow.collect { eligible ->
        claudeQuotaHintEligible = eligible
        syncClaudeQuotaHintState()
      }
    }

    scope.launch {
      treeUiStateService.claudeQuotaHintAcknowledgedFlow.collect { acknowledged ->
        claudeQuotaHintAcknowledged = acknowledged
        syncClaudeQuotaHintState()
      }
    }

    scope.launch {
      ClaudeQuotaStatusBarWidgetSettings.enabledFlow.collect { enabled ->
        isClaudeQuotaWidgetEnabled = enabled
        syncClaudeQuotaHintState()
      }
    }

    scope.launch(Default) {
      while (isActive) {
        ClaudeQuotaStatusBarWidgetSettings.syncEnabledState()
        delay(1.seconds)
      }
    }
  }

  private fun syncClaudeQuotaHintState() {
    if (shouldAcknowledgeClaudeQuotaHint(
        eligible = claudeQuotaHintEligible,
        acknowledged = claudeQuotaHintAcknowledged,
        widgetEnabled = isClaudeQuotaWidgetEnabled,
      )
    ) {
      treeUiStateService.acknowledgeClaudeQuotaHint()
    }
    quotaHintPanel.isVisible = shouldShowClaudeQuotaHint(
      eligible = claudeQuotaHintEligible,
      acknowledged = claudeQuotaHintAcknowledged,
      widgetEnabled = isClaudeQuotaWidgetEnabled,
    )
  }

  private fun paintNewSessionRowActions(graphics: Graphics) {
    val g2 = graphics.create() as? Graphics2D ?: return
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      val visibleRows = visibleRowRange() ?: return
      for (row in visibleRows) {
        val path = tree.getPathForRow(row) ?: continue
        val treeNode = treeNodeFromPath(path) ?: continue
        val presentation = rowActionPresentation(
          row = row,
          treeNode = treeNode,
          selected = tree.selectionModel.isRowSelected(row),
        ) ?: continue
        val rects = rowActionRects(row = row, presentation = presentation) ?: continue

        val loadingRect = rects.loadingRect
        if (loadingRect != null) {
          paintIconCentered(AnimatedIcon.Default.INSTANCE, loadingRect, g2)
        }

        val quickRect = rects.quickRect
        if (quickRect != null && presentation.quickIcon != null) {
          paintRowActionSlot(g2, quickRect, hover = presentation.hoveredKind == RowActionKind.QuickCreate)
          paintIconCentered(presentation.quickIcon, quickRect, g2)
        }

        val popupRect = rects.popupRect
        if (popupRect != null) {
          paintRowActionSlot(g2, popupRect, hover = presentation.hoveredKind == RowActionKind.ShowPopup)
          paintIconCentered(LayeredIcon.ADD_WITH_DROPDOWN, popupRect, g2)
        }
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun visibleRowRange(): IntRange? {
    val rowCount = tree.rowCount
    if (rowCount <= 0) return null
    val visibleRect = tree.visibleRect
    if (visibleRect.height <= 0) return null
    val first = tree.getClosestRowForLocation(visibleRect.x, visibleRect.y).coerceIn(0, rowCount - 1)
    val last = tree.getClosestRowForLocation(visibleRect.x, visibleRect.y + visibleRect.height - 1).coerceIn(first, rowCount - 1)
    return first..last
  }

  private fun paintRowActionSlot(graphics: Graphics2D, rect: Rectangle, hover: Boolean) {
    if (!hover) return
    val arc = JBUI.scale(8)
    graphics.color = JBColor.namedColor("ActionButton.hoverBackground", JBColor(0xE6EEF7, 0x4F5B66))
    graphics.fillRoundRect(rect.x, rect.y, rect.width, rect.height, arc, arc)
  }

  private fun paintIconCentered(icon: Icon, rect: Rectangle, graphics: Graphics2D) {
    val size = JBUI.scale(SESSION_TREE_ACTION_ICON_SIZE)
    val scaledIcon = IconUtil.toSize(icon, size, size)
    val x = rect.x + (rect.width - scaledIcon.iconWidth) / 2
    val y = rect.y + (rect.height - scaledIcon.iconHeight) / 2
    scaledIcon.paintIcon(tree, graphics, x, y)
  }

  private fun rebuildTree() {
    rebuildJob?.cancel()
    val snapshotState = sessionsState
    val snapshotSelectedChatTab = selectedChatTab
    val oldModel = sessionTreeModel
    val updateSequence = ++treeUpdateSequence
    rebuildJob = scope.launch {
      val (nextModel, treeModelDiff, selectedTreeId) = withContext(Default) {
        val model = buildSessionTreeModel(
          projects = snapshotState.projects,
          visibleClosedProjectCount = snapshotState.visibleClosedProjectCount,
          visibleThreadCounts = snapshotState.visibleThreadCounts,
          treeUiState = treeUiStateService,
        )
        val diff = diffSessionTreeModels(oldModel, model)
        val selection = resolveSelectedSessionTreeId(snapshotState.projects, snapshotSelectedChatTab)
        Triple(model, diff, selection)
      }
      if (treeUpdateSequence != updateSequence) return@launch

      popupPinnedRow = null
      clearRowActionHover()
      sessionTreeModel = nextModel
      updateEmptyText()

      invalidateTreeModel(treeModelDiff).thenRun {
        SwingUtilities.invokeLater {
          if (treeUpdateSequence != updateSequence) return@invokeLater
          applyDefaultExpansion(nextModel, selectedTreeId)
          applySelection(selectedTreeId)
        }
      }
    }
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

  private fun updateEmptyText() {
    val message = when {
      sessionsState.projects.isEmpty() && sessionsState.lastUpdatedAt == null -> AgentSessionsBundle.message("toolwindow.loading")
      sessionsState.projects.isEmpty() -> AgentSessionsBundle.message("toolwindow.empty.global")
      else -> ""
    }
    tree.emptyText.text = message
  }

  private fun applyDefaultExpansion(model: SessionTreeModel, selectedTreeId: SessionTreeId?) {
    val expandedProjects = TreeUtil.collectExpandedObjects(tree) { path ->
      idFromPath(path) as? SessionTreeId.Project
    }.toSet()

    model.autoOpenProjects.forEach { projectId ->
      if (projectId !in expandedProjects) {
        structureTreeModel.expand(projectId, tree) { }
      }
    }

    selectedTreeId?.let { treeId ->
      parentNodesForSelection(treeId).forEach { parentId ->
        if (parentId in model.entriesById) {
          structureTreeModel.expand(parentId, tree) { }
        }
      }
    }
  }

  private fun applySelection(selectedTreeId: SessionTreeId?) {
    if (selectedTreeId == null) {
      tree.clearSelection()
      return
    }
    if (selectedTreeId !in sessionTreeModel.entriesById) {
      tree.clearSelection()
      return
    }
    structureTreeModel.select(selectedTreeId, tree) { }
  }

  private fun activateSelectedNode(): Boolean {
    val id = selectedTreeId() ?: return false
    val treeNode = sessionTreeNode(id) ?: return false
    return runNodeAction(id = id, treeNode = treeNode, includeOpenActions = true)
  }

  private fun runNodeAction(id: SessionTreeId, treeNode: SessionTreeNode, includeOpenActions: Boolean): Boolean {
    return when (treeNode) {
      is SessionTreeNode.MoreProjects -> {
        service.showMoreProjects()
        true
      }

      is SessionTreeNode.MoreThreads -> {
        val path = pathForMoreThreadsNode(id) ?: return false
        service.showMoreThreads(path)
        true
      }

      is SessionTreeNode.Thread -> {
        if (!includeOpenActions) return false
        if (isAgentSessionNewSessionId(treeNode.thread.id)) return false
        val path = pathForThreadNode(id, treeNode.project.path)
        service.openChatThread(path, treeNode.thread, project)
        true
      }

      is SessionTreeNode.SubAgent -> {
        if (!includeOpenActions) return false
        val path = pathForThreadNode(id, treeNode.project.path)
        service.openChatSubAgent(path, treeNode.thread, treeNode.subAgent, project)
        true
      }

      is SessionTreeNode.Project -> {
        if (!includeOpenActions) return false
        service.openOrFocusProject(treeNode.project.path)
        true
      }

      is SessionTreeNode.Worktree -> {
        if (!includeOpenActions) return false
        service.openOrFocusProject(treeNode.worktree.path)
        true
      }

      is SessionTreeNode.Warning,
      is SessionTreeNode.Error,
      is SessionTreeNode.Empty -> false
    }
  }

  private fun selectedTreeId(): SessionTreeId? {
    return idFromPath(TreeUtil.getSelectedPathIfOne(tree))
  }

  private fun selectedTreeIds(): List<SessionTreeId> {
    val paths = tree.selectionPaths ?: return emptyList()
    return paths.mapNotNull { path -> idFromPath(path) }.distinct()
  }

  private fun idFromPath(path: TreePath?): SessionTreeId? {
    return path?.lastPathComponent?.let(::extractSessionTreeId)
  }

  private fun treeNodeFromPath(path: TreePath?): SessionTreeNode? {
    val id = idFromPath(path) ?: return null
    return sessionTreeNode(id)
  }

  private fun sessionTreeNode(id: SessionTreeId): SessionTreeNode? {
    return sessionTreeModel.entriesById[id]?.node
  }

  override fun dispose() {
    rebuildJob?.cancel()
    scope.cancel("Agent sessions toolwindow disposed")
  }
}

internal enum class RowActionKind {
  QuickCreate,
  ShowPopup,
}

internal fun resolveArchiveActionContext(
  popupActionContext: AgentSessionsTreePopupActionContext?,
  project: Project,
  selectedTreeId: SessionTreeId?,
  selectedTreeNode: SessionTreeNode?,
  selectedArchiveTargets: List<ArchiveThreadTarget>,
): AgentSessionsTreePopupActionContext? {
  if (popupActionContext != null) {
    return popupActionContext
  }

  if (selectedTreeId == null || selectedTreeNode == null) {
    return null
  }

  return AgentSessionsTreePopupActionContext(
    project = project,
    nodeId = selectedTreeId,
    node = selectedTreeNode,
    archiveTargets = selectedArchiveTargets,
  )
}

private data class RowActionRects(
  val loadingRect: Rectangle?,
  val quickRect: Rectangle?,
  val popupRect: Rectangle?,
)

private data class RowActionHit(
  val row: Int,
  val nodeId: SessionTreeId,
  val node: SessionTreeNode,
  val kind: RowActionKind,
  val actions: NewSessionRowActions,
  val rects: RowActionRects,
)

internal data class SessionTreeRowActionPresentation(
  val showLoadingAction: Boolean,
  val quickIcon: Icon?,
  val showQuickAction: Boolean,
  val showPopupAction: Boolean,
  val hoveredKind: RowActionKind?,
) {
  val actionSlots: Int
    get() =
      (if (showLoadingAction) 1 else 0) +
      (if (showQuickAction) 1 else 0) +
      (if (showPopupAction) 1 else 0)
}

private const val SESSION_TREE_ACTION_SLOT_SIZE = 18
private const val SESSION_TREE_ACTION_ICON_SIZE = 14
private const val SESSION_TREE_ACTION_GAP = 4
private const val SESSION_TREE_ACTION_RIGHT_GAP = 4
private const val SESSION_TREE_THREAD_PROVIDER_ICON_SIZE = 12
private const val SESSION_TREE_THREAD_META_LEFT_GAP = 8
private const val SESSION_TREE_THREAD_META_RIGHT_GAP = 2
private const val SESSION_TREE_THREAD_SELECTION_HORIZONTAL_INSET = 12
private val SESSION_TREE_TIME_LABEL_SAMPLES = listOf("59m", "23h", "7d", "4w", "11mo", "9y")
internal const val SESSION_TREE_MORE_ROW_FRAGMENT_TAG = "agent.sessions.tree.more.row"

internal data class SessionTreeThreadRowPresentation(
  val statusColor: Color,
  val title: @NlsSafe String,
  val timeLabel: @NlsSafe String?,
  val branchMismatchMessage: @NlsSafe String?,
  val accessibleStatusText: @NlsSafe String?,
)

internal data class SessionTreeThreadTrailingPaint(
  val reserveWidth: Int,
  val timeLabel: @NlsSafe String?,
  val timeX: Int?,
  val timeRightBoundary: Int,
  val timeTextWidth: Int,
)

internal fun sessionTreeRowActionRightPadding(actionSlots: Int): Int {
  if (actionSlots == 0) return 0
  val slot = JBUI.scale(SESSION_TREE_ACTION_SLOT_SIZE)
  val gap = JBUI.scale(SESSION_TREE_ACTION_GAP)
  val rightGap = JBUI.scale(SESSION_TREE_ACTION_RIGHT_GAP)
  return rightGap + (actionSlots * slot) + (if (actionSlots > 1) (actionSlots - 1) * gap else 0)
}

internal fun sessionTreeRowActionsRightBoundary(
  helperWidth: Int,
  helperRightMargin: Int,
  rightGap: Int,
  selectionRightInset: Int,
): Int {
  return helperWidth - helperRightMargin - selectionRightInset - rightGap
}

internal fun buildSessionTreeThreadRowPresentation(
  treeNode: SessionTreeNode.Thread,
  now: Long,
): SessionTreeThreadRowPresentation {
  val activityColor = JBColor(Color(treeNode.thread.activity.argb, true), Color(treeNode.thread.activity.argb, true))
  val timeLabel = treeNode.thread.updatedAt.takeIf { it > 0 }?.let { timestamp ->
    formatRelativeTimeShort(timestamp, now)
  }
  val originBranch = treeNode.thread.originBranch
  val parentBranch = treeNode.parentWorktreeBranch
  val branchMismatchMessage = if (originBranch != null && parentBranch != null && originBranch != parentBranch) {
    AgentSessionsBundle.message("toolwindow.thread.branch.mismatch", originBranch)
  }
  else {
    null
  }
  val providerName = providerDisplayName(treeNode.thread.provider)
  val accessibleStatusText = when {
    timeLabel != null -> "$providerName, $timeLabel"
    else -> providerName
  }
  return SessionTreeThreadRowPresentation(
    statusColor = activityColor,
    title = threadDisplayTitle(treeNode.thread),
    timeLabel = timeLabel,
    branchMismatchMessage = branchMismatchMessage,
    accessibleStatusText = accessibleStatusText,
  )
}

internal fun buildSessionTreeThreadTooltipHtml(
  treeNode: SessionTreeNode.Thread,
  now: Long,
): @NlsSafe String {
  val presentation = buildSessionTreeThreadRowPresentation(treeNode = treeNode, now = now)
  val title = StringUtil.escapeXmlEntities(presentation.title)
  val updatedText = presentation.timeLabel?.let { label ->
    StringUtil.escapeXmlEntities(AgentSessionsBundle.message("toolwindow.updated", label))
  }
  return if (updatedText == null) {
    "<html>$title</html>"
  }
  else {
    "<html>$title<br>$updatedText</html>"
  }
}

private object SessionTreeMiddleTextClipper : FragmentTextClipper {
  override fun clipText(
    component: com.intellij.ui.SimpleColoredComponent,
    g: Graphics2D,
    fragmentIndex: Int,
    text: String,
    availTextWidth: Int,
  ): String {
    if (availTextWidth <= 0) {
      return StringUtil.ELLIPSIS
    }

    val fontMetrics = component.getFontMetrics(g.font)
    if (fontMetrics.stringWidth(text) <= availTextWidth) {
      return text
    }

    val ellipsis = StringUtil.ELLIPSIS
    if (fontMetrics.stringWidth(ellipsis) > availTextWidth) {
      return ellipsis
    }

    var low = 1
    var high = text.length
    var best = ellipsis
    while (low <= high) {
      val mid = (low + high) ushr 1
      val candidate = StringUtil.trimMiddle(text, mid)
      if (fontMetrics.stringWidth(candidate) <= availTextWidth) {
        best = candidate
        low = mid + 1
      }
      else {
        high = mid - 1
      }
    }
    return best.ifBlank { ellipsis }
  }
}

internal fun computeSessionTreeThreadTrailingPaint(
  tree: JTree,
  actionRightPadding: Int,
  timeLabel: @NlsSafe String?,
  fontMetrics: FontMetrics,
  sharedTimeColumnWidth: Int,
): SessionTreeThreadTrailingPaint? {
  if (timeLabel == null) return null

  val helper = RenderingHelper(tree)
  val rightBoundary = (
    helper.width -
    helper.rightMargin -
    actionRightPadding -
    sessionTreeThreadSelectionRightInset(tree) -
    JBUI.scale(SESSION_TREE_THREAD_META_RIGHT_GAP)
  )
    .coerceAtLeast(0)

  val timeTextWidth = fontMetrics.stringWidth(timeLabel)
  val effectiveTimeColumnWidth = maxOf(sharedTimeColumnWidth, timeTextWidth)
  val timeX = (rightBoundary - timeTextWidth).coerceAtLeast(0)
  val leftEdge = (rightBoundary - effectiveTimeColumnWidth).coerceAtLeast(0)
  val reserveWidth = (rightBoundary - leftEdge + JBUI.scale(SESSION_TREE_THREAD_META_LEFT_GAP)).coerceAtLeast(0)

  return SessionTreeThreadTrailingPaint(
    reserveWidth = reserveWidth,
    timeLabel = timeLabel,
    timeX = timeX,
    timeRightBoundary = rightBoundary,
    timeTextWidth = timeTextWidth,
  )
}

internal fun resolveSessionTreeThreadTimePaintX(
  preferredX: Int,
  rendererWidth: Int,
  timeTextWidth: Int,
  selectionRightInset: Int = 0,
): Int {
  val maxVisibleX = (
    rendererWidth -
    selectionRightInset -
    JBUI.scale(SESSION_TREE_THREAD_META_RIGHT_GAP) -
    timeTextWidth
  ).coerceAtLeast(0)
  return preferredX.coerceAtMost(maxVisibleX)
}

private fun sessionTreeThreadSelectionRightInset(tree: JTree): Int {
  if (!ExperimentalUI.isNewUI()) return 0
  if (!Registry.`is`("ide.experimental.ui.tree.selection")) return 0
  if (tree is PlainSelectionTree) return 0
  return JBUI.scale(SESSION_TREE_THREAD_SELECTION_HORIZONTAL_INSET)
}

internal fun extractSessionTreeId(value: Any?): SessionTreeId? {
  val descriptor = TreeUtil.getUserObject(NodeDescriptor::class.java, value) ?: return null
  return descriptor.element as? SessionTreeId
}

private object SessionTreeRootElement

private class AgentSessionsTreeStructure(
  private val modelProvider: () -> SessionTreeModel,
) : AbstractTreeStructure() {
  override fun getRootElement(): Any = SessionTreeRootElement

  override fun getChildElements(element: Any): Array<Any> {
    val model = modelProvider()
    return when (element) {
      SessionTreeRootElement -> model.rootIds.toTypedArray()
      is SessionTreeId -> model.entriesById[element]?.childIds?.toTypedArray() ?: emptyArray()
      else -> emptyArray()
    }
  }

  override fun getParentElement(element: Any): Any? {
    if (element === SessionTreeRootElement) return null
    val id = element as? SessionTreeId ?: return null
    val entry = modelProvider().entriesById[id] ?: return null
    return entry.parentId ?: SessionTreeRootElement
  }

  override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> {
    return AgentSessionsTreeNodeDescriptor(parentDescriptor, element, modelProvider)
  }

  override fun commit() = Unit

  override fun hasSomethingToCommit(): Boolean = false

  override fun getLeafState(element: Any): LeafState {
    if (element === SessionTreeRootElement) return LeafState.NEVER
    val id = element as? SessionTreeId ?: return LeafState.DEFAULT
    if (id is SessionTreeId.Project) return LeafState.NEVER
    val entry = modelProvider().entriesById[id] ?: return LeafState.DEFAULT
    return if (entry.childIds.isEmpty()) LeafState.ALWAYS else LeafState.DEFAULT
  }

  override fun isValid(element: Any): Boolean {
    if (element === SessionTreeRootElement) return true
    return element is SessionTreeId && modelProvider().entriesById.containsKey(element)
  }
}

private class AgentSessionsTreeNodeDescriptor(
  parentDescriptor: NodeDescriptor<*>?,
  private val element: Any,
  private val modelProvider: () -> SessionTreeModel,
) : NodeDescriptor<Any?>(null, parentDescriptor) {
  private var presentationHash: Int = computePresentationHash()

  override fun update(): Boolean {
    val nextHash = computePresentationHash()
    if (presentationHash == nextHash) {
      return false
    }
    presentationHash = nextHash
    return true
  }

  override fun getElement(): Any = element

  private fun computePresentationHash(): Int {
    return when (element) {
      SessionTreeRootElement -> 0
      is SessionTreeId -> {
        val node = modelProvider().entriesById[element]?.node ?: return -1
        sessionTreeNodePresentation(node).hashCode()
      }
      else -> 0
    }
  }
}

internal class SessionTreeCellRenderer(
  private val nowProvider: () -> Long,
  private val rowActionsProvider: (row: Int, node: SessionTreeNode, selected: Boolean) -> SessionTreeRowActionPresentation?,
  private val nodeResolver: (SessionTreeId) -> SessionTreeNode?,
  private val providerIconProvider: (AgentSessionProvider) -> Icon? = ::providerIcon,
) : ColoredTreeCellRenderer() {
  private data class SharedTimeColumnWidthCacheKey(
    val fontHash: Int,
    val nowLabel: @NlsSafe String,
  )

  private data class ThreadCompositeIconCacheKey(
    val provider: AgentSessionProvider,
    val activity: AgentThreadActivity,
    val statusRgb: Int,
  )

  private var threadTrailingPaint: SessionTreeThreadTrailingPaint? = null
  private var sharedTimeColumnWidthCacheKey: SharedTimeColumnWidthCacheKey? = null
  private var sharedTimeColumnWidthCacheValue: Int = 0
  private var cachedProviderIconSize: Int = -1
  private val providerIconCache = LinkedHashMap<AgentSessionProvider, Icon>()
  private val threadCompositeIconCache = LinkedHashMap<ThreadCompositeIconCacheKey, Icon>()

  internal val trailingThreadPaintForTest: SessionTreeThreadTrailingPaint?
    get() = threadTrailingPaint

  override fun customizeCellRenderer(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) {
    threadTrailingPaint = null
    setAccessibleStatusText(null)

    val treeId = extractSessionTreeId(value) ?: return
    val treeNode = nodeResolver(treeId) ?: return
    val rowActions = rowActionsProvider(row, treeNode, selected)
    val actionSlots = rowActions?.actionSlots ?: 0
    val actionRightPadding = sessionTreeRowActionRightPadding(actionSlots)
    var metaRightPadding = 0

    when (treeNode) {
      is SessionTreeNode.Project -> {
        val projectIcon = ProductIcons.getInstance().getProjectNodeIcon()
        icon = projectIcon
        val titleAttributes = if (treeNode.project.isOpen || treeNode.project.worktrees.any { it.isOpen }) {
          SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        }
        else {
          SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
        val projectName: @NlsSafe String = treeNode.project.name
        append(projectName, titleAttributes)
        if (treeNode.project.worktrees.isNotEmpty()) {
          val branchLabel: @NlsSafe String = treeNode.project.branch ?: AgentSessionsBundle.message("toolwindow.worktree.detached")
          append(" [$branchLabel]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
        if (treeNode.project.isLoading) {
          setAccessibleStatusText(AgentSessionsBundle.message("toolwindow.loading"))
        }
      }

      is SessionTreeNode.Worktree -> {
        val worktreeIcon = AllIcons.Vcs.BranchNode
        icon = worktreeIcon
        val worktreeName: @NlsSafe String = treeNode.worktree.name
        append(worktreeName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        val branchLabel: @NlsSafe String = treeNode.worktree.branch ?: AgentSessionsBundle.message("toolwindow.worktree.detached")
        append(" [$branchLabel]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        if (treeNode.worktree.isLoading) {
          setAccessibleStatusText(AgentSessionsBundle.message("toolwindow.loading"))
        }
      }

      is SessionTreeNode.Thread -> {
        val baseFontMetrics = getFontMetrics(getBaseFont())
        val sharedTimeColumnWidth = computeSharedTimeColumnWidth(baseFontMetrics)
        val threadRowPresentation = buildSessionTreeThreadRowPresentation(
          treeNode = treeNode,
          now = nowProvider(),
        )
        icon = threadCompositeIcon(treeNode.thread.provider, treeNode.thread.activity, threadRowPresentation.statusColor)
        val threadTitle: @NlsSafe String = threadRowPresentation.title
        appendWithClipping(threadTitle, SimpleTextAttributes.REGULAR_ATTRIBUTES, SessionTreeMiddleTextClipper)
        threadTrailingPaint = computeSessionTreeThreadTrailingPaint(
          tree = tree,
          actionRightPadding = actionRightPadding,
          timeLabel = threadRowPresentation.timeLabel,
          fontMetrics = baseFontMetrics,
          sharedTimeColumnWidth = sharedTimeColumnWidth,
        )
        metaRightPadding = threadTrailingPaint?.reserveWidth ?: 0
        if (threadRowPresentation.accessibleStatusText != null) {
          setAccessibleStatusText(threadRowPresentation.accessibleStatusText)
        }

        if (threadRowPresentation.branchMismatchMessage != null) {
          append(
            "  ${threadRowPresentation.branchMismatchMessage}",
            SimpleTextAttributes.ERROR_ATTRIBUTES,
          )
        }
      }

      is SessionTreeNode.SubAgent -> {
        icon = AllIcons.Nodes.Plugin
        val subAgentLabel: @NlsSafe String = treeNode.subAgent.name.ifBlank { treeNode.subAgent.id }
        append(subAgentLabel, SimpleTextAttributes.GRAY_ATTRIBUTES)
      }

      is SessionTreeNode.Warning -> {
        icon = AllIcons.General.Warning
        append(treeNode.message, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor))
      }

      is SessionTreeNode.Error -> {
        icon = AllIcons.General.Error
        append(treeNode.message, SimpleTextAttributes.ERROR_ATTRIBUTES)
      }

      is SessionTreeNode.Empty -> {
        icon = AllIcons.General.Information
        append(treeNode.message, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }

      is SessionTreeNode.MoreProjects -> {
        icon = null
        append(
          AgentSessionsBundle.message("toolwindow.action.more.count", treeNode.hiddenCount),
          SimpleTextAttributes.GRAYED_ATTRIBUTES,
          SESSION_TREE_MORE_ROW_FRAGMENT_TAG,
        )
      }

      is SessionTreeNode.MoreThreads -> {
        icon = null
        val label = treeNode.hiddenCount?.let { AgentSessionsBundle.message("toolwindow.action.more.count", it) }
          ?: AgentSessionsBundle.message("toolwindow.action.more")
        append(label, SimpleTextAttributes.GRAYED_ATTRIBUTES, SESSION_TREE_MORE_ROW_FRAGMENT_TAG)
      }
    }

    val rightPadding = actionRightPadding + metaRightPadding
    ipad = if (rightPadding > 0) JBUI.insetsRight(rightPadding) else JBUI.emptyInsets()
  }

  override fun doPaint(g: Graphics2D) {
    super.doPaint(g)
    paintThreadTrailingMeta(g)
  }

  private fun paintThreadTrailingMeta(g: Graphics2D) {
    val trailing = threadTrailingPaint ?: return
    val area = computePaintArea()

    val font = getBaseFont()
    g.font = font
    val metrics = g.getFontMetrics(font)
    val baseline = area.y + getTextBaseLine(metrics, area.height)

    val label = trailing.timeLabel ?: return
    val preferredX = trailing.timeX ?: return
    val x = resolveSessionTreeThreadTimePaintX(
      preferredX = preferredX,
      rendererWidth = width,
      timeTextWidth = trailing.timeTextWidth,
      selectionRightInset = sessionTreeThreadSelectionRightInset(tree),
    )
    g.color = trailingTextColor()
    g.drawString(label, x, baseline)
  }

  private fun trailingTextColor(): Color {
    if (mySelected && isFocused() && JBUI.CurrentTheme.Tree.Selection.forceFocusedSelectionForeground()) {
      return UIUtil.getTreeSelectionForeground(true)
    }
    return getActiveTextColor(SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor)
  }

  private fun computeSharedTimeColumnWidth(fontMetrics: FontMetrics): Int {
    val nowLabel = AgentSessionsBundle.message("toolwindow.time.now")
    val key = SharedTimeColumnWidthCacheKey(
      fontHash = fontMetrics.font.hashCode(),
      nowLabel = nowLabel,
    )

    val cachedKey = sharedTimeColumnWidthCacheKey
    if (cachedKey == key) {
      return sharedTimeColumnWidthCacheValue
    }

    val width = (SESSION_TREE_TIME_LABEL_SAMPLES + nowLabel).maxOf(fontMetrics::stringWidth)

    sharedTimeColumnWidthCacheKey = key
    sharedTimeColumnWidthCacheValue = width
    return width
  }

  private fun scaledProviderIcon(provider: AgentSessionProvider): Icon {
    val iconSize = JBUI.scale(SESSION_TREE_THREAD_PROVIDER_ICON_SIZE)
    if (cachedProviderIconSize != iconSize) {
      cachedProviderIconSize = iconSize
      providerIconCache.clear()
      threadCompositeIconCache.clear()
    }
    return providerIconCache.getOrPut(provider) {
      val baseIcon = providerIconProvider(provider) ?: AllIcons.Toolwindows.ToolWindowMessages
      IconUtil.toSize(baseIcon, iconSize, iconSize)
    }
  }

  private fun threadCompositeIcon(provider: AgentSessionProvider, activity: AgentThreadActivity, statusColor: Color): Icon {
    val key = ThreadCompositeIconCacheKey(provider = provider, activity = activity, statusRgb = statusColor.rgb)
    return threadCompositeIconCache.getOrPut(key) {
      val baseIcon = scaledProviderIcon(provider)
      if (activity == AgentThreadActivity.READY) baseIcon else IconManager.getInstance().withIconBadge(baseIcon, statusColor)
    }
  }
}

private class ClaudeQuotaHintPanel(
  onEnable: () -> Unit,
  onDismiss: () -> Unit,
) : JPanel(BorderLayout()) {
  init {
    border = JBUI.Borders.compound(
      JBUI.Borders.customLine(JBColor.border(), 1),
      JBUI.Borders.empty(8),
    )

    val textPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(JLabel(AgentSessionsBundle.message("toolwindow.claude.quota.hint.title")).apply {
        font = font.deriveFont(font.style or Font.BOLD)
      })
      add(Box.createVerticalStrut(JBUI.scale(4)))
      add(JLabel(AgentSessionsBundle.message("toolwindow.claude.quota.hint.body")))
    }

    val actionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      isOpaque = false
      add(JButton(AgentSessionsBundle.message("toolwindow.claude.quota.hint.enable")).apply {
        addActionListener { onEnable() }
      })
      add(JButton(AgentSessionsBundle.message("toolwindow.claude.quota.hint.dismiss")).apply {
        addActionListener { onDismiss() }
      })
    }

    add(textPanel, BorderLayout.CENTER)
    add(actionsPanel, BorderLayout.SOUTH)
  }
}

internal fun shouldAcknowledgeClaudeQuotaHint(eligible: Boolean, acknowledged: Boolean, widgetEnabled: Boolean): Boolean {
  return eligible && !acknowledged && widgetEnabled
}

internal fun shouldShowClaudeQuotaHint(eligible: Boolean, acknowledged: Boolean, widgetEnabled: Boolean): Boolean {
  return eligible && !acknowledged && !widgetEnabled
}
