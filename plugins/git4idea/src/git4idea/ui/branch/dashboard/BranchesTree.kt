// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.branch.isGroupingEnabled
import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.TransferableList
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.ui.*
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.EditSourceOnDoubleClickHandler.isToggleEvent
import com.intellij.util.PlatformIcons
import com.intellij.util.ThreeState
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.dashboard.BranchesDashboardActions.BranchesTreeActionGroup
import icons.DvcsImplIcons
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.border.Border
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreePath

internal class BranchesTreeComponent(project: Project) : DnDAwareTree() {

  var doubleClickHandler: (BranchTreeNode) -> Unit = {}
  var searchField: SearchTextField? = null

  init {
    setCellRenderer(BranchTreeCellRenderer(project))
    isRootVisible = false
    setShowsRootHandles(true)
    isOpaque = false
    installDoubleClickHandler()
    SmartExpander.installOn(this)
    initDnD()
  }

  private inner class BranchTreeCellRenderer(project: Project) : ColoredTreeCellRenderer() {
    private val repositoryManager = GitRepositoryManager.getInstance(project)

    override fun customizeCellRenderer(tree: JTree,
                                       value: Any?,
                                       selected: Boolean,
                                       expanded: Boolean,
                                       leaf: Boolean,
                                       row: Int,
                                       hasFocus: Boolean) {
      if (value !is BranchTreeNode) return
      val descriptor = value.getNodeDescriptor()

      val branchInfo = descriptor.branchInfo
      val isBranchNode = descriptor.type == NodeType.BRANCH
      val isGroupNode = descriptor.type == NodeType.GROUP_NODE

      icon = when {
        isBranchNode && branchInfo != null && branchInfo.isCurrent && branchInfo.isFavorite -> {
          DvcsImplIcons.CurrentBranchFavoriteLabel
        }
        isBranchNode && branchInfo != null && branchInfo.isCurrent -> {
          DvcsImplIcons.CurrentBranchLabel
        }
        isBranchNode && branchInfo != null && branchInfo.isFavorite -> {
          AllIcons.Nodes.Favorite
        }
        isBranchNode -> {
          AllIcons.Vcs.BranchNode
        }
        isGroupNode -> {
          PlatformIcons.FOLDER_ICON
        }
        else -> null
      }

      append(value.getTextRepresentation(), SimpleTextAttributes.REGULAR_ATTRIBUTES, true)

      if (branchInfo != null && branchInfo.repositories.size < repositoryManager.repositories.size) {
        append(" (${DvcsUtil.getShortNames(branchInfo.repositories)})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }

    override fun calcFocusedState() = super.calcFocusedState() || searchField?.textEditor?.hasFocus() ?: false
  }

  override fun hasFocus() = super.hasFocus() || searchField?.textEditor?.hasFocus() ?: false

  private fun installDoubleClickHandler() {
    object : DoubleClickListener() {
      override fun onDoubleClick(e: MouseEvent): Boolean {
        val clickPath = getClosestPathForLocation(e.x, e.y) ?: return false
        val selectionPath = selectionPath
        if (selectionPath == null || clickPath != selectionPath) return false
        val node = (selectionPath.lastPathComponent as? BranchTreeNode) ?: return false
        if (isToggleEvent(this@BranchesTreeComponent, e)) return false

        doubleClickHandler(node)
        return true
      }
    }.installOn(this)
  }

  private fun initDnD() {
    if (!GraphicsEnvironment.isHeadless()) {
      transferHandler = BRANCH_TREE_TRANSFER_HANDLER
    }
  }

  fun getSelectedBranches(): Set<BranchInfo> {
    val paths = selectionPaths ?: return emptySet()
    return paths.asSequence()
      .map(TreePath::getLastPathComponent)
      .mapNotNull { it as? BranchTreeNode }
      .mapNotNull { it.getNodeDescriptor().branchInfo }
      .toSet()
  }
}

internal class FilteringBranchesTree(project: Project,
                                     val component: BranchesTreeComponent,
                                     private val uiController: BranchesDashboardController,
                                     rootNode: BranchTreeNode = BranchTreeNode(BranchNodeDescriptor(NodeType.ROOT)))
  : FilteringTree<BranchTreeNode, BranchNodeDescriptor>(project, component, rootNode) {

  private val expandedPaths = SmartHashSet<TreePath>()

  private val localBranchesNode = BranchTreeNode(BranchNodeDescriptor(NodeType.LOCAL_ROOT))
  private val remoteBranchesNode = BranchTreeNode(BranchNodeDescriptor(NodeType.REMOTE_ROOT))
  private val branchFilter: (BranchInfo) -> Boolean =
    { branch -> !uiController.showOnlyMy || branch.isMy == ThreeState.YES }
  private val nodeDescriptorsModel = NodeDescriptorsModel(localBranchesNode.getNodeDescriptor(),
                                                          remoteBranchesNode.getNodeDescriptor())

  private var localNodeExist = false
  private var remoteNodeExist = false

  private var useDirectoryGrouping = GitVcsSettings.getInstance(project).branchSettings.isGroupingEnabled(GroupingKey.GROUPING_BY_DIRECTORY)

  fun toggleDirectoryGrouping(state: Boolean) {
    useDirectoryGrouping = state
    refreshTree()
  }

  init {
    runInEdt {
      PopupHandler.installPopupHandler(component, BranchesTreeActionGroup(project, this), "BranchesTreePopup", ActionManager.getInstance())
      setupTreeExpansionListener()
      project.service<BranchesTreeStateHolder>().setTree(this)
    }
  }

  override fun installSearchField(textFieldBorder: Border?): SearchTextField {
    val searchField = super.installSearchField(textFieldBorder)
    component.searchField = searchField
    return searchField
  }

  private fun setupTreeExpansionListener() {
    component.addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        expandedPaths.add(event.path)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) {
        expandedPaths.remove(event.path)
      }
    })
  }

