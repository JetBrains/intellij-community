// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox

import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.internal.showSources
import com.intellij.internal.ui.sandbox.components.*
import com.intellij.internal.ui.sandbox.dsl.*
import com.intellij.internal.ui.sandbox.dsl.listCellRenderer.LcrComboBoxPanel
import com.intellij.internal.ui.sandbox.dsl.listCellRenderer.LcrListPanel
import com.intellij.internal.ui.sandbox.dsl.listCellRenderer.LcrOthersPanel
import com.intellij.internal.ui.sandbox.dsl.listCellRenderer.LcrSeparatorPanel
import com.intellij.internal.ui.sandbox.dsl.validation.CrossValidationPanel
import com.intellij.internal.ui.sandbox.dsl.validation.ValidationPanel
import com.intellij.internal.ui.sandbox.dsl.validation.ValidationRefactoringPanel
import com.intellij.internal.ui.sandbox.tests.accessibility.AccessibilityFailedInspectionsPanel
import com.intellij.internal.ui.sandbox.tests.components.JBTextAreaTestPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.breadcrumbs.Breadcrumbs
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.ui.dsl.builder.*
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
import java.awt.Font
import java.awt.event.KeyEvent
import java.util.function.Consumer
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

private const val SEARCH_UPDATE_DELAY = 300
private const val SELECTED_TREE_ITEM = "UISandboxDialog.selected.tree.item"
private const val TREE_ITEM_PATH_SEPARATOR = ">"

internal class UISandboxDialog(private val project: Project?) : DialogWrapper(project, null, true, IdeModalityType.MODELESS, true) {

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
      Group("ListCellRenderer", children = listOf(
        LcrListPanel(),
        LcrComboBoxPanel(),
        LcrSeparatorPanel(),
        LcrOthersPanel()
      )),
      Group("Validation", children = listOf(
        CrossValidationPanel(),
        ValidationPanel(),
        ValidationRefactoringPanel(),
      )),

