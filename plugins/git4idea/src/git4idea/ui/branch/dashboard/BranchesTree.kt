// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.ide.dnd.TransferableList
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
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
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.FList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchPresentation
import com.intellij.vcs.branch.LinkedBranchDataImpl
import com.intellij.vcs.git.shared.branch.calcTooltip
import com.intellij.vcs.git.shared.ui.GitBranchesTreeIconProvider
import com.intellij.vcsUtil.VcsImplUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.GitBranchesMatcherWrapper
import git4idea.ui.branch.dashboard.BranchesDashboardActions.BranchesTreeActionGroup
import git4idea.ui.branch.popup.createIncomingLabel
import git4idea.ui.branch.popup.createOutgoingLabel
import git4idea.ui.branch.popup.updateIncomingCommitLabel
import git4idea.ui.branch.popup.updateOutgoingCommitLabel
import git4idea.ui.branch.tree.GitBranchesTreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
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
        is BranchNodeDescriptor.Ref -> GitBranchesTreeIconProvider.forRef(
          descriptor.refInfo.ref,
          current = descriptor.refInfo.isCurrent,
          favorite = descriptor.refInfo.isFavorite,
          selected = selected
        )
        is BranchNodeDescriptor.Group, is BranchNodeDescriptor.RemoteGroup -> GitBranchesTreeIconProvider.forGroup()
        is BranchNodeDescriptor.Repository ->
          GitBranchesTreeIconProvider.forRepository(descriptor.repository.project, descriptor.repository.rpcId)
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
          if (refInfo.isLocalBranch) BranchPresentation.getTooltip(getBranchesTooltipData(refInfo.branchName, BranchesTreeSelection.getSelectedRepositories(value)))
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

  fun getSelection(): BranchesTreeSelection = BranchesTreeSelection(selectionPaths)

  private fun initDnD() {
    if (!GraphicsEnvironment.isHeadless()) {
      transferHandler = BRANCH_TREE_TRANSFER_HANDLER
    }
  }
}

internal abstract class FilteringBranchesTreeBase(val model: BranchesTreeModel, tree: Tree)
  : FilteringTree<BranchTreeNode, BranchNodeDescriptor>(tree, BranchTreeNode(model.root)) {

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
}

internal class FilteringBranchesTree(
  private val project: Project,
  model: BranchesTreeModel,
  val component: BranchesTreeComponent,
  place: @NonNls String,
  private val disposable: Disposable,
) : FilteringBranchesTreeBase(model, component) {

  init {
    UiNotifyConnector.installOn(tree, object : Activatable {
      private val listener = object : BranchesTreeModel.Listener {
        override fun onTreeChange() {
          updateTree()
        }
      }

      override fun showNotify() {
        updateTree()
        model.addListener(listener)
      }

      override fun hideNotify() {
        model.removeListener(listener)
      }

      private fun updateTree() {
        runPreservingTreeState(!initialUpdateDone) {
          searchModel.updateStructure()
        }
        initialUpdateDone = true
      }
    })
  }

  private var initialUpdateDone = false

  private val treeStateProvider = BranchesTreeStateProvider(this, disposable)

  private val treeStateHolder: BranchesTreeStateHolder get() =
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposable, Supplier { project.service() })

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
        treeStateHolder.setStateProvider(treeStateProvider)
      }

      override fun treeCollapsed(event: TreeExpansionEvent) {
        treeStateHolder.setStateProvider(treeStateProvider)
      }
    })
    component.addTreeSelectionListener { treeStateHolder.setStateProvider(treeStateProvider) }
  }

  fun update(initial: Boolean, repaint: Boolean) {
    runPreservingTreeState(initial) {
      searchModel.updateStructure()
    }
    if (repaint) {
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
}

private val BRANCH_TREE_TRANSFER_HANDLER = object : TransferHandler() {
  override fun createTransferable(tree: JComponent): Transferable? {
    if (tree is BranchesTreeComponent) {
      val branches = tree.getSelection().selectedBranches
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

@OptIn(FlowPreview::class)
private class BranchesFilteringSpeedSearch(
  private val tree: FilteringBranchesTreeBase,
  private val searchTextField: SearchTextField,
) : FilteringSpeedSearch<BranchTreeNode, BranchNodeDescriptor>(tree, searchTextField) {
  // null is the initial state - actual value is usually a non-null string
  private val filterPattern = MutableStateFlow<String?>(null)

  private var bestMatch: BestMatch? = null

  init {
    tree.tree.launchOnShow("Branches Tree Filterer") {
      // need EDT because of RA in TreeUtil.promiseVisit
      withContext(Dispatchers.EDT) {
        filterPattern.filterNotNull().debounce(GitBranchesTreeUtil.FILTER_DEBOUNCE_MS).collect(::refilter)
      }
    }
  }

  override fun checkMatching(node: BranchTreeNode): FilteringTree.Matching =
    if (node.getNodeDescriptor() is BranchNodeDescriptor.Group) FilteringTree.Matching.NONE
    else super.checkMatching(node)

  override fun onMatchingChecked(userObject: BranchNodeDescriptor, matchingFragments: Iterable<TextRange>?, result: FilteringTree.Matching) {
    val matcher = matcher ?: return
    if (result == FilteringTree.Matching.NONE) return
    val text = tree.getText(userObject) ?: return
    val singleMatch = matchingFragments?.singleOrNull() ?: return

    val matchingDegree = matcher.matchingDegree(text, false, FList.singleton(singleMatch))
    if (matchingDegree > (bestMatch?.matchingDegree ?: 0)) {
      val node = tree.searchModel.getNode(userObject)
      bestMatch = BestMatch(matchingDegree, node)
    }
  }

  override fun createNewMatcher(searchText: String?): MinusculeMatcher = BranchesTreeMatcher(searchText)

  override fun getMatcher(): MinusculeMatcher? = super.getMatcher() as MinusculeMatcher?

  override fun onSearchPatternUpdated(pattern: String?) {
    filterPattern.tryEmit(pattern.orEmpty())
  }

  override fun refilter(pattern: String?) {
    bestMatch = null
    super.refilter(pattern)
    updateSpeedSearchBackground()
  }

  private fun updateSpeedSearchBackground() {
    val textEditor = searchTextField.textEditor ?: return
    if (tree.isEmptyModel()) {
      textEditor.isOpaque = true
      noHits()
    }
    else {
      textEditor.isOpaque = false
      textEditor.background = UIUtil.getTextFieldBackground()
    }
  }

  override fun updateSelection() {
    val matcher = matcher
    val bestMatch = bestMatch
    if (matcher == null || bestMatch == null) {
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
