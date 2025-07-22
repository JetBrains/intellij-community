// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy
import com.intellij.vcs.log.VcsLogBranchLikeFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRootFilter
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.roots
import com.intellij.vcs.log.impl.*
import com.intellij.vcs.log.impl.VcsLogManager.BaseVcsLogUiFactory
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToBranch
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToRefOrHash
import com.intellij.vcs.log.ui.*
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.ui.frame.MainFrame
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.with
import com.intellij.vcs.log.visible.filters.without
import git4idea.GitVcs
import git4idea.i18n.GitBundleExtensions.messagePointer
import git4idea.repo.GitRepository
import git4idea.ui.branch.dashboard.BranchesDashboardTreeSelectionHandler.SelectionAction
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent

class BranchesInGitLogUiFactoryProvider(private val project: Project) : CustomVcsLogUiFactoryProvider {

  override fun isActive(providers: Map<VirtualFile, VcsLogProvider>): Boolean = hasGitRoots(project, providers.keys)

  override fun createLogUiFactory(
    logId: String,
    vcsLogManager: VcsLogManager,
    filters: VcsLogFilterCollection?,
  ): VcsLogManager.VcsLogUiFactory<out MainVcsLogUi> =
    BranchesVcsLogUiFactory(vcsLogManager, logId, filters)

  private fun hasGitRoots(project: Project, roots: Collection<VirtualFile>) =
    ProjectLevelVcsManager.getInstance(project).allVcsRoots.asSequence()
      .filter { it.vcs?.keyInstanceMethod == GitVcs.getKey() }
      .map(VcsRoot::getPath)
      .toSet()
      .containsAll(roots)
}

private class BranchesVcsLogUiFactory(
  logManager: VcsLogManager, logId: String, filters: VcsLogFilterCollection? = null,
) : BaseVcsLogUiFactory<BranchesVcsLogUi>(logId, filters, logManager.uiProperties, logManager.colorManager) {
  override fun createVcsLogUiImpl(
    logId: String,
    logData: VcsLogData,
    properties: MainVcsLogUiProperties,
    colorManager: VcsLogColorManager,
    refresher: VisiblePackRefresherImpl,
    filters: VcsLogFilterCollection?,
  ) = BranchesVcsLogUi(logId, logData, colorManager, properties, refresher, filters)
}

