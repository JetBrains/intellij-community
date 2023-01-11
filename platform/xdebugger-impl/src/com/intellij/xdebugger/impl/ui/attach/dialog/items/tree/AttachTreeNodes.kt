package com.intellij.xdebugger.impl.ui.attach.dialog.items.tree

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.UserDataHolder
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.render.RenderingUtil
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachSelectionIgnoredNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElement
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.AttachTreeGroupColumnRenderer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.AttachTreeGroupFirstColumnRenderer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.AttachTreeGroupLastColumnRenderer
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.table.TableCellRenderer

internal abstract class AttachTreeNode(defaultIndentLevel: Int = 0): AttachToProcessElement {
  private val children = mutableListOf<AttachTreeNode>()
  private var parent: AttachTreeNode? = null
  private var myIndentLevel: Int = defaultIndentLevel

  private var myTree: AttachToProcessTree? = null

  fun addChild(child: AttachTreeNode) {
    children.add(child)
    child.parent = this
    child.updateIndentLevel(myIndentLevel + 1)
  }

  fun getChildNodes(): List<AttachTreeNode> = children

  fun getParent(): AttachTreeNode? = parent

  fun getIndentLevel(): Int = myIndentLevel

  var tree: AttachToProcessTree
    get() = myTree ?: throw IllegalStateException("Parent tree is not yet set")
    set(value) {
      myTree = value
      for (child in children) {
        child.tree = value
      }
    }

  abstract fun getValueAtColumn(column: Int): Any
  abstract fun getRenderer(column: Int): TableCellRenderer?

  private fun updateIndentLevel(newIndentLevel: Int) {
    myIndentLevel = newIndentLevel
    for (child in children) {
      child.updateIndentLevel(newIndentLevel + 1)
    }
  }

  abstract fun getTreeCellRendererComponent(tree: JTree?,
                                            value: AttachTreeNode,
                                            selected: Boolean,
                                            expanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            hasFocus: Boolean): Component

  override fun getProcessItem(): AttachDialogProcessItem? = null
}

internal class AttachTreeRecentProcessNode(item: AttachDialogProcessItem,
                                           dialogState: AttachDialogState,
                                           dataHolder: UserDataHolder) : AttachTreeProcessNode(item,
                                                                                               dialogState,
                                                                                               dataHolder) {
  override fun isRecentItem(): Boolean = true
}

internal open class AttachTreeProcessNode(val item: AttachDialogProcessItem,
                                          val dialogState: AttachDialogState,
                                          val dataHolder: UserDataHolder) : AttachTreeNode() {

  companion object {
    private val logger = Logger.getInstance(AttachTreeProcessNode::class.java)
  }

  private val renderer = object : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree,
                                       value: Any?,
                                       selected: Boolean,
                                       expanded: Boolean,
                                       leaf: Boolean,
                                       row: Int,
                                       hasFocus: Boolean) {
      val cell = ExecutableCell(this@AttachTreeProcessNode, dialogState)
      val (text, tooltip) = cell.getPresentation(this, (cell.node.getIndentLevel() + 1) * JBUI.scale(18))
      this.append(text, modifyAttributes(cell.getTextAttributes(), this@AttachTreeProcessNode.tree, row))
      this.toolTipText = tooltip
    }

    private fun modifyAttributes(attributes: SimpleTextAttributes, tree: AttachToProcessTree, row: Int): SimpleTextAttributes {
      //We should not trust the selected value as the TreeTableTree
      // is inserted into table and usually has wrong selection information
      val isTableRowSelected = tree.isRowSelected(row)
      return if (!isTableRowSelected) attributes else SimpleTextAttributes(attributes.style, RenderingUtil.getSelectionForeground(tree))
    }
  }

  open fun isRecentItem(): Boolean = false

  override fun getValueAtColumn(column: Int): Any = when (column) {
    0 -> this
    1 -> PidCell(this, dialogState)
    2 -> DebuggersCell(this, dialogState)
    3 -> CommandLineCell(this, dialogState)
    else -> {
      logger.error("Unexpected column index: $column")
      Any()
    }
  }

  override fun getRenderer(column: Int): TableCellRenderer? = null

  override fun getTreeCellRendererComponent(tree: JTree?,
                                            value: AttachTreeNode,
                                            selected: Boolean,
                                            expanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            hasFocus: Boolean): Component {
    return renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
  }

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return filters.accept(item)
  }

  override fun getProcessItem(): AttachDialogProcessItem = item
}

internal class AttachTreeRecentNode(recentItemNodes: List<AttachTreeRecentProcessNode>) : AttachTreeNode(), AttachSelectionIgnoredNode {

  init {
    for (recentItemNode in recentItemNodes) {
      addChild(recentItemNode)
    }
  }

  override fun getValueAtColumn(column: Int): Any {
    return this
  }

  override fun getRenderer(column: Int): TableCellRenderer? = null
  override fun getTreeCellRendererComponent(tree: JTree?,
                                            value: AttachTreeNode,
                                            selected: Boolean,
                                            expanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            hasFocus: Boolean): JComponent {
    return JLabel(XDebuggerBundle.message("xdebugger.attach.dialog.recently.attached.message"))
  }

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return getChildNodes().any { filters.matches(it) }
  }
}

internal class AttachTreeSeparatorNode(private val relatedNodes: List<AttachTreeRecentProcessNode>) : AttachTreeNode(), AttachSelectionIgnoredNode {

  companion object {
    private val logger = Logger.getInstance(AttachTreeSeparatorNode::class.java)

    private val leftColumnRenderer = AttachTreeGroupFirstColumnRenderer()
    private val middleColumnRenderer = AttachTreeGroupColumnRenderer()
    private val rightColumnRenderer = AttachTreeGroupLastColumnRenderer()
  }

  override fun getValueAtColumn(column: Int): Any {
    return this
  }

  override fun getRenderer(column: Int): TableCellRenderer? {
    return when (column) {
      0 -> leftColumnRenderer
      1 -> middleColumnRenderer
      2 -> middleColumnRenderer
      3 -> rightColumnRenderer
      else -> {
        logger.error("Unexpected column index: $column")
        null
      }
    }
  }

  override fun getTreeCellRendererComponent(tree: JTree?,
                                            value: AttachTreeNode,
                                            selected: Boolean,
                                            expanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            hasFocus: Boolean): JComponent {
    return leftColumnRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
  }

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return relatedNodes.any { filters.matches(it) }
  }
}

internal class AttachTreeRootNode(items: List<AttachTreeNode>) : AttachTreeNode(-1), AttachSelectionIgnoredNode {

  init {
    for (item in items) {
      addChild(item)
    }
  }

  override fun getValueAtColumn(column: Int): Any {
    return this
  }

  override fun getRenderer(column: Int): TableCellRenderer? = null

  override fun getTreeCellRendererComponent(tree: JTree?,
                                            value: AttachTreeNode,
                                            selected: Boolean,
                                            expanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            hasFocus: Boolean): JComponent {
    return JLabel()
  }

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return true
  }
}