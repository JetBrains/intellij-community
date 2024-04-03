// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox

import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.internal.ui.sandbox.components.*
import com.intellij.internal.ui.sandbox.dsl.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.*
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.speedSearch.ElementFilter
import com.intellij.ui.tree.FilteringTreeModel
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.ui.treeStructure.SimpleTreeStructure
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure
import com.intellij.util.Alarm
import com.intellij.util.concurrency.Invoker
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.util.function.Consumer
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode
import javax.swing.tree.TreeSelectionModel

private const val SEARCH_UPDATE_DELAY = 300
private const val SELECTED_TREE_ITEM = "UISandboxDialog.selected.tree.item"

internal class UISandboxDialog(private val project: Project?) : DialogWrapper(project, null, true, IdeModalityType.IDE, true) {

  private val treeContent: List<Any> = listOf(
    Group("Components", children = listOf(
      JBIntSpinnerPanel(),
      JButtonPanel(),
      JBOptionButtonPanel(),
      JBTextAreaPanel(),
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

  private val filter = ElementFilter<SandboxTreeNodeBase> {
    it.title.contains(activeFilterText, true)
  }

  private val treeModel = FilteringTreeModel.createModel(SimpleTreeStructure.Impl(SandboxTreeGroup(null, myDisposable, "", treeContent)), filter, Invoker.forEventDispatchThread(myDisposable), myDisposable)

  private val tree = SimpleTree().apply {
    selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    isRootVisible = false
    setCellRenderer(SandboxTreeRenderer())
    model = treeModel
    addTreeSelectionListener {
      val newSelectionPath = it.newLeadSelectionPath
      onNodeSelected(if (newSelectionPath == null) null else extractSandboxTreeNode(newSelectionPath.lastPathComponent))
    }
  }

  private val splitter = OnePixelSplitter(false, "UISandboxDialog.splitter.proportion", 0.2f)

  private val searchTextField = object : SearchTextField("UISandboxDialog.filter.history") {
    init {
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(event: DocumentEvent) {
          updatingAlarm.cancelAllRequests()
          updatingAlarm.addRequest(::onFilterUpdated, SEARCH_UPDATE_DELAY)
        }
      })
    }

    override fun preprocessEventForTextField(e: KeyEvent?): Boolean {
      val stroke = KeyStroke.getKeyStrokeForEvent(e)
      when (stroke) {
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false) -> {
          text = ""
          return true
        }

        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false),
        KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false),
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false) -> {
          if (textEditor.isFocusOwner) {
            tree.processKeyEvent(e)
            return true
          }
        }
      }

      return super.preprocessEventForTextField(e)
    }
  }
  private val updatingAlarm = Alarm(myDisposable)
  private var activeFilterText = ""
    set(value) {
      if (field != value) {
        field = value
        val selection = (tree.selectedNode as? FilteringTreeStructure.FilteringNode)?.delegate
        treeModel.updateTree(tree, true, selection)
      }
    }

  private val emptyPanel = panel {
    row {
      label("Nothing selected")
        .align(Align.CENTER)
    }.resizableRow()
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
    val leftPanel = panel {
      row {
        val hOffset = DefaultTreeUI.HORIZONTAL_SELECTION_OFFSET
        cell(searchTextField)
          .customize(UnscaledGaps(left = hOffset, right = hOffset))
          .align(AlignX.FILL)
      }
      row {
        cell(ScrollPaneFactory.createScrollPane(tree, true))
          .align(Align.FILL)
      }.resizableRow()
    }
    splitter.apply {
      firstComponent = leftPanel
      secondComponent = emptyPanel
      minimumSize = JBDimension(400, 300)
      preferredSize = JBDimension(800, 600)
    }

    getPropertyComponent().getValue(SELECTED_TREE_ITEM)?.let {
      selectItem(it)
    }
    return splitter
  }

  private fun selectItem(item: String) {
    val node = findChild(treeModel.getChildren(treeModel.root), item)

    if (node != null) {
      val filteringNode = ((node as? DefaultMutableTreeNode)?.userObject as? FilteringTreeStructure.FilteringNode) ?: return
      treeModel.select(filteringNode, tree, Consumer {})
    }
  }

  private fun findChild(children: List<TreeNode>, item: String): TreeNode? {
    for (child in children) {
      if (extractSandboxTreeNode(child)?.title == item) {
        return child
      }
      val childResult = findChild(treeModel.getChildren(child), item)
      if (childResult != null) {
        return childResult
      }
    }

    return null
  }

  private fun onFilterUpdated() {
    activeFilterText = (searchTextField.text ?: "").trim()
  }

  private fun getPropertyComponent(): PropertiesComponent {
    return if (project == null) PropertiesComponent.getInstance() else PropertiesComponent.getInstance(project)
  }

  private fun onNodeSelected(node: SandboxTreeNodeBase?) {
    val selectedItem: String?
    when (node) {
      null -> {
        splitter.secondComponent = emptyPanel
        selectedItem = null
      }
      is SandboxTreeGroup -> {
        splitter.secondComponent = emptyPanel
        selectedItem = node.title
      }
      is SandboxTreeLeaf -> {
        val panel = JPanel(BorderLayout())
        panel.add(node.panelCache.value, BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(10)
        splitter.secondComponent = if (node.isScrollbarNeeded()) ScrollPaneFactory.createScrollPane(panel, true) else panel
        selectedItem = node.title
      }
    }

    // Don't store when treeModel is disposing while closing the dialog
    if (treeModel.getRoot() != null) {
      getPropertyComponent().setValue(SELECTED_TREE_ITEM, selectedItem)
    }
  }

  private fun extractSandboxTreeNode(node: Any): SandboxTreeNodeBase? {
    return ((node as? DefaultMutableTreeNode)?.userObject as? FilteringTreeStructure.FilteringNode)?.delegate as? SandboxTreeNodeBase
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
