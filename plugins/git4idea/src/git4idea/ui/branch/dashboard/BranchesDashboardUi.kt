// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.ide.CommonActionsManager
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
import com.intellij.util.ui.StatusText.DEFAULT_EMPTY_TEXT
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.ui.frame.ProgressStripe
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.ui.branch.dashboard.BranchesDashboardActions.CheckoutLocalBranchOnDoubleClickHandler
import git4idea.ui.branch.dashboard.BranchesDashboardActions.DeleteBranchAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.NewBranchAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.RenameLocalBranchOnF2KeyPressHandler
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ShowBranchDiffAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ShowMyBranchesAction
import org.jetbrains.annotations.CalledInAwt
import java.awt.Component
import javax.swing.JComponent
import javax.swing.event.TreeSelectionListener

internal class BranchesDashboardUi(val project: Project) : Disposable {
  private val uiController = BranchesDashboardController(project, this)

  private val tree = FilteringBranchesTree(project, BranchesTreeComponent(project), uiController)
  private val branchViewSplitter = BranchViewSplitter()
  private val branchesTreePanel = simplePanel().withBorder(createBorder(JBColor.border(), SideBorder.LEFT))
  private val branchesScrollPane = ScrollPaneFactory.createScrollPane(tree.component, true)
  private val branchesProgressStripe = ProgressStripe(branchesScrollPane, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
  private val rootPanel = simplePanel(branchesTreePanel)
  private val branchesSearchFieldPanel = simplePanel()
  private val branchesSearchField = Wrapper(tree.installSearchField(false, JBUI.Borders.emptyLeft(5)))

  lateinit var logUi: VcsLogUiImpl

  private val vcsLogListener = object : VcsProjectLog.ProjectLogListener {
    override fun logCreated(manager: VcsLogManager) {
      initLogUi(manager) //re-init log UI e.g. on mappings change
    }

    override fun logDisposed(manager: VcsLogManager) {
      disposeLogUi(manager)
    }
  }

  private val treeSelectionListener = TreeSelectionListener {
    if (::logUi.isInitialized) {
      val ui = logUi
      val branchNames = tree.getSelectedBranchNames()
      ui.mainFrame.filterUi.setFilter(if (branchNames.isNotEmpty()) VcsLogFilterObject.fromBranches(branchNames) else null)
    }
  }

  init {
    project.messageBus.connect(this).subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, vcsLogListener)
    initMainUi()
    initLogUiWhenLogIsReady()
  }

  @CalledInAwt
  private fun initLogUi(logManager: VcsLogManager) {
    val ui = logManager.createLogUi("BRANCHES_LOG", true, false)
    Disposer.register(this, ui)
    uiController.registerDataPackListener(ui.logData)
    branchViewSplitter.secondComponent = VcsLogPanel(logManager, ui)
    logUi = ui
    branchesSearchField.setVerticalSizeReferent(ui.mainFrame.toolbar)
    tree.component.addTreeSelectionListener(treeSelectionListener)
  }

  @CalledInAwt
  private fun disposeLogUi(logManager: VcsLogManager) {
    branchViewSplitter.secondComponent.removeAll()
    uiController.removeDataPackListener(logManager.dataManager)
    if (::logUi.isInitialized) {
      tree.component.removeTreeSelectionListener(treeSelectionListener)
      val ui = logUi
      Disposer.dispose(ui)
    }
  }

  private fun initMainUi() {
    CheckoutLocalBranchOnDoubleClickHandler.install(project, tree.component)
    RenameLocalBranchOnF2KeyPressHandler.install(project, tree.component)
    val diffAction = ShowBranchDiffAction()
    diffAction.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("Diff.ShowDiff"), rootPanel)

    val deleteAction = DeleteBranchAction()
    val shortcuts = KeymapUtil.getActiveKeymapShortcuts("SafeDelete").shortcuts + KeymapUtil.getActiveKeymapShortcuts(
      "EditorDeleteToLineStart").shortcuts
    deleteAction.registerCustomShortcutSet(CustomShortcutSet(*shortcuts), rootPanel)

    createFocusFilterFieldAction(branchesSearchField)

    val showMyBranchesAction = ShowMyBranchesAction(uiController)
    val newBranchAction = NewBranchAction()
    val defaultTreeExpander = DefaultTreeExpander(tree.component)
    val commonActionsManager = CommonActionsManager.getInstance()
    val expandAllAction = commonActionsManager.createExpandAllHeaderAction(defaultTreeExpander, tree.component)
    val collapseAllAction = commonActionsManager.createCollapseAllHeaderAction(defaultTreeExpander, tree.component)

    val group = DefaultActionGroup()
    group.add(newBranchAction)
    group.add(deleteAction)
    group.add(diffAction)
    group.add(showMyBranchesAction)
    group.add(Separator())
    group.add(expandAllAction)
    group.add(collapseAllAction)

    val toolbar = ActionManager.getInstance().createActionToolbar("Git.Cleanup.Branches", group, false)
    toolbar.setTargetComponent(tree.component)

    rootPanel.addToLeft(toolbar.component)
    branchesSearchFieldPanel.withBackground(UIUtil.getListBackground()).withBorder(createBorder(JBColor.border(), SideBorder.BOTTOM))
    branchesSearchFieldPanel.addToCenter(branchesSearchField)
    branchesTreePanel.addToTop(branchesSearchFieldPanel).addToCenter(branchesProgressStripe)
    branchViewSplitter.firstComponent = branchesTreePanel
    rootPanel.addToCenter(branchViewSplitter)
    startLoadingBranches()
  }

  private fun initLogUiWhenLogIsReady() {
    VcsProjectLog.runWhenLogIsReady(project) { _, logManager ->
      initLogUi(logManager)
      updateBranchesTree(true)
    } // schedule initialization: need the log for other actions
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
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("Find"), rootPanel)
  }

  fun getMainComponent(): JComponent {
    return rootPanel
  }

  fun updateBranchesTree(initial: Boolean) {
    tree.update(initial)
  }

  fun refreshTree(){
    tree.refreshTree()
  }

  fun startLoadingBranches() {
    tree.component.emptyText.text = "Loading branches..."
    branchesTreePanel.isEnabled = false
    branchesProgressStripe.startLoading()
  }

  fun stopLoadingBranches() {
    tree.component.emptyText.text = DEFAULT_EMPTY_TEXT
    branchesTreePanel.isEnabled = true
    branchesProgressStripe.stopLoading()
  }

  override fun dispose() {
  }
}

private class BranchViewSplitter(first: JComponent? = null, second: JComponent? = null)
  : OnePixelSplitter(false, "vcs.branch.view.splitter.proportion", 0.3f) {
  init {
    firstComponent = first
    secondComponent = second
  }
}
