// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.tree.CachingTreePath
import com.intellij.util.SlowOperations
import com.intellij.util.ui.JBUI
import java.awt.Rectangle
import java.util.*
import javax.swing.event.TreeModelEvent
import javax.swing.tree.AbstractLayoutCache
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.collections.ArrayDeque
import kotlin.math.max

internal class DefaultTreeLayoutCache(private val autoExpandHandler: (TreePath) -> Unit) : AbstractLayoutCache() {

  private var root: Node? = null
  private val rows = NodeList(onUpdate = {
    variableHeight?.updateSums()
  })
  private val nodeByPath = hashMapOf<TreePath, Node>()
  private val boundsBuffer = Rectangle()
  private val defaultRowHeight = JBUI.CurrentTheme.Tree.rowHeight()
  private var variableHeight: VariableHeightSupport? = VariableHeightSupport()

  override fun setModel(newModel: TreeModel?) {
    super.setModel(newModel)
    rebuild()
  }

  override fun setRootVisible(rootVisible: Boolean) {
    if (isRootVisible == rootVisible) {
      return
    }
    super.setRootVisible(rootVisible)
    val root = this.root ?: return
    val debugLocation = Location("setRootVisible(%s)", rootVisible)
    if (rootVisible) {
      root.invalidateSize()
      rows.update(debugLocation) {
        rows.add(0, root)
      }
    }
    else {
      rows.update(debugLocation) {
        rows.removeAt(0)
      }
      treeSelectionModel?.removeSelectionPath(root.path)
    }
    treeSelectionModel?.resetRowSelection()
    checkInvariants(debugLocation)
  }

  override fun setRowHeight(rowHeight: Int) {
    if (rowHeight == this.rowHeight) {
      return
    }
    super.setRowHeight(rowHeight)
    invalidateSizes()
    if (rowHeight <= 0 && variableHeight == null) {
      variableHeight = VariableHeightSupport()
    }
    else if (rowHeight > 0 && variableHeight != null) {
      variableHeight = null
    }
    checkInvariants(Location("setRowHeight(%d)", rowHeight))
  }

  override fun setNodeDimensions(nd: NodeDimensions?) {
    super.setNodeDimensions(nd)
    invalidateSizes()
    checkInvariants(Location("setNodeDimensions(%s)", nd))
  }

  override fun setExpandedState(path: TreePath?, isExpanded: Boolean) {
    val node = getOrCreateNode(path) ?: return
    val wasVisible = node.isVisible
    val oldVisibleChildren = node.visibleChildCount
    val debugLocation = Location("setExpandedState(%s, %s)", path, isExpanded)
    rows.update(debugLocation) {
      if (isExpanded) {
        node.ensureChildrenVisible()
      }
      else {
        node.parent?.ensureChildrenVisible()
        node.collapse()
        node.invalidateSize()
      }
    }
    checkInvariants(debugLocation)
    val newVisibleChildren = node.visibleChildCount
    if (wasVisible && oldVisibleChildren == 0 && newVisibleChildren == 1) {
      autoExpandHandler(node.getChildAt(0).path)
    }
  }

  // The following two methods appear to be almost identical, and their javadocs are very confusing:
  // according to them, the former checks whether the node is visible and expanded,
  // and the latter just checks whether a node is expanded.
  // However, this is not what they actually do. In fact, both check that the node is visible,
  // but in a rather weird way: the root is always considered visible, regardless of the isRootVisible value.
  // In practice, though, isExpanded() is used for checking whether the children are visible or not,
  // while getExpandedState() is used to paint the node itself (the expanded/collapsed indicators, etc.),
  // so we preserve this contract instead of trying to make sense of the javadocs.

  override fun getExpandedState(path: TreePath?): Boolean = getNode(path)?.run{ isVisible && isExpanded } == true

  override fun isExpanded(path: TreePath?): Boolean = getNode(path)?.isChildrenVisible == true

