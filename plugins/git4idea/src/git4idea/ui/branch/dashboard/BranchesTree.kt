// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.ide.dnd.TransferableList
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.util.BackgroundTaskUtil
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
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ThreeState
import com.intellij.util.containers.FList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchPresentation
import com.intellij.vcs.branch.LinkedBranchDataImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsImplUtil
import git4idea.branch.calcTooltip
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.GitBranchesMatcherWrapper
import git4idea.ui.branch.GitBranchesTreeIconProvider
import git4idea.ui.branch.dashboard.BranchesDashboardActions.BranchesTreeActionGroup
import git4idea.ui.branch.tree.createIncomingLabel
import git4idea.ui.branch.tree.createOutgoingLabel
import git4idea.ui.branch.tree.updateIncomingCommitLabel
import git4idea.ui.branch.tree.updateOutgoingCommitLabel
import org.jetbrains.annotations.NonNls
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.Transferable
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.TreePath

internal class BranchesTreeComponent(project: Project) : DnDAwareTree() {

  var searchField: SearchTextField? = null

  init {
    putClientProperty(AUTO_SELECT_ON_MOUSE_PRESSED, false)
    setCellRenderer(BranchTreeCellRenderer(project))
    isRootVisible = false
    setShowsRootHandles(true)
    isOpaque = false
    isHorizontalAutoScrollingEnabled = false
    SmartExpander.installOn(this)
    TreeHoverListener.DEFAULT.addTo(this)
    initDnD()
  }

