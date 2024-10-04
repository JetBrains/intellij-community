// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.ui.*
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.properties.Delegates.observable

@ApiStatus.Internal
class ChangesViewPanel(val changesView: ChangesListView) : BorderLayoutPanel() {
  val toolbarActionGroup = DefaultActionGroup()

  var isToolbarHorizontal: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      addToolbar(newValue) // this also removes toolbar from previous parent
    }
  }

  val toolbar: ActionToolbar =
    ActionManager.getInstance().createActionToolbar(CHANGES_VIEW_TOOLBAR, toolbarActionGroup, isToolbarHorizontal).apply {
      setTargetComponent(changesView)
    }

  var statusComponent by observable<JComponent?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    if (oldValue != null) centerPanel.remove(oldValue)
    if (newValue != null) centerPanel.addToBottom(newValue)
  }

  private val changesScrollPane = createScrollPane(changesView)
  private val centerPanel = simplePanel(changesScrollPane).andTransparent()

  init {
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
      ScrollableContentBorder.setup(changesScrollPane, Side.TOP, centerPanel)
      addToTop(toolbar.component)
    }
    else {
      toolbar.setOrientation(SwingConstants.VERTICAL)
      ScrollableContentBorder.setup(changesScrollPane, Side.LEFT, centerPanel)
      addToLeft(toolbar.component)
    }
  }
}
