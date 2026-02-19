// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import javax.swing.SwingConstants
import kotlin.properties.Delegates.observable

@ApiStatus.Internal
open class ChangesViewPanel(val changesView: ChangesListView) : BorderLayoutPanel() {
  var isToolbarHorizontal: Boolean by observable(true) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      addToolbar(newValue) // this also removes toolbar from previous parent
    }
  }

  val toolbar: ActionToolbar

  private val changesScrollPane = createScrollPane(changesView, true)
  private val scrollableBordersPanel = simplePanel(changesScrollPane).andTransparent()
  private val centerPanel = simplePanel(scrollableBordersPanel).andTransparent()

  init {
    val toolbarActionGroup = ActionManager.getInstance().getAction("ChangesViewToolbar.Shared") as DefaultActionGroup
    toolbar = ActionManager.getInstance().createActionToolbar(CHANGES_VIEW_TOOLBAR, toolbarActionGroup, isToolbarHorizontal).apply {
      setTargetComponent(changesView)
    }

    addToCenter(centerPanel)
    addToolbar(isToolbarHorizontal)
  }

  override fun updateUI() {
    super.updateUI()
    background = UIUtil.getTreeBackground()
  }

  private fun addToolbar(isHorizontal: Boolean) {
    toolbar.layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
    if (isHorizontal) {
      toolbar.setOrientation(SwingConstants.HORIZONTAL)
      ScrollableContentBorder.setup(changesScrollPane, Side.TOP, scrollableBordersPanel)
      addToTop(toolbar.component)
    }
    else {
      toolbar.setOrientation(SwingConstants.VERTICAL)
      ScrollableContentBorder.setup(changesScrollPane, setOf(Side.LEFT), scrollableBordersPanel)
      addToLeft(toolbar.component)
    }
  }
}