  override fun getPreferredHeight(): Int = if (rowHeight > 0) {
    rows.size * rowHeight
  }
  else {
    rows.size * defaultRowHeight + (variableHeight?.totalDelta() ?: 0)
  }

  override fun getPreferredWidth(bounds: Rectangle?): Int {
    if (rows.isEmpty()) {
      return 0
    }
    val firstRow: Int
    val lastRow: Int
    if (bounds == null) {
      firstRow = 0
      lastRow = rows.lastIndex
    }
    else {
      firstRow = getRowByY(bounds.y)
      lastRow = getRowByY(bounds.y + bounds.height)
    }
    var result = 0
    for (row in firstRow..lastRow) {
      rows[row].getBounds(boundsBuffer)
      result = max(result, boundsBuffer.x + boundsBuffer.width)
    }
    return result
  }

  override fun getBounds(path: TreePath?, placeIn: Rectangle?): Rectangle? = getNode(path)?.getBounds(placeIn)

  override fun getPathForRow(row: Int): TreePath? = getNode(row)?.path

  override fun getRowForPath(path: TreePath?): Int = getNode(path)?.row ?: -1

  override fun getPathClosestTo(x: Int, y: Int): TreePath? = getNode(getRowByY(y))?.path

  override fun getVisiblePathsFrom(path: TreePath?): Enumeration<TreePath>? = getNode(path)?.let { NodeEnumeration(it) }

  override fun getVisibleChildCount(path: TreePath?): Int = getNode(path)?.getVisibleChildCountRecursively() ?: 0

  override fun getRowCount(): Int = rows.size

  override fun invalidateSizes() {
    root?.invalidateSizeRecursively()
  }

  override fun invalidatePathBounds(path: TreePath?) {
    getNode(path)?.invalidateSize()
  }

  override fun treeNodesChanged(e: TreeModelEvent?) {
    val changedNode = getNode(e.treePathOrRoot) ?: return
    changedNode.invalidateSize()
    if (!changedNode.isChildrenLoaded) {
      return
    }
    val changedValue = changedNode.userObject
    val changedChildIndexes = e?.childIndices ?: return
    val model = this.model ?: return
    for (i in changedChildIndexes) {
      val changedChildNode = changedNode.getChildAt(i)
      changedChildNode.userObject = model.getChild(changedValue, i)
      changedChildNode.invalidateSize()
    }
    checkInvariants(Location("treeNodesChanged(value=%s, indices=%s)", changedValue, changedChildIndexes))
  }

  override fun treeNodesInserted(e: TreeModelEvent?) {
    val changedNode = getNode(e.treePathOrRoot) ?: return
    val insertedChildIndexes = e?.childIndices ?: return
    if (insertedChildIndexes.isEmpty()) {
      return
    }
    val changedValue = changedNode.userObject
    val model = this.model ?: return
    if (model.getChildCount(changedValue) == insertedChildIndexes.size) {
      changedNode.invalidateSize() // first children are inserted, may need to reflect empty/non-empty status
    }
    if (!changedNode.isChildrenLoaded) {
      return
    }
    val debugLocation = Location("treeNodesInserted(value=%s, indices=%s)", changedValue, insertedChildIndexes)
    rows.update(debugLocation) {
      for (i in insertedChildIndexes) {
        changedNode.createChildAt(i, treeModel.getChild(changedNode.userObject, i))
      }
    }
    treeSelectionModel?.resetRowSelection()
    if (insertedChildIndexes.size == 1 && changedNode.visibleChildCount == 1) {
      autoExpandHandler(changedNode.getChildAt(0).path)
    }
    checkInvariants(debugLocation)
  }

