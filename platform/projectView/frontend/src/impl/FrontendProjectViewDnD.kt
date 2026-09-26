// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.ide.dnd.DnDAction
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSource
import com.intellij.ide.projectView.impl.ProjectViewDragImageUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Pair
import com.intellij.platform.projectView.pane.ProjectViewNodeModel
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Image
import java.awt.Point
import javax.swing.tree.TreePath

internal fun enableDnD(tree: FrontendProjectViewTree, model: FrontendProjectViewPaneTreeModel) {
  if (ApplicationManager.getApplication().isHeadlessEnvironment) return
  tree.dragEnabled = true
  val dndManager = DnDManager.getInstance()
  dndManager.registerSource(FrontendProjectViewDragSource(tree), tree)
  dndManager.registerTarget(FrontendProjectViewDropTarget(tree, model), tree)
}

internal class FrontendProjectViewDragSource(private val tree: FrontendProjectViewTree) : DnDSource {
  override fun canStartDragging(action: DnDAction, dragOrigin: Point): Boolean {
    return tree.selectionCount > 0
  }

  override fun startDragging(action: DnDAction, dragOrigin: Point): DnDDragStartBean? {
    val ids = tree.selectionPaths?.mapNotNull { it.id }
    if (ids.isNullOrEmpty()) return null
    return DnDDragStartBean(DraggedNodes(ids))
  }

  override fun createDraggedImage(
    action: DnDAction,
    dragOrigin: Point,
    bean: DnDDragStartBean,
  ): Pair<Image, Point>? {
    return ProjectViewDragImageUtil.createDraggedImage(tree)
  }
}

private data class DraggedNodes(
  val ids: List<Long>,
)

internal class FrontendProjectViewDropTarget(
  private val tree: FrontendProjectViewTree,
  private val model: FrontendProjectViewPaneTreeModel,
) : DnDNativeTarget {
  override fun update(event: DnDEvent): Boolean {
    event.setDropPossible(false, "")

    val point = event.point ?: return false
    val targetPath = tree.getClosestPathForLocation(point.x, point.y) ?: return false
    val targetBounds = tree.getPathBounds(targetPath) ?: return false
    if (point.y < targetBounds.y || point.y >= targetBounds.y + targetBounds.height) return false

    val source = event.attachedObject as? DraggedNodes ?: return false
    if (targetPath.id == source.ids.singleOrNull()) return false // no DnD to itself
    
    event.setHighlighting(RelativeRectangle(tree, targetBounds), DnDEvent.DropTargetHighlightingType.RECTANGLE)
    event.setDropPossible(true, "")
    return false
  }

  override fun drop(event: DnDEvent) {
    val source = event.attachedObject as? DraggedNodes ?: return
    val point = event.point ?: return
    val targetID = tree.getClosestPathForLocation(point.x, point.y)?.id ?: return
    model.requestDnD(source.ids, targetID, event.action)
  }
}

private val TreePath.id: Long?
  get() = (TreeUtil.getLastUserObject(this) as? ProjectViewNodeModel)?.id
