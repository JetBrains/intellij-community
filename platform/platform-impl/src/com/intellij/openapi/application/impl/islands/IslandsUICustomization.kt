// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.BorderPainterHolder
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.impl.ToolWindowUIDecorator
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.EditorHeaderComponent
import com.intellij.openapi.editor.impl.SearchReplaceFacade
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.content.ContentLayout
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.xNext.island.XNextIslandHolder
import com.intellij.ui.*
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.mac.WindowTabsComponent
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.util.ui.*
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.*
import javax.swing.border.Border

private data class WindowBackgroundComponentData(val origOpaque: Boolean, val origBackground: Color?)

private val WINDOW_BACKGROUND_COMPONENT_KEY: Key<WindowBackgroundComponentData> = Key.create("Islands.WINDOW_BACKGROUND_COMPONENT_KEY")

internal class IslandsUICustomization : InternalUICustomization() {
  private val isIslandsAvailable = ExperimentalUI.isNewUI()

  private var isManyIslandEnabledCache: Boolean? = null

  private var isManyIslandCustomTheme = false

  private val isManyIslandEnabled: Boolean
    get() {
      var value = isManyIslandEnabledCache
      if (value == null) {
        if (isIslandsAvailable) {
          val themeValue = JBUI.getInt("Islands", 0)
          isManyIslandCustomTheme = themeValue == 0 && !isDefaultTheme() && AdvancedSettings.getBoolean("ide.ui.theme.custom.islands")
          value = isManyIslandCustomTheme || themeValue == 1
        }
        else {
          value = false
          isManyIslandCustomTheme = false
        }
        isManyIslandEnabledCache = value
      }
      return value
    }

  private fun isDefaultTheme(): Boolean {
    val id = LafManager.getInstance().currentUIThemeLookAndFeel?.id ?: return false

    return id == "ExperimentalDark" || id == "ExperimentalLight" || id == "ExperimentalLightWithLightHeader" ||
           id == "JetBrainsHighContrastTheme" || id == "Darcula"
  }

  private var isIslandsGradientEnabledCache: Boolean? = null

  private val isIslandsGradientEnabled: Boolean
    get() {
      var value = isIslandsGradientEnabledCache
      if (value == null) {
        value = UISettings.getInstance().differentiateProjects && !isBackgroundImageSet()
        isIslandsGradientEnabledCache = value
      }
      return value
    }

