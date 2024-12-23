// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy
import com.intellij.vcs.log.impl.VcsLogApplicationSettings
import com.intellij.vcs.log.impl.VcsLogContentProvider
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToBranch
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToRefOrHash
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.i18n.GitBundleExtensions.messagePointer
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent

internal class BranchesDashboardUi(private val logUi: BranchesVcsLogUi) : Disposable {
  internal val mainComponent: JComponent

  private fun navigateToSelection(selection: BranchNodeDescriptor.LogNavigatable, focus: Boolean) {
    val navigateSilently = false
    when (selection) {
      BranchNodeDescriptor.Head -> logUi.jumpToBranch(VcsLogUtil.HEAD, navigateSilently, focus)
      is BranchNodeDescriptor.Branch -> logUi.jumpToBranch(selection.branchInfo.branchName, navigateSilently, focus)
      is BranchNodeDescriptor.Ref -> logUi.jumpToRefOrHash(selection.refInfo.refName, navigateSilently, focus)
    }
  }

  init {
    val toolbar = ActionManager.getInstance().createActionToolbar("Git.Log.Branches", BranchesDashboardTreeComponent.createActionGroup(), false)
    val treePanel = BranchesDashboardTreeComponent.create(this,
                                                          logUi.logData,
                                                          logUi.properties,
                                                          logUi.filterUi,
                                                          ::navigateToSelection,
                                                          logUi.toolbar
    )
    toolbar.setTargetComponent(treePanel)

    val logProperties = logUi.properties
    val branchesButton = ExpandStripeButton(messagePointer("action.Git.Log.Show.Branches.text"), AllIcons.Actions.ArrowExpand)
      .apply {
        border = createBorder(SideBorder.RIGHT)
        addActionListener {
          if (logProperties.exists(SHOW_GIT_BRANCHES_LOG_PROPERTY)) {
            logProperties[SHOW_GIT_BRANCHES_LOG_PROPERTY] = true
          }
        }
      }

    val expandablePanelController = ExpandablePanelController(toolbar.component, branchesButton, treePanel)
    val expandedListener = object : VcsLogUiProperties.PropertiesChangeListener {
      override fun <T : Any?> onPropertyChanged(property: VcsLogUiProperties.VcsLogUiProperty<T>) {
        if (property == SHOW_GIT_BRANCHES_LOG_PROPERTY) {
          expandablePanelController.toggleExpand(logProperties[SHOW_GIT_BRANCHES_LOG_PROPERTY])
        }
      }
    }
    logProperties.addChangeListener(expandedListener)
    Disposer.register(this) {
      logProperties.removeChangeListener(expandedListener)
    }
    expandablePanelController.toggleExpand(logProperties[SHOW_GIT_BRANCHES_LOG_PROPERTY])


    val branchViewSplitter = OnePixelSplitter(false, "vcs.branch.view.splitter.proportion", 0.3f).apply {
      name = "Log Branches Panel Splitter"
      firstComponent = treePanel
      secondComponent = logUi.mainLogComponent
    }

    val branchesTreeWithLogPanel = simplePanel()
      .addToLeft(expandablePanelController.expandControlPanel)
      .addToCenter(branchViewSplitter)

    mainComponent = simplePanel(branchesTreeWithLogPanel).apply {
      isFocusCycleRoot = true
      focusTraversalPolicy = object : ComponentsListFocusTraversalPolicy() {
        override fun getOrderedComponents(): List<Component> = buildList {
          addIfNotNull(IdeFocusTraversalPolicy.getPreferredFocusedComponent(treePanel))
          add(logUi.table)
          add(logUi.changesBrowser.preferredFocusedComponent)
          add(logUi.filterUi.textFilterComponent.focusedComponent)
        }
      }
    }
  }

  override fun dispose() = Unit
}

@ApiStatus.Internal
val SHOW_GIT_BRANCHES_LOG_PROPERTY: VcsLogUiProperties.VcsLogUiProperty<Boolean> =
  object : VcsLogProjectTabsProperties.CustomBooleanTabProperty("Show.Git.Branches") {
    override fun defaultValue(logId: String) = logId == VcsLogContentProvider.MAIN_LOG_ID
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
