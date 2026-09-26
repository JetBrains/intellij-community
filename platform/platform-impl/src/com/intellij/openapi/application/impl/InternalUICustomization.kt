// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.editor.impl.EditorHeaderComponent
import com.intellij.openapi.fileEditor.impl.EditorTabPainterAdapter
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.content.ContentLayout
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.ui.Graphics2DDelegate
import com.intellij.ui.JBColor
import com.intellij.ui.mac.WindowTabsComponent
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.ui.tabs.impl.UIThemeCustomization
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Paint
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

@ApiStatus.Experimental
@ApiStatus.Internal
abstract class InternalUICustomization : UIThemeCustomization {
  companion object {
    @JvmStatic
    fun getInstance(): InternalUICustomization? = UIThemeCustomization.getInstance() as? InternalUICustomization?

    @JvmStatic
    fun runGlobalCGTransformWithInactiveFrameSupport(component: JComponent, graphics: Graphics): Graphics {
      if (graphics is Graphics2DDelegate) {
        return graphics
      }

      val customization = getInstance()
      val inactiveFrameGraphics = customization?.inactiveFrameGraphics(graphics, component) ?: graphics

      return JBSwingUtilities.runGlobalCGTransform(component, inactiveFrameGraphics)
    }
  }

  open fun progressWidget(project: Project): JComponent? = null

  open val aiComponentMarker: AiInternalUiComponentMarker = AiInternalUiComponentMarker()

  open val editorTabPainterAdapter: TabPainterAdapter = EditorTabPainterAdapter()

  override val commonTabPainterAdapter: TabPainterAdapter? = null

  open val debuggerTabPainterAdapter: TabPainterAdapter? = null

  open val toolWindowUIDecorator: ToolWindowUIDecorator = ToolWindowUIDecorator()

  open val toolWindowTabPainter: JBTabPainter = JBTabPainter.TOOL_WINDOW

  open val isProjectCustomDecorationActive: Boolean = true

  open val isProjectCustomDecorationGradientPaint: Boolean
    get() {
      return isProjectCustomDecorationActive
    }

  open val isMainMenuBottomBorder: Boolean = true

  override val isRoundedTabDuringDrag: Boolean = false

  internal open fun configureToolWindowPane(toolWindowPaneParent: JComponent, buttonManager: ToolWindowButtonManager) {}

  open fun configureMainFrame(frame: IdeFrameImpl) {}

  open fun configureMainToolbar(toolbar: MainToolbar) {}

  /**
   * For Islands theme: the components are painted with the IDE background or gradient if set.
   * For other themes: has no effect
   */
  open fun registerWindowBackgroundComponent(component: JComponent) {}

  open fun getEditorToolbarButtonLook(): ActionButtonLook? = null

  open fun configureEditorsSplitters(component: EditorsSplitters) {}

  open fun installEditorBackground(component: JComponent) {}

  open fun updateEditorHeader(editorHeaderPanel: JComponent) {}

  open fun configureSearchReplaceComponent(component: EditorHeaderComponent): JComponent = component

  open fun configureLfeSearchReplaceComponent(component: EditorHeaderComponent): JComponent = component

  open fun configureTerminalSearchReplaceComponent(component: EditorHeaderComponent): JComponent = component

  open fun configureEditorTopComponent(component: JComponent, top: Boolean): JComponent? = null

  open fun configureEditorTopContainer(container: JComponent) {}

  open fun shouldPaintEditorTabsBottomBorder(editorCompositePanel: JComponent): Boolean = true

  open fun frameHeaderBackgroundConverter(color: Color?): Color? = color

  open fun transformGraphics(component: JComponent, graphics: Graphics): Graphics = graphics

  open fun transformButtonGraphics(graphics: Graphics): Graphics = graphics

  open fun preserveGraphics(graphics: Graphics): Graphics = graphics

  open fun inactiveFrameGraphics(graphics: Graphics, component: Component): Graphics = graphics

  open fun backgroundImageGraphics(component: JComponent, graphics: Graphics): Graphics = graphics

  open fun createCustomDivider(isVertical: Boolean, splitter: Splittable): Divider? = null

  open fun createCustomToolWindowPaneHolder(): JPanel = JPanel()

  open fun configureRendererComponent(component: JComponent) {}

  open val isCustomPaintersAllowed: Boolean = false

  override val isMacScrollBar: Boolean = false

  open fun attachIdeFrameBackgroundPainter(frame: IdeFrame, glassPane: IdeGlassPane): Unit = Unit

  open fun updateBackgroundPainter() {}

  open fun getToolWindowsPaneThreeSplitterBackground(): Color = JBColor.GRAY

  open fun getCustomDefaultButtonFillPaint(c: JComponent, r: Rectangle, defaultPaint: Paint?): Paint? {
    return aiComponentMarker.getCustomDefaultButtonFillPaint(c, r, defaultPaint)
  }

  open fun getCustomButtonFillPaint(c: JComponent, r: Rectangle, defaultPaint: Paint?): Paint? {
    return aiComponentMarker.getCustomButtonFillPaint(c, r, defaultPaint)
  }

  open fun getMainToolbarBackground(active: Boolean): Color {
    return JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(active)
  }

  open fun getCustomMainBackgroundColor(): Color? = null

  fun statusBarRequired(): Boolean = true

  open fun getProjectTabContentInsets(): Insets? = null

  open fun createProjectTab(frame: JFrame, tabsComponent: WindowTabsComponent) {}

  open fun paintProjectTab(
    frame: JFrame,
    label: TabLabel,
    g: Graphics,
    tabs: JBTabsImpl,
    selected: Boolean,
    index: Int,
    lastIndex: Int,
  ): Boolean = false

  open fun paintTab(g: Graphics, position: JBTabsPosition, rect: Rectangle, hovered: Boolean, selected: Boolean): Boolean = false

  open fun paintTabBorder(g: Graphics, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean): Boolean =
    false

  open fun getTabLayoutStart(layout: ContentLayout): Int = 0

  override fun getSingleRowTabInsets(tabsPosition: JBTabsPosition): Insets? = null

  override fun getEditorTabComposedBgColor(
    component: JComponent,
    tabPainter: JBTabPainter,
    tabColor: Color?,
    active: Boolean,
    hovered: Boolean,
    selected: Boolean,
  ): Color? = null

  open fun calculateTabWidth(widthWithInsets: Int, insetsWidth: Int): Int = widthWithInsets

  open fun onStatusBarVisibilityChanged(centerComponent: JComponent, isStatusBarVisible: Boolean) {}

  open fun getTabHOffsetUnscaled(compactMode: Boolean, position: JBTabsPosition): Int = 0
}