  private fun isBackgroundImageSet(): Boolean =
    IdeBackgroundUtil.isEditorBackgroundImageSet(null) ||
    IdeBackgroundUtil.isFrameBackgroundImageSet(null) ||
    ProjectUtil.getOpenProjects().any {
      IdeBackgroundUtil.isEditorBackgroundImageSet(it) ||
      IdeBackgroundUtil.isFrameBackgroundImageSet(it)
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
          configureBackgroundPainting(child, recursive = false)
        }
        else {
          border = originalBorderBuilder()
        }
      }
    }
  }

  private var forcedBackground = false

  private val noBackground: Predicate<JComponent> = Predicate<JComponent> { component ->
    if (forcedBackground) {
      false
    }
    else {
      val explicitlySetValue = component.getClientProperty(IdeBackgroundUtil.NO_BACKGROUND)
      if (explicitlySetValue is Boolean) {
        explicitlySetValue
      }
      else {
        isManyIslandEnabled && isIslandsGradientEnabled
      }
    }
  }

  private fun configureBackgroundPainting(component: JComponent, recursive: Boolean) {
    if (recursive) {
      ClientProperty.putRecursive(component, IdeBackgroundUtil.NO_BACKGROUND_PREDICATE, noBackground)
    }
    else {
      component.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND_PREDICATE, noBackground)
    }
  }

  private var isBrightCached = false

  private val uiSettings by lazy { UISettings.getInstance() }

  private val awtListener = AWTEventListener { event ->
    val component = (event as HierarchyEvent).component
    if (
      (!isBrightCached && !uiSettings.differentToolwindowBackground) ||
      (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) == 0L ||
      !component.isShowing
    ) {
      return@AWTEventListener
    }

    val isToolWindow = UIUtil.getGeneralizedParentOfType(InternalDecorator::class.java, component) != null

    if (isToolWindow) {
      if (component.background == JBColor.PanelBackground) {
        component.background = JBUI.CurrentTheme.ToolWindow.background()
      }
    }
  }

  init {
    if (isManyIslandEnabled) {
      isBrightCached = JBColor.isBright()
      Toolkit.getDefaultToolkit().addAWTEventListener(awtListener, AWTEvent.HIERARCHY_EVENT_MASK)
      applyMissingKeys()
    }

    var oldManyIsland = isManyIslandEnabled

    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      val toolkit = Toolkit.getDefaultToolkit()

      toolkit.removeAWTEventListener(awtListener)
      isManyIslandEnabledCache = null

      val newManyIsland = isManyIslandEnabled

      if (newManyIsland) {
        isBrightCached = JBColor.isBright()
        toolkit.addAWTEventListener(awtListener, AWTEvent.HIERARCHY_EVENT_MASK)
        applyMissingKeys()
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

    connection.subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
      override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
        if (id == "ide.ui.theme.custom.islands") {
          RegistryBooleanOptionDescriptor.suggestRestart(null)
        }
      }
    })
  }

  private fun applyMissingKeys() {
    if (isManyIslandCustomTheme) {
      val uiDefaults = UIManager.getLookAndFeelDefaults()

      uiDefaults["MainToolbar.borderColor"] = Gray.TRANSPARENT
      uiDefaults["ToolWindow.borderColor"] = Gray.TRANSPARENT
      uiDefaults["ToolWindow.Stripe.borderColor"] = Gray.TRANSPARENT
      uiDefaults["StatusBar.borderColor"] = Gray.TRANSPARENT

      val background = EditorColorsManager.getInstance().globalScheme.defaultBackground

      uiDefaults["ToolWindow.background"] = background
      uiDefaults["ToolWindow.Header.background"] = background
      uiDefaults["ToolWindow.Header.inactiveBackground"] = background
      uiDefaults["EditorTabs.background"] = background
      uiDefaults["Island.borderColor"] = background

      uiDefaults["Island.arc"] = 20
    }
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
              configureBackgroundPainting(it, recursive = true)
            }
          }
          is ManyIslandDivider -> {
            it.configure(true)
          }
          is SearchReplaceFacade -> {
            configureSearchReplaceComponent(it as EditorHeaderComponent, true)
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
          ClientProperty.removeRecursive(it, IdeBackgroundUtil.NO_BACKGROUND_PREDICATE)
        }

        when (it) {
          is EditorsSplitters -> {
            it.border = null
            it.borderPainter = DefaultBorderPainter()
          }
          is ManyIslandDivider -> {
            it.configure(false)
          }
          is SearchReplaceFacade -> {
            configureSearchReplaceComponent(it as EditorHeaderComponent, false)
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
      configureBackgroundPainting(child as JComponent, recursive = true)
    }
  }

  private fun clearParentNoBackground(component: JComponent) {
    var nextComponent: JComponent? = component

    while (nextComponent != null && ClientProperty.get(nextComponent, IdeBackgroundUtil.NO_BACKGROUND_PREDICATE) != null) {
      ClientProperty.removeRecursive(nextComponent, IdeBackgroundUtil.NO_BACKGROUND_PREDICATE)
      nextComponent = nextComponent.parent as JComponent?
    }
  }

  override fun configureToolWindowPane(toolWindowPaneParent: JComponent, buttonManager: ToolWindowButtonManager) {
    if (buttonManager is ToolWindowPaneNewButtonManager) {
      buttonManager.addVisibleToolbarsListener { leftVisible, rightVisible ->
        val isIslands = isManyIslandEnabled
        val gap = JBUI.getInt("Islands.emptyGap", 4)

        if (SystemInfo.isMac) {
          UIUtil.getRootPane(toolWindowPaneParent)?.let { rootPane ->
            val tabContainer = rootPane.getClientProperty("WINDOW_TABS_CONTAINER_KEY")
            if (tabContainer is JComponent) {
              if (isIslands) {
                val frame = rootPane.parent
                if (frame is IdeFrame) {
                  val project = frame.project
                  if (project != null) {
                    tabContainer.putClientProperty("PROJECT_ID", project.locationHash)
                  }
                }

                if (DistractionFreeModeController.isDistractionFreeModeEnabled()) {
                  tabContainer.border = JBUI.Borders.emptyBottom(2)
                }
                else {
                  val left = if (leftVisible) JBUI.unscale(buttonManager.left.width) + 1 else gap
                  val right = if (rightVisible) JBUI.unscale(buttonManager.right.width) + 2 else gap + 1

                  tabContainer.border = JBUI.Borders.empty(0, left, 2, right)
                }
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

        if (leftVisible && rightVisible || DistractionFreeModeController.isDistractionFreeModeEnabled()) {
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

  override fun registerWindowBackgroundComponent(component: JComponent) {
    component.putUserData(WINDOW_BACKGROUND_COMPONENT_KEY, WindowBackgroundComponentData(component.isOpaque, component.background))

    if (isManyIslandEnabled) {
      configureMainFrameChildren(component, true)
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
      is IdeStatusBarImpl -> {
        component.borderPainter = if (install) inactivePainter else DefaultBorderPainter()
      }
      is BorderPainterHolder -> {
        component.borderPainter = if (install) inactivePainter else DefaultBorderPainter()
      }
    }

    if (component is JComponent) {
      val data = component.getUserData(WINDOW_BACKGROUND_COMPONENT_KEY)
      if (data != null) {
        if (install) {
          component.isOpaque = true
          component.background = getMainBackgroundColor()
        }
        else {
          component.isOpaque = data.origOpaque
          component.background = data.origBackground
        }
      }
    }
  }

  override fun createCustomDivider(isVertical: Boolean, splitter: Splittable): Divider {
    return ManyIslandDivider(isVertical, splitter).also {
      it.configure(isManyIslandEnabled)
    }
  }

  override fun configureRendererComponent(component: JComponent) {
    configureBackgroundPainting(component, recursive = true)
  }

  override fun installEditorBackground(component: JComponent) {
    configureBackgroundPainting(component, recursive = true)
  }

  override fun configureSearchReplaceComponent(component: EditorHeaderComponent): JComponent {
    component.putClientProperty("originalBorder", component.border)

    val wrapper = SearchReplaceWrapper(component)
    wrapper.background = JBUI.CurrentTheme.EditorTabs.background()
    wrapper.isOpaque = true

    if (isManyIslandEnabled) {
      configureSearchReplaceComponent(component, true)
    }

    return wrapper
  }

  private inner class SearchReplaceWrapper(private val component: EditorHeaderComponent) : Wrapper(component), UiDataProvider {
    val fillColor = JBColor.namedColor("Editor.SearchField.background")
    val borderColor = JBColor.namedColor("Editor.SearchField.borderColor")

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)

      if (isManyIslandEnabled) {
        val isTop = UISettings.getInstance().editorTabPlacement == SwingConstants.TOP

        val rect = Rectangle(size)
        JBInsets.removeFrom(rect, if (isTop) JBInsets.create(0, 7) else JBInsets(6, 7, 2, 7))

        g as Graphics2D

        g.color = fillColor

        RectanglePainter2D.FILL.paint(g, rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble(),
                                      12.0, LinePainter2D.StrokeType.CENTERED, 1.0, RenderingHints.VALUE_ANTIALIAS_ON)

        g.color = borderColor

        RectanglePainter2D.DRAW.paint(g, rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble(),
                                      12.0, LinePainter2D.StrokeType.CENTERED, 1.0, RenderingHints.VALUE_ANTIALIAS_ON)
      }
    }

    override fun uiDataSnapshot(sink: DataSink) {
      (component as UiDataProvider).uiDataSnapshot(sink)
    }
  }

  private fun configureSearchReplaceComponent(component: EditorHeaderComponent, enabled: Boolean) {
    val originalBorder = component.getClientProperty("originalBorder")
    val parent = component.parent

    if (originalBorder !is Border || parent !is JComponent) {
      return
    }

    if (enabled) {
      component.border = null

      @Suppress("UseDPIAwareInsets")
      val supplier = Supplier {
        if (UISettings.getInstance().editorTabPlacement == SwingConstants.TOP) {
          Insets(2, 10, 2, 10)
        }
        else {
          Insets(8, 10, 4, 10)
        }
      }
      @Suppress("UNCHECKED_CAST")
      parent.border = JBUI.Borders.empty(JBInsets.create(supplier as Supplier<Insets?>, supplier.get()))
    }
    else {
      component.border = originalBorder
      parent.border = null
    }

    (component as SearchReplaceFacade).configureUI(enabled)
  }

  override fun shouldPaintEditorTabsBottomBorder(editorCompositePanel: JComponent): Boolean {
    if (isManyIslandEnabled) {
      return UIUtil.findComponentOfType(editorCompositePanel, SearchReplaceWrapper::class.java) == null
    }
    return true
  }

  override fun configureEditorsSplitters(component: EditorsSplitters) {
    if (isManyIslandEnabled) {
      createEditorBorderPainter(component)
    }
  }

  private fun createToolWindowBorderPainter(toolwindow: ToolWindow, component: XNextIslandHolder) {
    component.border = JBEmptyBorder(JBUI.insets("Island.ToolWindow.border", JBUI.insets(3)))

    component.borderPainter = object : AbstractBorderPainter() {
      override fun paintAfterChildren(component: JComponent, g: Graphics) {
        if (toolwindow.type.isInternal) {
          paintIslandBorder(component, g, false)
        }
      }
    }
  }

  private fun createEditorBorderPainter(component: EditorsSplitters) {
    component.border = JBEmptyBorder(JBUI.insets("Island.Editor.border", JBUI.insets(2)))

    configureBackgroundPainting(component, recursive = true)

    component.borderPainter = object : AbstractBorderPainter() {
      override fun paintAfterChildren(component: JComponent, g: Graphics) {
        val project = ProjectUtil.getProjectForComponent(component)
        val fileEditorManager = project?.getServiceIfCreated(FileEditorManager::class.java)

        // A bit special handling of the "empty frame" background.
        // The editor empty text consists of the editor itself and the surrounding island.
        // Both are technically parts of the same component (EditorsSplitters),
        // but must use different backgrounds because the border is visually a part of the "editor and tools" background,
        // and the empty text must use the "empty frame" background.
        val frameBG = IdeBackgroundUtil.withFrameBackground(g, component)
        val editorBG = IdeBackgroundUtil.withEditorBackground(g, component)
        if (fileEditorManager?.openFiles?.isEmpty() == true) {
          paintBeforeEditorEmptyText(component, frameBG, editorTabPainterAdapter)

          val editorEmptyTextPainter = ApplicationManager.getApplication().getService(EditorEmptyTextPainter::class.java)
          val glassPane = IdeGlassPaneUtil.find(component) as JComponent
          val shift = SwingUtilities.convertPoint(component, 0, 0, glassPane)

          frameBG.translate(-shift.x, -shift.y)
          editorEmptyTextPainter.doPaintEmptyText(glassPane, frameBG)
          frameBG.translate(shift.x, shift.y)
        }

        paintIslandBorder(component, editorBG, true)
      }
    }
  }

  private fun paintBeforeEditorEmptyText(component: JComponent, graphics: Graphics, editorTabPainter: TabPainterAdapter) {
    graphics.color = editorTabPainter.tabPainter.getBackgroundColor()
    graphics.fillRect(0, 0, component.width, component.height)
  }

  private fun paintIslandBorder(component: JComponent, g: Graphics, editor: Boolean) {
    val isGradient = isIslandsGradientEnabled

    val gg: Graphics2D

    if (isGradient) {
      forcedBackground = true
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
      val cornerRadius = JBUIScale.scale(JBUI.getInt("Island.arc", 10).toFloat())
      val borderWith = JBUI.scale(JBUI.getInt("Island.borderWidth", 4))
      val offset = borderWith / 2f
      val offsetWidth = borderWith + 0.5f
      val border = Area(RoundRectangle2D.Float(offset, offset, width.toFloat() - offsetWidth, height.toFloat() - offsetWidth, cornerRadius, cornerRadius))

      shape.subtract(border)

      paintIslandBackground(gg, shape)

      if (isIslandBorderLineNeeded(component)) {
        paintIslandBorderLine(gg, border)
      }
    }
    finally {
      if (isGradient) {
        forcedBackground = false
      }
    }
  }

  private fun paintIslandBackground(gg: Graphics2D, shape: Area) {
    gg.color = getMainBackgroundColor()
    gg.fill(shape)
  }

  private fun isIslandBorderLineNeeded(component: JComponent): Boolean {
    if (isIslandsGradientEnabled) return true
    val project = ProjectUtil.getProjectForComponent(component)
    return !IdeBackgroundUtil.isEditorBackgroundImageSet(project) // the border looks ugly with a background image
  }

  private fun paintIslandBorderLine(gg: Graphics2D, border: Area) {
    gg.color = JBColor.namedColor("Island.borderColor", getMainBackgroundColor())
    gg.stroke = BasicStroke(JBUIScale.scale(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    gg.draw(border)
  }

  override val editorTabPainterAdapter: IslandsTabPainterAdapter = IslandsTabPainterAdapter(false, false, isManyIslandEnabled)

  override val toolWindowTabPainter: IslandsTabPainter = object : IslandsTabPainter(false, false) {
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

  override fun paintFrameBackground(frame: IdeFrame, component: Component, g: Graphics2D) {
    if (isManyIslandEnabled && isIslandsGradientEnabled) {
      val point = SwingUtilities.convertPoint(component, 0, 0, frame.component)
      g.translate(-point.x, -point.y)

      islandsGradientPaint(frame, getMainBackgroundColor(), ProjectWindowCustomizerService.getInstance(), component, g)

      g.translate(point.x, point.y)
    }
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

  override fun backgroundImageGraphics(component: JComponent, graphics: Graphics): Graphics {
    if (isManyIslandEnabled && isIslandsGradientEnabled) {
      return IdeBackgroundUtil.getOriginalGraphics(graphics) // not supported for island themes with gradients yet
    }
    return JBSwingUtilities.runGlobalCGTransform(component, graphics)
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

  override fun createProjectTab(frame: JFrame, tabsComponent: WindowTabsComponent) {
    if (isManyIslandEnabled) {
      if (frame is IdeFrame) {
        frame.project?.also { project ->
          updateToolStripesVisibility(ToolWindowManager.getInstance(project))
        }
      }
      configureMainFrameChildren(tabsComponent, true)
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

    g.color = getMainBackgroundColor()
    g.fillRect(0, 0, rect.width, rect.height)

    if (selected || hovered) {
      val gg = if (isGradient) IdeBackgroundUtil.getOriginalGraphics(g) else g
      val cornerRadius = JBUI.scale(JBUI.getInt("Island.arc", 10))

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