  override fun treeNodesRemoved(e: TreeModelEvent?) {
    val changedNode = getNode(e.treePathOrRoot) ?: return
    val removedChildIndexes = e?.childIndices ?: return
    if (removedChildIndexes.isEmpty()) {
      return
    }
    val changedValue = changedNode.userObject
    val model = this.model ?: return
    if (model.getChildCount(changedValue) == 0) {
      changedNode.invalidateSize() // last children are removed, may need to reflect empty/non-empty status
    }
    if (!changedNode.isChildrenLoaded) {
      return
    }
    val debugLocation = Location("treeNodesRemoved(value=%s, indices=%s)", changedValue, removedChildIndexes)
    rows.update(debugLocation) {
      for (index in removedChildIndexes.reversed()) {
        changedNode.removeChildAt(index)
      }
      if (changedNode.childCount == 0 && changedNode.isLeaf) {
        changedNode.collapse()
      }
    }
    treeSelectionModel?.resetRowSelection()
    checkInvariants(debugLocation)
  }

  override fun treeStructureChanged(e: TreeModelEvent?) {
    if (e == null) {
      return
    }
    val changedPath = e.treePathOrRoot
    val changedNode = getNode(changedPath)
    val debugLocation = Location("treeStructureChanged(path=%s)", changedPath)
    if (changedPath.isRoot) {
      rebuild()
      treeSelectionModel?.clearSelection()
    }
    else if (changedPath != null && changedNode != null) {
      val childrenWereVisible = changedNode.isChildrenVisible
      val parent = changedNode.parent
      checkNotNull(parent) { "Changed node $changedNode is not root, but its parent is null" }
      val index = parent.getChildIndex(changedNode)
      check(index != -1) { "The node $parent has no child $changedNode" }
      rows.update(debugLocation) {
        parent.removeChildAt(index)
        val newNode = parent.createChildAt(index, changedPath.lastPathComponent)
        if (childrenWereVisible) {
          newNode.ensureChildrenVisible()
        }
      }
    }
    checkInvariants(debugLocation)
  }

  private fun rebuild() {
    nodeByPath.clear()
    rows.clear()
    this.root = null
    val newRootObject = treeModel?.root ?: return
    val newRootNode = Node(null, CachingTreePath(newRootObject))
    this.root = newRootNode
    val location = Location("rebuild")
    rows.update(location) {
      if (isRootVisible) {
        rows.add(0, newRootNode)
      }
      newRootNode.ensureChildrenVisible()
    }
    checkInvariants(location)
  }

  private fun getRowByY(y: Int): Int =
    if (rows.isEmpty()) {
      -1
    }
    else {
      variableHeight?.binarySearchRow(y) ?: (y / rowHeight).coerceIn(rows.indices)
    }

  private fun getNode(path: TreePath?): Node? = nodeByPath[path]

  private fun getNode(row: Int): Node? = rows.getOrNull(row)

  private fun getOrCreateNode(path: TreePath?): Node? {
    val node = getNode(path)
    if (node != null || path == null) {
      return node
    }
    return createNode(path)

  }

  private fun createNode(path: TreePath): Node {
    val paths = findPathsUpToExistingParent(path)
    loadNodesDownTo(paths)
    val loadedNode = getNode(path)
    checkNotNull(loadedNode) { "Loaded $path, but it still doesn't exist (it's a bug)" }
    return loadedNode
  }

  private fun findPathsUpToExistingParent(path: TreePath): ArrayDeque<TreePath> {
    val paths = ArrayDeque(listOf(path))
    var parentPath: TreePath? = path.parentPath
    var parent: Node? = null
    while (parentPath != null) {
      paths.addLast(parentPath)
      parent = getNode(parentPath)
      if (parent != null) {
        break
      }
      parentPath = parentPath.parentPath
    }
    requireNotNull(parent) { "Node $path doesn't have the same root as this tree (${root?.path})" }
    return paths
  }

  private fun loadNodesDownTo(paths: ArrayDeque<TreePath>) {
    var parentPath: TreePath? = paths.removeLast()
    var childPath: TreePath? = paths.removeLast()
    while (childPath != null) {
      val parentNode = getNode(parentPath)
      requireNotNull(parentNode) { "Asked to load children of a non-existing parent $parentPath" }
      require(!parentNode.isChildrenLoaded) { "Asked to load children of the path $parentPath when they're already loaded" }
      parentNode.loadChildren()
      parentPath = childPath
      childPath = paths.removeLastOrNull()
    }
  }