  private inner class BranchTreeCellRenderer(project: Project) : ColoredTreeCellRenderer() {
    private val repositoryManager = GitRepositoryManager.getInstance(project)
    private val branchManager = project.service<GitBranchManager>()

    private val iconProvider = GitBranchesTreeIconProvider(project)

    private val incomingLabel = createIncomingLabel()
    private val outgoingLabel = createOutgoingLabel()

    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      if (value !is BranchTreeNode) return
      val descriptor = value.getNodeDescriptor()

      icon = when(descriptor) {
        is BranchNodeDescriptor.Ref -> iconProvider.forRef(
          descriptor.refInfo.ref,
          current = descriptor.refInfo.isCurrent,
          favorite = descriptor.refInfo.isFavorite,
          selected = selected
        )
        is BranchNodeDescriptor.Group, is BranchNodeDescriptor.RemoteGroup -> iconProvider.forGroup()
        is BranchNodeDescriptor.Repository -> iconProvider.forRepository(descriptor.repository)
        else -> null
      }

      append(value.getNodeDescriptor().displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES, true)

      val refInfo = (descriptor as? BranchNodeDescriptor.Ref)?.refInfo
      if (refInfo != null) {
        val repositoryGrouping = branchManager.isGroupingEnabled(GroupingKey.GROUPING_BY_REPOSITORY)
        if (!repositoryGrouping && refInfo.repositories.size < repositoryManager.repositories.size) {
          append(" (${DvcsUtil.getShortNames(refInfo.repositories)})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
      }
      
      if (refInfo is BranchInfo) {
        toolTipText =
          if (refInfo.isLocalBranch) BranchPresentation.getTooltip(getBranchesTooltipData(refInfo.branchName, getSelectedRepositories(value)))
          else null

        val incomingOutgoingState = refInfo.incomingOutgoingState
        updateIncomingCommitLabel(incomingLabel, incomingOutgoingState)
        updateOutgoingCommitLabel(outgoingLabel, incomingOutgoingState)

        val fontMetrics = incomingLabel.getFontMetrics(incomingLabel.font)
        incomingLabel.size = Dimension(fontMetrics.stringWidth(incomingLabel.text) + JBUI.scale(1) + incomingLabel.icon.iconWidth, fontMetrics.height)
        outgoingLabel.size = Dimension(fontMetrics.stringWidth(outgoingLabel.text) + JBUI.scale(1) + outgoingLabel.icon.iconWidth, fontMetrics.height)
        tree.toolTipText = incomingOutgoingState.calcTooltip()
      }
      else {
        incomingLabel.isVisible = false
        outgoingLabel.isVisible = false
        tree.toolTipText = null
      }
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

      var xOffset = preferredSize.width + tree.insets.left
      var yShifted = false
      if (incomingLabel.isVisible) {
        val incIcon = incomingLabel.icon
        g.translate(xOffset, (size.height - incIcon.iconHeight) / 2)
        yShifted = true

        incomingLabel.paint(g)
        xOffset = incomingLabel.width + JBUI.scale(3)
      }

      if (outgoingLabel.isVisible) {
        val outIcon = outgoingLabel.icon
        g.translate(xOffset, if (yShifted) 0 else (size.height - outIcon.iconHeight) / 2)
        outgoingLabel.paint(g)
      }
    }
  }

  override fun hasFocus() = super.hasFocus() || searchField?.textEditor?.hasFocus() ?: false

  private fun initDnD() {
    if (!GraphicsEnvironment.isHeadless()) {
      transferHandler = BRANCH_TREE_TRANSFER_HANDLER
    }
  }

  fun getSelectedBranches(): List<BranchInfo> {
    return getSelectedNodes()
      .mapNotNull { (it.getNodeDescriptor() as? BranchNodeDescriptor.Branch)?.branchInfo }
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
      .mapNotNull {
        val descriptor = it.getNodeDescriptor()
        val parentDescriptor = it.parent?.getNodeDescriptor()
        if (descriptor is BranchNodeDescriptor.RemoteGroup) {
          RemoteInfo(descriptor.remote.name, (parentDescriptor as? BranchNodeDescriptor.Repository)?.repository)
        } else null
      }
      .toSet()
  }

  fun getSelectedRepositories(node: BranchTreeNode): List<GitRepository> {
    var parent: BranchTreeNode? = node

    while (parent != null) {
      val descriptor = parent.getNodeDescriptor()
      if (descriptor is BranchNodeDescriptor.Repository) return listOf(descriptor.repository)
      parent = parent.parent
    }

    val nodeDescriptor = node.getNodeDescriptor()
    return (nodeDescriptor as? BranchNodeDescriptor.Branch)?.branchInfo?.repositories ?: emptyList()
  }


  fun getSelectedRepositories(branchInfo: BranchInfo): Set<GitRepository> {
    return getSelectedRepositories(branchInfo, selectionPaths)
  }

  companion object {
    internal fun getSelectedBranches(selectionPaths: Array<TreePath>?): List<BranchInfo> {
      val paths = selectionPaths ?: return emptyList()
      return paths.asSequence()
        .map(TreePath::getLastPathComponent)
        .mapNotNull { (it as? BranchTreeNode)?.getNodeDescriptor() }
        .filterIsInstance<BranchNodeDescriptor.Branch>()
        .map { it.branchInfo }
        .toList()
    }

    internal fun getSelectedRepositories(branchInfo: BranchInfo, selectionPaths: Array<TreePath>?): Set<GitRepository> {
      val paths = selectionPaths ?: return emptySet()
      return paths.asSequence()
        .filter {
          val lastPathComponent = it.lastPathComponent
          val branchNode = (lastPathComponent as? BranchTreeNode)?.getNodeDescriptor() as? BranchNodeDescriptor.Branch
          branchNode?.branchInfo == branchInfo
        }
        .mapNotNull {
          val repoNode = findNodeDescriptorInPath(it) { descriptor -> descriptor is BranchNodeDescriptor.Repository }
          (repoNode as? BranchNodeDescriptor.Repository)?.repository
        }
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

internal abstract class FilteringBranchesTreeBase(
  tree: Tree,
  protected val project: Project,
) : FilteringTree<BranchTreeNode, BranchNodeDescriptor>(tree, BranchTreeNode(BranchNodeDescriptor.Root())) {
  private val nodeDescriptorsModel = NodeDescriptorsModel(root.getNodeDescriptor() as BranchNodeDescriptor.Root, project)

  protected abstract val groupingConfig: Map<GroupingKey, Boolean>

  final override fun getNodeClass() = BranchTreeNode::class.java

  public final override fun getText(nodeDescriptor: BranchNodeDescriptor?) =
    when (nodeDescriptor) {
      is BranchNodeDescriptor.Ref -> nodeDescriptor.refInfo.refName
      is BranchNodeDescriptor.Repository -> nodeDescriptor.displayName
      is BranchNodeDescriptor.RemoteGroup -> nodeDescriptor.remote.name
      is BranchNodeDescriptor.Group -> nodeDescriptor.displayName
      else -> null // Note that nodes with null text are always matched while filtering the tree
    }

  override fun createNode(nodeDescriptor: BranchNodeDescriptor) = BranchTreeNode(nodeDescriptor)

  override fun getChildren(nodeDescriptor: BranchNodeDescriptor) = nodeDescriptor.children

  final override fun createSpeedSearch(searchTextField: SearchTextField): SpeedSearchSupply =
    BranchesFilteringSpeedSearch(this, searchTextField)

  fun isEmptyModel() = root.children().asSequence().all {
    searchModel.isLeaf(it)
  }

  internal fun refreshNodeDescriptorsModel(refs: RefsCollection, showOnlyMy: Boolean) {
    nodeDescriptorsModel.rebuildFrom(
      refs,
      if (showOnlyMy) { ref -> (ref as? BranchInfo)?.isMy == ThreeState.YES } else { _ -> true },
      groupingConfig,
    )
  }
}

internal class FilteringBranchesTree(
  project: Project,
  val component: BranchesTreeComponent,
  private val uiController: BranchesDashboardController,
  place: @NonNls String,
  private val disposable: Disposable
) : FilteringBranchesTreeBase(component, project) {

  private val expandedPaths = HashSet<TreePath>()

  private val treeStateProvider = BranchesTreeStateProvider(this, disposable)

  private val treeStateHolder: BranchesTreeStateHolder get() =
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposable, Supplier { project.service() })

  override val groupingConfig: MutableMap<GroupingKey, Boolean> =
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
      .mapNotNull {
        when (val descriptor = it.getNodeDescriptor()) {
          is BranchNodeDescriptor.Branch -> descriptor.branchInfo.branchName
          BranchNodeDescriptor.Head -> VcsLogUtil.HEAD
          else -> null
        }
      }.toList()
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
    refreshNodeDescriptorsModel(uiController.refs, uiController.showOnlyMy, )
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

private class BranchesFilteringSpeedSearch(private val tree: FilteringBranchesTreeBase, searchTextField: SearchTextField):
  FilteringSpeedSearch<BranchTreeNode, BranchNodeDescriptor>(tree, searchTextField) {
  private var matcher = BranchesTreeMatcher(searchTextField.text)
  private var bestMatch: BestMatch? = null

  override fun checkMatching(node: BranchTreeNode): FilteringTree.Matching =
    if (node.getNodeDescriptor() is BranchNodeDescriptor.Group) FilteringTree.Matching.NONE
    else super.checkMatching(node)

  override fun onMatchingChecked(userObject: BranchNodeDescriptor, matchingFragments: Iterable<TextRange>?, result: FilteringTree.Matching) {
    if (result == FilteringTree.Matching.NONE) return
    val text = tree.getText(userObject) ?: return
    val singleMatch = matchingFragments?.singleOrNull() ?: return

    val matchingDegree = matcher.matchingDegree(text, valueStartCaseMatch = false, fragments = FList.singleton(singleMatch))
    if (matchingDegree > (bestMatch?.matchingDegree ?: 0)) {
      val node = tree.searchModel.getNode(userObject)
      bestMatch = BestMatch(matchingDegree, node)
    }
  }

  override fun getMatcher(): MinusculeMatcher = matcher

  override fun updatePattern(string: String?) {
    super.updatePattern(string)
    onUpdatePattern(string)
  }

  override fun updateSelection() {
    val bestMatch = bestMatch
    if (bestMatch == null) {
      super.updateSelection()
    }
    else {
      val selectionText = tree.getText(selection?.getNodeDescriptor())
      val selectionMatchingDegree = if (selectionText != null) matcher.matchingDegree(selectionText) else Int.MIN_VALUE
      if (selectionMatchingDegree < bestMatch.matchingDegree) {
        select(bestMatch.node)
      }
    }

    if (!enteredPrefix.isNullOrBlank()) {
      scrollToSelected()
    }
  }

  private fun scrollToSelected() {
    val innerTree = tree.tree
    innerTree.selectionPath?.let { TreeUtil.scrollToVisible(innerTree, it, false) }
  }

  override fun onUpdatePattern(text: String?) {
    matcher = BranchesTreeMatcher(text)
    bestMatch = null
  }
}

private class BranchesTreeMatcher(private val rawPattern: String?) : MinusculeMatcher() {
  private val matchers: List<MinusculeMatcher> = if (rawPattern.isNullOrBlank()) {
    listOf(createMatcher(""))
  }
  else {
    StringUtil.split(rawPattern, " ").map { word ->
      val trimmedWord = word.trim() //otherwise Character.isSpaceChar would affect filtering
      createMatcher(trimmedWord)
    }
  }

  override fun getPattern(): String = rawPattern.orEmpty()

  override fun matchingFragments(name: String): FList<TextRange>? {
    val candidates = matchers.mapNotNull { matcher ->
      matcher.matchingFragments(name)
    }
    val fragments = candidates.maxByOrNull { fragments ->
      fragments.sumOf { textRange -> textRange.endOffset - textRange.startOffset }
    }
    return fragments
  }

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int =
    matchers.singleOrNull()?.matchingDegree(name, valueStartCaseMatch, fragments)
    ?: multipleMatchersMatchingDegree(fragments)

  private fun multipleMatchersMatchingDegree(fragments: FList<out TextRange>?) =
    if (fragments?.isNotEmpty() == true) PARTIAL_MATCH_DEGREE
    else NO_MATCH_DEGREE

  companion object {
    const val NO_MATCH_DEGREE = 0
    const val PARTIAL_MATCH_DEGREE = 1

    private fun createMatcher(word: String) =
      GitBranchesMatcherWrapper(FixingLayoutMatcher("*$word", NameUtil.MatchingCaseSensitivity.NONE, ""))
  }
}

private data class BestMatch(val matchingDegree: Int, val node: BranchTreeNode)
