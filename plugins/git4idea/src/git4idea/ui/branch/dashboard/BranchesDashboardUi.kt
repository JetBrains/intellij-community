// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.branch.GroupingKey
import com.intellij.icons.AllIcons
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionPlaces.VCS_LOG_BRANCHES_PLACE
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEMS
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.StatusText.getDefaultEmptyText
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy
import com.intellij.vcs.log.VcsLogBranchLikeFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.impl.VcsLogManager.BaseVcsLogUiFactory
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToBranch
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.with
import com.intellij.vcs.log.visible.filters.without
import com.intellij.vcs.ui.ProgressStripe
import git4idea.i18n.GitBundle.message
import git4idea.i18n.GitBundleExtensions.messagePointer
import git4idea.repo.GitRepository
import git4idea.ui.branch.dashboard.BranchesDashboardActions.DeleteBranchAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.FetchAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ShowBranchDiffAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ShowMyBranchesAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ToggleFavoriteAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.UpdateSelectedBranchAction
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.datatransfer.DataFlavor
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.TransferHandler
import javax.swing.event.TreeSelectionListener

internal class BranchesDashboardUi(project: Project, private val logUi: BranchesVcsLogUi) : Disposable {
  private val uiController = BranchesDashboardController(project, this)

  private val tree = BranchesTreeComponent(project).apply {
    accessibleContext.accessibleName = message("git.log.branches.tree.accessible.name")
  }
  private val filteringTree = FilteringBranchesTree(project, tree, uiController, place = VCS_LOG_BRANCHES_PLACE, disposable = this)
  private val branchViewSplitter = BranchViewSplitter()
  private val branchesTreePanel = BranchesTreePanel().withBorder(createBorder(JBColor.border(), SideBorder.LEFT))
  private val branchesScrollPane = ScrollPaneFactory.createScrollPane(filteringTree.component, true)
  private val branchesProgressStripe = ProgressStripe(branchesScrollPane, this)
  private val branchesTreeWithLogPanel = simplePanel()
  private val mainPanel = simplePanel()
  private val mainComponent = UiDataProvider.wrapComponent(mainPanel, uiController)
  private val branchesSearchFieldPanel = simplePanel()
  private val branchesSearchField = filteringTree.installSearchField().apply {
    textEditor.border = JBUI.Borders.emptyLeft(5)
    accessibleContext.accessibleName = message("git.log.branches.search.field.accessible.name")
    // fixme: this needs to be dynamic
    accessibleContext.accessibleDescription = message("git.log.branches.search.field.accessible.description",
                                                      KeymapUtil.getFirstKeyboardShortcutText("Vcs.Log.FocusTextFilter"))
  }
  private val branchesSearchFieldWrapper = NonOpaquePanel(branchesSearchField).apply(UIUtil::setNotOpaqueRecursively)

  private lateinit var branchesPanelExpandableController: ExpandablePanelController

  private val treeSelectionListener = TreeSelectionListener {
    if (!branchesPanelExpandableController.isExpanded()) return@TreeSelectionListener

    val ui = logUi

    val properties = ui.properties

    if (properties[CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY]) {
      updateLogBranchFilter()
    }
    else if (properties[NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY]) {
      navigateToSelectedBranch(false)
    }
  }

  internal fun updateLogBranchFilter() {
    val ui = logUi
    val selectedFilters = filteringTree.getSelectedBranchFilters()
    val oldFilters = ui.filterUi.filters
    val newFilters = if (selectedFilters.isNotEmpty()) {
      oldFilters.without(VcsLogBranchLikeFilter::class.java).with(VcsLogFilterObject.fromBranches(selectedFilters))
    }
    else {
      oldFilters.without(VcsLogBranchLikeFilter::class.java)
    }
    ui.filterUi.filters = newFilters
  }

  internal fun navigateToSelectedBranch(focus: Boolean) {
    val selectedReference = filteringTree.getSelectedBranchFilters().singleOrNull() ?: return

    logUi.jumpToBranch(selectedReference, false, focus)
  }

