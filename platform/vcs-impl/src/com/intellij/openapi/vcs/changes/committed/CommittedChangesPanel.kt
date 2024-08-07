// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

abstract class CommittedChangesPanel(val project: Project) : BorderLayoutPanel(), UiDataProvider, Disposable {

  protected val browser: CommittedChangesTreeBrowser =
    CommittedChangesTreeBrowser(project, emptyList()).also { Disposer.register(this, it) }

  protected fun setup(extraActions: ActionGroup?, auxiliaryView: VcsCommittedViewAuxiliary?) {
    addToCenter(browser)

    val group = ActionManager.getInstance().getAction("CommittedChangesToolbar") as ActionGroup
    val toolBar = browser.createGroupFilterToolbar(project, group, extraActions, auxiliaryView?.toolbarActions.orEmpty())
    toolBar.targetComponent = browser.changesTree

    val filterComponent = CommittedChangesFilterComponent()
    Disposer.register(this, filterComponent)

    val toolbarPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)

      add(toolBar.component)
      add(Box.createHorizontalGlue())
      add(filterComponent)
    }
    filterComponent.minimumSize = filterComponent.preferredSize
    filterComponent.maximumSize = filterComponent.preferredSize
    browser.setToolBar(toolbarPanel)

    auxiliaryView?.let { Disposer.register(this, Disposable { it.calledOnViewDispose }) }
    browser.setTableContextMenu(group, auxiliaryView?.popupActions.orEmpty())
    browser.addFilter(filterComponent)

    ActionUtil.wrap("CommittedChanges.Refresh").registerCustomShortcutSet(CommonShortcuts.getRerun(), this)
  }

  abstract fun refreshChanges()

  override fun uiDataSnapshot(sink: DataSink) {
    DataSink.uiDataSnapshot(sink, browser)
  }

  override fun dispose() = Unit
}