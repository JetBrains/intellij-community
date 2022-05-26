// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

class FilteringTreeModel(private val delegate: TreeModel) : AbstractTreeModel() {

  private val childrenCache = mutableMapOf<Any, List<Any>>()

  var filterer by observable<((Any) -> Boolean)?>(null) { _, _, _ ->
    rebuildTree(null)
  }

  init {
    delegate.addTreeModelListener(object : TreeModelListener {
      override fun treeNodesChanged(e: TreeModelEvent) = rebuildTree(e)
      override fun treeNodesInserted(e: TreeModelEvent) = rebuildTree(e)
      override fun treeNodesRemoved(e: TreeModelEvent) = rebuildTree(e)
      override fun treeStructureChanged(e: TreeModelEvent) = rebuildTree(e)
    })
  }

  //TODO: more granular cache updates
  private fun rebuildTree(e: TreeModelEvent?) {
    childrenCache.clear()
    treeStructureChanged(rootPath, null, null)
  }

  private val rootPath: TreePath?
    get() = delegate.root?.let { TreePath(it) }

  override fun getRoot(): Any? = delegate.root

  override fun isLeaf(node: Any): Boolean {
    if (filterer != null) {
      return getFilteredChildren(node, filterer!!).isEmpty()
    }
    return delegate.isLeaf(node)
  }

  override fun getChildCount(parent: Any): Int {
    if (filterer != null) {
      return getFilteredChildren(parent, filterer!!).count()
    }
    return delegate.getChildCount(parent)
  }

  override fun getChild(parent: Any, index: Int): Any {
    if (filterer != null) {
      return getFilteredChildren(parent, filterer!!)[index]
    }
    return delegate.getChild(parent, index)
  }

  override fun getIndexOfChild(parent: Any, child: Any): Int {
    if (filterer != null) {
      return getFilteredChildren(parent, filterer!!).indexOf(child)
    }
    return delegate.getIndexOfChild(parent, child)
  }

  private fun getFilteredChildren(node: Any, filter: (Any) -> Boolean) =
    childrenCache.getOrPut(node) {
      TreeUtil.nodeChildren(node, delegate).filter {
        (!delegate.isLeaf(it) && !isLeaf(it)) || (delegate.isLeaf(it) && filter(it))
      }.toList()
    }
}