  private val TreeModelEvent?.treePathOrRoot: TreePath?
    get() = this?.treePath ?: model?.root?.let { root -> CachingTreePath(root) }

  private inner class Node(
    val parent: Node?,
    var path: TreePath,
  ) {

    init {
      nodeByPath[path] = this
    }

    var row = -1
    var x = 0
    var width = 0
    var heightDelta = 0
    var fenwickTreeNodeForY = 0

    val y: Int
      get() = variableHeight?.getY(row) ?: (row * rowHeight)

    val height: Int
      get() = if (rowHeight > 0) rowHeight else defaultRowHeight + heightDelta

    var children: MutableList<Node>? = null
      private set

    val childCount get() = children?.size ?: 0

    val isLeaf: Boolean
      get() = model.isLeaf(userObject)

    val isVisible: Boolean
      get() = row != -1

    var isExpanded: Boolean = false

    val isChildrenLoaded: Boolean
      get() = children != null

    val isChildrenVisible: Boolean
      get() = if (parent == null) isExpanded else isVisible && isExpanded

    val visibleChildCount: Int
      get() = if (isChildrenVisible) childCount else 0

    var userObject: Any
      get() = path.lastPathComponent
      set(value) {
        updatePathsRecursively(parent?.path, value)
      }

    private fun updatePathsRecursively(newParent: TreePath?, newUserObject: Any) {
      nodeByPath.remove(path)
      path = newParent?.pathByAddingChild(newUserObject) ?: CachingTreePath(newUserObject)
      nodeByPath[path] = this
      children?.forEach { child ->
        child.updatePathsRecursively(path, child.userObject)
      }
    }

    fun getChildAt(i: Int): Node {
      val children = this.children
      requireNotNull(children) { "No children or not expanded yet" }
      return children[i]
    }

    fun createChildAt(index: Int, value: Any): Node {
      val children = this.children
      requireNotNull(children) { "Can't create a child for a node that isn't expanded" }
      val node = Node(this, path.pathByAddingChild(value))
      children.add(index, node)
      if (!isChildrenVisible) {
        return node
      }
      val row = when (index) {
        0 -> row + 1 // Special case covered: if the parent is the invisible root, then -1 + 1 = 0, which is correct.
        childCount -> getLastVisibleNode().row + 1
        else -> getChildAt(index - 1).getLastVisibleNode().row + 1
      }
      rows.add(row, node)
      node.row = row
      return node
    }

    private fun getLastVisibleNode(): Node {
      var node = this
      while (true) {
        if (!node.isChildrenVisible) {
          break
        }
        val children = node.children
        if (children.isNullOrEmpty()) {
          break
        }
        node = children.last()
      }
      return node
    }

    fun removeChildAt(index: Int) {
      val children = this.children
      requireNotNull(children) { "No children or not expanded yet" }
      val child = children.removeAt(index)
      if (child.isVisible) {
        rows.update {
          rows.clearRange(child.row, child.row + child.visibleSubtreeNodeCount())
        }
      }
      child.disposeRecursively()
    }

    fun getChildIndex(child: Node): Int = children?.indexOf(child) ?: -1

    fun getBounds(placeIn: Rectangle?): Rectangle? {
      if (!isVisible) {
        return null
      }
      val buffer = placeIn ?: Rectangle()
      if (width == 0) {
        val nd = getNodeDimensions(userObject, row, path.pathCount - 1, isExpanded, buffer)
        x = nd.x
        width = nd.width
        val newHeightDelta = nd.height - defaultRowHeight
        variableHeight?.updateHeightDelta(row, heightDelta, newHeightDelta)
        heightDelta = newHeightDelta
      }
      buffer.x = x
      buffer.y = y
      buffer.width = width
      buffer.height = height
      return buffer
    }

    fun invalidateSizeRecursively() {
      doInvalidateSize()
      fenwickTreeNodeForY = 0 // We're resetting them all, so no point to update one-by-one.
      children?.forEach { it.invalidateSizeRecursively() }
    }

    fun invalidateSize() {
      variableHeight?.updateHeightDelta(row, heightDelta, 0)
      doInvalidateSize()
    }

    private fun doInvalidateSize() {
      x = 0
      width = 0
      heightDelta = 0
    }

    fun getVisibleChildCountRecursively(): Int {
      if (!isChildrenVisible) {
        return 0
      }
      var result = childCount
      for (i in 0 until childCount) {
        result += getChildAt(i).getVisibleChildCountRecursively()
      }
      return result
    }

    fun ensureChildrenVisible() {
      parent?.ensureChildrenVisible()
      expand()
    }

    private fun expand() {
      if (isExpanded || isLeaf) {
        return
      }
      doExpand()
      isExpanded = true
    }

    private fun doExpand() {
      val firstChildRow = row + 1
      val children = this.children ?: loadChildren()
      rows.update {
        rows.addAll(firstChildRow, children)
      }
    }

    fun loadChildren(): MutableList<Node> {
      val children = (0 until treeModel.getChildCount(userObject)).mapTo(mutableListOf()) { i ->
        Node(this, path.pathByAddingChild(treeModel.getChild(userObject, i)))
      }
      this.children = children
      return children
    }

    fun collapse() {
      if (!isExpanded) {
        return
      }
      doCollapse()
      isExpanded = false
    }

    private fun doCollapse() {
      if (!isVisible) {
        return
      }
      val visibleChildrenCount = visibleSubtreeNodeCount() - 1 // minus this node, it remains visible
      val firstChildRow = row + 1
      rows.update {
        rows.clearRange(firstChildRow, firstChildRow + visibleChildrenCount)
      }
    }

    fun visibleSubtreeNodeCount(): Int = when {
      !isVisible -> 0
      !isChildrenVisible -> 1
      else -> 1 + (children?.sumOf { it.visibleSubtreeNodeCount() } ?: 0)
    }

    fun disposeRecursively() {
      nodeByPath.remove(path)
      row = -1
      children?.forEach { it.disposeRecursively() }
    }

  }