  internal fun toggleGrouping(key: GroupingKey, state: Boolean) {
    filteringTree.toggleGrouping(key, state)
  }

  internal fun isGroupingEnabled(key: GroupingKey) = filteringTree.isGroupingEnabled(key)

  internal fun getSelectedRepositories(branchInfo: BranchInfo): List<GitRepository> {
    return filteringTree.getSelectedRepositories(branchInfo)
  }

  internal fun getSelectedRemotes(): Set<RemoteInfo> {
    return filteringTree.getSelectedRemotes()
  }

  internal fun getRootsToFilter(): Set<VirtualFile> {
    val roots = logUi.logData.roots.toSet()
    if (roots.size == 1) return roots

    return VcsLogUtil.getAllVisibleRoots(roots, logUi.filterUi.filters)
  }

  private val BRANCHES_UI_FOCUS_TRAVERSAL_POLICY = object : ComponentsListFocusTraversalPolicy() {
    override fun getOrderedComponents(): List<Component> = listOf(filteringTree.component, logUi.table,
                                                                  logUi.changesBrowser.preferredFocusedComponent,
                                                                  logUi.filterUi.textFilterComponent.focusedComponent)
  }

  private val showBranches get() = logUi.properties[SHOW_GIT_BRANCHES_LOG_PROPERTY]

  init {
    initMainUi()
    installLogUi()
    toggleBranchesPanelVisibility()
  }

  @RequiresEdt
  private fun installLogUi() {
    uiController.registerDataPackListener(logUi.logData)
    uiController.registerLogUiPropertiesListener(logUi.properties)
    uiController.registerLogUiFilterListener(logUi.filterUi)
    branchesSearchFieldWrapper.setVerticalSizeReferent(logUi.toolbar)
    branchViewSplitter.secondComponent = logUi.mainLogComponent
    mainPanel.add(branchesTreeWithLogPanel)
    filteringTree.component.addTreeSelectionListener(treeSelectionListener)
  }

  @RequiresEdt
  private fun disposeBranchesUi() {
    branchViewSplitter.secondComponent.removeAll()
    uiController.removeDataPackListener(logUi.logData)
    uiController.removeLogUiPropertiesListener(logUi.properties)
    filteringTree.component.removeTreeSelectionListener(treeSelectionListener)
  }

  private fun initMainUi() {
    ShowBranchDiffAction().registerCustomShortcutSet(branchesTreeWithLogPanel, null)
    DeleteBranchAction().registerCustomShortcutSet(branchesTreeWithLogPanel, null)

    createFocusFilterFieldAction(branchesSearchFieldWrapper)
    installPasteAction(filteringTree)

    val toolbar = ActionManager.getInstance().createActionToolbar("Git.Log.Branches", createToolbarGroup(), false)
    toolbar.setTargetComponent(branchesTreePanel)

    val branchesButton = ExpandStripeButton(messagePointer("action.Git.Log.Show.Branches.text"), AllIcons.Actions.ArrowExpand)
      .apply {
        border = createBorder(JBColor.border(), SideBorder.RIGHT)
        addActionListener {
          if (logUi.properties.exists(SHOW_GIT_BRANCHES_LOG_PROPERTY)) {
            logUi.properties[SHOW_GIT_BRANCHES_LOG_PROPERTY] = true
          }
        }
      }
    branchesSearchFieldPanel.withBackground(UIUtil.getListBackground()).withBorder(createBorder(JBColor.border(), SideBorder.BOTTOM))
    branchesSearchFieldPanel.addToCenter(branchesSearchFieldWrapper)
    branchesTreePanel.addToTop(branchesSearchFieldPanel).addToCenter(branchesProgressStripe)
    branchesPanelExpandableController = ExpandablePanelController(toolbar.component, branchesButton, branchesTreePanel)
    branchViewSplitter.firstComponent = branchesTreePanel
    branchesTreeWithLogPanel.addToLeft(branchesPanelExpandableController.expandControlPanel).addToCenter(branchViewSplitter)
    mainPanel.isFocusCycleRoot = true
    mainPanel.focusTraversalPolicy = BRANCHES_UI_FOCUS_TRAVERSAL_POLICY
  }

