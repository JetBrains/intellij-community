// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")

package com.intellij.platform.projectView.frontend.impl

import com.intellij.ide.dnd.DnDAction
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDEventImpl
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSource
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.ide.projectView.impl.FrontendProjectViewFileTransfer
import com.intellij.ide.projectView.impl.ProjectViewDragImageUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.projectView.pane.ProjectViewExternalFileDTO
import com.intellij.platform.projectView.pane.ProjectViewNodeModelImpl
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Image
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.dnd.DragSourceDropEvent
import java.io.File
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

internal fun enableDnD(tree: FrontendProjectViewTree, model: FrontendProjectViewPaneTreeModel) {
  if (ApplicationManager.getApplication().isHeadlessEnvironment) return
  tree.dragEnabled = true
  val dndManager = DnDManager.getInstance()
  dndManager.registerSource(FrontendProjectViewDragSource(tree, model), tree)
  dndManager.registerTarget(FrontendProjectViewDropTarget(tree, model), tree)
}

internal class FrontendProjectViewDragSource(
  private val tree: FrontendProjectViewTree,
  private val model: FrontendProjectViewPaneTreeModel,
) : DnDSource {
  override fun canStartDragging(action: DnDAction, dragOrigin: Point): Boolean {
    return tree.selectionCount > 0
  }

  override fun startDragging(action: DnDAction, dragOrigin: Point): DnDDragStartBean? {
    val nodes = tree.selectionPaths?.mapNotNull { it.lastPathComponent as? DefaultMutableTreeNode }
    if (nodes.isNullOrEmpty()) return null
    val draggedNodes = if (FrontendProjectViewFileTransfer.getInstanceOrNull() == null) DraggedNodes(nodes) else RemoteDraggedNodes(nodes)
    return DnDDragStartBean(draggedNodes)
  }

  override fun createDraggedImage(
    action: DnDAction,
    dragOrigin: Point,
    bean: DnDDragStartBean,
  ): Pair<Image, Point>? {
    return ProjectViewDragImageUtil.createDraggedImage(tree)
  }

  override fun dragDropEnd(dragEvent: DnDEvent?, dropEvent: DragSourceDropEvent?) {
    if (FrontendProjectViewFileTransfer.getInstanceOrNull() == null) return
    if (dragEvent == null || dropEvent == null) {
      LOG.debug { "Drag end: no events, dragEvent=$dragEvent, dropEvent=$dropEvent" }
      return
    }
    if (dropEvent.dropSuccess) {
      LOG.debug { "Drag end: a target inside the IDE accepted the drop" }
      return
    }
    val source = dragEvent.attachedObject as? DraggedNodes
    if (source == null) {
      LOG.debug { "Drag end: the dragged object is not the PV nodes: ${dragEvent.attachedObject}" }
      return
    }
    val dropLocation = dropEvent.location
    if (dropLocation == null) {
      LOG.debug { "Drag end: no drop location" }
      return
    }
    val rootPane = SwingUtilities.getRootPane(tree)
    if (rootPane == null) {
      LOG.debug { "Drag end: the tree has no root pane" }
      return
    }
    SwingUtilities.convertPointFromScreen(dropLocation, rootPane)
    if (rootPane.contains(dropLocation)) {
      LOG.debug { "Drag end: the drop location $dropLocation is inside the window of the tree" }
      return
    }
    val nodeIds = source.nodes.mapNotNull { it.pvNode?.id }
    LOG.debug { "Drag end: sending the nodes $nodeIds to the client" }
    model.requestSendDropToClient(nodeIds)
  }
}

private open class DraggedNodes(
  val nodes: List<DefaultMutableTreeNode>,
) : TransferableWrapper {
  override fun getTreeNodes(): Array<out TreeNode> = nodes.toTypedArray()

  override fun getPsiElements(): Array<out PsiElement?> = emptyArray()

  override fun asFileList(): List<File>? {
    if (!IdeProductMode.isMonolith) return null // the ancient File API only makes sense locally
    // a piece of legacy logic copied from com.intellij.ide.PsiCopyPasteManager.asFileList
    return virtualFiles.mapNotNull { 
      if (it.isInLocalFileSystem) {
        File(it.path)
      }
      else {
        null
      }
    }
  }

  override fun getVirtualFiles(): Array<out VirtualFile> {
    return nodes.mapNotNull {
      it.pvNode?.userObject?.getVirtualFile()
    }.toTypedArray()
  }
}

private class RemoteDraggedNodes(nodes: List<DefaultMutableTreeNode>) : DraggedNodes(nodes), Transferable {
  override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DnDEventImpl.ourDataFlavor)

  override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DnDEventImpl.ourDataFlavor

  override fun getTransferData(flavor: DataFlavor): Any {
    if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
    return this
  }
}

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

    val source = event.attachedObject as? DraggedNodes
    if (source != null) {
      if (targetPath.pvNode?.id == source.nodes.singleOrNull()?.pvNode?.id) return false // no DnD to itself
    }
    else if (!isExternalFileDropPossible(event)) {
      return false
    }

    event.setHighlighting(RelativeRectangle(tree, targetBounds), DnDEvent.DropTargetHighlightingType.RECTANGLE)
    event.setDropPossible(true, "")
    return false
  }

  override fun drop(event: DnDEvent) {
    val point = event.point ?: return
    val targetID = tree.getClosestPathForLocation(point.x, point.y)?.pvNode?.id ?: return
    val source = event.attachedObject as? DraggedNodes
    if (source != null) {
      model.requestDnD(source.nodes.mapNotNull { it.pvNode?.id }, targetID, event.action)
    }
    else {
      val files = getExternalFiles(event)
      if (files.isEmpty()) {
        LOG.debug { "External drop: no files to send, attached=${event.attachedObject}" }
        return
      }
      LOG.debug { "External drop: ${event.action} of $files onto the node $targetID" }
      model.requestExternalDrop(files, targetID, event.action)
    }
  }

  private fun isExternalFileDropPossible(event: DnDEvent): Boolean {
    if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
      LOG.debug { "External drop: no file list flavor, attached=${event.attachedObject}" }
      return false
    }
    val transfer = FrontendProjectViewFileTransfer.getInstanceOrNull() ?: return true
    if (!transfer.isTransferAllowed()) {
      LOG.debug { "External drop: the file transfer is not allowed" }
      return false
    }
    return true
  }

  private fun getExternalFiles(event: DnDEvent): List<ProjectViewExternalFileDTO> {
    if (!isExternalFileDropPossible(event)) return emptyList()
    val files = FileCopyPasteUtil.getFileListFromAttachedObject(event.attachedObject)
    val transfer = FrontendProjectViewFileTransfer.getInstanceOrNull()
                   ?: return files.map { ProjectViewExternalFileDTO.LocalPath(it.path) }
    return files.map {
      val file = transfer.allowTransfer(it.toPath())
      ProjectViewExternalFileDTO.Transferable(file.name, file.transferId)
    }
  }
}

private val LOG = logger<FrontendProjectViewDropTarget>()

private val TreePath.pvNode: ProjectViewNodeModelImpl<*>?
  get() = (lastPathComponent as? DefaultMutableTreeNode)?.pvNode

private val DefaultMutableTreeNode.pvNode: ProjectViewNodeModelImpl<*>?
  get() = TreeUtil.getUserObject(this) as? ProjectViewNodeModelImpl<*>
