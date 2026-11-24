// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.ex.FrameProjectConfigurable
import com.intellij.openapi.project.Project
import com.intellij.toolWindow.emptyTabComponent
import com.intellij.ui.GuiUtils
import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.JBDefaultTabsBorder
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The project selector of a Settings window that configures several projects. The selector shows above one page, see [show].
 * It has one tab for each project that has that page, see [FrameProjectConfigurable.projectsWithPage].
 *
 * The tabs show the selection of [projects] and change it. They use the common tab painter, as the tabs of a dialog do,
 * so they follow the Islands theme when it is on. The row is higher and its font is larger than in an editor tab row,
 * because the row selects the project of the whole page.
 */
internal class SettingsProjectTabs(private val projects: SettingsFrameProjects, parentDisposable: Disposable) {
  private val tabs = ProjectTabs(projects.frameOwner, parentDisposable)

  /** `true` while [update] changes the tabs. The tabs select a tab on their own then, and that is no request of the user. */
  private var isUpdating = false

  /** The page below the selector, or `null` before the first project-level page. */
  private var page: FrameProjectConfigurable? = null

  /** The tab row with an empty space above and below it. */
  val component: JComponent = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty(ROW_TOP_GAP, 0, ROW_BOTTOM_GAP, 0)
    add(tabs.component, BorderLayout.CENTER)
  }

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

  /** Shows the projects of [page]. */
  fun show(page: FrameProjectConfigurable) {
    this.page = page
    update()
  }

  private fun update() {
    val page = page ?: return
    val pageProjects = page.projectsWithPage
    isUpdating = true
    try {
      if (tabs.tabs.map { it.`object` } != pageProjects) {
        tabs.removeAllTabs()
        for (project in pageProjects) {
          tabs.addTab(TabInfo(emptyTabComponent())
                        .setText(project.name)
                        .setIcon(AllIcons.Nodes.Folder)
                        .setTooltipText(project.presentableUrl)
                        .setObject(project))
        }
      }
      val shownProject = page.shownProject
      val selectedTab = tabs.tabs.firstOrNull { it.`object` === shownProject }
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

  override fun createTabLabel(info: TabInfo): TabLabel = ProjectLabel(info)

  private inner class ProjectLabel(info: TabInfo) : SingleHeightLabel(this@ProjectTabs, info) {
    init {
      updateFont()
    }

    override fun updateUI() {
      super.updateUI()
      updateFont()
    }

    /** The label component of the tab is a child, so each child gets the font. */
    private fun updateFont() {
      val font = JBFont.label().biggerOn(FONT_SIZE_INCREASE)
      GuiUtils.iterateChildren(this, { it.font = font })
    }

    override fun getPreferredHeight(): Int = JBUI.scale(TAB_HEIGHT)
  }
}

/** The height of a tab, unscaled. An editor tab row is [SingleHeightTabs.UNSCALED_PREF_HEIGHT] high. */
private const val TAB_HEIGHT = 36

/** The points that the font of a tab adds to the label font. */
private const val FONT_SIZE_INCREASE = 1f

private const val ROW_TOP_GAP = 6

private const val ROW_BOTTOM_GAP = 6