  fun getSelectedBranchNames() = getSelectedBranches().map(BranchInfo::branchName)

  fun getSelectedBranches() = component.getSelectedBranches()

  private fun restorePreviouslyExpandedPaths() {
    TreeUtil.restoreExpandedPaths(component, expandedPaths.toList())
  }

  override fun onSpeedSearchUpdateComplete() {
    restorePreviouslyExpandedPaths()
    updateSpeedSearchBackground()
  }

  private fun updateSpeedSearchBackground() {
    val speedSearch = searchModel.speedSearchSupply as? SpeedSearch ?: return
    val textEditor = component.searchField?.textEditor ?: return
    if (isEmptyModel()) {
      textEditor.isOpaque = true
      speedSearch.noHits()
    }
    else {
      textEditor.isOpaque = false
      textEditor.background = UIUtil.getTextFieldBackground()
    }
  }

  private fun isEmptyModel() = searchModel.isLeaf(localBranchesNode) && searchModel.isLeaf(remoteBranchesNode)

  override fun getNodeClass() = BranchTreeNode::class.java

  override fun createNode(nodeDescriptor: BranchNodeDescriptor) =
    when (nodeDescriptor.type) {
      NodeType.LOCAL_ROOT -> localBranchesNode
      NodeType.REMOTE_ROOT -> remoteBranchesNode
      else -> BranchTreeNode(nodeDescriptor)
    }

  override fun getChildren(nodeDescriptor: BranchNodeDescriptor) =
    when (nodeDescriptor.type) {
      NodeType.ROOT -> getRootNodeDescriptors()
      NodeType.LOCAL_ROOT -> localBranchesNode.getNodeDescriptor().getDirectChildren()
      NodeType.REMOTE_ROOT -> remoteBranchesNode.getNodeDescriptor().getDirectChildren()
      NodeType.GROUP_NODE -> nodeDescriptor.getDirectChildren()
      else -> emptyList() //leaf branch node
    }

  private fun BranchNodeDescriptor.getDirectChildren() = nodeDescriptorsModel.getChildrenForParent(this)

  override fun rebuildTree(initial: Boolean): Boolean {
    val rebuilded = buildTreeNodesIfNeeded()
    val treeState = project.service<BranchesTreeStateHolder>()
    if (!initial) {
      treeState.createNewState()
    }
    searchModel.updateStructure()
    if (initial) {
      treeState.applyStateToTreeOrExpandAll()
    }
    else {
      treeState.applyStateToTree()
    }

    return rebuilded
  }

  fun refreshTree() {
    val treeState = project.service<BranchesTreeStateHolder>()
    treeState.createNewState()
    refreshNodeDescriptorsModel()
    searchModel.updateStructure()
    treeState.applyStateToTree()
  }

  private fun buildTreeNodesIfNeeded(): Boolean {
    with(uiController) {
      val changed = checkForBranchesUpdate()
      if (!changed) return false

      refreshNodeDescriptorsModel()

      return changed
    }
  }

  private fun refreshNodeDescriptorsModel() {
    with(uiController) {
      nodeDescriptorsModel.clear()

      localNodeExist = localBranches.isNotEmpty()
      remoteNodeExist = remoteBranches.isNotEmpty()

      nodeDescriptorsModel.populateFrom((localBranches.asSequence() + remoteBranches.asSequence()).filter(branchFilter), useDirectoryGrouping)
    }
  }

  override fun getText(nodeDescriptor: BranchNodeDescriptor?) = nodeDescriptor?.branchInfo?.branchName ?: nodeDescriptor?.displayName

  private fun getRootNodeDescriptors() =
    mutableListOf<BranchNodeDescriptor>().apply {
      if (localNodeExist) add(localBranchesNode.getNodeDescriptor())
      if (remoteNodeExist) add(remoteBranchesNode.getNodeDescriptor())
    }
}

private val BRANCH_TREE_TRANSFER_HANDLER = object : TransferHandler() {
  override fun createTransferable(tree: JComponent): Transferable? {
    if (tree is BranchesTreeComponent) {
      val branches = tree.getSelectedBranches()
      if (branches.isEmpty()) return null

      return object : TransferableList<BranchInfo>(branches.toList()) {
        override fun toString(branch: BranchInfo) = branch.toString()
      }
    }
    return null
  }

  override fun getSourceActions(c: JComponent) = COPY_OR_MOVE
}

@State(name = "BranchesTreeState", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], reportStatistic = false)
internal class BranchesTreeStateHolder : PersistentStateComponent<TreeState> {
  private lateinit var branchesTree: FilteringBranchesTree
  private lateinit var treeState: TreeState

  override fun getState(): TreeState? {
    createNewState()
    if (::treeState.isInitialized) {
      return treeState
    }
    return null
  }

  override fun loadState(state: TreeState) {
    treeState = state
  }

  fun createNewState() {
    if (::branchesTree.isInitialized) {
      treeState = TreeState.createOn(branchesTree.tree, branchesTree.root)
    }
  }

  fun applyStateToTree(ifNoStatePresent: () -> Unit = {}) {
    if (!::branchesTree.isInitialized) return

    if (::treeState.isInitialized) {
      treeState.applyTo(branchesTree.tree)
    }
    else {
      ifNoStatePresent()
    }
  }

  fun applyStateToTreeOrExpandAll() = applyStateToTree { TreeUtil.expandAll(branchesTree.tree) }

  fun setTree(tree: FilteringBranchesTree) {
    branchesTree = tree
  }
}
