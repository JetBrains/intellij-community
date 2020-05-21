// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.icons.AllIcons
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DataManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.StatusText.getDefaultEmptyText
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy
import com.intellij.vcs.log.VcsLogBranchLikeFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsLogManager.BaseVcsLogUiFactory
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties.MAIN_LOG_ID
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.ui.frame.*
import com.intellij.vcs.log.util.VcsLogUiUtil.isDiffPreviewInEditor
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.with
import com.intellij.vcs.log.visible.filters.without
import git4idea.i18n.GitBundle.message
import git4idea.i18n.GitBundleExtensions.messagePointer
import git4idea.ui.branch.dashboard.BranchesDashboardActions.DeleteBranchAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.FetchAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.GroupByDirectoryAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.NewBranchAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ShowBranchDiffAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ShowMyBranchesAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ToggleFavoriteAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.UpdateSelectedBranchAction
import org.jetbrains.annotations.CalledInAwt
import java.awt.Component
import javax.swing.JComponent
import javax.swing.event.TreeSelectionListener

internal class BranchesDashboardUi(project: Project, private val logUi: BranchesVcsLogUi) : Disposable {
  private val uiController = BranchesDashboardController(project, this)

  private val tree = FilteringBranchesTree(project, BranchesTreeComponent(project), uiController)
  private val branchViewSplitter = BranchViewSplitter()
  private val branchesTreePanel = BranchesTreePanel().withBorder(createBorder(JBColor.border(), SideBorder.LEFT))
  private val branchesScrollPane = ScrollPaneFactory.createScrollPane(tree.component, true)
  private val branchesProgressStripe = ProgressStripe(branchesScrollPane, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
  private val branchesTreeWithLogPanel = simplePanel()
  private val mainPanel = simplePanel().apply { DataManager.registerDataProvider(this, uiController) }
  private val branchesSearchFieldPanel = simplePanel()
  private val branchesSearchField = NonOpaquePanel(tree.installSearchField(JBUI.Borders.emptyLeft(5))).apply(UIUtil::setNotOpaqueRecursively)

  private lateinit var branchesPanelExpandableController: ExpandablePanelController

  private val treeSelectionListener = TreeSelectionListener {
    if (!branchesPanelExpandableController.isExpanded()) return@TreeSelectionListener

    val ui = logUi
    val branchNames = tree.getSelectedBranchNames()
    val oldFilters = ui.filterUi.filters
    val newFilters = if (branchNames.isNotEmpty()) {
      oldFilters.without(VcsLogBranchLikeFilter::class.java).with(VcsLogFilterObject.fromBranches(branchNames))
    } else {
      oldFilters.without(VcsLogBranchLikeFilter::class.java)
    }
    ui.filterUi.filters = newFilters
  }

  private val BRANCHES_UI_FOCUS_TRAVERSAL_POLICY = object : ComponentsListFocusTraversalPolicy() {
    override fun getOrderedComponents(): List<Component> = listOf(tree.component, logUi.table,
                                                                  logUi.changesBrowser.preferredFocusedComponent,
                                                                  logUi.filterUi.textFilterComponent.textEditor)
  }

  private val showBranches get() = logUi.properties.get(SHOW_GIT_BRANCHES_LOG_PROPERTY)

  init {
    initMainUi()
    installLogUi()
    toggleBranchesPanelVisibility()
  }

  @CalledInAwt
  private fun installLogUi() {
    uiController.registerDataPackListener(logUi.logData)
    uiController.registerLogUiPropertiesListener(logUi.properties)
    branchesSearchField.setVerticalSizeReferent(logUi.toolbar)
    branchViewSplitter.secondComponent = logUi.mainLogComponent
    val isDiffPreviewInEditor = isDiffPreviewInEditor()
    val diffPreview = logUi.createDiffPreview(isDiffPreviewInEditor)
    if (isDiffPreviewInEditor) {
      mainPanel.add(branchesTreeWithLogPanel)
    }
    else {
      mainPanel.add(DiffPreviewSplitter(diffPreview, logUi.properties, branchesTreeWithLogPanel).mainComponent)
    }
    tree.component.addTreeSelectionListener(treeSelectionListener)
  }

  @CalledInAwt
  private fun disposeBranchesUi() {
    branchViewSplitter.secondComponent.removeAll()
    uiController.removeDataPackListener(logUi.logData)
    uiController.removeLogUiPropertiesListener(logUi.properties)
    tree.component.removeTreeSelectionListener(treeSelectionListener)
  }

  private fun initMainUi() {
    val diffAction = ShowBranchDiffAction()
    diffAction.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("Diff.ShowDiff"), branchesTreeWithLogPanel)

    val deleteAction = DeleteBranchAction()
    val shortcuts = KeymapUtil.getActiveKeymapShortcuts("SafeDelete").shortcuts + KeymapUtil.getActiveKeymapShortcuts(
      "EditorDeleteToLineStart").shortcuts
    deleteAction.registerCustomShortcutSet(CustomShortcutSet(*shortcuts), branchesTreeWithLogPanel)

    createFocusFilterFieldAction(branchesSearchField)

    val groupByDirectoryAction = GroupByDirectoryAction(tree)
    val toggleFavoriteAction = ToggleFavoriteAction()
    val fetchAction = FetchAction(this)
    val showMyBranchesAction = ShowMyBranchesAction(uiController)
    val newBranchAction = NewBranchAction()
    val updateSelectedAction = UpdateSelectedBranchAction()
    val defaultTreeExpander = DefaultTreeExpander(tree.component)
    val commonActionsManager = CommonActionsManager.getInstance()
    val expandAllAction = commonActionsManager.createExpandAllHeaderAction(defaultTreeExpander, tree.component)
    val collapseAllAction = commonActionsManager.createCollapseAllHeaderAction(defaultTreeExpander, tree.component)
    val hideBranchesAction = ActionManager.getInstance().getAction("Git.Log.Hide.Branches")

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
    group.add(Separator())
    group.add(groupByDirectoryAction)
    group.add(expandAllAction)
    group.add(collapseAllAction)

    val toolbar = ActionManager.getInstance().createActionToolbar("Git.Log.Branches", group, false)
    toolbar.setTargetComponent(branchesTreePanel)

    val branchesButton = ExpandStripeButton(messagePointer("action.Git.Log.Show.Branches.text"), AllIcons.Actions.ArrowExpand)
      .apply {
        border = createBorder(JBColor.border(), SideBorder.RIGHT)
        addActionListener {
          if (logUi.properties.exists(SHOW_GIT_BRANCHES_LOG_PROPERTY)) {
            logUi.properties.set(SHOW_GIT_BRANCHES_LOG_PROPERTY, true)
          }
        }
      }
    branchesSearchFieldPanel.withBackground(UIUtil.getListBackground()).withBorder(createBorder(JBColor.border(), SideBorder.BOTTOM))
    branchesSearchFieldPanel.addToCenter(branchesSearchField)
    branchesTreePanel.addToTop(branchesSearchFieldPanel).addToCenter(branchesProgressStripe)
    branchesPanelExpandableController = ExpandablePanelController(toolbar.component, branchesButton, branchesTreePanel)
    branchViewSplitter.firstComponent = branchesTreePanel
    branchesTreeWithLogPanel.addToLeft(branchesPanelExpandableController.expandControlPanel).addToCenter(branchViewSplitter)
    mainPanel.isFocusCycleRoot = true
    mainPanel.focusTraversalPolicy = BRANCHES_UI_FOCUS_TRAVERSAL_POLICY
    startLoadingBranches()
  }