  private class NodeList(private val onUpdate: () -> Unit) : Iterable<Node> {

    val indices: ClosedRange<Int> get() = nodes.indices
    val size: Int get() = nodes.size
    fun isEmpty(): Boolean = nodes.isEmpty()
    val lastIndex: Int get() = nodes.lastIndex
    operator fun get(index: Int): Node = nodes[index]
    fun getOrNull(index: Int): Node? = nodes.getOrNull(index)

    private val nodes = mutableListOf<Node>()
    private var minimumAffectedRow: Int = -1

    override fun iterator(): Iterator<Node> = nodes.iterator()

    fun clear() {
      nodes.clear()
    }

    inline fun update(update: () -> Unit) {
      updateImpl(null, update)
    }

    inline fun update(location: Location, update: () -> Unit) {
      updateImpl(location, update)
    }

    inline fun updateImpl(location: Location?, update: () -> Unit) {
      val reentry = minimumAffectedRow != -1
      if (reentry) { // Indirect recursion, will update later up the stack.
        update()
        return
      }
      if (location == null) {
        throw IllegalStateException("A non-reentry call of NodeList.update with no location is detected")
      }
      val initialSize = size
      try {
        minimumAffectedRow = Integer.MAX_VALUE
        update()
        for (i in minimumAffectedRow..nodes.lastIndex) {
          nodes[i].row = i
        }
        onUpdate()
      } catch (e: Exception) {
        LOG.error(AssertionError("A bug in DefaultTreeLayoutCache is detected in $location, size was $initialSize, now $size", e))
      } finally {
        minimumAffectedRow = -1
      }
    }

