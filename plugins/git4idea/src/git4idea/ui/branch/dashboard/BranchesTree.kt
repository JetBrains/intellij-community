// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode.Companion.getColorManager
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode.Companion.getRepositoryIcon
import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.TransferableList
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.FixingLayoutMatcher
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.*
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.EditSourceOnDoubleClickHandler.isToggleEvent
import com.intellij.util.PlatformIcons
import com.intellij.util.ThreeState
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchPresentation
import com.intellij.vcs.branch.LinkedBranchDataImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsImplUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.GitBranchPopupActions.LocalBranchActions.constructIncomingOutgoingTooltip
import git4idea.ui.branch.dashboard.BranchesDashboardActions.BranchesTreeActionGroup
import icons.DvcsImplIcons
import org.jetbrains.annotations.NonNls
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreePath

internal class BranchesTreeComponent(project: Project) : DnDAwareTree() {

  var doubleClickHandler: (BranchTreeNode) -> Unit = {}
  var searchField: SearchTextField? = null

  init {
    putClientProperty(AUTO_SELECT_ON_MOUSE_PRESSED, false)
    setCellRenderer(BranchTreeCellRenderer(project))
    isRootVisible = false
    setShowsRootHandles(true)
    isOpaque = false
    isHorizontalAutoScrollingEnabled = false
    installDoubleClickHandler()
    SmartExpander.installOn(this)
    TreeHoverListener.DEFAULT.addTo(this)
    initDnD()
  }

