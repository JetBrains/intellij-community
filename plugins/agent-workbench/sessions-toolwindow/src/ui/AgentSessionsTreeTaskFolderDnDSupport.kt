// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.sessions.toolwindow.actions.TaskFolderThreadMove
import com.intellij.agent.workbench.sessions.toolwindow.actions.assignThreadsToTaskFolder
import com.intellij.agent.workbench.sessions.toolwindow.actions.canMoveThreadsToTaskFolder
import com.intellij.agent.workbench.sessions.toolwindow.actions.resolveSessionActionTarget
import com.intellij.agent.workbench.sessions.toolwindow.actions.resolveTaskFolderThreadMove
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.ide.dnd.DnDActionInfo
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.DnDTargetChecker
import com.intellij.openapi.Disposable
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolder
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.treeStructure.Tree
import java.awt.Rectangle
import javax.swing.tree.TreePath

internal class AgentSessionsTreeTaskFolderDnDSupport(
  private val tree: Tree,
  private val nodeResolver: (SessionTreeId) -> SessionTreeNode?,
  private val selectedThreadTargets: () -> List<SessionActionTarget.Thread>,
  private val assignThread: (SessionActionTarget.Thread, AgentTaskFolder) -> Unit,
) : DnDTargetChecker, DnDDropHandler.WithResult {
  fun install(parentDisposable: Disposable) {
    DnDSupport.createBuilder(tree)
      .setDisposableParent(parentDisposable)
      .setBeanProvider(::createDragStartBean)
      .setTargetChecker(this)
      .setDropHandlerWithResult(this)
      .install()
  }

  private fun createDragStartBean(info: DnDActionInfo): DnDDragStartBean? {
    if (!info.isMove) return null
    val originPath = tree.getPathForLocation(info.point.x, info.point.y) ?: return null
    val primaryTarget = threadTarget(originPath) ?: return null
    val selectionTargets = if (tree.selectionModel.isPathSelected(originPath)) selectedThreadTargets() else emptyList()
    val move = resolveTaskFolderThreadMove(primaryTarget, selectionTargets) ?: return null
    return DnDDragStartBean(DraggedTaskFolderThreads(move))
  }

  override fun update(event: DnDEvent): Boolean {
    val drop = resolveDrop(event)
    if (drop == null) {
      event.hideHighlighter()
      event.setDropPossible(false)
      return true
    }

    event.setDropPossible(true)
    event.setHighlighting(RelativeRectangle(tree, drop.bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE)
    return false
  }

  override fun tryDrop(event: DnDEvent): Boolean {
    val drop = resolveDrop(event) ?: return false
    assignThreadsToTaskFolder(drop.drag.move, drop.folder, assignThread)
    event.hideHighlighter()
    return true
  }

  private fun resolveDrop(event: DnDEvent): TaskFolderDrop? {
    val drag = event.attachedObject as? DraggedTaskFolderThreads ?: return null
    val point = event.point ?: return null
    val path = tree.getPathForLocation(point.x, point.y) ?: return null
    val id = idFromPath(path) ?: return null
    val folderNode = nodeResolver(id) as? SessionTreeNode.TaskFolder ?: return null
    if (!canMoveThreadsToTaskFolder(drag.move, folderNode.folder)) return null
    val bounds = tree.getPathBounds(path) ?: return null
    return TaskFolderDrop(drag = drag, folder = folderNode.folder, bounds = bounds)
  }

  private fun threadTarget(path: TreePath): SessionActionTarget.Thread? {
    val id = idFromPath(path) ?: return null
    val threadNode = nodeResolver(id) as? SessionTreeNode.Thread ?: return null
    if (threadNode.thread.archived) return null
    return resolveSessionActionTarget(id, threadNode) as? SessionActionTarget.Thread
  }

  private fun idFromPath(path: TreePath): SessionTreeId? {
    return path.lastPathComponent?.let(::extractSessionTreeId)
  }

  private data class DraggedTaskFolderThreads(
    @JvmField val move: TaskFolderThreadMove,
  )

  private data class TaskFolderDrop(
    @JvmField val drag: DraggedTaskFolderThreads,
    @JvmField val folder: AgentTaskFolder,
    @JvmField val bounds: Rectangle,
  )
}
