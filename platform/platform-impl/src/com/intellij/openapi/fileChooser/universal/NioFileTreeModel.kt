// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.icons.PredefinedIconOverlayService
import com.intellij.ui.tree.MapBasedTree
import com.intellij.ui.tree.MapBasedTree.Entry
import com.intellij.util.PlatformIcons
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.InvokerSupplier
import com.intellij.util.io.PlatformNioHelper
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.tree.AbstractTreeModel
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import javax.swing.Icon
import javax.swing.tree.TreePath
import kotlin.io.path.invariantSeparatorsPathString

/**
 * A tree model backed by Java NIO [Path] instead of [com.intellij.openapi.vfs.VirtualFile].
 * Provides the same public interface as [com.intellij.openapi.fileChooser.tree.FileTreeModel]
 * but does not use the Virtual File System (VFS) internally.
 */
@ApiStatus.Internal
class NioFileTreeModel(
  descriptor: FileChooserDescriptor,
  sortDirectories: Boolean = true,
) : AbstractTreeModel(), InvokerSupplier {

  companion object {

    private val LOG = Logger.getInstance(NioFileTreeModel::class.java)

    private fun fileName(path: Path): String {
      val contributor = UniversalFileChooserContributor.findOwner(path)
      return contributor?.getFileName(path) ?: path.toString()
    }
  }

  private val invoker: Invoker = Invoker.forBackgroundThreadWithoutReadAction(this)
  private val state: State = State(descriptor, sortDirectories, this)

  @Volatile
  private var contributorRoots: List<UniversalFileChooserContributor.Root> = emptyList()

  @Volatile
  private var roots: List<Root>? = null

  fun invalidate() {
    invoker.invoke {
      roots?.forEach { it.tree.invalidate() }
      treeStructureChanged(state.path, null, null)
    }
  }

  fun setContributorRoots(newRoots: List<UniversalFileChooserContributor.Root>) {
    invoker.invoke {
      contributorRoots = newRoots
      roots = null
      treeStructureChanged(state.path, null, null)
    }
  }

  override fun getInvoker(): Invoker = invoker

  override fun getRoot(): Any? {
    if (state.path != null) return state
    if (roots == null) roots = state.getRoots()
    val r = roots!!
    return if (r.size == 1) r.first() else null
  }

  override fun getChild(parent: Any?, index: Int): Any? {
    if (parent === state) {
      if (roots == null) roots = state.getRoots()
      val r = roots!!
      if (index in r.indices) return r[index]
    }
    else if (parent is Node) {
      val entry = getEntry(parent, true)
      if (entry != null) return entry.getChild(index)
    }
    return null
  }

  override fun getChildCount(parent: Any?): Int {
    if (parent === state) {
      if (roots == null) roots = state.getRoots()
      return roots!!.size
    }
    else if (parent is Node) {
      val entry = getEntry(parent, true)
      if (entry != null) return entry.childCount
    }
    return 0
  }

  override fun isLeaf(node: Any?): Boolean {
    if (node is Node) {
      val entry = getEntry(node, false)
      if (entry != null) return entry.isLeaf
    }
    return false
  }

  override fun getIndexOfChild(parent: Any?, child: Any?): Int {
    if (parent === state) {
      if (roots == null) roots = state.getRoots()
      val r = roots!!
      for (i in r.indices) {
        if (child === r[i]) return i
      }
    }
    else if (parent is Node && child is Node) {
      val entry = getEntry(parent, true)
      if (entry != null) return entry.getIndexOf(child)
    }
    return -1
  }

  private fun getEntry(node: Node, loadChildren: Boolean): Entry<Node>? {
    val r = roots ?: return null
    for (root in r) {
      val entry = root.tree.getEntry(node)
      if (entry != null) {
        if (loadChildren && entry.isLoadingRequired) {
          root.updateChildren(state, entry)
        }
        return entry
      }
    }
    return null
  }

  fun matchRoot(path: Path): Path? =
    state.getRoots().firstOrNull { root -> root.path?.invariantSeparatorsPathString == path.invariantSeparatorsPathString }?.path

  fun getVirtualRoot(node: NioFileNode): UniversalFileChooserContributor.Root? =
    (node as? VirtualRoot)?.contributorRoot

  fun setRootError(path: Path) {
    invoker.invoke {
      val r = roots ?: return@invoke
      val target = r.firstOrNull { it.path?.invariantSeparatorsPathString == path.invariantSeparatorsPathString }
                   ?: return@invoke
      if (target.updateIcon(AllIcons.General.Error)) {
        treeNodesChanged(TreePath(target), null, null)
      }
    }
  }


  private data class ChildEntry(
    val path: Path,
    val attrs: BasicFileAttributes,
    val isDirectory: Boolean = if (attrs.isSymbolicLink) Files.isDirectory(path) else attrs.isDirectory,
  )

  private class State(
    val descriptor: FileChooserDescriptor,
    private val sortDirectories: Boolean,
    val model: NioFileTreeModel,
  ) {
    val descriptorRoots: List<Path>? = getRoots(descriptor)
    val path: TreePath? = if (descriptorRoots != null && descriptorRoots.size == 1) null else TreePath(this)

    fun compare(one: ChildEntry, two: ChildEntry): Int {
      if (sortDirectories) {
        if (one.isDirectory != two.isDirectory) return if (one.isDirectory) -1 else 1
      }
      return StringUtil.naturalCompare(fileName(one.path), fileName(two.path))
    }

    fun isVisible(entry: ChildEntry): Boolean {
      if (!descriptor.isShowHiddenFiles) {
        if (isHiddenFromAttrs(entry)) return false
      }
      if (!descriptor.isChooseFiles && !entry.isDirectory) return false
      return true
    }

    private fun isHiddenFromAttrs(entry: ChildEntry): Boolean = NioFileChooserUtil.isHidden(entry.path, entry.attrs)

    fun getChildrenWithAttributes(path: Path): List<ChildEntry>? {
      if (!isValid(path)) return null
      if (!Files.isDirectory(path)) return null
      return try {
        val result = mutableListOf<ChildEntry>()
        PlatformNioHelper.visitDirectory(path, null) { childPath, attrResult ->
          try {
            val attrs = attrResult.get()
            result.add(ChildEntry(childPath, attrs))
          }
          catch (_: IOException) {
            // skip unreadable entries
          }
          catch (_: RuntimeException) {
            // skip unreadable entries
          }
          true
        }
        result
      }
      catch (e: IOException) {
        LOG.debug("cannot list directory: $path", e)
        null
      }
      catch (e: SecurityException) {
        LOG.debug("cannot list directory: $path", e)
        null
      }
    }

    fun getRoots(): List<Root> {
      if (!model.invoker.isValidThread) {
        LOG.error(IllegalStateException(Thread.currentThread().name))
      }
      if (descriptorRoots != null) {
        val files = descriptorRoots
        if (files.isEmpty()) return emptyList()
        return files.map { file -> Root(this, file, contributorRoot = null) }
      }
      val realRoots = model.contributorRoots.filter { it.path != null }
      val virtualRoots = model.contributorRoots.filter { it.path == null }
      if (realRoots.isEmpty() && virtualRoots.isEmpty()) return emptyList()
      val realRootNodes = realRoots.map { root -> Root(this, root.path!!, contributorRoot = root) }
      val virtualRootNodes = virtualRoots.map { root -> VirtualRoot(this, root) }
      return realRootNodes + virtualRootNodes
    }


    override fun toString(): String = descriptor.title

    companion object {
      fun isValid(path: Path?): Boolean = path != null && Files.exists(path)

      fun isLeaf(path: Path?): Boolean = path != null && (path.parent != null && !Files.isDirectory(path))

      fun getRoots(descriptor: FileChooserDescriptor): List<Path>? {
        val list = descriptor.roots
          .mapNotNull { vf ->
            try {
              vf.toNioPath()
            }
            catch (_: UnsupportedOperationException) {
              null
            }
          }
          .filter { isValid(it) }
        return if (list.isEmpty() && descriptor.isShowFileSystemRoots) null else list
      }
    }
  }

  private open class Node(path: Path?, attrs: BasicFileAttributes? = null, isDirectory: Boolean? = null) : NioFileNode(path) {
    init {
      updateContent(attrs, isDirectory)
    }

    protected open fun updateContent(attrs: BasicFileAttributes?, isDirectory: Boolean?) {
      val p = path
      check(p != null)
      updateName(fileName(p))
      if (attrs != null) {
        val directory = isDirectory ?: attrs.isDirectory
        var icon: Icon? =
          if (directory) PlatformIcons.FOLDER_ICON
          else FileTypeRegistry.getInstance().getFileTypeByFileName(p.toString()).icon
        val isSymlink = attrs.isSymbolicLink
        if (isSymlink && icon != null) {
          icon = PredefinedIconOverlayService.getInstance().createSymlinkIcon(icon)
        }
        updateIcon(icon)
        updateValid(true)
        updateHidden(NioFileChooserUtil.isHidden(path, attrs))
        updateSymlink(isSymlink)
        updateWritable(Files.isWritable(p))
      }
      else if (p.parent ==null) {
        updateIcon(PlatformIcons.FOLDER_ICON)
        updateValid(true)
        updateHidden(false)
        updateSymlink(false)
        updateWritable(false)
      }
      else {
        var icon: Icon? = NioFileChooserUtil.getIcon(p)
        val isSymlink = Files.isSymbolicLink(p)
        if (isSymlink && icon != null) {
          icon = PredefinedIconOverlayService.getInstance().createSymlinkIcon(icon)
        }
        updateIcon(icon)
        updateValid(Files.exists(p))
        updateHidden(NioFileChooserUtil.isHidden(p))
        updateSymlink(isSymlink)
        updateWritable(Files.isWritable(p))
      }
    }

    override fun toString(): String = name ?: ""
  }

  private open class Root(
    state: State,
    path: Path?,
    attrs: BasicFileAttributes? = null,
    isDirectory: Boolean? = null,
    contributorRoot: UniversalFileChooserContributor.Root? = null,
  ) :
    Node(path, attrs, isDirectory) {
    val tree: MapBasedTree<Path, Node> = MapBasedTree(false, { it.path }, state.path)

    init {
      tree.updateRoot(Pair.create(this, attrs?.let { !(isDirectory ?: it.isDirectory) } ?: State.isLeaf(path)))
      if (contributorRoot != null) {
        val presentation = contributorRoot.presentation
        presentation.icon?.let { updateIcon(it) }
        updateName(presentation.presentableName)
      }
    }

    open fun updateChildren(state: State, parent: Entry<Node>): MapBasedTree.UpdateResult<Node> {
      val children = parent.node.path?.let{ state.getChildrenWithAttributes(it) }
        ?: return tree.update(parent, null)
      if (children.isEmpty()) return tree.update(parent, emptyList())
      return tree.update(parent, children
        .filter { state.isVisible(it) }
        .sortedWith { a, b -> state.compare(a, b) }
        .map { entry ->
          val existing = tree.findEntry(entry.path)
          if (existing != null && parent === existing.parentPath)
            Pair.create(existing.node, !entry.isDirectory)
          else
            Pair.create(Node(entry.path, entry.attrs, entry.isDirectory), !entry.isDirectory)
        })
    }
  }

  private class VirtualRoot(
    state: State,
    val contributorRoot: UniversalFileChooserContributor.Root,
  ) : Root(state, null) {
    init {
      updateName(contributorRoot.presentation.presentableName)
      updateIcon(contributorRoot.presentation.icon ?: EmptyIcon.ICON_16)
      updateValid(true)
    }

    override fun updateContent(attrs: BasicFileAttributes?, isDirectory: Boolean?) {
    }

    override fun updateChildren(state: State, parent: Entry<Node>): MapBasedTree.UpdateResult<Node> {
      return tree.update(parent, emptyList())
    }

    override fun toString(): String = name ?: ""
  }
}
