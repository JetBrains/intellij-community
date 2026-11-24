// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.Project
import com.intellij.toolWindow.emptyTabComponent
import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.JBDefaultTabsBorder
import com.intellij.ui.tabs.impl.SingleHeightTabs
import javax.swing.JComponent

/**
 * The project selector of a Settings window that configures several projects: one tab for each project of [projects].
 *
 * The tabs show the selection of [projects] and change it. They use the common tab painter, as the tabs of a dialog do,
 * so they follow the Islands theme when it is on.
 */
internal class SettingsProjectTabs(private val projects: SettingsFrameProjects, parentDisposable: Disposable) {
  private val tabs = ProjectTabs(projects.frameOwner, parentDisposable)

  /** `true` while [update] changes the tabs. The tabs select a tab on their own then, and that is no request of the user. */
  private var isUpdating = false

  val component: JComponent
    get() = tabs.component

  init {
    // the tabs keep the order of the projects, whatever the setting of the editor tabs says
    tabs.setAlphabeticalMode(false)
    tabs.setPaintFocus(false)
    tabs.setTabDraggingEnabled(false)
    tabs.component.accessibleContext.accessibleName = ApplicationBundle.message("settings.frame.project.selector.accessible.name")
    tabs.addListener(object : TabsListener {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        val project = newSelection?.`object` as? Project ?: return
        if (!isUpdating) {
          projects.select(project)
        }
      }
    }, parentDisposable)
    projects.addListener(object : SettingsFrameProjects.Listener {
      override fun selectionChanged() = update()

      override fun projectsChanged() = update()
    }, parentDisposable)
    update()
  }

  private fun update() {
    isUpdating = true
    try {
      if (tabs.tabs.map { it.`object` } != projects.projects) {
        tabs.removeAllTabs()
        for (project in projects.projects) {
          tabs.addTab(TabInfo(emptyTabComponent())
                        .setText(project.name)
                        .setIcon(AllIcons.Nodes.Folder)
                        .setTooltipText(project.presentableUrl)
                        .setObject(project))
        }
      }
      val selectedTab = tabs.tabs.firstOrNull { it.`object` === projects.selected }
      if (selectedTab != null && tabs.selectedInfo !== selectedTab) {
        tabs.select(selectedTab, false)
      }
    }
    finally {
      isUpdating = false
    }
  }
}

private class ProjectTabs(project: Project, parentDisposable: Disposable) : SingleHeightTabs(project, parentDisposable) {
  override fun createTabBorder(): JBTabsBorder = JBDefaultTabsBorder(this)
}
