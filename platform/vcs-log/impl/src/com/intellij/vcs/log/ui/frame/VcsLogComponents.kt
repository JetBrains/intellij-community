// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.google.common.primitives.Ints
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.wrap
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.GuiUtils
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.navigation.History
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogFilterUi
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.getLogProvider
import com.intellij.vcs.log.data.roots
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToHash
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.AbstractVcsLogUi
import com.intellij.vcs.log.ui.VcsLogActionIds
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.util.VcsLogUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
object VcsLogComponents {
  @JvmStatic
  fun createTable(
    logData: VcsLogData,
    logUi: AbstractVcsLogUi,
    filterUi: VcsLogFilterUiEx,
    colorManager: VcsLogColorManager,
    parentDisposable: Disposable,
  ): VcsLogGraphTable {
    val graphTableModel = GraphTableModel(logData, { logUi.requestMore(EmptyRunnable.INSTANCE) }, logUi.properties)
    return createTable(logUi.id, graphTableModel, logUi.properties, colorManager, { logUi.refresher.onRefresh() }, filterUi,
                       { commitHash: String -> logUi.jumpToHash(commitHash, false, true) },
                       parentDisposable)
  }

  fun createTable(
    logId: String,
    tableModel: GraphTableModel,
    uiProperties: VcsLogUiProperties,
    colorManager: VcsLogColorManager,
    refresher: () -> Unit,
    filterUi: VcsLogFilterUiEx,
    commitByHashNavigator: (commitHash: String) -> Unit,
    parentDisposable: Disposable,
  ): VcsLogGraphTable {
    return VcsLogMainGraphTable(logId, tableModel, uiProperties, colorManager,
                                refresher,
                                filterUi,
                                commitByHashNavigator,
                                parentDisposable
    ).apply {
      val vcsDisplayName = VcsLogUtil.getVcsDisplayName(tableModel.logData.project, tableModel.logData.logProviders.values)
      accessibleContext.accessibleName = VcsLogBundle.message("vcs.log.table.accessible.name", vcsDisplayName)
      resetDefaultFocusTraversalKeys()
    }.also {
      PopupHandler.installPopupMenu(it, VcsLogActionIds.POPUP_ACTION_GROUP, ActionPlaces.VCS_LOG_TABLE_PLACE)
    }
  }

  @JvmStatic
  fun createActionsToolbar(graphTable: VcsLogGraphTable, filterUi: VcsLogFilterUiEx): JComponent {
    val actionManager = ActionManager.getInstance()

    val toolbarGroup = actionManager.getAction(VcsLogActionIds.TOOLBAR_ACTION_GROUP) as DefaultActionGroup

    val mainGroup = DefaultActionGroup().apply {
      add(filterUi.createActionGroup())
      addSeparator()
      add(toolbarGroup)
    }

    val toolbar = actionManager.createActionToolbar(ActionPlaces.VCS_LOG_TOOLBAR_PLACE, mainGroup, true).apply {
      targetComponent = graphTable
    }

    val textFilter = Wrapper(filterUi.textFilterComponent.component).apply {
      setVerticalSizeReferent(toolbar.component)
      val logData = graphTable.logData
      accessibleContext.accessibleName = VcsLogBundle.message(
        "vcs.log.text.filter.accessible.name",
        VcsLogUtil.getVcsDisplayName(logData.project, logData.logProviders.values)
      )
    }

    val rightCornerGroup = CustomActionsSchema.getInstance()
                             .getCorrectedAction(VcsLogActionIds.TOOLBAR_RIGHT_CORNER_ACTION_GROUP) as ActionGroup?
                           ?: throw IllegalStateException("Action group not found: ${VcsLogActionIds.TOOLBAR_RIGHT_CORNER_ACTION_GROUP}")
    val rightCornerToolbar = actionManager.createActionToolbar(ActionPlaces.VCS_LOG_TOOLBAR_PLACE, rightCornerGroup, true).apply {
      targetComponent = graphTable
      setReservePlaceAutoPopupIcon(false)
    }

    return JPanel(MigLayout("ins 0, fill", "[left]0[left, fill]push[pref:pref, right]", "center")).apply {
      GuiUtils.installVisibilityReferent(this, toolbar.component)
      add(textFilter)
      add(toolbar.component)
      add(rightCornerToolbar.component)
    }
  }

  @JvmStatic
  fun collectLogKeys(
    sink: DataSink,
    uiProperties: VcsLogUiProperties,
    graphTable: VcsLogGraphTable,
    history: History,
    filterUi: VcsLogFilterUi,
    toolbarComponent: JComponent,
    mainComponent: JComponent,
  ) {
    sink[VcsLogInternalDataKeys.LOG_UI_PROPERTIES] = uiProperties

    val logData = graphTable.logData
    val roots = getSelectedRoots(graphTable, filterUi.filters)
    sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = VfsUtilCore.toVirtualFileArray(roots)
    val onlyRoot = ContainerUtil.getOnlyItem<VirtualFile?>(roots)
    if (onlyRoot != null) {
      sink[VcsLogInternalDataKeys.LOG_DIFF_HANDLER] = logData.getLogProvider(onlyRoot).getDiffHandler()
    }
    sink[VcsLogInternalDataKeys.VCS_LOG_VISIBLE_ROOTS] = VcsLogUtil.getAllVisibleRoots(logData.roots, filterUi.filters)
    sink[PlatformCoreDataKeys.HELP_ID] = HELP_ID
    sink[History.KEY] = history
    sink[QuickActionProvider.KEY] = object : QuickActionProvider {
      override fun getActions(originalProvider: Boolean): MutableList<AnAction?> {
        val textFilterAction = wrap(VcsLogActionIds.VCS_LOG_FOCUS_TEXT_FILTER)
        textFilterAction.getTemplatePresentation().text = VcsLogBundle.message("vcs.log.text.filter.action.text")
        val actions: MutableList<AnAction?> = ArrayList()
        actions.add(textFilterAction)
        actions.addAll(SimpleToolWindowPanel.collectActions(toolbarComponent))
        return actions
      }

      override fun getComponent(): JComponent = mainComponent

      override fun getName(): @NlsActions.ActionText String? = null
    }
  }

  private fun getSelectedRoots(graphTable: VcsLogGraphTable, filters: VcsLogFilterCollection): Collection<VirtualFile> {
    val tableModel = graphTable.model
    val roots = tableModel.logData.roots
    if (roots.size == 1) return roots
    val selectedRows = graphTable.selectedRows
    if (selectedRows.isEmpty() || selectedRows.size > VcsLogUtil.MAX_SELECTED_COMMITS) {
      return VcsLogUtil.getAllVisibleRoots(roots, filters)
    }
    return ContainerUtil.map2Set(Ints.asList(*selectedRows)) { row: Int? -> tableModel.getRootAtRow(row!!) }.filterNotNull()
  }

  private const val HELP_ID: @NonNls String = "reference.changesToolWindow.log"
}
