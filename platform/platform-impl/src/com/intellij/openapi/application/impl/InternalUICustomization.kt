// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.components.serviceOrNull
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
import com.intellij.toolWindow.StripesUxCustomizer
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.xNext.XNextStripesUxCustomizer
import com.intellij.ui.Graphics2DDelegate
import com.intellij.ui.JBColor
import com.intellij.ui.mac.WindowTabsComponent
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

@ApiStatus.Experimental
@ApiStatus.Internal
open class InternalUICustomization {
  companion object {

    // Caching the service instance improves performance due to frequent usage during painting.
    // Service replacement is not possible in runtime.
    // However, storing a mutable static instance is generally discouraged.
    // Don't do that.
    private var instance: InternalUICustomization? = null

    @JvmStatic
    fun getInstance(): InternalUICustomization? {
      instance?.let { return it }

      val result = serviceOrNull<InternalUICustomization>()
      instance = result
      return result
    }

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

  open val commonTabPainterAdapter: TabPainterAdapter? = null

  open val debuggerTabPainterAdapter: TabPainterAdapter? = null

  open val toolWindowUIDecorator: ToolWindowUIDecorator = ToolWindowUIDecorator()

  open val toolWindowTabPainter: JBTabPainter = JBTabPainter.TOOL_WINDOW

  open val isProjectCustomDecorationActive: Boolean = true

  open val isProjectCustomDecorationGradientPaint: Boolean
    get() {
      return isProjectCustomDecorationActive
    }

  open val isMainMenuBottomBorder: Boolean = true

  open val isTabOccupiesWholeHeight: Boolean = true

  internal open fun configureToolWindowPane(toolWindowPaneParent: JComponent, buttonManager: ToolWindowButtonManager) {}

  /**
   * TODO
   * in the case of singleStripe, it is necessary to remove or recycle all actions related to the statusbar.
   * in the menu - appearance too
   */
  open fun isSingleStripe(): Boolean = false

  internal val internalCustomizer: StripesUxCustomizer = if (isSingleStripe())
    XNextStripesUxCustomizer()
  else
    StripesUxCustomizer()

  open fun configureMainFrame(frame: IdeFrameImpl) {}

  open fun configureMainToolbar(toolbar: MainToolbar) {}

  /**
   * For Islands theme: the components are painted with the IDE background or gradient if set.
   * For other themes: has no effect
   */
  open fun registerWindowBackgroundComponent(component: JComponent) {}

  open fun getEditorToolbarButtonLook(): ActionButtonLook? = null

  open fun configureEditorsSplitters(component: EditorsSplitters) {}

  open fun installBackgroundUpdater(component: JComponent) {}

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

  open val isMacScrollBar: Boolean = false

  open fun attachIdeFrameBackgroundPainter(frame: IdeFrame, glassPane: IdeGlassPane): Unit = Unit

  open fun updateBackgroundPainter() {}

  open fun attachIdeFallbackBackgroundPainter(glassPane: IdeGlassPane): Unit = Unit

  open fun attachDialogFallbackBackgroundPainter(glassPane: IdeGlassPane): Unit = Unit

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

  fun statusBarRequired(): Boolean = !isSingleStripe()

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

  open fun getSingleRowTabInsets(tabsPosition: JBTabsPosition): Insets? = null
}
