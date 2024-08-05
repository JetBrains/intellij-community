// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.CommonBundle
import com.intellij.accessibility.AccessibilityUtils
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.isCompactHeader
import com.intellij.openapi.wm.impl.headertoolbar.HeaderClickTransparentListener
import com.intellij.ui.*
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextCache
import com.intellij.util.ui.*
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations.CustomTitleBar
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.border.Border
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal const val HEADER_HEIGHT_DFM = 30
internal const val HEADER_HEIGHT_COMPACT = 32
internal const val HEADER_HEIGHT_NORMAL = 40

private val windowBorderThicknessInPhysicalPx: Int = run {
  // Windows 10 (tested on 1809) determines the window border size by the main display scaling, rounded down. This value is
  // calculated once on desktop session start, so it should be okay to store once per IDE session.
  val scale = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleY
  floor(scale).toInt()
}

internal fun updateWinControlsTheme(background: Color, customTitleBar: CustomTitleBar) {
  customTitleBar.putProperty("controls.dark", ColorUtil.isDark(background))
  customTitleBar.putProperty("controls.background.hovered", UIManager.getColor("TitlePane.Button.hoverBackground"))
}

internal sealed class CustomHeader(@JvmField internal val window: Window) : JPanel() {
  companion object {
    val H: Int
      get() = 12
    val V: Int
      get() = 5

    val LABEL_BORDER: JBEmptyBorder
      get() = JBUI.Borders.empty(V, 0)

    internal fun createCloseAction(header: CustomHeader): Action {
      return CustomFrameAction(name = CommonBundle.getCloseButtonText(), icon = AllIcons.Windows.CloseSmall, action = header::close)
    }

    fun enableCustomHeader(w: Window) {
      JBR.getWindowDecorations()?.let {
        val bar = it.createCustomTitleBar()
        bar.height = 1f
        if (w is Dialog) {
          it.setCustomTitleBar(w, bar)
        }
        else if (w is Frame) {
          it.setCustomTitleBar(w, bar)
        }
      }
    }

    fun ensureClickTransparent(originalComponent: Component) {
      var cmp = originalComponent.parent
      while (cmp != null) {
        if (cmp is CustomHeader) {
          cmp.customTitleBar?.let { bar ->
            val listener = HeaderClickTransparentListener(bar)
            originalComponent.addMouseListener(listener)
            originalComponent.addMouseMotionListener(listener)
          }
          return
        }
        cmp = cmp.parent
      }
    }
  }

  private val windowListener = object : WindowAdapter() {
    override fun windowActivated(ev: WindowEvent?) {
      setActive(true)
    }

    override fun windowDeactivated(ev: WindowEvent?) {
      setActive(false)
    }

    override fun windowStateChanged(e: WindowEvent?) {
      windowStateChanged()
    }
  }

