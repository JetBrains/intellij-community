// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

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
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.StatusText.getDefaultEmptyText
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
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
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.ui.frame.FrameDiffPreview
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.ui.frame.ProgressStripe
import com.intellij.vcs.log.ui.frame.VcsLogChangeProcessor
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
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
  private val branchesTreeWithToolbarPanel = simplePanel()
  private val branchesScrollPane = ScrollPaneFactory.createScrollPane(tree.component, true)
  private val branchesProgressStripe = ProgressStripe(branchesScrollPane, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
  private val branchesTreeWithLogPanel = simplePanel()
  private val mainPanel = simplePanel().apply { DataManager.registerDataProvider(this, uiController) }
  private val branchesSearchFieldPanel = simplePanel()
  private val branchesSearchField = Wrapper(tree.installSearchField(false, JBUI.Borders.emptyLeft(5)))

  private val treeSelectionListener = TreeSelectionListener {
    if (!branchesTreeWithToolbarPanel.isVisible) return@TreeSelectionListener

    val ui = logUi
    val branchNames = tree.getSelectedBranchNames()
    ui.filterUi.setFilter(if (branchNames.isNotEmpty()) VcsLogFilterObject.fromBranches(branchNames) else null)
  }

  init {
    initMainUi()
    installLogUi()
    updateBranchesTree(true)
  }

  @CalledInAwt
  private fun installLogUi() {
    uiController.registerDataPackListener(logUi.logData)
    uiController.registerLogUiPropertiesListener(logUi.properties)
    branchesSearchField.setVerticalSizeReferent(logUi.toolbar)
    branchViewSplitter.secondComponent = logUi.mainFrame
    val diffPreview = logUi.createDiffPreview()
    mainPanel.add(DiffPreviewSplitter(diffPreview, logUi.properties, branchesTreeWithLogPanel).mainComponent)
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

    val group = DefaultActionGroup()
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

    val toolbar = ActionManager.getInstance().createActionToolbar("Git.Cleanup.Branches", group, false)
    toolbar.setTargetComponent(branchesTreePanel)

    branchesTreeWithToolbarPanel.addToLeft(toolbar.component)
    branchesSearchFieldPanel.withBackground(UIUtil.getListBackground()).withBorder(createBorder(JBColor.border(), SideBorder.BOTTOM))
    branchesSearchFieldPanel.addToCenter(branchesSearchField)
    branchesTreePanel.addToTop(branchesSearchFieldPanel).addToCenter(branchesProgressStripe)
    branchesTreeWithToolbarPanel.addToCenter(branchesTreePanel)
    branchViewSplitter.firstComponent = branchesTreeWithToolbarPanel
    branchesTreeWithLogPanel.addToCenter(branchViewSplitter)
    startLoadingBranches()
    toggleBranchesPanelVisibility()
  }

  fun toggleBranchesPanelVisibility() {
    branchesTreeWithToolbarPanel.isVisible = logUi.properties.get(SHOW_GIT_BRANCHES_LOG_PROPERTY)
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
      return null
    }
  }

  fun getMainComponent(): JComponent {
    return mainPanel
  }

  fun updateBranchesTree(initial: Boolean) {
    tree.update(initial)
  }

  fun refreshTree() {
    tree.refreshTree()
  }

  fun startLoadingBranches() {
    tree.component.emptyText.text = "Loading branches..."
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

  override fun createMainFrame(logData: VcsLogData, uiProperties: MainVcsLogUiProperties, filterUi: VcsLogFilterUiEx): MainFrame {
    return MainFrame(logData, this, uiProperties, filterUi, false)
  }

  override fun getMainComponent() = branchesUi.getMainComponent()

  fun createDiffPreview(): VcsLogChangeProcessor {
    return mainFrame.createDiffPreview(false, mainFrame.changesBrowser)
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
                                            "vcs.branch.view.diff.splitter.proportion", true, 0.3f) {
  override fun updatePreview(state: Boolean) {
    previewDiff.updatePreview(state)
  }
}
