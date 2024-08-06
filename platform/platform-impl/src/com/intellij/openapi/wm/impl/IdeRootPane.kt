// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.ide.ui.UISettings
import com.intellij.jdkEx.JdkEx
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.customFrameDecorations.header.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.ui.*
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseMotionAdapter
import javax.accessibility.AccessibleContext
import javax.swing.*

private val EXTENSION_KEY = Key.create<String>("extensionKey")

@Suppress("LeakingThis")
@ApiStatus.Internal
open class IdeRootPane internal constructor(
  parentCs: CoroutineScope,
  frame: IdeFrameImpl,
) : JRootPane() {
  protected val coroutineScope = parentCs.childScope("IdeRootPane", Dispatchers.Default)

  private var customFrameTitle: JComponent? = null
  private val macTabsHandler: JComponent?
  private var toolbar: JComponent? = null

  private var statusBar: StatusBar? = null

  private val northPanel = JBBox.createVerticalBox()

  private var toolWindowPane: ToolWindowPane? = null
  private var fixedGlassPane: Boolean = false

  protected open val isLightEdit: Boolean
    get() = false

  init {
    if (SystemInfoRt.isWindows) {
      runCatching {
        windowDecorationStyle = FRAME
      }.getOrLogException(logger<IdeRootPane>())
    }

    macTabsHandler = if (SystemInfoRt.isMac && JdkEx.isTabbingModeAvailable()) {
      MacWinTabsHandler.createAndInstallHandlerComponent(this).also {
        layeredPane.add(it, (JLayeredPane.DEFAULT_LAYER - 3) as Any)
      }
    }
    else {
      null
    }

    val contentPane = contentPane
    // listen to mouse motion events for a11y
    contentPane.addMouseMotionListener(object : MouseMotionAdapter() {})

    putClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY, true)

    contentPane.add(northPanel, BorderLayout.NORTH)

    @Suppress("LeakingThis")
    contentPane.add(createCenterComponent(frame), BorderLayout.CENTER)
  }

  protected open fun createCenterComponent(frame: JFrame): Component {
    val paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
    val pane = ToolWindowPane.create(frame, coroutineScope, paneId)
    toolWindowPane = pane
    return pane.buttonManager.wrapWithControls(pane)
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (!SystemInfoRt.isMac) {
      return super.getAccessibleContext()
    }

    if (accessibleContext == null) {
      // we need to turn `IdeRootPane` into an accessible group to make notifications announcing working
      accessibleContext = object : AccessibleJRootPane() {
        override fun getAccessibleRole() = AccessibilityUtils.GROUPED_ELEMENTS
        override fun getAccessibleName() = UIBundle.message("root.pane.accessible.group.name")
      }
    }
    return accessibleContext
  }

  open fun getToolWindowPane(): ToolWindowPane = toolWindowPane!!

  override fun createRootLayout(): LayoutManager = CustomHeaderRootLayout()

  final override fun setGlassPane(glass: Component?) {
    check(!fixedGlassPane) { "Setting of glass pane for IdeFrame is prohibited" }
    super.setGlassPane(glass)
  }

  fun overrideGlassPane(glassPane: Component) {
    setGlassPane(glassPane)
    fixedGlassPane = true
  }

  /**
   * Invoked on disposal of the enclosing frame.
   */
  override fun removeNotify() {
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      coroutineScope.cancel()
      statusBar = null
      jMenuBar = null
      if (customFrameTitle != null) {
        layeredPane.remove(customFrameTitle)
        customFrameTitle = null
      }
    }
    super.removeNotify()
  }

  override fun createLayeredPane(): JLayeredPane {
    val result = JBLayeredPane()
    result.name = "$name.layeredPane"
    return result
  }

  override fun createContentPane(): Container {
    val contentPane = JPanel(BorderLayout())
    contentPane.background = JBColor.PanelBackground
    return contentPane
  }

  fun updateNorthComponents() {
    if (isLightEdit) {
      return
    }

    for (i in 0 until componentCount) {
      val component = northPanel.getComponent(i)
      if (ClientProperty.isSet(component, EXTENSION_KEY)) {
        component.revalidate()
      }
    }
    contentPane!!.revalidate()
  }

  @RequiresEdt
  internal fun installStatusBar(statusBar: StatusBar) {
    check(this.statusBar == null) { "Updating a status bar is ot supported" }
    this.statusBar = statusBar
    val component = statusBar.component
    if (component != null) {
      contentPane!!.add(component, BorderLayout.SOUTH)
    }
  }

  @RequiresEdt
  internal fun installToolbar(component: JComponent) {
    check(toolbar == null) { "Toolbar update is not supported" }
    layeredPane.add(component, (JLayeredPane.DEFAULT_LAYER - 3) as Any)
    toolbar = component
  }

  @RequiresEdt
  internal fun installCustomFrameTitle(component: JComponent) {
    check(customFrameTitle == null) { "Frame title update is not supported" }
    layeredPane.add(component, (JLayeredPane.DEFAULT_LAYER - 3) as Any)
    customFrameTitle = component
  }

  suspend fun setProject(project: Project) {
    installNorthComponents(project)
  }

  private suspend fun installNorthComponents(project: Project) {
    if (isLightEdit) {
      return
    }

    val northExtensions = IdeRootPaneNorthExtension.EP_NAME.extensionList
    if (northExtensions.isEmpty()) {
      return
    }

    for (extension in northExtensions) {
      val flow = extension.component(project = project, isDocked = false, statusBar = statusBar!!)
      val key = extension.key
      if (flow != null) {
        coroutineScope.launch(ModalityState.any().asContextElement()) {
          flow.collect(FlowCollector { component ->
            withContext(Dispatchers.EDT) {
              if (component == null) {
                val count = northPanel.componentCount
                for (i in count - 1 downTo 0) {
                  val c = northPanel.getComponent(i)
                  if (ClientProperty.isSet(c, EXTENSION_KEY, key)) {
                    northPanel.remove(i)
                    break
                  }
                }
              }
              else {
                ClientProperty.put(component, EXTENSION_KEY, key)
                northPanel.add(component)
              }
            }
          })
        }
        continue
      }

      withContext(Dispatchers.EDT) {
        extension.createComponent(project, isDocked = false)?.let {
          ClientProperty.put(it, EXTENSION_KEY, key)
          northPanel.add(it)
        }
      }
    }
  }

  fun findNorthUiComponentByKey(key: String): JComponent? =
    northPanel.components.firstOrNull { ClientProperty.isSet(it, EXTENSION_KEY, key) } as? JComponent

  /**
   * A custom layout which supports a custom frame title [customFrameTitle], macOS tabs handler [macTabsHandler],
   * a floating menu [getJMenuBar] and an optional toolbar [toolbar].
   * Lays out components in a vertical stack stretching all of them to window width.
   * Frame title, menu and toolbar will be at their preferred height, and the content will occupy the remaining space.
   */
  private inner class CustomHeaderRootLayout : RootLayout() {
    private val isFullScreen
      get() = ClientProperty.isTrue(this@IdeRootPane, IdeFrameDecorator.FULL_SCREEN)

    override fun preferredLayoutSize(parent: Container): Dimension = computeLayoutSize(parent) { it.preferredSize }
    override fun minimumLayoutSize(parent: Container): Dimension = computeLayoutSize(parent) { it.preferredSize }
    override fun maximumLayoutSize(target: Container): Dimension = computeLayoutSize(parent) { it.maximumSize }

    private inline fun computeLayoutSize(parent: Container, dimensionGetter: (component: Container) -> Dimension): Dimension {
      val insets = insets
      val frameTitleDimension = customFrameTitle?.takeIf { it.isVisible }?.preferredSize
                                ?: Dimension()
      val menuBarDimension = menuBar?.takeIf { it.isVisible && isFixedMenu() }?.let(dimensionGetter)
                             ?: Dimension()
      val tabsHandlerDimension = macTabsHandler?.takeIf { it.isVisible }?.let(dimensionGetter)
                                 ?: Dimension()
      val toolbarDimension = toolbar?.takeIf { it.isVisible }?.let(dimensionGetter)
                             ?: Dimension()
      val contentDimension = contentPane?.let { dimensionGetter(it) } ?: parent.size

      // sum as longs and then coerce to prevent overflow
      val width = maxOf(frameTitleDimension.width, menuBarDimension.width, tabsHandlerDimension.width, toolbarDimension.width, contentDimension.width).toLong() +
                  insets.left + insets.right
      val height = (0L + frameTitleDimension.height + menuBarDimension.height + tabsHandlerDimension.height + toolbarDimension.height + contentDimension.height) +
                   insets.top + insets.bottom

      return Dimension(width.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                       height.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    override fun layoutContainer(parent: Container) {
      val b = parent.bounds
      val i = insets
      val w = b.width - i.right - i.left
      val h = b.height - i.top - i.bottom
      if (layeredPane != null) {
        layeredPane.setBounds(i.left, i.top, w, h)
      }
      glassPane?.setBounds(i.left, i.top, w, h)
      var yShift = 0
      val customFrameTitleComponent = customFrameTitle
      if (customFrameTitleComponent != null && customFrameTitleComponent.isVisible) {
        val tpd = customFrameTitleComponent.preferredSize
        if (tpd != null) {
          val tpHeight = tpd.height
          customFrameTitleComponent.setBounds(0, yShift, w, tpHeight)
          yShift += tpHeight
        }
      }
      if (menuBar != null && menuBar.isVisible) {
        val mbd = menuBar.preferredSize
        menuBar.setBounds(0, yShift, w, mbd.height)
        if (isFixedMenu()) {
          yShift += mbd.height
        }
      }
      val tabsHandler = macTabsHandler
      if (tabsHandler != null && tabsHandler.isVisible) {
        val toolbarDim = tabsHandler.preferredSize
        if (toolbarDim != null) {
          val toolbarHeight = toolbarDim.height
          tabsHandler.setBounds(0, yShift, w, toolbarHeight)
          yShift += toolbarHeight
        }
      }
      val toolbar = toolbar
      if (toolbar != null && toolbar.isVisible) {
        val toolbarDim = toolbar.preferredSize
        if (toolbarDim != null) {
          val toolbarHeight = toolbarDim.height
          toolbar.setBounds(0, yShift, w, toolbarHeight)
          yShift += toolbarHeight
        }
      }
      contentPane?.setBounds(0, yShift, w, h - yShift)
    }

    private fun isFixedMenu(): Boolean = !isFullScreen && !CustomWindowHeaderUtil.isDecoratedMenu(UISettings.getInstance())
  }
}
