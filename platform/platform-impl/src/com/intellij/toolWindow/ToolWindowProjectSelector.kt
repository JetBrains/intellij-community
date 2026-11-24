// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GuiUtils
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.DefaultTabPainterAdapter
import com.intellij.ui.tabs.impl.JBDefaultTabsBorder
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The component that selects the project whose tool window a tool window of the frame owner shows: one tab for each project.
 *
 * The component keeps no selection of its own. [update] gives it the tool window of each project and the selected one, and
 * [onSelect] gets the tool window of a tab that the user selects. A tab holds the tool window of its project, not the project.
 * [project] is the frame owner. The tabs live until [parentDisposable] is disposed.
 */
internal class ToolWindowProjectSelector(
  project: Project,
  parentDisposable: Disposable,
  private val onSelect: (ToolWindowImpl) -> Unit,
) {
  private val tabs = ToolWindowProjectSelectorTabs(project, parentDisposable)

  /** The selection that the last [update] gave. A change of the tabs to this tool window is no request of the user. */
  private var selected: ToolWindowImpl? = null

  /** `true` while [update] changes the tabs. The tabs select a tab on their own then, and that is no request of the user either. */
  private var isUpdating = false

  val component: JComponent
    get() = tabs.component

  init {
    // the tabs keep the order of the projects, whatever the setting of the editor tabs says
    tabs.setAlphabeticalMode(false)
    tabs.setPaintFocus(false)
    tabs.setTabDraggingEnabled(false)
    tabs.addListener(object : TabsListener {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        val newToolWindow = newSelection?.`object` as? ToolWindowImpl ?: return
        if (!isUpdating && newToolWindow !== selected) {
          onSelect(newToolWindow)
        }
      }
    }, parentDisposable)
  }

  /** Shows one tab for the project of each tool window in [toolWindows], in their order. An empty list lets every tool window go. */
  fun update(toolWindows: List<ToolWindowImpl>, selected: ToolWindowImpl?) {
    this.selected = selected
    isUpdating = true
    try {
      if (tabs.tabs.map { it.`object` } != toolWindows) {
        tabs.removeAllTabs()
        for (toolWindow in toolWindows) {
          val project = toolWindow.project
          tabs.addTab(TabInfo(emptyTabComponent()).setText(project.name).setTooltipText(project.presentableUrl).setObject(toolWindow))
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

/**
 * The tabs of a [ToolWindowProjectSelector]. They paint as the tabs of the Run tool window do, see `JBRunnerTabs`:
 * the painter of a tool window, the font and the row height of its tabs, and one line below the row.
 */
internal class ToolWindowProjectSelectorTabs(project: Project, parentDisposable: Disposable) : SingleHeightTabs(project, parentDisposable) {
  override fun createTabPainterAdapter(): TabPainterAdapter {
    return InternalUICustomization.getInstance()?.debuggerTabPainterAdapter ?: DefaultTabPainterAdapter(JBTabPainter.DEBUGGER)
  }

  override fun createTabBorder(): JBTabsBorder = JBDefaultTabsBorder(this)

  override fun createTabLabel(info: TabInfo): TabLabel = ProjectLabel(info)

  private inner class ProjectLabel(info: TabInfo) : SingleHeightLabel(this@ToolWindowProjectSelectorTabs, info) {
    init {
      updateFont()
    }

    override fun updateUI() {
      super.updateUI()
      updateFont()
    }

    private fun updateFont() {
      if (ExperimentalUI.isNewUI()) {
        val font = JBUI.CurrentTheme.DebuggerTabs.font()
        GuiUtils.iterateChildren(this, { it.font = font })
      }
    }

    override fun getPreferredHeight(): Int {
      return if (ExperimentalUI.isNewUI()) JBUI.scale(JBUI.CurrentTheme.DebuggerTabs.tabHeight()) else super.getPreferredHeight()
    }
  }
}

/** The component of a tab of a project selector. The tab shows nothing itself: the UI below the selector shows the content of the project. */
internal fun emptyTabComponent(): JComponent {
  return JPanel(null).also {
    it.isOpaque = false
    it.preferredSize = Dimension(0, 0)
  }
}