internal class BranchesVcsLogUi(
  id: String, logData: VcsLogData, colorManager: VcsLogColorManager,
  uiProperties: MainVcsLogUiProperties, refresher: VisiblePackRefresher,
  initialFilters: VcsLogFilterCollection?,
) : VcsLogUiImpl(id, logData, colorManager, uiProperties, refresher, initialFilters) {

  private lateinit var mainComponent: JComponent

  override fun createMainFrame(
    logData: VcsLogData, uiProperties: MainVcsLogUiProperties,
    filterUi: VcsLogFilterUiEx, isEditorDiffPreview: Boolean,
  ): MainFrame {
    val mainFrame = super.createMainFrame(logData, uiProperties, filterUi, isEditorDiffPreview).apply {
      isFocusCycleRoot = false
      focusTraversalPolicy = null //new focus traversal policy will be configured include branches tree
    }
    mainComponent = createMainComponent(logData, uiProperties, mainFrame)
    return mainFrame
  }

  private fun createMainComponent(logData: VcsLogData, properties: MainVcsLogUiProperties, mainFrame: MainFrame): JComponent {
    val model = SyncBranchesDashboardTreeModel(logData).also {
      Disposer.register(this, it)
    }

    val filterUi = mainFrame.filterUi
    val roots = logData.roots.toSet()
    val logUiFilterListener = VcsLogFilterUiEx.VcsLogFilterListener {
      model.rootsToFilter = when {
        roots.size == 1 || !logData.project.service<BranchesDashboardFilteringLogic>().showBranchesMatchingRootFilter() -> roots
        else -> VcsLogUtil.getAllVisibleRoots(roots, filterUi.filters)
      }
    }

    filterUi.addFilterListener(logUiFilterListener)
    logUiFilterListener.onFiltersChanged()

    val selectionHandler = SelectionHandler()
    val treePanel = BranchesDashboardTreeComponent.create(this,
                                                          logData.project,
                                                          model,
                                                          selectionHandler,
                                                          BranchesDashboardTreeComponent.SearchLook.Inline(mainFrame.toolbar)
    ).apply {
      border = createBorder(SideBorder.LEFT)
    }
    val actionManager = ActionManager.getInstance()
    val actions = DefaultActionGroup().apply {
      val hideBranchesAction = actionManager.getAction("Git.Log.Hide.Branches")
      add(hideBranchesAction)
      add(Separator())
      add(BranchesDashboardTreeComponent.createActionGroup())
    }
    val toolbar = actionManager
      .createActionToolbar("Git.Log.Branches", actions, false).apply {
        setTargetComponent(treePanel)
      }

    val branchesButton = ExpandStripeButton(messagePointer("action.Git.Log.Show.Branches.text"), AllIcons.Actions.ArrowExpand)
      .apply {
        border = createBorder(SideBorder.RIGHT)
        addActionListener {
          if (properties.exists(SHOW_GIT_BRANCHES_LOG_PROPERTY)) {
            properties[SHOW_GIT_BRANCHES_LOG_PROPERTY] = true
          }
        }
      }

    val expandablePanelController = ExpandablePanelController(toolbar.component, branchesButton, treePanel)
    val expandedListener = object : VcsLogUiProperties.PropertiesChangeListener {
      override fun <T : Any?> onPropertyChanged(property: VcsLogUiProperties.VcsLogUiProperty<T>) {
        if (property == SHOW_GIT_BRANCHES_LOG_PROPERTY) {
          expandablePanelController.toggleExpand(properties[SHOW_GIT_BRANCHES_LOG_PROPERTY])
        }
      }
    }
    properties.addChangeListener(expandedListener)
    Disposer.register(this) {
      properties.removeChangeListener(expandedListener)
    }
    expandablePanelController.toggleExpand(properties[SHOW_GIT_BRANCHES_LOG_PROPERTY])


    val branchViewSplitter = OnePixelSplitter(false, "vcs.branch.view.splitter.proportion", 0.3f).apply {
      name = "Log Branches Panel Splitter"
      firstComponent = treePanel
      secondComponent = mainFrame
    }

    return simplePanel()
      .addToLeft(expandablePanelController.expandControlPanel)
      .addToCenter(branchViewSplitter)
      .apply {
        isFocusCycleRoot = true
        isFocusTraversalPolicyProvider = true
        focusTraversalPolicy = object : ComponentsListFocusTraversalPolicy() {
          override fun getOrderedComponents(): List<Component> = buildList {
            addIfNotNull(IdeFocusTraversalPolicy.getPreferredFocusedComponent(treePanel))
            add(mainFrame.graphTable)
            add(mainFrame.changesBrowser.preferredFocusedComponent)
            add(filterUi.textFilterComponent.focusedComponent)
          }
        }
      }.let { panel ->
        UiDataProvider.wrapComponent(panel) { sink ->
          sink[VcsLogInternalDataKeys.LOG_UI_PROPERTIES] = properties
          sink[QuickActionProvider.KEY] = object : QuickActionProvider {
            override fun getName(): @NlsActions.ActionText String? = null
            override fun getComponent(): JComponent = panel
            override fun isCycleRoot(): Boolean = true
            override fun getActions(originalProvider: Boolean): List<AnAction> = listOf(actions)
          }
        }
      }
  }

  private inner class SelectionHandler : BranchesDashboardTreeSelectionHandler {
    override var selectionAction: SelectionAction?
      get() =
        if (properties[CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY]) {
          SelectionAction.FILTER
        }
        else if (properties[NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY]) {
          SelectionAction.NAVIGATE
        }
        else null
      set(value) =
        when (value) {
          SelectionAction.FILTER -> {
            properties[CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY] = true
            properties[NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY] = false
          }
          SelectionAction.NAVIGATE -> {
            properties[CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY] = false
            properties[NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY] = true
          }
          null -> {
            properties[CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY] = false
            properties[NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY] = false
          }
        }

    override fun filterBy(branches: List<String>, repositories: Set<GitRepository>) {
      if (logData.project.service<BranchesDashboardFilteringLogic>().filterByBranchAndRoot() && repositories.isNotEmpty()) {
        val roots = repositories.map { it.root }.takeIf { !Comparing.haveEqualElements(logData.roots, it) }
        filterUi.filterBy(branches, roots)
      }
      else {
        filterUi.filterBy(branches)
      }
    }

    override fun navigateTo(navigatable: VcsLogNavigatable, focus: Boolean) {
      val navigateSilently = false
      val repositoryRoot = navigatable.repository?.root
      when (navigatable) {
        is VcsLogNavigatable.Branch -> jumpToBranch(repositoryRoot, navigatable.branchName, navigateSilently, focus)
        is VcsLogNavigatable.Ref -> jumpToRefOrHash(repositoryRoot, navigatable.refName, navigateSilently, focus)
      }
    }
  }

  override fun getMainComponent() = mainComponent
}

@ApiStatus.Internal
fun VcsLogFilterUiEx.filterBy(branches: List<String>, roots: List<VirtualFile>?) {
  val oldFilters = filters
  var newFilters = oldFilters.without(VcsLogBranchLikeFilter::class.java)
  if (branches.isNotEmpty()) {
    newFilters = newFilters.with(VcsLogFilterObject.fromBranches(branches))
  }

  newFilters = newFilters.without(VcsLogRootFilter::class.java)
  if (roots != null) {
    newFilters = newFilters.with(VcsLogFilterObject.fromRoots(roots))
  }
  filters = newFilters
}

@ApiStatus.Internal
fun VcsLogFilterUiEx.filterBy(branches: List<String>) {
  val oldFilters = filters
  var newFilters = oldFilters.without(VcsLogBranchLikeFilter::class.java)

  if (branches.isNotEmpty()) {
    newFilters = newFilters.with(VcsLogFilterObject.fromBranches(branches))
  }
  filters = newFilters
}

@ApiStatus.Internal
val SHOW_GIT_BRANCHES_LOG_PROPERTY: VcsLogUiProperties.VcsLogUiProperty<Boolean> =
  object : VcsLogProjectTabsProperties.CustomBooleanTabProperty("Show.Git.Branches") {
    override fun defaultValue(logId: String) = logId == VcsLogManager.MAIN_LOG_ID
  }

@ApiStatus.Internal
val CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY: VcsLogUiProperties.VcsLogUiProperty<Boolean> =
  object : VcsLogApplicationSettings.CustomBooleanProperty("Change.Log.Filter.on.Branch.Selection") {
    override fun defaultValue() = false
  }

@ApiStatus.Internal
val NAVIGATE_LOG_TO_BRANCH_ON_BRANCH_SELECTION_PROPERTY: VcsLogUiProperties.VcsLogUiProperty<Boolean> =
  object : VcsLogApplicationSettings.CustomBooleanProperty("Navigate.Log.To.Branch.on.Branch.Selection") {
    override fun defaultValue() = false
  }