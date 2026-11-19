// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The component that selects the project whose tool window a tool window of the frame owner shows: one tab for each project.
 *
 * The component keeps no selection of its own. [update] gives it the projects and the selected one, and [onSelect] gets the
 * project of a tab that the user selects. [project] is the frame owner. The tabs live until [parentDisposable] is disposed.
 */
internal class ToolWindowProjectSelector(
  project: Project,
  parentDisposable: Disposable,
  private val onSelect: (Project) -> Unit,
) {
  private val tabs: JBTabs = JBTabsFactory.createTabs(project, parentDisposable)

  /** The selection that the last [update] gave. A change of the tabs to this project is no request of the user. */
  private var selected: Project? = null

  /** `true` while [update] changes the tabs. The tabs select a tab on their own then, and that is no request of the user either. */
  private var isUpdating = false

  val component: JComponent
    get() = tabs.component

  init {
    tabs.addListener(object : TabsListener {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        val newProject = newSelection?.`object` as? Project ?: return
        if (!isUpdating && newProject !== selected) {
          onSelect(newProject)
        }
      }
    }, parentDisposable)
  }

  fun update(projects: List<Project>, selected: Project?) {
    this.selected = selected
    isUpdating = true
    try {
      if (tabs.tabs.map { it.`object` } != projects) {
        tabs.removeAllTabs()
        for (project in projects) {
          tabs.addTab(TabInfo(emptyTabComponent()).setText(project.name).setTooltipText(project.presentableUrl).setObject(project))
        }
      }
      val selectedTab = tabs.tabs.firstOrNull { it.`object` === selected }
      if (selectedTab != null && tabs.selectedInfo !== selectedTab) {
        tabs.select(selectedTab, false)
      }
    }
    finally {
      isUpdating = false
    }
  }
}

/** A tab shows no component of its own: the tool window below the selector shows the content of the project. */
private fun emptyTabComponent(): JComponent {
  return JPanel(null).also {
    it.isOpaque = false
    it.preferredSize = Dimension(0, 0)
  }
}