  private val componentListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      SwingUtilities.invokeLater { updateCustomTitleBar() }
    }
  }

  private val iconProvider = ScaleContextCache {
    loadSmallApplicationIcon(scaleContext = it)
  }

  @JvmField
  internal var isActive = false

  private var customFrameTopBorder: CustomFrameTopBorder? = null

  @ApiStatus.Internal
  val customTitleBar: CustomTitleBar?

  protected val productIcon: JComponent by lazy {
    createProductIcon()
  }

  init {
    isOpaque = true
    background = getHeaderBackground()
    isActive = window.isActive

    setCustomFrameTopBorder()

    customTitleBar = JBR.getWindowDecorations()?.createCustomTitleBar()
  }

  override fun updateUI() {
    super.updateUI()
    customTitleBar?.let {
      updateWinControlsTheme(background = background, customTitleBar = it)
    }

    updateSize()
  }

  private fun updateSize() {
    if (!ExperimentalUI.isNewUI()) {
      return
    }

    val size = preferredSize
    val height = calcHeight()
    size.height = JBUI.scale(height)
    preferredSize = size
    minimumSize = size
  }

  protected open fun calcHeight(): Int = CustomWindowHeaderUtil.getPreferredWindowHeaderHeight(isCompactHeader(UISettings.getInstance()))

  protected open fun getHeaderBackground(active: Boolean = true) = JBUI.CurrentTheme.CustomFrameDecorations.titlePaneBackground(active)

  protected fun setCustomFrameTopBorder(isTopNeeded: () -> Boolean = { true }, isBottomNeeded: () -> Boolean = { false }) {
    customFrameTopBorder = CustomFrameTopBorder(isTopNeeded = isTopNeeded, isBottomNeeded = isBottomNeeded, header = this)
    border = customFrameTopBorder
  }

  open fun windowStateChanged() {
    updateCustomTitleBar()
  }

  protected var added = false

  override fun addNotify() {
    super.addNotify()
    added = true
    installListeners()
    updateCustomTitleBar()
    customFrameTopBorder!!.addNotify()
  }

  override fun removeNotify() {
    added = false
    super.removeNotify()
    uninstallListeners()
    customFrameTopBorder!!.removeNotify()
  }

  protected open fun installListeners() {
    updateActive()

    window.addWindowListener(windowListener)
    window.addWindowStateListener(windowListener)
    window.addComponentListener(componentListener)
  }

  protected open fun uninstallListeners() {
    window.removeWindowListener(windowListener)
    window.removeWindowStateListener(windowListener)
    window.removeComponentListener(componentListener)
  }

  protected open fun updateCustomTitleBar() {
    if (!added || customTitleBar == null) {
      return
    }

    if ((window is JDialog && window.isUndecorated) || (window is JFrame && window.isUndecorated)) {
      setCustomTitleBar(null)
    }
    else {
      if (height == 0) {
        return
      }

      customTitleBar.height = (height - insets.bottom).toFloat()
      setCustomTitleBar(customTitleBar)
    }
  }

  private fun setCustomTitleBar(titleBar: CustomTitleBar?) {
    JBR.getWindowDecorations()?.let {
      if (window is Dialog) {
        it.setCustomTitleBar(window, titleBar)
      }
      else if (window is Frame) {
        it.setCustomTitleBar(window, titleBar)
      }
    }
  }

  private fun setActive(value: Boolean) {
    isActive = value
    updateActive()
    updateCustomTitleBar()
  }

  protected open fun updateActive() {
    customFrameTopBorder?.repaintBorder()

    val headerBackground = getHeaderBackground(isActive)
    background = headerBackground
    customTitleBar?.let {
      updateWinControlsTheme(background = headerBackground, customTitleBar = it)
    }
  }

  protected fun close() {
    window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
  }

  protected class CustomFrameAction(@NlsActions.ActionText name: String, icon: Icon, val action: () -> Unit) : AbstractAction(name, icon) {
    override fun actionPerformed(e: ActionEvent) = action()
  }

  private fun createProductIcon(): JComponent {
    val menu = JPopupMenu()
    MnemonicHelper.init(menu)

    val ic = object : JLabel() {
      override fun getIcon(): Icon {
        return iconProvider.getOrProvide(ScaleContext.create(window))!!
      }
    }

    if (ApplicationManager.getApplication()?.isInternal == true) {
      @Suppress("HardCodedStringLiteral")
      ic.accessibleContext.accessibleName = "Application icon"
    }

    ic.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        JBPopupMenu.showBelow(ic, menu)
      }
    })

    menu.isFocusable = false
    menu.isBorderPainted = true

    addMenuItems(menu)
    return ic
  }

  open fun addMenuItems(menu: JPopupMenu) {
    val closeMenuItem = menu.add(createCloseAction(this))
    closeMenuItem.font = JBFont.label().deriveFont(Font.BOLD)
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleCustomHeader()
      accessibleContext.accessibleName = UIBundle.message("frame.header.accessible.group.name")
    }
    return accessibleContext
  }

  private inner class AccessibleCustomHeader : AccessibleJPanel() {
    override fun getAccessibleRole() = AccessibilityUtils.GROUPED_ELEMENTS
  }
}