      CellsWithSubPanelsPanel(),
      CheckBoxRadioButtonPanel(),
      CommentsPanel(),
      DeprecatedApiPanel(),
      GroupsPanel(),
      LabelsPanel(),
      LongTextsPanel(),
      OnChangePanel(),
      OthersPanel(),
      PlaceholderPanel(),
      ResizableRowsPanel(),
      SegmentedButtonPanel(),
      TextFieldsPanel(),
      TextMaxLinePanel(),
      VisibleEnabledPanel()
    )),

    Group("Tests", children = listOf(
      Group("Components", children = listOf(JBTextAreaTestPanel())),
      Group("Accessibility", children = listOf(
        AccessibilityFailedInspectionsPanel())
      )
    )
    )
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

  private var selectedNode: SandboxTreeNodeBase? = null

  private lateinit var placeholder: Placeholder

  private val breadcrumbs = object : Breadcrumbs() {
    override fun getFontStyle(crumb: Crumb): Int {
      return Font.BOLD
    }
  }

  private lateinit var viewSource: ActionLink

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

    val rightPanel = panel {
      panel {
        row {
          cell(breadcrumbs)
          viewSource = link("View source") {
            showSources()
          }.align(AlignX.RIGHT)
            .customize(UnscaledGaps.EMPTY)
            .component
        }
      }.customize(UnscaledGaps(top = 10, left = 10, right = 16))

      row {
        placeholder = placeholder()
          .align(Align.FILL)
      }.resizableRow()
    }

    val result = OnePixelSplitter(false, "UISandboxDialog.splitter.proportion", 0.2f).apply {
      firstComponent = leftPanel
      secondComponent = rightPanel
      minimumSize = JBDimension(400, 300)
      preferredSize = JBDimension(800, 600)
    }

    getPropertyComponent().getValue(SELECTED_TREE_ITEM)?.let {
      selectItem(it.split(TREE_ITEM_PATH_SEPARATOR))
    }
    return result
  }

  private fun selectItem(path: List<String>) {
    var currentNode = treeModel.root
    for (item in path) {
      val children = treeModel.getChildren(currentNode)
      val foundChild = children.find { extractSandboxTreeNode(it)?.title == item } ?: break
      currentNode = foundChild
    }

    if (currentNode != null) {
      val filteringNode = ((currentNode as? DefaultMutableTreeNode)?.userObject as? FilteringTreeStructure.FilteringNode) ?: return
      treeModel.select(filteringNode, tree, Consumer {})
    }
  }

  private fun onFilterUpdated() {
    activeFilterText = (searchTextField.text ?: "").trim()
  }

  private fun getPropertyComponent(): PropertiesComponent {
    return if (project == null) PropertiesComponent.getInstance() else PropertiesComponent.getInstance(project)
  }

  private fun onNodeSelected(node: SandboxTreeNodeBase?) {
    selectedNode = node

    when (node) {
      null -> {
        placeholder.component = emptyPanel
      }
      is SandboxTreeGroup -> {
        placeholder.component = createGroupContent(node).apply {
          prepareContent()
        }
      }
      is SandboxTreeLeaf -> {
        val panel = node.panelCache.value
        panel.prepareContent()
        placeholder.component = if (node.isScrollbarNeeded()) ScrollPaneFactory.createScrollPane(panel, true) else panel
      }
    }

    fillBreadcrumbs()
    viewSource.isEnabled = selectedNode is SandboxTreeLeaf

    // Don't store when treeModel is disposing while closing the dialog
    if (treeModel.getRoot() != null) {
      getPropertyComponent().setValue(SELECTED_TREE_ITEM, getNodePathString(selectedNode))
    }
  }

  private fun JComponent.prepareContent() {
    border = JBUI.Borders.empty(6, 16, 16, 16)
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
  }

  private fun createGroupContent(group: SandboxTreeGroup): JComponent {
    return panel {
      for (child in group.children) {
        row {
          link(child.title) {
            selectItem(getNodePath(child))
          }.customize(UnscaledGaps(bottom = 4))
        }
      }
    }
  }

  private fun getNodePath(node: SandboxTreeNodeBase?): List<String> {
    val result = mutableListOf<String>()
    var currentNode = node
    while (currentNode != null && currentNode.title != "") {
      result.add(currentNode.title)
      currentNode = currentNode.parent as SandboxTreeNodeBase?
    }

    if (result.isEmpty()) {
      return result
    }

    return result.reversed()
  }

  private fun fillBreadcrumbs() {
    val crumbs = getNodePath(selectedNode).map {
      Crumb.Impl(null, it, null, null)
    }
    breadcrumbs.setCrumbs(crumbs)
  }

  private fun getNodePathString(node: SandboxTreeNodeBase?): String {
    return getNodePath(node).joinToString(TREE_ITEM_PATH_SEPARATOR)
  }

  private fun extractSandboxTreeNode(node: Any): SandboxTreeNodeBase? {
    return ((node as? DefaultMutableTreeNode)?.userObject as? FilteringTreeStructure.FilteringNode)?.delegate as? SandboxTreeNodeBase
  }

  private fun showSources() {
    val leaf = selectedNode as? SandboxTreeLeaf ?: return
    val src = "src/${leaf.sandboxPanel::class.java.name.replace('.', '/')}.kt"
    showSources(project, src)
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

  private val children: Array<SandboxTreeNodeBase>

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

  override fun getChildren(): Array<SandboxTreeNodeBase> {
    return children
  }
}

private class SandboxTreeLeaf(parent: SimpleNode?, disposable: Disposable, val sandboxPanel: UISandboxPanel) :
  SandboxTreeNodeBase(parent) {

  override val title: String = sandboxPanel.title

  val panelCache = lazy {
    sandboxPanel.createContent(disposable)
  }

  fun isScrollbarNeeded(): Boolean {
    return sandboxPanel.isScrollbarNeeded
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