  fun toggleBranchesPanelVisibility() {
    branchesPanelExpandableController.toggleExpand(showBranches)
    updateBranchesTree(true)
  }

  private fun createFocusFilterFieldAction(searchField: Component) {
    DumbAwareAction.create { e ->
      val project = e.getRequiredData(CommonDataKeys.PROJECT)
      if (IdeFocusManager.getInstance(project).getFocusedDescendantFor(tree.component) != null) {
        IdeFocusManager.getInstance(project).requestFocus(searchField, true)
      }
      else {
        IdeFocusManager.getInstance(project).requestFocus(tree.component, true)
      }
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("Find"), branchesTreePanel)
  }

  inner class BranchesTreePanel : BorderLayoutPanel(), DataProvider {
    override fun getData(dataId: String): Any? {
      if (GIT_BRANCHES.`is`(dataId)) {
        return tree.getSelectedBranches()
      }
      else if (VcsLogInternalDataKeys.LOG_UI_PROPERTIES.`is`(dataId)) {
        return logUi.properties
      }
      return null
    }
  }

  fun getMainComponent(): JComponent {
    return mainPanel
  }

  fun updateBranchesTree(initial: Boolean) {
    if (showBranches) {
      tree.update(initial)
    }
  }

  fun refreshTree() {
    tree.refreshTree()
  }

  fun startLoadingBranches() {
    tree.component.emptyText.text = message("action.Git.Loading.Branches.progress")
    branchesTreePanel.isEnabled = false
    branchesProgressStripe.startLoading()
  }

  fun stopLoadingBranches() {
    tree.component.emptyText.text = getDefaultEmptyText()
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
                                  colorManager: VcsLogColorManagerImpl,
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

  override fun createMainFrame(logData: VcsLogData, uiProperties: MainVcsLogUiProperties, filterUi: VcsLogFilterUiEx) =
    MainFrame(logData, this, uiProperties, filterUi, false, this)
      .apply {
        isFocusCycleRoot = false
        focusTraversalPolicy = null //new focus traversal policy will be configured include branches tree
        if (isDiffPreviewInEditor()) {
          VcsLogEditorDiffPreview(myProject, uiProperties, this)
        }
      }

  override fun getMainComponent() = branchesUi.getMainComponent()

  fun createDiffPreview(isInEditor: Boolean): VcsLogChangeProcessor {
    return mainFrame.createDiffPreview(isInEditor, mainFrame.changesBrowser)
  }
}

internal val SHOW_GIT_BRANCHES_LOG_PROPERTY =
  object : VcsLogProjectTabsProperties.CustomBooleanTabProperty("Show.Git.Branches") {
    override fun defaultValue(logId: String) = logId == MAIN_LOG_ID
  }

private class BranchViewSplitter(first: JComponent? = null, second: JComponent? = null)
  : OnePixelSplitter(false, "vcs.branch.view.splitter.proportion", 0.3f) {
  init {
    firstComponent = first
    secondComponent = second
  }
}

private class DiffPreviewSplitter(diffPreview: VcsLogChangeProcessor, uiProperties: VcsLogUiProperties, mainComponent: JComponent)
  : FrameDiffPreview<VcsLogChangeProcessor>(diffPreview, uiProperties, mainComponent,
                                            "vcs.branch.view.diff.splitter.proportion",
                                            uiProperties[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT], 0.3f) {
  override fun updatePreview(state: Boolean) {
    previewDiff.updatePreview(state)
  }
}
