// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.ide.ui.UISettings
import com.intellij.jdkEx.JdkEx
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.customFrameDecorations.header.*
import com.intellij.ui.*
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.accessibility.AccessibleContext
import javax.swing.*

/**
 * A custom root pane that supports a custom frame title, macOS tabs and an additional toolbar below the menu bar
 */
@ApiStatus.Internal
class IdeRootPane internal constructor() : JRootPane() {
  private var customFrameTitle: JComponent? = null
  private val macTabsHandler: JComponent?
  private var toolbar: JComponent? = null

  private var fixedGlassPane: Boolean = false

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

  override fun createRootLayout(): LayoutManager = CustomHeaderRootLayout()

  override fun setGlassPane(glass: Component?) {
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