  private fun createToolbarGroup(): DefaultActionGroup {
    val commonActionsManager = CommonActionsManager.getInstance()
    val actionManager = ActionManager.getInstance()

    val diffAction = ShowBranchDiffAction()
    val deleteAction = DeleteBranchAction()
    val toggleFavoriteAction = ToggleFavoriteAction()
    val fetchAction = FetchAction(this)
    val showMyBranchesAction = ShowMyBranchesAction(uiController)
    val newBranchAction = actionManager.getAction("Git.New.Branch.In.Log")
    val updateSelectedAction = UpdateSelectedBranchAction()
    val defaultTreeExpander = DefaultTreeExpander(filteringTree.component)
    val expandAllAction = commonActionsManager.createExpandAllHeaderAction(defaultTreeExpander, branchesTreePanel)
    val collapseAllAction = commonActionsManager.createCollapseAllHeaderAction(defaultTreeExpander, branchesTreePanel)
    val hideBranchesAction = actionManager.getAction("Git.Log.Hide.Branches")
    val settings = actionManager.getAction("Git.Log.Branches.Settings")

    val group = DefaultActionGroup()
    group.add(hideBranchesAction)
    group.add(Separator())
    group.add(newBranchAction)
    group.add(updateSelectedAction)
    group.add(deleteAction)
    group.add(diffAction)
    group.add(showMyBranchesAction)
    group.add(fetchAction)
    group.add(toggleFavoriteAction)
    group.add(actionManager.getAction("Git.Log.Branches.Navigate.Log.To.Selected.Branch"))
    group.add(Separator())
    group.add(settings)
    group.add(actionManager.getAction("Git.Log.Branches.Grouping.Settings"))
    group.add(expandAllAction)
    group.add(collapseAllAction)
    return group
  }

  fun toggleBranchesPanelVisibility() {
    branchesPanelExpandableController.toggleExpand(showBranches)
    updateBranchesTree(true)
  }

  private fun createFocusFilterFieldAction(searchField: Component) {
    DumbAwareAction.create { e ->
      val project = e.getData(CommonDataKeys.PROJECT) ?: return@create
      if (IdeFocusManager.getInstance(project).getFocusedDescendantFor(filteringTree.component) != null) {
        IdeFocusManager.getInstance(project).requestFocus(searchField, true)
      }
      else {
        IdeFocusManager.getInstance(project).requestFocus(filteringTree.component, true)
      }
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("Find"), branchesTreePanel)
  }

  private fun installPasteAction(tree: FilteringBranchesTree) {
    tree.component.actionMap.put(TransferHandler.getPasteAction().getValue(Action.NAME), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        val speedSearch = tree.searchModel.speedSearch as? SpeedSearch ?: return
        val pasteContent =
          CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
            // the same filtering logic as in javax.swing.text.PlainDocument.insertString (e.g. DnD to search field)
            ?.let { StringUtil.convertLineSeparators(it, " ") }
        speedSearch.type(pasteContent)
        speedSearch.update()
      }
    })
  }

  inner class BranchesTreePanel : BorderLayoutPanel(), UiDataProvider, QuickActionProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      sink[SELECTED_ITEMS] = filteringTree.component.selectionPaths
      sink[GIT_BRANCHES] = filteringTree.getSelectedBranches()
      sink[GIT_BRANCH_FILTERS] = filteringTree.getSelectedBranchFilters()
      sink[GIT_BRANCH_REMOTES] = filteringTree.getSelectedRemotes()
      sink[GIT_BRANCH_DESCRIPTORS] = filteringTree.getSelectedBranchNodes()
      sink[BRANCHES_UI_CONTROLLER] = uiController
      sink[VcsLogInternalDataKeys.LOG_UI_PROPERTIES] = logUi.properties
      sink[QuickActionProvider.KEY] = this
    }

    override fun getActions(originalProvider: Boolean): List<AnAction> {
      return createToolbarGroup().getChildren(ActionManager.getInstance()).asList()
    }

    override fun getComponent(): JComponent = filteringTree.component

    override fun isCycleRoot(): Boolean = true
  }

  fun getMainComponent(): JComponent {
    return mainComponent
  }

  fun updateBranchesTree(initial: Boolean) {
    if (showBranches) {
      filteringTree.update(initial)
    }
  }

  fun refreshTree() {
    filteringTree.refreshTree()
  }

  fun refreshTreeModel() {
    filteringTree.refreshNodeDescriptorsModel()
  }

  fun startLoadingBranches() {
    filteringTree.component.emptyText.text = message("action.Git.Loading.Branches.progress")
    branchesTreePanel.isEnabled = false
    branchesProgressStripe.startLoading()
  }

  fun stopLoadingBranches() {
    filteringTree.component.emptyText.text = getDefaultEmptyText()
    branchesTreePanel.isEnabled = true
    branchesProgressStripe.stopLoading()
  }

  override fun dispose() {
    disposeBranchesUi()
  }
}

