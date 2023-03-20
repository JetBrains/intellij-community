// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.properties.Delegates.observable

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

  private val centerPanel = simplePanel(createScrollPane(changesView)).andTransparent()

  init {
    addToCenter(centerPanel)
    addToolbar(isToolbarHorizontal)
  }

  override fun updateUI() {
    super.updateUI()
    background = UIUtil.getTreeBackground()
  }

  private fun addToolbar(isHorizontal: Boolean) {
    if (isHorizontal) {
      toolbar.setOrientation(SwingConstants.HORIZONTAL)
      val sideBorder = if (ExperimentalUI.isNewUI()) SideBorder.NONE else SideBorder.TOP
      centerPanel.border = createBorder(JBColor.border(), sideBorder)
      addToTop(toolbar.component)
    }
    else {
      toolbar.setOrientation(SwingConstants.VERTICAL)
      val sideBorder = if (ExperimentalUI.isNewUI()) SideBorder.NONE else SideBorder.LEFT
      centerPanel.border = createBorder(JBColor.border(), sideBorder)
      addToLeft(toolbar.component)
    }
  }
}
