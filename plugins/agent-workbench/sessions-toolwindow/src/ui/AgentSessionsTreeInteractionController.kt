// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.agent.workbench.sessions.state.AgentSessionTreeUiStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.pathForMoreThreadsNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.pathForThreadNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.shouldHandleSingleClick
import com.intellij.agent.workbench.sessions.toolwindow.tree.shouldRetargetSelectionForContextMenu
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreePath

internal class AgentSessionsTreeInteractionController(
    private val project: Project,
    private val tree: Tree,
    private val launchService: AgentSessionLaunchService,
    private val syncService: AgentSessionRefreshService,
    private val stateStore: AgentSessionsStateStore,
    private val treeUiStateService: AgentSessionTreeUiStateService,
    private val rowActionsOverlayProvider: () -> AgentSessionsTreeRowActionsOverlay,
    private val nodeResolver: (SessionTreeId) -> SessionTreeNode?,
    private val selectedArchiveTargets: () -> List<ArchiveThreadTarget>,
) {
  var popupActionContext: AgentSessionsTreePopupActionContext? = null
    private set

  fun install() {
    TreeUtil.installActions(tree)
    TreeUIHelper.getInstance().installTreeSpeedSearch(tree)
    TreeHoverListener.DEFAULT.addTo(tree)
    EditSourceOnDoubleClickHandler.install(tree, Runnable { activateSelectedNode() })
    installEnterKeyActivation()
    installMouseListeners()
    installTreeExpansionListener()
  }

  fun showNewSessionActionPopup(
    nodeId: SessionTreeId,
    node: SessionTreeNode,
    anchorRect: Rectangle,
    row: Int,
  ) {
    val actionGroup = ActionManager.getInstance().getAction(AgentWorkbenchActionIds.Sessions.TreePopup.NEW_THREAD) as? ActionGroup
                      ?: return
    popupActionContext = AgentSessionsTreePopupActionContext(
      project = project,
      nodeId = nodeId,
      node = node,
      archiveTargets = selectedArchiveTargets(),
    )
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, actionGroup)
    popupMenu.setTargetComponent(tree)
    rowActionsOverlayProvider().pinPopupRow(row)
    popupMenu.component.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
      override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) = Unit

      override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {
        rowActionsOverlayProvider().clearPopupPinnedRow(row)
        clearPopupActionContext()
      }

      override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {
        rowActionsOverlayProvider().clearPopupPinnedRow(row)
        clearPopupActionContext()
      }
    })
    popupMenu.component.show(tree, anchorRect.x, anchorRect.y + anchorRect.height)
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

  private fun installMouseListeners() {
    val mouseHandler = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (!SwingUtilities.isLeftMouseButton(e) || e.clickCount != 1) return
        if (rowActionsOverlayProvider().handleClick(e.point)) {
          e.consume()
          return
        }
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        val id = idFromPath(path) ?: return
        val treeNode = nodeResolver(id) ?: return
        if (!shouldHandleSingleClick(treeNode)) return
        if (runNodeAction(id = id, treeNode = treeNode, includeOpenActions = false)) {
          e.consume()
        }
      }

      override fun mouseMoved(e: MouseEvent) {
        rowActionsOverlayProvider().updateHover(e.point)
      }

      override fun mouseExited(e: MouseEvent) {
        rowActionsOverlayProvider().clearHover()
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
  }

  private fun installTreeExpansionListener() {
    tree.addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        when (val id = idFromPath(event.path) ?: return) {
          is SessionTreeId.Project -> {
            treeUiStateService.setProjectCollapsed(id.path, collapsed = false)
            val projectNode = nodeResolver(id) as? SessionTreeNode.Project ?: return
            if (!projectNode.project.hasLoaded && !projectNode.project.isLoading) {
              syncService.loadProjectThreadsOnDemand(id.path)
            }
          }

          is SessionTreeId.Worktree -> {
            val worktreeNode = nodeResolver(id) as? SessionTreeNode.Worktree ?: return
            if (!worktreeNode.worktree.hasLoaded && !worktreeNode.worktree.isLoading) {
              syncService.loadWorktreeThreadsOnDemand(id.projectPath, id.worktreePath)
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

  private fun maybeShowPopup(event: MouseEvent) {
    if (!event.isPopupTrigger) return
    val path = tree.getPathForLocation(event.x, event.y) ?: return
    if (shouldRetargetSelectionForContextMenu(tree.selectionModel.isPathSelected(path))) {
      tree.selectionPath = path
    }
    val id = idFromPath(path) ?: return
    val treeNode = nodeResolver(id) ?: return
    val actionGroup = ActionManager.getInstance().getAction(AgentWorkbenchActionIds.Sessions.TreePopup.GROUP) as? ActionGroup
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

  private fun activateSelectedNode(): Boolean {
    val id = selectedTreeId() ?: return false
    val treeNode = nodeResolver(id) ?: return false
    return runNodeAction(id = id, treeNode = treeNode, includeOpenActions = true)
  }

  private fun runNodeAction(id: SessionTreeId, treeNode: SessionTreeNode, includeOpenActions: Boolean): Boolean {
    return when (treeNode) {
      is SessionTreeNode.MoreProjects -> {
        stateStore.showMoreProjects()
        true
      }

      is SessionTreeNode.MoreThreads -> {
        val path = pathForMoreThreadsNode(id) ?: return false
        stateStore.showMoreThreads(path)
        true
      }

      is SessionTreeNode.Thread -> {
        if (!includeOpenActions) return false
        if (isAgentSessionNewSessionId(treeNode.thread.id)) return false
        val path = pathForThreadNode(id, treeNode.project.path)
        launchService.openChatThread(path, treeNode.thread, project)
        true
      }

      is SessionTreeNode.SubAgent -> {
        if (!includeOpenActions) return false
        val path = pathForThreadNode(id, treeNode.project.path)
        launchService.openChatSubAgent(path, treeNode.thread, treeNode.subAgent, project)
        true
      }

      is SessionTreeNode.Project -> {
        if (!includeOpenActions) return false
        launchService.openOrFocusProject(treeNode.project.path)
        true
      }

      is SessionTreeNode.Worktree -> {
        if (!includeOpenActions) return false
        launchService.openOrFocusProject(treeNode.worktree.path)
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

  private fun idFromPath(path: TreePath?): SessionTreeId? {
    return path?.lastPathComponent?.let(::extractSessionTreeId)
  }
}