  private inner class BranchTreeCellRenderer(project: Project) : ColoredTreeCellRenderer() {
    private val repositoryManager = GitRepositoryManager.getInstance(project)
    private val colorManager = getColorManager(project)
    private val branchManager = project.service<GitBranchManager>()

    private var incomingOutgoingIcon: NodeIcon? = null

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
      val isRepositoryNode = descriptor.type == NodeType.GROUP_REPOSITORY_NODE

      icon = when {
        isBranchNode && branchInfo != null && branchInfo.isCurrent && branchInfo.isFavorite -> DvcsImplIcons.CurrentBranchFavoriteLabel
        isBranchNode && branchInfo != null && branchInfo.isCurrent -> DvcsImplIcons.CurrentBranchLabel
        isBranchNode && branchInfo != null && branchInfo.isFavorite -> AllIcons.Nodes.Favorite
        isBranchNode -> AllIcons.Vcs.BranchNode
        isGroupNode -> PlatformIcons.FOLDER_ICON
        isRepositoryNode -> getRepositoryIcon(descriptor.repository!!, colorManager)
        else -> null
      }

      toolTipText =
        if (branchInfo != null && branchInfo.isLocal)
          BranchPresentation.getTooltip(getBranchesTooltipData(branchInfo.branchName, getSelectedRepositories(descriptor)))
        else null

      append(value.getTextRepresentation(), SimpleTextAttributes.REGULAR_ATTRIBUTES, true)

      val repositoryGrouping = branchManager.isGroupingEnabled(GroupingKey.GROUPING_BY_REPOSITORY)
      if (!repositoryGrouping && branchInfo != null && branchInfo.repositories.size < repositoryManager.repositories.size) {
        append(" (${DvcsUtil.getShortNames(branchInfo.repositories)})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }

      val incomingOutgoingState = branchInfo?.incomingOutgoingState
      incomingOutgoingIcon = incomingOutgoingState?.icon?.let { NodeIcon(it, preferredSize.width + tree.insets.left) }
      tree.toolTipText = incomingOutgoingState?.run { constructIncomingOutgoingTooltip(hasIncoming(), hasOutgoing()) }
    }

    override fun calcFocusedState() = super.calcFocusedState() || searchField?.textEditor?.hasFocus() ?: false

    private fun getBranchesTooltipData(branchName: String, repositories: Collection<GitRepository>): List<BranchData> {
      return repositories.map { repo ->
        val trackedBranchName = repo.branches.findLocalBranch(branchName)?.findTrackedBranch(repo)?.name
        val presentableRootName = VcsImplUtil.getShortVcsRootName(repo.project, repo.root)

        LinkedBranchDataImpl(presentableRootName, branchName, trackedBranchName)
      }
    }

    override fun paint(g: Graphics) {
      super.paint(g)
      incomingOutgoingIcon?.let { (icon, locationX) ->
        icon.paintIcon(this@BranchTreeCellRenderer, g, locationX, (size.height - icon.iconHeight) / 2)
      }
    }
  }

  private data class NodeIcon(val icon: Icon, val locationX: Int)

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

  fun getSelectedBranches(): List<BranchInfo> {
    return getSelectedNodes()
      .mapNotNull { it.getNodeDescriptor().branchInfo }
      .toList()
  }

  fun getSelectedNodes(): Sequence<BranchTreeNode> {
    val paths = selectionPaths ?: return emptySequence()
    return paths.asSequence()
      .map(TreePath::getLastPathComponent)
      .mapNotNull { it as? BranchTreeNode }
  }

  fun getSelectedRemotes(): Set<RemoteInfo> {
    val paths = selectionPaths ?: return emptySet()
    return paths.asSequence()
      .map(TreePath::getLastPathComponent)
      .mapNotNull { it as? BranchTreeNode }
      .filter {
        it.getNodeDescriptor().displayName != null &&
        it.getNodeDescriptor().type == NodeType.GROUP_NODE &&
        (it.getNodeDescriptor().parent?.type == NodeType.REMOTE_ROOT || it.getNodeDescriptor().parent?.repository != null)
      }
      .mapNotNull { with(it.getNodeDescriptor()) { RemoteInfo(displayName!!, parent?.repository) } }
      .toSet()
  }

  fun getSelectedRepositories(descriptor: BranchNodeDescriptor): List<GitRepository> {
    var parent = descriptor.parent

    while (parent != null) {
      val repository = parent.repository
      if (repository != null) return listOf(repository)

      parent = parent.parent
    }

    return descriptor.branchInfo?.repositories ?: emptyList()
  }

  fun getSelectedRepositories(branchInfo: BranchInfo): Set<GitRepository> {
    return getSelectedRepositories(branchInfo, selectionPaths)
  }

  companion object {
    internal fun getSelectedRepositories(branchInfo: BranchInfo, selectionPaths: Array<TreePath>?): Set<GitRepository> {
      val paths = selectionPaths ?: return emptySet()
      return paths.asSequence()
        .filter {
          val lastPathComponent = it.lastPathComponent
          lastPathComponent is BranchTreeNode && lastPathComponent.getNodeDescriptor().branchInfo == branchInfo
        }
        .mapNotNull { findNodeDescriptorInPath(it) { descriptor -> Objects.nonNull(descriptor.repository) } }
        .mapNotNull(BranchNodeDescriptor::repository)
        .toSet()
    }

    private fun findNodeDescriptorInPath(path: TreePath, condition: (BranchNodeDescriptor) -> Boolean): BranchNodeDescriptor? {
      var curPath: TreePath? = path
      while (curPath != null) {
        val node = curPath.lastPathComponent as? BranchTreeNode
        if (node != null && condition(node.getNodeDescriptor())) return node.getNodeDescriptor()
        curPath = curPath.parentPath
      }

      return null
    }
  }
}

internal class FilteringBranchesTree(
  val project: Project,
  val component: BranchesTreeComponent,
  private val uiController: BranchesDashboardController,
  rootNode: BranchTreeNode = BranchTreeNode(BranchNodeDescriptor(NodeType.ROOT)),
  place: @NonNls String,
  disposable: Disposable
) : FilteringTree<BranchTreeNode, BranchNodeDescriptor>(component, rootNode) {

  private val expandedPaths = HashSet<TreePath>()

  private val localBranchesNode = BranchTreeNode(BranchNodeDescriptor(NodeType.LOCAL_ROOT))
  private val remoteBranchesNode = BranchTreeNode(BranchNodeDescriptor(NodeType.REMOTE_ROOT))
  private val headBranchesNode = BranchTreeNode(BranchNodeDescriptor(NodeType.HEAD_NODE))
  private val branchFilter: (BranchInfo) -> Boolean =
    { branch -> !uiController.showOnlyMy || branch.isMy == ThreeState.YES }
  private val nodeDescriptorsModel = NodeDescriptorsModel(localBranchesNode.getNodeDescriptor(),
                                                          remoteBranchesNode.getNodeDescriptor())

  private var localNodeExist = false
  private var remoteNodeExist = false
  private val treeStateProvider = BranchesTreeStateProvider(this, disposable)

  private val treeStateHolder: BranchesTreeStateHolder get() = project.service()

  private val groupingConfig: MutableMap<GroupingKey, Boolean> =
    with(project.service<GitBranchManager>()) {
      hashMapOf(
        GroupingKey.GROUPING_BY_DIRECTORY to isGroupingEnabled(GroupingKey.GROUPING_BY_DIRECTORY),
        GroupingKey.GROUPING_BY_REPOSITORY to isGroupingEnabled(GroupingKey.GROUPING_BY_REPOSITORY)
      )
    }

  fun toggleGrouping(key: GroupingKey, state: Boolean) {
    groupingConfig[key] = state
    refreshTree()
  }

  fun isGroupingEnabled(key: GroupingKey) = groupingConfig[key] == true

  init {
    runInEdt {
      PopupHandler.installPopupMenu(component, BranchesTreeActionGroup(), place)
      setupTreeListeners()
    }
  }

  override fun createSpeedSearch(searchTextField: SearchTextField): SpeedSearchSupply =
    object : FilteringSpeedSearch(searchTextField) {

      private val customWordMatchers = hashSetOf<MinusculeMatcher>()

      override fun matchingFragments(text: String): Iterable<TextRange?>? {
        val allTextRanges = super.matchingFragments(text)
        if (customWordMatchers.isEmpty()) return allTextRanges

        val candidates = arrayListOf<List<TextRange>>()
        allTextRanges?.let { candidates.add(it.toList()) }
        for (wordMatcher in customWordMatchers) {
          wordMatcher.matchingFragments(text)?.let { candidates.add(it.toList()) }
        }
        return candidates.maxByOrNull { fragments ->
          fragments.sumOf { textRange -> textRange.endOffset - textRange.startOffset }
        }
      }

      override fun updatePattern(string: String?) {
        super.updatePattern(string)
        onUpdatePattern(string)
      }

      override fun onUpdatePattern(text: String?) {
        customWordMatchers.clear()
        customWordMatchers.addAll(buildCustomWordMatchers(text))
      }

      private fun buildCustomWordMatchers(text: String?): Set<MinusculeMatcher> {
        if (text == null) return emptySet()

        val wordMatchers = hashSetOf<MinusculeMatcher>()
        for (word in StringUtil.split(text, " ")) {
          val trimmedWord = word.trim() //otherwise Character.isSpaceChar would affect filtering
          wordMatchers.add(
            FixingLayoutMatcher("*$trimmedWord", NameUtil.MatchingCaseSensitivity.NONE, ""))
        }

        return wordMatchers
      }
    }

  override fun installSearchField(): SearchTextField {
    val searchField = super.installSearchField()
    component.searchField = searchField
    return searchField
  }

  private fun setupTreeListeners() {
    component.addTreeExpansionListener(object : TreeExpansionListener {
      override fun treeExpanded(event: TreeExpansionEvent) {
        expandedPaths.add(event.path)
        treeStateHolder.setStateProvider(treeStateProvider)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) {
        expandedPaths.remove(event.path)
        treeStateHolder.setStateProvider(treeStateProvider)
      }
    })
    component.addTreeSelectionListener { treeStateHolder.setStateProvider(treeStateProvider) }
  }

  fun getSelectedRepositories(branchInfo: BranchInfo): List<GitRepository> {
    val selectedRepositories = component.getSelectedRepositories(branchInfo)
    return if (selectedRepositories.isNotEmpty()) selectedRepositories.toList() else branchInfo.repositories
  }

  fun getSelectedBranches() = component.getSelectedBranches()

  fun getSelectedBranchFilters(): List<String> {
    return component.getSelectedNodes()
      .mapNotNull { with(it.getNodeDescriptor()) { if (type == NodeType.HEAD_NODE) VcsLogUtil.HEAD else branchInfo?.branchName } }
      .toList()
  }

  fun getSelectedRemotes() = component.getSelectedRemotes()

  fun getSelectedBranchNodes() = component.getSelectedNodes().map(BranchTreeNode::getNodeDescriptor).toSet()

  private fun restorePreviouslyExpandedPaths() {
    TreeUtil.restoreExpandedPaths(component, expandedPaths.toList())
  }

  override fun expandTreeOnSearchUpdateComplete(pattern: String?) {
    restorePreviouslyExpandedPaths()
  }

  override fun onSpeedSearchUpdateComplete(pattern: String?) {
    updateSpeedSearchBackground()
  }

  override fun useIdentityHashing(): Boolean = false

  private fun updateSpeedSearchBackground() {
    val speedSearch = searchModel.speedSearch as? SpeedSearch ?: return
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
      NodeType.HEAD_NODE -> headBranchesNode
      else -> BranchTreeNode(nodeDescriptor)
    }

  override fun getChildren(nodeDescriptor: BranchNodeDescriptor) =
    when (nodeDescriptor.type) {
      NodeType.ROOT -> getRootNodeDescriptors()
      NodeType.LOCAL_ROOT -> localBranchesNode.getNodeDescriptor().getDirectChildren()
      NodeType.REMOTE_ROOT -> remoteBranchesNode.getNodeDescriptor().getDirectChildren()
      NodeType.GROUP_NODE -> nodeDescriptor.getDirectChildren()
      NodeType.GROUP_REPOSITORY_NODE -> nodeDescriptor.getDirectChildren()
      else -> emptyList() //leaf branch node
    }

  private fun BranchNodeDescriptor.getDirectChildren() = nodeDescriptorsModel.getChildrenForParent(this)

  fun update(initial: Boolean) {
    val branchesReloaded = uiController.reloadBranches()
    runPreservingTreeState(initial) {
      searchModel.updateStructure()
    }
    if (branchesReloaded) {
      tree.revalidate()
      tree.repaint()
    }
  }

  private fun runPreservingTreeState(loadSaved: Boolean, runnable: () -> Unit) {
    if (Registry.`is`("git.branches.panel.persist.tree.state")) {
      val treeState = if (loadSaved) treeStateHolder.getInitialTreeState() else TreeState.createOn(tree, root)
      runnable()
      if (treeState != null) {
        treeState.applyTo(tree)
      }
      else {
        initDefaultTreeExpandState()
      }
    }
    else {
      runnable()
      if (loadSaved) {
        initDefaultTreeExpandState()
      }
    }
  }

  private fun initDefaultTreeExpandState() {
    // expanding lots of nodes is a slow operation (and result is not very useful)
    if (TreeUtil.hasManyNodes(tree, 30000)) {
      TreeUtil.collapseAll(tree, 1)
    }
    else {
      TreeUtil.expandAll(tree)
    }
  }

  fun refreshTree() {
    runPreservingTreeState(false) {
      tree.selectionModel.clearSelection()
      refreshNodeDescriptorsModel()
      searchModel.updateStructure()
    }
  }

  fun refreshNodeDescriptorsModel() {
    with(uiController) {
      nodeDescriptorsModel.clear()

      localNodeExist = localBranches.isNotEmpty()
      remoteNodeExist = remoteBranches.isNotEmpty()

      nodeDescriptorsModel.populateFrom((localBranches.asSequence() + remoteBranches.asSequence()).filter(branchFilter), groupingConfig)
    }
  }

  override fun getText(nodeDescriptor: BranchNodeDescriptor?) = nodeDescriptor?.branchInfo?.branchName ?: nodeDescriptor?.displayName

  private fun getRootNodeDescriptors() =
    mutableListOf<BranchNodeDescriptor>().apply {
      if (localNodeExist || remoteNodeExist) add(headBranchesNode.getNodeDescriptor())
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

@State(name = "BranchesTreeState", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)],
       reportStatistic = false, getStateRequiresEdt = true)
@Service(Service.Level.PROJECT)
internal class BranchesTreeStateHolder : PersistentStateComponent<TreeState> {
  private var treeStateProvider: BranchesTreeStateProvider? = null
  private var _treeState: TreeState? = null

  fun getInitialTreeState(): TreeState? = state

  override fun getState(): TreeState? {
    return treeStateProvider?.getState() ?: _treeState
  }

  override fun loadState(state: TreeState) {
    _treeState = state
  }

  fun setStateProvider(provider: BranchesTreeStateProvider) {
    treeStateProvider = provider
  }
}

internal class BranchesTreeStateProvider(tree: FilteringBranchesTree, disposable: Disposable) {
  private var tree: FilteringBranchesTree? = tree
  private var state: TreeState? = null

  init {
    Disposer.register(disposable) {
      persistTreeState()
      this.tree = null
    }
  }

  fun getState(): TreeState? {
    persistTreeState()
    return state
  }

  private fun persistTreeState() {
    if (Registry.`is`("git.branches.panel.persist.tree.state")) {
      tree?.let {
        state = TreeState.createOn(it.tree, it.root)
      }
    }
  }
}
