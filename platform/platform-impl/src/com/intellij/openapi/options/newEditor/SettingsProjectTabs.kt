// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.ex.FrameProjectConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.toolWindow.emptyTabComponent
import com.intellij.ui.JBColor
import com.intellij.ui.paint.RectanglePainter2D
import com.intellij.ui.paint.useCopy
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.UiDecorator
import com.intellij.ui.tabs.UiDecorator.UiDecoration
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The project selector of a Settings window that configures several projects. The selector shows below the breadcrumbs of
 * one page, see [show]. It has one tab for each project that has that page, see [FrameProjectConfigurable.projectsWithPage].
 *
 * The tabs show the selection of [projects] and change it. Each tab is a pill. The selected pill has the colors of a selected
 * editor tab of the Islands theme. The first pill starts at the left edge of the breadcrumbs and of the page. A long project
 * name ends with an ellipsis. The row scrolls when the tabs do not fit, and its chevron lists all tabs.
 */
internal class SettingsProjectTabs(private val projects: SettingsFrameProjects, parentDisposable: Disposable) {
  private val tabs = ProjectTabs(projects.frameOwner, parentDisposable)

  /** `true` while [update] changes the tabs. The tabs select a tab on their own then, and that is no request of the user. */
  private var isUpdating = false

  /** The page below the selector, or `null` before the first project-level page. */
  private var page: FrameProjectConfigurable? = null

  /** The tab row with an empty space around it. */
  val component: JComponent = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty(ROW_TOP_GAP, PAGE_LEFT_INSET, ROW_BOTTOM_GAP, ROW_RIGHT_INSET)
    add(tabs.component, BorderLayout.CENTER)
  }

  init {
    // the tabs keep the order of the projects, whatever the setting of the editor tabs says
    tabs.setAlphabeticalMode(false)
    tabs.setPaintFocus(false)
    tabs.setTabDraggingEnabled(false)
    tabs.setUiDecorator(object : UiDecorator {
      override fun getDecoration(): UiDecoration = UiDecoration(
        labelFont = tabFont(),
        labelInsets = JBUI.insets(0, PILL_SIDE_PADDING),
        contentInsetsSupplier = { JBUI.insets(0, PILL_SIDE_PADDING) },
        iconTextGap = JBUI.scale(ICON_TEXT_GAP),
      )
    })
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
        val metrics = tabs.component.getFontMetrics(tabFont())
        for (project in pageProjects) {
          val title = shortTitle(project.name, metrics)
          val tooltip = if (title == project.name) {
            HtmlChunk.text(project.presentableUrl.orEmpty())
          }
          else {
            HtmlBuilder().append(project.name).br().append(project.presentableUrl.orEmpty()).toFragment()
          }
          tabs.addTab(TabInfo(emptyTabComponent())
                        .setText(title)
                        .setIcon(AllIcons.Nodes.Folder)
                        .setTooltipText(tooltip)
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

/** Gives [name], or its start with an ellipsis when [name] is wider than [MAX_TITLE_WIDTH]. */
private fun shortTitle(name: String, metrics: FontMetrics): String {
  val maxWidth = JBUI.scale(MAX_TITLE_WIDTH)
  if (metrics.stringWidth(name) <= maxWidth) {
    return name
  }
  var end = name.length
  while (end > 1 && metrics.stringWidth(name.substring(0, end) + StringUtil.ELLIPSIS) > maxWidth) {
    end--
  }
  return name.substring(0, end).trimEnd() + StringUtil.ELLIPSIS
}

private fun tabFont() = JBFont.label().biggerOn(FONT_SIZE_INCREASE)

private class ProjectTabs(project: Project, parentDisposable: Disposable) : SingleHeightTabs(project, parentDisposable) {
  override fun createTabPainterAdapter(): TabPainterAdapter = PillPainterAdapter()

  /** The row has no line below the tabs. */
  override fun createTabBorder(): JBTabsBorder = object : JBTabsBorder(this) {
    override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {}
  }

  override fun createTabLabel(info: TabInfo): TabLabel = ProjectLabel(info)

  private inner class ProjectLabel(info: TabInfo) : SingleHeightLabel(this@ProjectTabs, info) {
    override fun getPreferredHeight(): Int = JBUI.scale(PILL_HEIGHT)
  }
}

/** Paints the selected tab and the hovered tab as a pill. The other tabs show only their icon and text. */
private class PillPainterAdapter : TabPainterAdapter {
  override val tabPainter: JBTabPainter = object : JBTabPainter by JBTabPainter.DEFAULT {
    /** The row has the background of the page. */
    override fun fillBackground(component: Component, g: Graphics2D, rect: Rectangle) {
      g.color = UIUtil.getBgFillColor(component)
      RectanglePainter2D.FILL.paint(g, rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble())
    }
  }

  override fun paintBackground(label: TabLabel, g: Graphics, tabs: JBTabsImpl) {
    val selected = label.info == tabs.selectedInfo
    val fill: Color = when {
      selected -> SELECTED_BACKGROUND
      tabs.isHoveredOrWithPopup(label) -> HOVER_BACKGROUND
      else -> return
    }
    g.useCopy { g2 ->
      GraphicsUtil.setupAAPainting(g2)
      val arc = JBUI.CurrentTheme.MainToolbar.Button.hoverArc().float.toDouble()
      val width = label.width.toDouble()
      val height = label.height.toDouble()
      g2.color = fill
      RectanglePainter2D.FILL.paint(g2, 0.0, 0.0, width, height, arc)
      if (selected) {
        g2.color = SELECTED_BORDER
        RectanglePainter2D.DRAW.paint(g2, 0.0, 0.0, width, height, arc)
      }
    }
  }
}

private val SELECTED_BACKGROUND = JBColor.namedColor("EditorTabs.underlinedTabBackground", JBColor(0xE5EEFF, 0x343E51))

private val SELECTED_BORDER = JBColor.namedColor("EditorTabs.underlinedBorderColor", JBColor(0x7F99C3, 0x7F99C3))

private val HOVER_BACKGROUND = JBColor.namedColor("EditorTabs.hoverBackground",
                                                  JBColor(Color(0xE5, 0xEE, 0xFF, 0x80), Color(0x34, 0x3E, 0x51, 0x80)))

/** The height of a pill, unscaled. */
private const val PILL_HEIGHT = 24

/** The space between the edge of a pill and its icon or text, in two equal parts, unscaled. */
private const val PILL_SIDE_PADDING = 4

private const val ICON_TEXT_GAP = 4

/** The widest text of a tab, unscaled. A longer project name ends with an ellipsis. */
private const val MAX_TITLE_WIDTH = 120

/** The points that the font of a tab adds to the label font. */
private const val FONT_SIZE_INCREASE = 1f

/**
 * The left inset of the page and of the breadcrumbs text, unscaled. See `ConfigurableCardPanel.createDecoratedComponent`
 * and `SettingsEditor.createHeaderPanel`.
 */
private const val PAGE_LEFT_INSET = 16

private const val ROW_RIGHT_INSET = 16

private const val ROW_TOP_GAP = 8

private const val ROW_BOTTOM_GAP = 4
