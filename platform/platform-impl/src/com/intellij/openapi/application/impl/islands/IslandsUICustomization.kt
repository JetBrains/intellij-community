// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.impl.ToolWindowUIDecorator
import com.intellij.openapi.editor.impl.EditorHeaderComponent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.content.ContentLayout
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MacToolbarFrameHeader
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.ToolWindowToolbar
import com.intellij.toolWindow.xNext.island.XNextIslandHolder
import com.intellij.ui.*
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.border.Border

internal class IslandsUICustomization : InternalUICustomization() {
  private val isIslandsAvailable = !Registry.`is`("llm.riderNext.enabled", false) && ExperimentalUI.isNewUI()

  private var isManyIslandEnabledCache: Boolean? = null

  private val isManyIslandEnabled: Boolean
    get() {
      var value = isManyIslandEnabledCache
      if (value == null) {
        value = isIslandsAvailable && JBUI.getInt("Islands", 0) == 1
        isManyIslandEnabledCache = value
      }
      return value
    }

  private var isIslandsGradientEnabledCache: Boolean? = null

  private val isIslandsGradientEnabled: Boolean
    get() {
      var value = isIslandsGradientEnabledCache
      if (value == null) {
        value = Registry.`is`("idea.islands.gradient.enabled", true) && UISettings.getInstance().differentiateProjects
        isIslandsGradientEnabledCache = value
      }
      return value
    }

  override fun updateBackgroundPainter() {
    isIslandsGradientEnabledCache = null
  }

  override val isProjectCustomDecorationGradientPaint: Boolean
    get() {
      return !isManyIslandEnabled
    }

  override val shouldPaintEditorFadeout: Boolean
    get() {
      return !isManyIslandEnabled
    }

  override val isMainMenuBottomBorder: Boolean
    get() {
      return !isManyIslandEnabled
    }

  override val toolWindowUIDecorator: ToolWindowUIDecorator = object : ToolWindowUIDecorator() {
    override fun decorateAndReturnHolder(divider: JComponent, child: JComponent, toolWindow: ToolWindow, originalBorderBuilder: () -> Border): JComponent {
      return XNextIslandHolder().apply {
        layout = BorderLayout()
        add(divider, BorderLayout.NORTH)
        add(child, BorderLayout.CENTER)

        putClientProperty("originalBorderBuilder", originalBorderBuilder)

        if (isManyIslandEnabled) {
          background = JBUI.CurrentTheme.ToolWindow.background()
          createToolWindowBorderPainter(toolWindow, this)
          child.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
        }
        else {
          border = originalBorderBuilder()
        }
      }
    }
  }

  private val awtListener = AWTEventListener { event ->
    val component = (event as HierarchyEvent).component
    val isToolWindow = UIUtil.getParentOfType(XNextIslandHolder::class.java, component) != null

    if (isToolWindow) {
      UIUtil.forEachComponentInHierarchy(component) {
        if (it.background == JBColor.PanelBackground) {
          it.background = JBUI.CurrentTheme.ToolWindow.background()
        }
      }
    }
  }

