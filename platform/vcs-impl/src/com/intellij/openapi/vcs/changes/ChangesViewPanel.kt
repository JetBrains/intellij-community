// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.DataManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SideBorder
import com.intellij.util.EditSourceOnDoubleClickHandler.isToggleEvent
import com.intellij.util.OpenSourceUtil.openSourcesFrom
import com.intellij.util.Processor
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree
import javax.swing.SwingConstants
import kotlin.properties.Delegates.observable

private class ChangesViewPanel(project: Project) : BorderLayoutPanel() {
  val changesView: ChangesListView = ChangesListView(project, false).apply {
    treeExpander = object : DefaultTreeExpander(this) {
      override fun collapseAll(tree: JTree, keepSelectionLevel: Int) {
        super.collapseAll(tree, 2)
        TreeUtil.expand(tree, 1)
      }
    }
    doubleClickHandler = Processor { e ->
      if (isToggleEvent(this, e)) return@Processor false

      openSourcesFrom(DataManager.getInstance().getDataContext(this), true)
      true
    }
    enterKeyHandler = Processor {
      openSourcesFrom(DataManager.getInstance().getDataContext(this), false)
      true
    }
  }

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

  private val centerPanel = simplePanel(createScrollPane(changesView))

  init {
    addToCenter(centerPanel)
    addToolbar(isToolbarHorizontal)
  }

  private fun addToolbar(isHorizontal: Boolean) {
    if (isHorizontal) {
      toolbar.setOrientation(SwingConstants.HORIZONTAL)
      centerPanel.border = createBorder(JBColor.border(), SideBorder.TOP)
      addToTop(toolbar.component)
    }
    else {
      toolbar.setOrientation(SwingConstants.VERTICAL)
      centerPanel.border = createBorder(JBColor.border(), SideBorder.LEFT)
      addToLeft(toolbar.component)
    }
  }
}
