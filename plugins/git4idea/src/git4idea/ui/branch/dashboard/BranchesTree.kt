// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.TransferableList
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.ui.*
import com.intellij.util.ThreeState
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.tree.WideSelectionTreeUI
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
      if (branchInfo != null && descriptor.type == NodeType.BRANCH) {
        when {
          branchInfo.isCurrent -> {
            icon = if (branchInfo.isFavorite) DvcsImplIcons.CurrentBranchFavoriteLabel else DvcsImplIcons.CurrentBranchLabel
          }
          branchInfo.isFavorite -> {
            icon = AllIcons.Nodes.Favorite
          }
          else -> {
            icon = EmptyIcon.ICON_16
          }
        }
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
        val clickPath =
          (if (WideSelectionTreeUI.isWideSelection(this@BranchesTreeComponent))
            getClosestPathForLocation(e.x, e.y)
          else
            getPathForLocation(e.x, e.y))
          ?: return false
        val selectionPath = selectionPath
        if (selectionPath == null || clickPath != selectionPath) return false
        val node = (selectionPath.lastPathComponent as? BranchTreeNode) ?: return false
        if (model.isLeaf(node) || getToggleClickCount() != e.clickCount) {
          doubleClickHandler(node)
          return true
        }
        return false
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

  private val localBranchesDescriptors = mutableListOf<BranchNodeDescriptor>()
  private val remoteBranchesDescriptors = mutableListOf<BranchNodeDescriptor>()
  private val nodeDescriptorsToNodes = hashMapOf<BranchNodeDescriptor, BranchTreeNode>()

  private val baseNodeDescriptorsToNodes =
    mapOf(
      rootNode.getNodeDescriptor() to rootNode,
      localBranchesNode.getNodeDescriptor() to localBranchesNode,
      remoteBranchesNode.getNodeDescriptor() to remoteBranchesNode
    )

  init {
    runInEdt {
      PopupHandler.installPopupHandler(component, BranchesTreeActionGroup(project, this), "BranchesTreePopup", ActionManager.getInstance())
      setupTreeExpansionListener()
    }
  }

  override fun installSearchField(isOpaque: Boolean, textFieldBorder: Border?): SearchTextField {
    val searchField = super.installSearchField(isOpaque, textFieldBorder)
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

  override fun updateExpandedPathsOnSpeedSearchUpdateComplete() {
    restorePreviouslyExpandedPaths()
  }

  override fun getNodeClass() = BranchTreeNode::class.java

  override fun createNode(nodeDescriptor: BranchNodeDescriptor) = nodeDescriptorsToNodes[nodeDescriptor] ?: BranchTreeNode(nodeDescriptor)

  override fun getChildren(nodeDescriptor: BranchNodeDescriptor) =
    when (nodeDescriptor.type) {
      NodeType.ROOT -> getRootNodeDescriptors(localBranchesDescriptors.isNotEmpty(), remoteBranchesDescriptors.isNotEmpty())
      NodeType.LOCAL_ROOT -> localBranchesDescriptors.filterByMyBranches()
      NodeType.REMOTE_ROOT -> remoteBranchesDescriptors.filterByMyBranches()
      else -> mutableListOf() //leaf branch node
    }

  private fun Iterable<BranchNodeDescriptor>.filterByMyBranches() =
    filter { !uiController.showOnlyMy || it.branchInfo?.isMy == ThreeState.YES }

  override fun rebuildTree(initial: Boolean): Boolean {
    val rebuilded = buildTreeNodesIfNeeded()
    expandedPaths.addAll(TreeUtil.collectExpandedPaths(tree))
    searchModel.updateStructure()
    if (initial) {
      TreeUtil.expand(tree, 2)
    }
    else {
      restorePreviouslyExpandedPaths()
    }

    return rebuilded
  }

  fun refreshTree() {
    expandedPaths.addAll(TreeUtil.collectExpandedPaths(tree))
    val selectionPaths = tree.selectionModel.selectionPaths
    refreshTreeNodesFromModel()
    searchModel.updateStructure()
    restorePreviouslyExpandedPaths()
    tree.selectionModel.selectionPaths = selectionPaths
  }

  private fun buildTreeNodesIfNeeded(): Boolean {
    with(uiController) {
      val changed = checkForBranchesUpdate()
      if (!changed) return false

      refreshTreeNodesFromModel()

      return changed
    }
  }

  private fun refreshTreeNodesFromModel() {
    with(uiController) {
      nodeDescriptorsToNodes.clear()
      localBranchesDescriptors.clear()
      remoteBranchesDescriptors.clear()

      localBranchesDescriptors += localBranches.toNodeDescriptors()
      remoteBranchesDescriptors += remoteBranches.toNodeDescriptors()
      nodeDescriptorsToNodes += baseNodeDescriptorsToNodes
      nodeDescriptorsToNodes += localBranchesDescriptors.associateWith(::BranchTreeNode)
      nodeDescriptorsToNodes += remoteBranchesDescriptors.associateWith(::BranchTreeNode)
    }
  }

  override fun getText(nodeDescriptor: BranchNodeDescriptor?) = nodeDescriptor?.branchInfo?.branchName

  private fun getRootNodeDescriptors(localNodeExist: Boolean, remoteNodeExist: Boolean) =
    mutableListOf<BranchNodeDescriptor>().apply {
      if (localNodeExist) add(localBranchesNode.getNodeDescriptor())
      if (remoteNodeExist) add(remoteBranchesNode.getNodeDescriptor())
    }
}

internal val BRANCH_TREE_NODE_COMPARATOR = Comparator<BranchNodeDescriptor> { d1, d2 ->
  val b1 = d1.branchInfo
  val b2 = d2.branchInfo
  if (b1 == null || b2 == null) d1.type.compareTo(d2.type)
  else if (b1.isCurrent && !b2.isCurrent) -1
  else if (!b1.isCurrent && b2.isCurrent) 1
  else if (b1.isFavorite && !b2.isFavorite) -1
  else if (!b1.isFavorite && b2.isFavorite) 1
  else if (b1.isLocal && !b2.isLocal) -1
  else if (!b1.isLocal && b2.isLocal) 1
  else {
    b1.branchName.compareTo(b2.branchName)
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