    fun add(index: Int, value: Node) {
      nodes.add(index, value)
      value.row = index
      if (minimumAffectedRow >= index) { // The current dirty region is to the right?
        minimumAffectedRow = index + 1 // Extend it to this index, excluding this node.
      } // Otherwise, we're operating inside a dirty region already, do nothing.
    }

    fun removeAt(index: Int) {
      nodes[index].row = -1
      nodes.removeAt(index)
      if (minimumAffectedRow > index) { // The current dirty region is to the right?
        minimumAffectedRow = index // Extend it to this index.
      } // Otherwise, we're operating inside a dirty region already, do nothing.
    }

    fun clearRange(fromInclusive: Int, toExclusive: Int) {
      val subList = nodes.subList(fromInclusive, toExclusive)
      subList.forEach { it.row = -1 }
      subList.clear()
      if (minimumAffectedRow > fromInclusive) { // The current dirty region is to the right?
        minimumAffectedRow = fromInclusive // Extend it to this index.
      } // Otherwise, we're operating inside a dirty region already, do nothing.
    }

    fun addAll(index: Int, newNodes: List<Node>) {
      nodes.addAll(index, newNodes)
      for ((i, node) in newNodes.withIndex()) {
        node.row = index + i
      }
      if (minimumAffectedRow >= index) { // The current dirty region is to the right?
        minimumAffectedRow = index + newNodes.size // Extend it to this index, excluding the added nodes.
      } // Otherwise, we're operating inside a dirty region already, do nothing.
    }

  }

  private inner class NodeEnumeration(start: Node) : Enumeration<TreePath> {

    private var row = start.row

    override fun hasMoreElements(): Boolean = row in rows.indices

    override fun nextElement(): TreePath {
      val result = rows[row]
      ++row
      return result.path
    }

  }

  private inner class VariableHeightSupport {

    fun updateSums() {
      for (node in rows) {
        node.fenwickTreeNodeForY = node.heightDelta
      }
      for (i in 1..rows.size) {
        val j = i + (i and -i)
        if (j <= rows.size) {
          rows[j - 1].fenwickTreeNodeForY += rows[i - 1].fenwickTreeNodeForY
        }
      }
    }

    fun totalDelta() = getSum(rows.size)

    fun getY(row: Int) = row * defaultRowHeight + getSum(row)

    fun getSum(rowCount: Int): Int {
      var sum = 0
      var i = rowCount
      while (i > 0) {
        sum += rows[i - 1].fenwickTreeNodeForY
        i -= i and -i
      }
      return sum
    }

    fun updateHeightDelta(row: Int, oldHeightDelta: Int, newHeightDelta: Int) {
      if (row == -1) {
        return
      }
      val change = newHeightDelta - oldHeightDelta
      var i = row + 1
      while (i <= rows.size) {
        rows[i - 1].fenwickTreeNodeForY += change
        i += i and -i
      }
    }

    fun binarySearchRow(y: Int): Int
    {
      if (y <= 0) {
        return 0
      }
      var low = 0
      var high = rows.size - 1
      while (low <= high) {
        val mid = (low + high) / 2 // no need to be overflow-conscious, two billion node trees will blow before we even get here
        val midY = getY(mid)
        val midHeight = rows[mid].height
        if (y >= midY + midHeight)
          low = mid + 1
        else if (y < midY)
          high = mid - 1
        else
          return mid
      }
      return low.coerceIn(rows.indices) // an invariant violation (gaps between rows) or y out of range
    }

  }

  private fun checkInvariants(location: Location) {
    if (!LOG.isDebugEnabled) {
      return
    }
    SlowOperations.startSection(SlowOperations.GENERIC).use { // Only for debugging, so slow ops are fine here.
      InvariantChecker(location).checkInvariants()
    }
  }

  private class Location(location: String, vararg args: Any?) {

    private val location = location.format(
      *args
        .map { if (it is IntArray) it.toList() else it }
        .toTypedArray()
    )