  init {
    if (isManyIslandEnabled && JBColor.isBright()) {
      Toolkit.getDefaultToolkit().addAWTEventListener(awtListener, AWTEvent.HIERARCHY_EVENT_MASK)
    }

    var oldManyIsland = isManyIslandEnabled

    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      val toolkit = Toolkit.getDefaultToolkit()

      toolkit.removeAWTEventListener(awtListener)
      isManyIslandEnabledCache = null

      val newManyIsland = isManyIslandEnabled

      if (newManyIsland && JBColor.isBright()) {
        toolkit.addAWTEventListener(awtListener, AWTEvent.HIERARCHY_EVENT_MASK)
      }

      if (oldManyIsland == newManyIsland) {
        return@LafManagerListener
      }
      if (newManyIsland) {
        enableManyIslands()
      }
      else {
        disableManyIslands()
      }

      oldManyIsland = newManyIsland
    })
  }

  private fun enableManyIslands() {
    editorTabPainterAdapter.isEnabled = true
    commonTabPainterAdapter.isEnabled = true
    debuggerTabPainterAdapter.isEnabled = true

    // XXX: dialogs

    for (frameHelper in WindowManager.getInstance().allProjectFrames) {
      if (frameHelper is ProjectFrameHelper) {
        configureMainFrame(frameHelper.frame, true)
      }

      UIUtil.forEachComponentInHierarchy(frameHelper.component) {
        configureMainFrameChildren(it, true)

        when (it) {
          is EditorsSplitters -> {
            createEditorBorderPainter(it)
            clearParentNoBackground(it)
          }
          is JBEditorTabs -> {
            if (it.parent is EditorsSplitters) {
              ClientProperty.putRecursive(it, IdeBackgroundUtil.NO_BACKGROUND, true)
            }
          }
          is ManyIslandDivider -> {
            it.configure(true)
          }
        }
      }

      val project = frameHelper.project
      if (project != null) {
        val manager = ToolWindowManager.getInstance(project) as ToolWindowManagerEx
        updateToolStripesVisibility(manager)
        for (toolwindow in manager.toolWindows) {
          if (toolwindow is ToolWindowImpl) {
            toolwindow.getNullableDecorator()?.also {
              UIUtil.findComponentOfType(it, XNextIslandHolder::class.java)?.also { holder ->
                setToolWindowManyBorder(toolwindow, holder)
              }
            }
          }
        }
      }
    }

    ExperimentalUiCollector.islandsThemeOn.log()
  }

  private fun disableManyIslands() {
    editorTabPainterAdapter.isEnabled = false
    commonTabPainterAdapter.isEnabled = false
    debuggerTabPainterAdapter.isEnabled = false

    // XXX: dialogs

    for (frameHelper in WindowManager.getInstance().allProjectFrames) {
      if (frameHelper is ProjectFrameHelper) {
        configureMainFrame(frameHelper.frame, false)
      }

      UIUtil.forEachComponentInHierarchy(frameHelper.component) {
        configureMainFrameChildren(it, false)

        if (it is JComponent) {
          ClientProperty.removeRecursive(it, IdeBackgroundUtil.NO_BACKGROUND)
        }

        when (it) {
          is EditorsSplitters -> {
            it.borderPainter = DefaultBorderPainter()
          }
          is ManyIslandDivider -> {
            it.configure(false)
          }
        }
      }

      val project = frameHelper.project
      if (project != null) {
        val manager = ToolWindowManager.getInstance(project) as ToolWindowManagerEx
        updateToolStripesVisibility(manager)
        for (toolwindow in manager.toolWindows) {
          if (toolwindow is ToolWindowImpl) {
            toolwindow.getNullableDecorator()?.also {
              UIUtil.findComponentOfType(it, XNextIslandHolder::class.java)?.also { holder ->
                setOriginalToolWindowBorder(holder)
              }
            }
          }
        }
      }
    }

    ExperimentalUiCollector.islandsThemeOff.log()
  }

  private fun setOriginalToolWindowBorder(holder: XNextIslandHolder) {
    val builder = holder.getClientProperty("originalBorderBuilder")
    if (builder is Function0<*>) {
      val border = builder()
      if (border is Border) {
        holder.border = border
      }
    }
    holder.borderPainter = DefaultBorderPainter()
    holder.background = JBColor.PanelBackground
  }

  private fun setToolWindowManyBorder(toolwindow: ToolWindow, holder: XNextIslandHolder) {
    holder.background = JBUI.CurrentTheme.ToolWindow.background()
    createToolWindowBorderPainter(toolwindow, holder)
    clearParentNoBackground(holder)

    for (child in holder.components) {
      ClientProperty.putRecursive(child as JComponent, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  private fun clearParentNoBackground(component: JComponent) {
    var nextComponent: JComponent? = component

    while (nextComponent != null && ClientProperty.get(nextComponent, IdeBackgroundUtil.NO_BACKGROUND) != null) {
      ClientProperty.removeRecursive(nextComponent, IdeBackgroundUtil.NO_BACKGROUND)
      nextComponent = nextComponent.parent as JComponent?
    }
  }

  override fun configureToolWindowPane(toolWindowPaneParent: JComponent, buttonManager: ToolWindowButtonManager) {
    if (buttonManager is ToolWindowPaneNewButtonManager) {
      buttonManager.addVisibleToolbarsListener { leftVisible, rightVisible ->
        val isIslands = isManyIslandEnabled
        val gap = JBUI.unscale(JBUI.getInt("Islands.emptyGap", JBUI.scale(4)))

        if (SystemInfo.isMac) {
          UIUtil.getRootPane(toolWindowPaneParent)?.let { rootPane ->
            val tabContainer = rootPane.getClientProperty("WINDOW_TABS_CONTAINER_KEY")
            if (tabContainer is JComponent) {
              if (isIslands) {
                val left = if (leftVisible) JBUI.unscale(buttonManager.left.width) + 1 else gap
                val right = if (rightVisible) JBUI.unscale(buttonManager.right.width) + 2 else gap + 1

                val frame = rootPane.parent
                if (frame is IdeFrame) {
                  val project = frame.project
                  if (project != null) {
                    tabContainer.putClientProperty("PROJECT_ID", project.locationHash)
                  }
                }

                tabContainer.border = JBUI.Borders.empty(0, left, 2, right)
              }
              else if (tabContainer.border != null) {
                tabContainer.border = null
              }
            }
          }
        }

        if (!isIslands) {
          if (toolWindowPaneParent.border != null) {
            toolWindowPaneParent.border = null
          }
          return@addVisibleToolbarsListener
        }

        if (leftVisible && rightVisible) {
          if (toolWindowPaneParent.border != null) {
            toolWindowPaneParent.border = null
          }
        }
        else {
          val left = if (leftVisible) 0 else gap
          val right = if (rightVisible) 0 else gap

          val border = toolWindowPaneParent.border
          if (border == null) {
            toolWindowPaneParent.border = JBUI.Borders.empty(0, left, 0, right)
          }
          else {
            val insets = border.getBorderInsets(toolWindowPaneParent)
            if (insets.left != left || insets.right != right) {
              toolWindowPaneParent.border = JBUI.Borders.empty(0, left, 0, right)
            }
          }
        }
      }
      buttonManager.updateToolStripesVisibility()
    }
  }

  private val inactivePainter = object : DefaultBorderPainter() {
    override fun paintAfterChildren(component: JComponent, g: Graphics) {
      val window = UIUtil.getWindow(component) ?: return
      if (!window.isActive) {
        if (component is MainToolbar && component.parent !is JBLayeredPane) {
          return
        }
        if (component is ToolWindowPane && JBColor.isBright()) {
          return
        }

        val alphaKey = if (component is IdeStatusBarImpl) "Island.inactiveAlphaInStatusBar" else "Island.inactiveAlpha"

        g as Graphics2D
        g.color = getMainBackgroundColor()
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, JBUI.getFloat(alphaKey, 0.5f))

        if (component is ToolWindowPane) {
          val extraBorder = JBUI.scale(4)
          g.fillRect(0, 0, component.width, extraBorder)
          g.fillRect(0, extraBorder, extraBorder, component.height)
        }
        else {
          g.fillRect(0, 0, component.width, component.height)
        }
      }
    }
  }

  private val frameActiveListener = object : WindowAdapter() {
    override fun windowActivated(e: WindowEvent) {
      e.window?.repaint()
    }

    override fun windowDeactivated(e: WindowEvent) {
      e.window?.repaint()
    }
  }

  override fun configureMainFrame(frame: IdeFrameImpl) {
    if (isManyIslandEnabled) {
      configureMainFrame(frame, true)

      UIUtil.forEachComponentInHierarchy(frame.component) {
        configureMainFrameChildren(it, true)
      }
    }
  }

  override fun configureMainToolbar(toolbar: MainToolbar) {
    if (isManyIslandEnabled) {
      configureMainFrameChildren(toolbar, true)
    }
  }

  private fun configureMainFrame(frame: IdeFrameImpl, install: Boolean) {
    if (install) {
      frame.addWindowListener(frameActiveListener)
    }
    else {
      frame.removeWindowListener(frameActiveListener)
    }
  }

  private fun configureMainFrameChildren(component: Component, install: Boolean) {
    when (component) {
      is ToolWindowToolbar -> {
        component.borderPainter = if (install) inactivePainter else DefaultBorderPainter()
      }
      is CustomHeader -> {
        component.borderPainter = if (install) inactivePainter else DefaultBorderPainter()
      }
      is MacToolbarFrameHeader -> {
        component.borderPainter = if (install) inactivePainter else DefaultBorderPainter()
      }
      is IdeStatusBarImpl -> {
        component.borderPainter = if (install) inactivePainter else DefaultBorderPainter()
      }
      is MainToolbar -> {
        component.borderPainter = if (install) inactivePainter else DefaultBorderPainter()
      }
      is ToolWindowPane -> {
        component.borderPainter = if (install) inactivePainter else DefaultBorderPainter()
      }
    }
  }

  override fun createCustomDivider(isVertical: Boolean, splitter: Splittable): Divider {
    return ManyIslandDivider(isVertical, splitter).also {
      it.configure(isManyIslandEnabled)
    }
  }

  override fun configureRendererComponent(component: JComponent) {
    if (isManyIslandEnabled) {
      ClientProperty.putRecursive(component, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  override fun installEditorBackground(component: JComponent) {
    if (isManyIslandEnabled) {
      ClientProperty.putRecursive(component, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  override fun configureEditorsSplitters(component: EditorsSplitters) {
    if (isManyIslandEnabled) {
      createEditorBorderPainter(component)
    }
  }

  private fun createToolWindowBorderPainter(toolwindow: ToolWindow, component: XNextIslandHolder) {
    component.border = null
    component.borderPainter = object : AbstractBorderPainter() {
      override fun paintAfterChildren(component: JComponent, g: Graphics) {
        if (toolwindow.type.isInternal) {
          paintIslandBorder(component, g, false)
        }
      }
    }
  }

  private fun createEditorBorderPainter(component: EditorsSplitters) {
    ClientProperty.putRecursive(component, IdeBackgroundUtil.NO_BACKGROUND, true)
    component.borderPainter = object : AbstractBorderPainter() {
      override fun paintAfterChildren(component: JComponent, g: Graphics) {
        val fileEditorManager = ProjectUtil.getProjectForComponent(component)?.getServiceIfCreated(FileEditorManager::class.java)

        if (fileEditorManager?.openFiles?.isEmpty() == true) {
          IslandsRoundedBorder.paintBeforeEditorEmptyText(component, g, editorTabPainterAdapter)

          val editorEmptyTextPainter = ApplicationManager.getApplication().getService(EditorEmptyTextPainter::class.java)
          val glassPane = IdeGlassPaneUtil.find(component) as JComponent
          val shift = SwingUtilities.convertPoint(component, 0, 0, glassPane)

          g.translate(-shift.x, -shift.y)
          editorEmptyTextPainter.doPaintEmptyText(glassPane, g)
          g.translate(shift.x, shift.y)
        }

        paintIslandBorder(component, g, true)
      }
    }
  }

  private fun paintIslandBorder(component: JComponent, g: Graphics, editor: Boolean) {
    val isGradient = isIslandsGradientEnabled

    val gg: Graphics2D

    if (isGradient) {
      component.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, null)
      gg = if (editor) IdeBackgroundUtil.withEditorBackground(g, component) else IdeBackgroundUtil.withFrameBackground(g, component)
    }
    else {
      gg = g as Graphics2D
    }

    gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    try {
      val width = component.width
      val height = component.height

      val shape = Area(Rectangle(0, 0, width, height))
      val cornerRadius = JBUI.getInt("Island.arc", 10).toFloat()
      val borderWith = JBUI.getInt("Island.borderWidth", 4)
      val offset = borderWith / 2f
      val offsetWidth = borderWith + 0.5f
      val border = Area(RoundRectangle2D.Float(offset, offset, width.toFloat() - offsetWidth, height.toFloat() - offsetWidth, cornerRadius, cornerRadius))

      shape.subtract(border)

      gg.color = getMainBackgroundColor()
      gg.fill(shape)

      gg.color = JBColor.namedColor("Island.borderColor", getMainBackgroundColor())
      gg.stroke = BasicStroke(JBUIScale.scale(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      gg.draw(border)
    }
    finally {
      if (isGradient) {
        component.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
      }
    }
  }

  override val editorTabPainterAdapter: IslandsTabPainterAdapter = IslandsTabPainterAdapter(false, false, isManyIslandEnabled)

  override val toolWindowTabPainter: IslandsTabPainter = object : IslandsTabPainter(false) {
    private val defaultPainter = JBTabPainter.TOOL_WINDOW

    override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
      if (isManyIslandEnabled) {
        super.paintTab(position, g, rect, borderThickness, tabColor, active, hovered)
      }
      else {
        defaultPainter.paintTab(position, g, rect, borderThickness, tabColor, active, hovered)
      }
    }

    override fun paintSelectedTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
      if (isManyIslandEnabled) {
        super.paintSelectedTab(position, g, rect, borderThickness, tabColor, active, hovered)
      }
      else {
        defaultPainter.paintSelectedTab(position, g, rect, borderThickness, tabColor, active, hovered)
      }
    }

    override fun paintTab(g: Graphics2D, rect: Rectangle, tabColor: Color?, active: Boolean, hovered: Boolean, selected: Boolean) {
      JBInsets.removeFrom(rect, JBInsets(6, 4, 6, 4))
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      super.paintTab(g, rect, tabColor, active, hovered, selected)
    }
  }

  override val commonTabPainterAdapter: IslandsTabPainterAdapter = IslandsTabPainterAdapter(true, false, isManyIslandEnabled)

  override val debuggerTabPainterAdapter: IslandsTabPainterAdapter = IslandsTabPainterAdapter(true, true, isManyIslandEnabled)

  override fun paintTab(g: Graphics, rect: Rectangle, hovered: Boolean, selected: Boolean): Boolean {
    if (isManyIslandEnabled) {
      toolWindowTabPainter.paintTab(g as Graphics2D, rect, null, true, hovered, selected)
      return true
    }
    return true
  }

  override fun paintTabBorder(g: Graphics, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean): Boolean {
    return isManyIslandEnabled
  }

  override fun getTabLayoutStart(layout: ContentLayout): Int {
    if (isManyIslandEnabled && !layout.isIdVisible) {
      return JBUI.scale(4)
    }
    return 0
  }

  private fun getMainBackgroundColor(): Color {
    return JBColor.namedColor("MainWindow.background", JBColor.PanelBackground)
  }

  override fun getCustomMainBackgroundColor(): Color? {
    if (isManyIslandEnabled) {
      return getMainBackgroundColor()
    }
    return null
  }

  override fun attachIdeFrameBackgroundPainter(frame: IdeFrame, glassPane: IdeGlassPane) {
    if (glassPane is IdeGlassPaneImpl) {
      glassPane.addFallbackBackgroundPainter(IslandsGradientPainter(frame, getMainBackgroundColor()) {
        isManyIslandEnabled && isIslandsGradientEnabled
      })
    }
  }

  override fun paintFrameBackground(frame: Window, component: Component, g: Graphics2D) {
    if (isManyIslandEnabled && isIslandsGradientEnabled) {
      islandsGradientPaint(frame as IdeFrame, getMainBackgroundColor(), ProjectWindowCustomizerService.getInstance(), component, g)
    }
  }

  override fun configureSearchReplaceComponentBorder(component: EditorHeaderComponent) {
    component.border = object : CustomLineBorder(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 0, 1, 0) {
      override fun getBorderInsets(c: Component): Insets {
        if (isManyIslandEnabled) {
          return JBUI.insets(1, 0)
        }
        return super.getBorderInsets(c)
      }

      override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, w: Int, h: Int) {
        super.paintBorder(c, g, x, y, w, h)
        if (isManyIslandEnabled) {
          g.color = color
          g.fillRect(x, y, w, JBUI.scale(1))
        }
      }
    }
  }

  override fun configureButtonLook(look: ActionButtonLook, g: Graphics): Graphics? {
    if (isManyIslandEnabled && isIslandsGradientEnabled && look is SquareStripeButtonLook) {
      return IdeBackgroundUtil.getOriginalGraphics(g)
    }
    return null
  }

  override fun transformGraphics(component: JComponent, graphics: Graphics): Graphics {
    if (isManyIslandEnabled && isIslandsGradientEnabled) {
      return JBSwingUtilities.runGlobalCGTransform(component, graphics)
    }
    return graphics
  }

  override fun transformButtonGraphics(graphics: Graphics): Graphics {
    return preserveGraphics(graphics)
  }

  override fun preserveGraphics(graphics: Graphics): Graphics {
    if (isManyIslandEnabled && isIslandsGradientEnabled) {
      return IdeBackgroundUtil.getOriginalGraphics(graphics)
    }
    return graphics
  }

  override val isMacScrollBar: Boolean
    get() {
      return !SystemInfoRt.isMac && isManyIslandEnabled
    }

  private fun updateToolStripesVisibility(toolWindowManager: ToolWindowManager) {
    if (toolWindowManager is ToolWindowManagerImpl) {
      for (pane in toolWindowManager.getToolWindowPanes()) {
        val buttonManager = pane.buttonManager
        if (buttonManager is ToolWindowPaneNewButtonManager) {
          buttonManager.updateToolStripesVisibility()
        }
      }
    }
  }

  override fun getProjectTabContentInsets(): Insets? {
    if (isManyIslandEnabled) {
      return JBUI.insets(0, 4, 0, 15)
    }
    return null
  }

  override fun paintProjectTabsContainer(component: JComponent, g: Graphics): Boolean {
    if (isManyIslandEnabled) {
      val gg = IdeBackgroundUtil.withFrameBackground(g, component)
      gg.color = getMainBackgroundColor()
      gg.fillRect(0, 0, component.width, component.height)
      return true
    }
    return false
  }

  private val PROJECT_TAB_SELECTED_BACKGROUND = JBColor.namedColor("MainWindow.Tab.selectedBackground", JBUI.CurrentTheme.ToolWindow.background())
  private val PROJECT_TAB_HOVER_BACKGROUND = JBColor.namedColor("MainWindow.Tab.hoverBackground", JBColor.namedColor("MainToolbar.Dropdown.transparentHoverBackground"))
  private val PROJECT_TAB_SEPARATOR_COLOR = JBColor.namedColor("MainWindow.Tab.separatorColor", 0xD3D5DB, 0x43454A)

  private fun isColorfulToolbar(frame: JFrame): Boolean {
    val glassPane = frame.glassPane
    if (glassPane is IdeGlassPaneEx) {
      return glassPane.isColorfulToolbar
    }
    return true
  }

  override fun createProjectTab(frame: JFrame) {
    if (frame is IdeFrame) {
      frame.project?.also { project ->
        updateToolStripesVisibility(ToolWindowManager.getInstance(project))
      }
    }
  }

  override fun paintProjectTab(frame: JFrame, label: TabLabel, g: Graphics, tabs: JBTabsImpl, selected: Boolean, index: Int, lastIndex: Int): Boolean {
    if (!isManyIslandEnabled) {
      return false
    }

    val parent = tabs.parent

    if (parent is JComponent && frame is IdeFrame) {
      val project = frame.project

      if (project != null && (parent.border == null || project.locationHash != parent.getClientProperty("PROJECT_ID"))) {
        updateToolStripesVisibility(ToolWindowManager.getInstance(project))
      }
    }

    val hovered = tabs.isHoveredTab(label)
    val isGradient = isIslandsGradientEnabled && !CustomWindowHeaderUtil.isCompactHeader() && isColorfulToolbar(frame)
    val rect = Rectangle(label.width, label.height)

    if (!isGradient) {
      g.color = getMainBackgroundColor()
      g.fillRect(0, 0, rect.width, rect.height)
    }

    if (selected || hovered) {
      val gg = if (isGradient) IdeBackgroundUtil.getOriginalGraphics(g) else g
      val cornerRadius = JBUI.getInt("Island.arc", 10)

      rect.x += JBUI.scale(1)
      rect.width -= rect.x * 2

      (gg as Graphics2D).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      gg.color = if (selected) PROJECT_TAB_SELECTED_BACKGROUND else PROJECT_TAB_HOVER_BACKGROUND
      gg.fillRoundRect(rect.x, rect.y, rect.width, rect.height, cornerRadius, cornerRadius)
    }
    else if (lastIndex > 1 && index < lastIndex) {
      val nextTab = tabs.getTabAt(index + 1)

      if (nextTab != tabs.selectedInfo && !tabs.isHoveredTab(tabs.getTabLabel(nextTab))) {
        val gg = if (isGradient) IdeBackgroundUtil.getOriginalGraphics(g) else g
        val border = JBUI.scale(1).toDouble()
        val width = label.width - border
        val offset = JBUI.scale(5).toDouble()

        g.color = PROJECT_TAB_SEPARATOR_COLOR
        LinePainter2D.paint(gg as Graphics2D, width, offset, width, rect.height - offset, LinePainter2D.StrokeType.INSIDE, border)
      }
    }

    return true
  }
}

private class ManyIslandDivider(isVertical: Boolean, splitter: Splittable) : OnePixelDivider(isVertical, splitter) {
  private var doPaint = true

  fun configure(manyIsland: Boolean) {
    doPaint = !manyIsland

    if (manyIsland) {
      putClientProperty("DividerWidth", 0)
    }
    else {
      putClientProperty("DividerWidth", null)
    }
  }

  override fun paint(g: Graphics) {
    if (doPaint) {
      super.paint(g)
    }
  }
}