internal class BranchesVcsLogUiFactory(logManager: VcsLogManager, logId: String, filters: VcsLogFilterCollection? = null)
  : BaseVcsLogUiFactory<BranchesVcsLogUi>(logId, filters, logManager.uiProperties, logManager.colorManager) {
  override fun createVcsLogUiImpl(logId: String,
                                  logData: VcsLogData,
                                  properties: MainVcsLogUiProperties,
                                  colorManager: VcsLogColorManager,
                                  refresher: VisiblePackRefresherImpl,
                                  filters: VcsLogFilterCollection?) =
    BranchesVcsLogUi(logId, logData, colorManager, properties, refresher, filters)
}

internal class BranchesVcsLogUi(id: String, logData: VcsLogData, colorManager: VcsLogColorManager,
                                uiProperties: MainVcsLogUiProperties, refresher: VisiblePackRefresher,
                                initialFilters: VcsLogFilterCollection?) :
  VcsLogUiImpl(id, logData, colorManager, uiProperties, refresher, initialFilters) {

  private val branchesUi =
    BranchesDashboardUi(logData.project, this)
      .also { branchesUi -> Disposer.register(this, branchesUi) }

  internal val mainLogComponent: JComponent
    get() = mainFrame

  internal val changesBrowser: ChangesBrowserBase
    get() = mainFrame.changesBrowser

  override fun createMainFrame(logData: VcsLogData, uiProperties: MainVcsLogUiProperties,
                               filterUi: VcsLogFilterUiEx, isEditorDiffPreview: Boolean) =
    MainFrame(logData, this, uiProperties, filterUi, myColorManager, isEditorDiffPreview, this)
      .apply {
        isFocusCycleRoot = false
        focusTraversalPolicy = null //new focus traversal policy will be configured include branches tree
      }

  override fun getMainComponent() = branchesUi.getMainComponent()
}

@ApiStatus.Internal
val SHOW_GIT_BRANCHES_LOG_PROPERTY =
  object : VcsLogProjectTabsProperties.CustomBooleanTabProperty("Show.Git.Branches") {
    override fun defaultValue(logId: String) = logId == VcsLogContentProvider.MAIN_LOG_ID
  }

@ApiStatus.Internal
val CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY =
  object : VcsLogApplicationSettings.CustomBooleanProperty("Change.Log.Filter.on.Branch.Selection") {
    override fun defaultValue() = false
  }

@ApiStatus.Internal
val NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY =
  object : VcsLogApplicationSettings.CustomBooleanProperty("Navigate.Log.To.Branch.on.Branch.Selection") {
    override fun defaultValue() = false
  }

private class BranchViewSplitter(first: JComponent? = null, second: JComponent? = null)
  : OnePixelSplitter(false, "vcs.branch.view.splitter.proportion", 0.3f) {
  init {
    firstComponent = first
    secondComponent = second
  }
}