    override fun toString() = location

  }

  private inner class InvariantChecker(private val location: Location) {

    private val messages = mutableListOf<String>()

    fun checkInvariants() {
      if (rows.isEmpty()) {
        checkEmptyTree()
        return
      }
      checkRows()
      checkFenwickTree()
      checkY()
      checkVisibleSubtrees()
      if (messages.isNotEmpty()) {
        val details = StringBuilder()
        details.append("DefaultTreeLayoutCache invariants are broken in $location:\n")
        for (message in messages) {
          details.append(message).append('\n')
        }
        // Log as ERROR to get a notification, but it still only happens when DEBUG is enabled, for performance reasons.
        LOG.error(Exception(details.toString()))
      }
    }

    private fun checkRows() {
      for (i in 0 until rows.size) {
        if (rows[i].row != i) {
          messages += "Row inconsistency: row $i contains ${rows[i].path} which is supposed to be at ${rows[i].row}"
        }
      }
    }

    private fun checkEmptyTree() {
      val root = this@DefaultTreeLayoutCache.root
      if (root != null) {
        if (isRootVisible) {
          messages += "No visible rows, but the root is $root and it's visible"
        }
        else if (root.childCount > 0) {
          messages += "No visible rows, but the invisible root $root has children: ${root.children}"
        }
      }
      else if (nodeByPath.isNotEmpty()) {
        messages += "An empty tree has ${nodeByPath.size} nodes: $nodeByPath"
      }
    }

    private fun checkFenwickTree() {
      val vh = variableHeight
      if (vh != null) {
        var sum = 0
        for (i in 1..rows.size) {
          sum += rows[i - 1].heightDelta
          val fenwickTreeSum = vh.getSum(i)
          if (fenwickTreeSum != sum) {
            messages += "Fenwick tree sum for $i rows is $fenwickTreeSum, but should be $sum"
          }
        }
      }
    }

    private fun checkY() {
      for (i in 1 until rows.size) {
        val node = rows[i]
        val prevNode = rows[i - 1]
        val y = node.y
        val prevY = prevNode.y
        val prevHeight = prevNode.height
        if (y != prevY + prevHeight) {
          messages += "Row $i is at y=$y, but previous row is at y=$prevY and has height=$prevHeight (sum=${prevY + prevHeight})"
        }
      }
    }

    private fun checkVisibleSubtrees() {
      for (i in 0 until rows.size) {
        val node = rows[i]
        val visibleSubtreeSize = node.visibleSubtreeNodeCount()
        if (visibleSubtreeSize <= 0) {
          messages += "Node ${node.path} at row $i is visible, but has the visible subtree of size $visibleSubtreeSize"
        }
        if (!node.isChildrenVisible) {
          if (visibleSubtreeSize > 1) {
            messages += "Node ${node.path} at row $i is not expanded, but has the visible subtree of size $visibleSubtreeSize"
          }
          checkAllChildrenAreInvisible(node)
        }
        if (i + visibleSubtreeSize > rows.size) {
          messages += "Node ${node.path} at row $i has the visible subtree of size $visibleSubtreeSize, which extends past rowCount=${rows.size}"
        }
      }
    }

    private fun checkAllChildrenAreInvisible(parent: Node) {
      val children = parent.children
      if (children == null) {
        return
      }
      for (child in children) {
        checkAllChildrenAreInvisible(parent, child)
      }
    }

    private fun checkAllChildrenAreInvisible(parent: Node, node: Node) {
      if (node.isVisible) {
        messages += "Parent ${parent.path} is not expanded, but has a visible child ${node.path} at row ${node.row}"
      }
      val children = node.children
      if (children == null) {
        return
      }
      for (child in children) {
        checkAllChildrenAreInvisible(parent, child)
      }
    }

  }

}

private val TreePath?.isRoot: Boolean get() = this == null || this.pathCount == 1

private val LOG = Logger.getInstance("ide.ui.tree.layout.cache")
