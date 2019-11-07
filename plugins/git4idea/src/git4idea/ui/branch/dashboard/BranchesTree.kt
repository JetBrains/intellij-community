// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.ide.dnd.TransferableList
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ThreeState
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.tree.WideSelectionTreeUI
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.dashboard.BranchesDashboardActions.BranchesTreeActionGroup
import icons.DvcsImplIcons
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.Transferable
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.TransferHandler
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreePath

internal class BranchesTreeComponent(project: Project) : DnDAwareTree(), DataProvider {

  var doubleClickHandler: (BranchTreeNode) -> Unit = {}
  var keyPressHandler: (BranchTreeNode) -> Unit = {}

  init {
    setCellRenderer(BranchTreeCellRenderer(project))
    isRootVisible = false
    setShowsRootHandles(true)
    isOpaque = false
    installDoubleClickHandler()
    installF2KeyPressHandler()
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
      if (branchInfo?.isCurrent == true) {
        icon = DvcsImplIcons.CurrentBranchLabel
      }
      append(value.getTextRepresentation(), SimpleTextAttributes.REGULAR_ATTRIBUTES, true)

      if (branchInfo != null && branchInfo.repositories.size < repositoryManager.repositories.size) {
        append(" (${DvcsUtil.getShortNames(branchInfo.repositories)})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }

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

  private fun installF2KeyPressHandler() {
    registerKeyboardAction({
                             val selectionPaths = selectionPaths ?: return@registerKeyboardAction
                             if (selectionPaths.size != 1) return@registerKeyboardAction
                             val node = selectionPaths.firstOrNull()?.lastPathComponent as? BranchTreeNode
                             if (node != null) {
                               keyPressHandler(node)
                             }
                           }, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), JComponent.WHEN_FOCUSED)
  }

  override fun getData(dataId: String): Any? {
    if (GIT_BRANCHES.`is`(dataId)) {
      return getSelectedBranches()
    }
    return null
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
                                     private val uiController: BranchesDashboardController)
  : FilteringTree<BranchTreeNode, BranchNodeDescriptor>(project, component, BranchTreeNodes.rootNode) {

  private val expandedPaths = SmartHashSet<TreePath>()

  private val localBranchesDescriptors = mutableListOf<BranchNodeDescriptor>()
  private val remoteBranchesDescriptors = mutableListOf<BranchNodeDescriptor>()
  private val nodeDescriptorsToNodes = hashMapOf<BranchNodeDescriptor, BranchTreeNode>()

  private val baseNodeDescriptorsToNodes =
    mapOf(
      BranchTreeNodes.rootNode.getNodeDescriptor() to BranchTreeNodes.rootNode,
      BranchTreeNodes.localBranchesNode.getNodeDescriptor() to BranchTreeNodes.localBranchesNode,
      BranchTreeNodes.remoteBranchesNode.getNodeDescriptor() to BranchTreeNodes.remoteBranchesNode
    )

  init {
    runInEdt {
      PopupHandler.installFollowingSelectionTreePopup(component, BranchesTreeActionGroup(project, this), "BranchesTreePopup",
                                                      ActionManager.getInstance())
      setupTreeExpansionListener()
    }
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
    searchModel.updateStructure()
    restorePreviouslyExpandedPaths()
  }

  private fun buildTreeNodesIfNeeded(): Boolean {
    with(uiController) {
      val changed = checkForBranchesUpdate()
      if (!changed) return false

      nodeDescriptorsToNodes.clear()
      localBranchesDescriptors.clear()
      remoteBranchesDescriptors.clear()

      localBranchesDescriptors += localBranches.toNodeDescriptors()
      remoteBranchesDescriptors += remoteBranches.toNodeDescriptors()
      nodeDescriptorsToNodes += baseNodeDescriptorsToNodes
      nodeDescriptorsToNodes += localBranchesDescriptors.associateWith(::BranchTreeNode)
      nodeDescriptorsToNodes += remoteBranchesDescriptors.associateWith(::BranchTreeNode)

      return changed
    }
  }

  override fun getText(nodeDescriptor: BranchNodeDescriptor?) = nodeDescriptor?.branchInfo?.branchName
}

internal val BRANCH_TREE_NODE_COMPARATOR = Comparator<BranchNodeDescriptor> { d1, d2 ->
  val b1 = d1.branchInfo
  val b2 = d2.branchInfo
  if (b1 == null || b2 == null) d1.type.compareTo(d2.type)
  else if (b1.isCurrent && !b2.isCurrent) -1
  else if (!b1.isCurrent && b2.isCurrent) 1
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
