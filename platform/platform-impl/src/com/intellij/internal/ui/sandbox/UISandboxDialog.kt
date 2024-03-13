// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox

import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.internal.ui.sandbox.components.*
import com.intellij.internal.ui.sandbox.dsl.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeExpandCollapse
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.speedSearch.ElementFilter
import com.intellij.ui.tree.FilteringTreeModel
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTreeStructure
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure
import com.intellij.util.concurrency.Invoker
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

internal class UISandboxDialog(project: Project?) : DialogWrapper(project, null, true, IdeModalityType.IDE, true) {

  private val treeContent: List<Any> = listOf(
    Group("Components", children = listOf(
      JBIntSpinnerPanel(),
      JButtonPanel(),
      JBOptionButtonPanel(),
      JCheckBoxPanel(),
      JComboBoxPanel(),
      JRadioButtonPanel(),
      JSpinnerPanel(),
      JTextFieldPanel(),
      SearchTextFieldPanel(),
      ThreeStateCheckBoxPanel(),
      JBTabsPanel())),

    Group("Kotlin UI DSL", children = listOf(
      CellsWithSubPanelsPanel(),
      CheckBoxRadioButtonPanel(),
      CommentsPanel(),
      DeprecatedApiPanel(),
      GroupsPanel(),
      LabelsPanel(),
      ListCellRendererPanel(),
      LongTextsPanel(),
      OnChangePanel(),
      OthersPanel(),
      PlaceholderPanel(),
      ResizableRowsPanel(),
      SegmentedButtonPanel(),
      TextFieldsPanel(),
      TextMaxLinePanel(),
      ValidationPanel(),
      ValidationRefactoringPanel(),
      VisibleEnabledPanel()
    ))
  )

  private val splitter = OnePixelSplitter(false, "UISandboxDialog.splitter.proportion", 0.2f)

  private val emptyPanel = panel {
    row {
      label("Nothing selected")
        .align(Align.CENTER)
    }.resizableRow()
  }

  private val filter = ElementFilter<SimpleNode> {
    true
  }

  init {
    init()
  }

  override fun createDefaultActions() {
    super.createDefaultActions()
    cancelAction.putValue(Action.NAME, IdeBundle.message("action.close"))
  }

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction)
  }

  override fun getStyle(): DialogStyle {
    return DialogStyle.COMPACT

  }

  override fun createCenterPanel(): JComponent {
    val tree = Tree().apply {
      selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
      isRootVisible = false
      val structure = SimpleTreeStructure.Impl(SandboxTreeGroup(null, myDisposable, "", treeContent))
      setCellRenderer(SandboxTreeRenderer())
      model = FilteringTreeModel.createModel(structure, filter, Invoker.forEventDispatchThread(myDisposable), myDisposable)
      addTreeSelectionListener {
        if (it.isAddedPath) {
          onNodeSelected(extractNode(it.newLeadSelectionPath))
        }
      }
    }
    TreeExpandCollapse.expandAll(tree)
    splitter.apply {
      firstComponent = ScrollPaneFactory.createScrollPane(tree, true)
      secondComponent = emptyPanel
      minimumSize = JBDimension(400, 300)
      preferredSize = JBDimension(800, 600)
    }
    return splitter
  }

  private fun onNodeSelected(node: SandboxTreeNodeBase?) {
    when (node) {
      null, is SandboxTreeGroup -> {
        splitter.secondComponent = emptyPanel
      }
      is SandboxTreeLeaf -> {
        val panel = JPanel(BorderLayout())
        panel.add(node.panelCache.value, BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(10)
        splitter.secondComponent = if (node.isScrollbarNeeded()) ScrollPaneFactory.createScrollPane(panel, true) else panel
      }
    }
  }

  private fun extractNode(path: TreePath): SandboxTreeNodeBase? {
    return ((path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? FilteringTreeStructure.FilteringNode)?.delegate as? SandboxTreeNodeBase
  }
}

private data class Group(val title: String, val children: List<Any>)

private sealed class SandboxTreeNodeBase(parent: SimpleNode?) : SimpleNode(parent) {

  abstract val title: String

  override fun update(presentation: PresentationData) {
    super.update(presentation)

    presentation.clear()
    presentation.addText(title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }
}

private class SandboxTreeGroup(parent: SimpleNode?, disposable: Disposable, override val title: String, children: List<Any>) :
  SandboxTreeNodeBase(parent) {

  private val children: Array<SimpleNode>

  init {
    if (children.isEmpty()) {
      throw IllegalArgumentException("Empty children")
    }

    this.children = children.map {
      when (it) {
        is Group -> {
          if (it.children.isEmpty()) {
            throw IllegalArgumentException("Empty group in $it")
          }
          SandboxTreeGroup(this, disposable, it.title, it.children)
        }
        is UISandboxPanel -> {
          SandboxTreeLeaf(this, disposable, it)
        }
        else -> {
          throw IllegalArgumentException("Invalid child type $it")
        }
      }
    }.toTypedArray()
  }

  override fun getChildren(): Array<SimpleNode> {
    return children
  }
}

private class SandboxTreeLeaf(parent: SimpleNode?, disposable: Disposable, private val child: UISandboxPanel) :
  SandboxTreeNodeBase(parent) {

  override val title: String = child.title

  val panelCache = lazy {
    child.createContent(disposable)
  }

  fun isScrollbarNeeded(): Boolean {
    return child.isScrollbarNeeded
  }

  override fun getChildren(): Array<SimpleNode> {
    return NO_CHILDREN
  }
}


private class SandboxTreeRenderer : NodeRenderer() {

  override fun customizeCellRenderer(tree: JTree,
                                     value: Any?,
                                     selected: Boolean,
                                     expanded: Boolean,
                                     leaf: Boolean,
                                     row: Int,
                                     hasFocus: Boolean) {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
  }
}