internal class CustomFrameTopBorder(@JvmField val isTopNeeded: () -> Boolean = { true },
                                    @JvmField val isBottomNeeded: () -> Boolean = { false },
                                    @JvmField val header: CustomHeader) : Border {
  // The bottom border is a line between a window title/main menu area and the frame content.
  private val bottomBorderWidthLogicalPx = JBUI.scale(1)

  // In reality, Windows uses #262626 with alpha-blending with alpha=0.34, but we have no (easy) way of doing the same, so let's just
  // use the value on a white background (since it is most noticeable on white).
  //
  // Unfortunately, DWM doesn't offer an API to determine this value, so it has to be hardcoded here.
  private val defaultActiveBorder = Color(0x707070)
  private val inactiveColor = Color(0xaaaaaa)

  private val menuBarBorderColor = JBColor.namedColor("MenuBar.borderColor", JBColor(Gray.xCD, Gray.x51))
  private var colorizationAffectsBorders: Boolean = false
  private var activeColor = defaultActiveBorder

  private fun calculateAffectsBorders(): Boolean {
    if (SystemInfoRt.isWindows) {
      val windowsBuild = SystemInfo.getWinBuildNumber() ?: 0
      if (windowsBuild < 17763) {
        // should always be active on older versions on Windows
        return true
      }
    }
    return Toolkit.getDefaultToolkit().getDesktopProperty("win.dwm.colorizationColor.affects.borders") as Boolean? ?: true
  }

  private fun calculateActiveBorderColor(): Color {
    if (!colorizationAffectsBorders) {
      return defaultActiveBorder
    }

    try {
      val toolkit = Toolkit.getDefaultToolkit()
      val colorizationColor = toolkit.getDesktopProperty("win.dwm.colorizationColor") as Color?
      if (colorizationColor != null) {
        // The border color is a result of an alpha blend of colorization color and #D9D9D9 with the alpha value set by the
        // colorization color balance.
        var colorizationColorBalance = toolkit.getDesktopProperty("win.dwm.colorizationColorBalance") as Int?
        if (colorizationColorBalance != null) {
          if (colorizationColorBalance > 100) {
            // May be caused by custom Windows themes installed.
            colorizationColorBalance = 100
          }

          // If the desktop setting "Automatically pick an accent color from my background" is active, then the border
          // color should be the same as the colorization color read from the registry. To detect that setting, we use the
          // fact that colorization color balance is set to 0xfffffff3 when the setting is active.
          if (colorizationColorBalance < 0)
            colorizationColorBalance = 100

          return when (colorizationColorBalance) {
            0 -> Color(0xD9D9D9)
            100 -> colorizationColor
            else -> {
              val alpha = colorizationColorBalance / 100.0f
              val remainder = 1 - alpha
              val r = (colorizationColor.red * alpha + 0xD9 * remainder).roundToInt()
              val g = (colorizationColor.green * alpha + 0xD9 * remainder).roundToInt()
              val b = (colorizationColor.blue * alpha + 0xD9 * remainder).roundToInt()
              Color(r, g, b)
            }
          }
        }
      }

      return colorizationColor
             ?: toolkit.getDesktopProperty("win.frame.activeBorderColor") as Color?
             ?: menuBarBorderColor
    }
    catch (t: Throwable) {
      // Should be as fail-safe as possible, since any errors during border coloring could lead to an IDE being broken.
      logger<CustomHeader>().error(t)
      return defaultActiveBorder
    }
  }

  private fun calculateWindowBorderThicknessInLogicalPx(): Double {
    return windowBorderThicknessInPhysicalPx.toDouble() / JBUIScale.sysScale(header.window)
  }

  private val listeners = mutableListOf<Pair<String, PropertyChangeListener>>()
  private inline fun listenForPropertyChanges(vararg propertyNames: String, crossinline action: () -> Unit) {
    val toolkit = Toolkit.getDefaultToolkit()
    val listener = PropertyChangeListener { action() }
    for (property in propertyNames) {
      toolkit.addPropertyChangeListener(property, listener)
      listeners.add(property to listener)
    }
  }

  fun addNotify() {
    colorizationAffectsBorders = calculateAffectsBorders()
    listenForPropertyChanges("win.dwm.colorizationColor.affects.borders") {
      colorizationAffectsBorders = calculateAffectsBorders()
      activeColor = calculateActiveBorderColor() // active border color is dependent on whether colorization affects borders or not
    }

    activeColor = calculateActiveBorderColor()
    listenForPropertyChanges("win.dwm.colorizationColor", "win.dwm.colorizationColorBalance", "win.frame.activeBorderColor") {
      activeColor = calculateActiveBorderColor()
    }
  }

  fun removeNotify() {
    val toolkit = Toolkit.getDefaultToolkit()
    for ((propertyName, listener) in listeners) {
      toolkit.removePropertyChangeListener(propertyName, listener)
    }
    listeners.clear()
  }

  fun repaintBorder() {
    val borderInsets = getBorderInsets(header)

    val thickness = calculateWindowBorderThicknessInLogicalPx()
    header.repaint(0, 0, header.width, ceil(thickness).toInt())
    header.repaint(0, header.height - borderInsets.bottom, header.width, borderInsets.bottom)
  }

  private val shouldDrawTopBorder: Boolean
    get() {
      val drawTopBorderActive = header.isActive && (colorizationAffectsBorders || UIUtil.isUnderIntelliJLaF()) // omit in Darcula with colorization disabled
      val drawTopBorderInactive = !header.isActive && UIUtil.isUnderIntelliJLaF()
      return drawTopBorderActive || drawTopBorderInactive
    }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val thickness = calculateWindowBorderThicknessInLogicalPx()
    if (isTopNeeded() && shouldDrawTopBorder) {
      g.color = if (header.isActive) activeColor else inactiveColor
      LinePainter2D.paint(g as Graphics2D, x.toDouble(), y.toDouble(), width.toDouble(), y.toDouble(), LinePainter2D.StrokeType.INSIDE,
                          thickness)
    }

    if (isBottomNeeded()) {
      g.color = menuBarBorderColor
      val y1 = y + height - bottomBorderWidthLogicalPx
      LinePainter2D.paint(g as Graphics2D, x.toDouble(), y1.toDouble(), width.toDouble(), y1.toDouble())
    }
  }

  override fun getBorderInsets(c: Component): Insets {
    val thickness = calculateWindowBorderThicknessInLogicalPx()
    val top = if (isTopNeeded() && (colorizationAffectsBorders || StartupUiUtil.isUnderIntelliJLaF())) ceil(thickness).toInt() else 0
    val bottom = if (isBottomNeeded()) bottomBorderWidthLogicalPx else 0
    val left = header.customTitleBar?.leftInset?.toInt() ?: 0
    val right = header.customTitleBar?.rightInset?.toInt() ?: 0
    return Insets(top, left, bottom, right)
  }

  override fun isBorderOpaque(): Boolean = true
}


