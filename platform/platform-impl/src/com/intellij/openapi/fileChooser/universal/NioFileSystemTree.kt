// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.universal.NioFileChooserUtil.toNioPathSafe
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader.getTransparentIcon
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * An independent file system tree implementation backed by Java NIO [Path] instead of [VirtualFile].
 * Provides the same public interface as [com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl]
 * to be usable from [UniversalFileChooser].
 *
 * Does not use VFS internally. Where the [FileSystemTree] interface requires [VirtualFile],
 * conversions are performed at the boundary.
 */
@ApiStatus.Internal
class NioFileSystemTree(
  @Suppress("unused") private val project: Project?,
  private val descriptor: FileChooserDescriptor,
  private val myTree: Tree,
  contributor: UniversalFileChooserContributor,
  private val scope: CoroutineScope,
) : Disposable {

  private val okActions: MutableList<Runnable> = ArrayList(2)
  private val fileTreeModel: NioFileTreeModel = NioFileTreeModel(descriptor)
  private val asyncTreeModel: AsyncTreeModel = AsyncTreeModel(fileTreeModel, this)
  private val listeners: MutableList<Listener> = ContainerUtil.createLockFreeCopyOnWriteList()
  private val subscriptionJobs: ConcurrentHashMap<Path, Job> = ConcurrentHashMap()
  private val fileWatcherAdapter = contributor.getFileWatcherAdapter()

  init {
    myTree.model = asyncTreeModel
    myTree.selectionModel.addTreeSelectionListener { processSelectionChange() }
    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree)
    TreeUtil.installActions(myTree)
    myTree.selectionModel.selectionMode =
      if (descriptor.isChooseMultiple) TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
      else TreeSelectionModel.SINGLE_TREE_SELECTION
    registerTreeActions()
    myTree.cellRenderer = FileRenderer()

    myTree.addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        val nioPath = getNioPath(event.path) ?: return
        subscribeToChanges(nioPath)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) {
      }
    })
  }

  private fun registerTreeActions() {
    myTree.registerKeyboardAction(
      {
        performEnterAction(true)
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED
    )

    object : DoubleClickListener() {
      override fun onDoubleClick(e: MouseEvent): Boolean {
        performEnterAction(false)
        return true
      }
    }.installOn(myTree)
  }

  private fun performEnterAction(toggleNodeState: Boolean) {
    val path = myTree.selectionPath ?: return
    if (isLeaf(path)) {
      fireOkAction()
    }
    else if (toggleNodeState) {
      if (myTree.isExpanded(path)) {
        myTree.collapsePath(path)
      }
      else {
        myTree.expandPath(path)
      }
    }
  }

  fun addOkAction(action: Runnable) {
    okActions.add(action)
  }

  private fun fireOkAction() {
    for (action in okActions) {
      action.run()
    }
  }

  fun registerMouseListener(group: ActionGroup) {
    PopupHandler.installPopupMenu(myTree, group, "FileSystemTreePopup")
  }

  fun areHiddensShown(): Boolean = descriptor.isShowHiddenFiles

  fun showHiddens(showHidden: Boolean) {
    descriptor.withShowHiddenFiles(showHidden)
    val selectedFiles = getSelectedFiles().filterNotNull().toTypedArray()
    updateTree()
    if (selectedFiles.isNotEmpty()) {
      select(selectedFiles, null)
    }
  }

  fun updateTree() {
    fileTreeModel.invalidate()
  }

  fun setRoots(roots: List<UniversalFileChooserContributor.Root>) {
    fileTreeModel.setContributorRoots(roots)
  }

  fun setRootError(path: Path, message: String? = null) {
    fileTreeModel.setRootError(path)
    if (!message.isNullOrBlank()) {
      showErrorBalloon(path, message)
    }
  }

  private fun showErrorBalloon(path: Path, @Nls message: String) {
    if (!myTree.isShowing) return
    var anchorRow = 0
    for (i in 0 until myTree.rowCount) {
      val p = myTree.getPathForRow(i) ?: continue
      val last = p.lastPathComponent
      if (last is NioFileNode && last.path == path) {
        anchorRow = i
        break
      }
    }
    ApplicationManager.getApplication().invokeLater(
      {
        val bounds = myTree.getRowBounds(anchorRow) ?: return@invokeLater
        val point = RelativePoint(myTree,
                                  java.awt.Point(bounds.x + bounds.width / 2,
                                                 bounds.y + bounds.height))
        JBPopupFactory.getInstance()
          .createHtmlTextBalloonBuilder(StringUtil.escapeXmlEntities(message),
                                        MessageType.ERROR,
                                        null)
          .setFadeoutTime(5000)
          .setHideOnClickOutside(true)
          .setHideOnKeyOutside(true)
          .createBalloon()
          .show(point, Balloon.Position.below)
      }, ModalityState.any())
  }

  fun matchRoot(path: Path): Path? {
    return fileTreeModel.matchRoot(path)
  }

  fun getSelectedVirtualRoot(): UniversalFileChooserContributor.Root? {
    val component = myTree.selectionPath?.lastPathComponent as? NioFileNode ?: return null
    return fileTreeModel.getVirtualRoot(component)
  }

  fun getVirtualRoot(treePath: TreePath): UniversalFileChooserContributor.Root? {
    val component = treePath.lastPathComponent as? NioFileNode ?: return null
    return fileTreeModel.getVirtualRoot(component)
  }

  private fun subscribeToChanges(path: Path) {
    if (fileWatcherAdapter == null) return
    if (subscriptionJobs.containsKey(path)) return
    val job = scope.launch {
      try {
        val flow = fileWatcherAdapter.subscribe(path) ?: return@launch
        flow.collect {
          fileTreeModel.invalidate()
        }
      }
      catch (e: Exception) {
        LOG.debug("Error subscribing to changes for $path", e)
      }
    }
    subscriptionJobs[path] = job
  }

  private fun unsubscribeAll() {
    if (fileWatcherAdapter == null) return
    for ((path, job) in subscriptionJobs) {
      job.cancel()
      scope.launch {
        try {
          fileWatcherAdapter.unsubscribe(path)
        }
        catch (e: Exception) {
          LOG.debug("Error unsubscribing from changes for $path", e)
        }
      }
    }
    subscriptionJobs.clear()
  }

  override fun dispose() {
    unsubscribeAll()
    if (fileWatcherAdapter != null) {
      try {
        FileWatcherAppScopeHolder.getInstance().scope.launch {
          fileWatcherAdapter.stop()
        }
      }
      catch (e: Exception) {
        LOG.debug("Error stopping file watcher adapter", e)
      }
    }
  }

  fun select(file: Path?, onDone: Runnable?) {
    if (file == null) {
      myTree.clearSelection()
      onDone?.run()
      return
    }
    select(arrayOf(file), onDone)
  }

  fun select(files: Array<out Path>, onDone: Runnable?) {
    when (files.size) {
      0 -> {
        myTree.clearSelection()
        onDone?.run()
      }
      1 -> {
        myTree.clearSelection()
        val path = files[0]
        TreeUtil.promiseSelect(myTree, NioFileNode.Visitor(path)).onProcessed { onDone?.run() }
      }
      else -> {
        myTree.clearSelection()
        val visitors = files.map { it }.map { NioFileNode.Visitor(it) }
        if (visitors.isNotEmpty()) {
          @Suppress("SSBasedInspection")
          TreeUtil.promiseSelect(myTree, visitors.stream()).onProcessed { onDone?.run() }
        }
        else {
          onDone?.run()
        }
      }
    }
  }

  fun expand(path: Path, onDone: Runnable?) {
    TreeUtil.promiseExpand(myTree, NioFileNode.Visitor(path)).onSuccess { treePath ->
      if (treePath != null) onDone?.run()
    }
  }

  fun createNewFolder(parentPath: Path, newFolderName: String): Exception? {
    return try {
      for (name in StringUtil.tokenize(newFolderName, "\\/")) {
        val folderPath = parentPath.resolve(name)
        Files.createDirectories(folderPath)
        updateTree()
        select(folderPath, null)
      }
      null
    }
    catch (e: IOException) {
      e
    }
  }

  fun getTree(): JTree = myTree

  fun getSelectedFile(): Path? {
    val path = myTree.selectionPath ?: return null
    return getNioPath(path)
  }

  fun getNewFileParent(): Path? {
    val selected = getSelectedFile()
    if (selected != null) return selected
    val descriptorRoot = descriptor.roots.takeIf({it.size == 1})?.first()
    return descriptorRoot?.toNioPath()
  }

  fun <T> getData(key: DataKey<T>): T? = descriptor.getUserData(key)

  fun getSelectedFiles(): List<Path?> {
    val paths = myTree.selectionPaths ?: return emptyList()
    val files = mutableListOf<Path?>()
    for (path in paths) {
      files.add(getNioPath(path))
    }
    return files
  }

  private fun isLeaf(path: TreePath): Boolean {
    val component = path.lastPathComponent
    return asyncTreeModel.isLeaf(component)
  }

  fun selectionExists(): Boolean {
    val selectedPaths = myTree.selectionPaths
    return selectedPaths != null && selectedPaths.isNotEmpty()
  }

  fun isUnderRoots(file: VirtualFile): Boolean {
    val roots = descriptor.roots
    if (roots.isEmpty()) return true
    val filePath = toNioPathSafe(file) ?: return false
    for (root in roots) {
      val rootPath = toNioPathSafe(root) ?: continue
      if (filePath.startsWith(rootPath)) return true
    }
    return false
  }

  fun addListener(listener: Listener, parent: Disposable) {
    listeners.add(listener)
    Disposer.register(parent) { listeners.remove(listener) }
  }

  private fun fireSelection(selection: List<Path>) {
    for (each in listeners) {
      each.selectionChanged(selection)
    }
  }

  private fun processSelectionChange() {
    if (listeners.isEmpty()) return
    val selection = mutableListOf<Path>()
    val paths = myTree.selectionPaths
    if (paths != null) {
      for (each in paths) {
        val file = getNioPath(each)
        if (file != null) {
          selection.add(file)
        }
      }
    }
    fireSelection(selection)
  }

  fun computeSelectionAfterDeletion(): Path? {
    val selectionPath = myTree.selectionPath ?: return null
    val parentPath = selectionPath.parentPath ?: return null
    val selectionRow = myTree.getRowForPath(selectionPath)
    if (selectionRow < 0) return getNioPath(parentPath)

    // Look for next sibling among the rows following the selected one.
    for (row in (selectionRow + 1) until myTree.rowCount) {
      val rowPath = myTree.getPathForRow(row) ?: continue
      if (rowPath.parentPath == parentPath) {
        return getNioPath(rowPath)
      }
      // Once we leave the parent's subTree, there are no more siblings ahead.
      if (!parentPath.isDescendant(rowPath)) break
    }

    // Look for previous sibling among the rows preceding the selected one.
    for (row in (selectionRow - 1) downTo 0) {
      val rowPath = myTree.getPathForRow(row) ?: continue
      if (rowPath.parentPath == parentPath) {
        return getNioPath(rowPath)
      }
      // Once we leave the parent's subTree backwards, no more previous siblings.
      if (!parentPath.isDescendant(rowPath)) break
    }

    // Fall back to the containing directory.
    return getNioPath(parentPath)
  }

  companion object {
    private val LOG = Logger.getInstance(NioFileSystemTree::class.java)

    @JvmStatic
    fun getVirtualFile(path: TreePath): VirtualFile? {
      val component = path.lastPathComponent
      if (component is NioFileNode && component.path != null) {
        return LocalFileSystem.getInstance().findFileByNioFile(component.path)
      }
      return null
    }

    @JvmStatic
    fun getNioPath(path: TreePath): Path? {
      val component = path.lastPathComponent
      if (component is NioFileNode) {
        return component.path
      }
      return null
    }
  }

  interface Listener {
    fun selectionChanged(selection: List<Path?>)
  }

  private class FileRenderer: ColoredTreeCellRenderer() {
    companion object {
      val GRAYED: Color? = SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor()
      val HIDDEN: Color? = SimpleTextAttributes.DARK_TEXT.getFgColor()
    }
    override fun customizeCellRenderer(
      tree: JTree, value: Any?,
      selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, focused: Boolean,
    ) {
      val hidden = (value as? NioFileNode)?.isHidden ?: false
      val style =
        if ((value as? NioFileNode)?.isValid ?: true) SimpleTextAttributes.STYLE_PLAIN
        else SimpleTextAttributes.STYLE_PLAIN or SimpleTextAttributes.STYLE_STRIKEOUT
      val color = if (hidden) HIDDEN
      else {
        if (!(value is NioFileNode)) GRAYED else null
      }
      val icon = (value as? NioFileNode)?.icon
      val name = (value as? NioFileNode)?.name
      val comment = (value as? NioFileNode)?.comment
      this.setIcon(if (!hidden || icon == null) icon else getTransparentIcon(icon))
      val attributes = SimpleTextAttributes(style, color)
      if (name != null) this.append(name, attributes)
      if (comment != null) this.append(comment, attributes)
    }

  }
}
