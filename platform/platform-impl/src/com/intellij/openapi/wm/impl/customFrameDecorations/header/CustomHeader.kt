// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.jdkEx.JdkEx
import com.intellij.jna.JnaLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.WindowsRegistryUtil
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.ui.AppUIUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.*
import javax.swing.border.Border
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

abstract class CustomHeader(private val window: Window) : JPanel(), Disposable {
    companion object {
        private val LOGGER = logger<CustomHeader>()

        val H
            get() = 7
        val V
            get() = 5


        val LABEL_BORDER get() = JBUI.Borders.empty(V, 0)

        val WINDOWS_VERSION = getWindowsReleaseId()

        private fun getWindowsReleaseId(): String? {
            try {
                if (JnaLoader.isLoaded()) {
                    return Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                                                               "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                                                               "ReleaseId")
                }
            }
            catch (e: Throwable) {
                LOGGER.warn(e)
            }
            return WindowsRegistryUtil.readRegistryValue("HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "ReleaseId")
        }

        fun create(window: Window): CustomHeader {
            return if (window is JFrame) {
                if(window.rootPane is IdeRootPane) {
                    createMainFrameHeader(window, null)
                } else {
                    createFrameHeader(window)
                }
            } else {
                DialogHeader(window)
            }
        }

        private fun createFrameHeader(frame: JFrame): DefaultFrameHeader = DefaultFrameHeader(frame)
        @JvmStatic
        fun createMainFrameHeader(frame: JFrame, delegatingMenuBar: IdeMenuBar?): MainFrameHeader = MainFrameHeader(frame, delegatingMenuBar)

        private val windowBorderThicknessInPhysicalPx: Int = run {
            // Windows 10 (tested on 1809) determines the window border size by the main display scaling, rounded down. This value is
            // calculated once on desktop session start, so it should be okay to store once per IDE session.
            val scale = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleY
            floor(scale).toInt()
        }
    }

    private var windowListener: WindowAdapter
    private val myComponentListener: ComponentListener
    private val myIconProvider = ScaleContext.Cache { ctx -> getFrameIcon(ctx) }

    protected var myActive = false
    protected val windowRootPane: JRootPane? = when (window) {
        is JWindow -> window.rootPane
        is JDialog -> window.rootPane
        is JFrame -> window.rootPane
        else -> null
    }

    private var customFrameTopBorder: CustomFrameTopBorder? = null

    private val icon: Icon
        get() = getFrameIcon()

    protected val iconSize = (16 * UISettings.defFontScale).toInt()

    private fun getFrameIcon(): Icon {
        val ctx = ScaleContext.create(window)
        ctx.overrideScale(ScaleType.USR_SCALE.of(UISettings.defFontScale.toDouble()))
        return myIconProvider.getOrProvide(ctx)!!
    }

    protected open fun getFrameIcon(ctx: ScaleContext): Icon {
        return AppUIUtil.loadSmallApplicationIcon(ctx, iconSize)
    }

    protected val productIcon: JComponent by lazy {
        createProductIcon()
    }

    protected val buttonPanes: CustomFrameTitleButtons by lazy {
        createButtonsPane()
    }

    init {
        isOpaque = true
        background = JBUI.CurrentTheme.CustomFrameDecorations.titlePaneBackground()

        fun onClose() {
            Disposer.dispose(this)
        }

        windowListener = object : WindowAdapter() {
            override fun windowActivated(ev: WindowEvent?) {
                setActive(true)
            }

            override fun windowDeactivated(ev: WindowEvent?) {
                setActive(false)
            }

            override fun windowClosed(e: WindowEvent?) {
                onClose()
            }

            override fun windowStateChanged(e: WindowEvent?) {
                windowStateChanged()
            }
        }

        myComponentListener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
               SwingUtilities.invokeLater{updateCustomDecorationHitTestSpots()}
            }
        }

        setCustomFrameTopBorder()
    }

    protected fun setCustomFrameTopBorder(isTopNeeded: ()-> Boolean = {true}, isBottomNeeded: ()-> Boolean = {false}) {
        customFrameTopBorder = CustomFrameTopBorder(isTopNeeded, isBottomNeeded)
        border = customFrameTopBorder
    }


    abstract fun createButtonsPane(): CustomFrameTitleButtons

    open fun windowStateChanged() {
      updateCustomDecorationHitTestSpots()
    }

    private var added = false

    override fun addNotify() {
        super.addNotify()
        added = true
        installListeners()
        updateCustomDecorationHitTestSpots()
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
        window.addComponentListener(myComponentListener)
    }

    protected open fun uninstallListeners() {
        window.removeWindowListener(windowListener)
        window.removeWindowStateListener(windowListener)
        window.removeComponentListener(myComponentListener)
    }

    protected fun updateCustomDecorationHitTestSpots() {
        if(!added) return
        if ((window is JDialog && window.isUndecorated) ||
            (window is JFrame && window.isUndecorated)) {
            JdkEx.setCustomDecorationHitTestSpots(window, Collections.emptyList())
            JdkEx.setCustomDecorationTitleBarHeight(window, 0)
        } else {
            val toList = getHitTestSpots().map { it.getRectangleOn(window) }.toList()
            JdkEx.setCustomDecorationHitTestSpots(window, toList)
            JdkEx.setCustomDecorationTitleBarHeight(window, height)
        }
    }

    abstract fun getHitTestSpots(): List<RelativeRectangle>

    private fun setActive(value: Boolean) {
        myActive = value
        updateActive()
        updateCustomDecorationHitTestSpots()
    }

    protected open fun updateActive() {
        buttonPanes.isSelected = myActive
        buttonPanes.updateVisibility()
        customFrameTopBorder?.repaintBorder()

        background = JBUI.CurrentTheme.CustomFrameDecorations.titlePaneBackground(myActive)
    }

    protected val myCloseAction: Action = CustomFrameAction("Close", AllIcons.Windows.CloseSmall) { close() }

    protected fun close() {
        window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }

    override fun dispose() {
    }

    protected class CustomFrameAction(name: String, icon: Icon, val action: () -> Unit) : AbstractAction(name, icon) {
        override fun actionPerformed(e: ActionEvent) = action()
    }

    private fun createProductIcon(): JComponent {
        val menu = JPopupMenu()

        val ic = object :  JLabel(){
            override fun getIcon(): Icon {
                return this@CustomHeader.icon
            }
        }
        ic.addMouseListener(object : MouseAdapter(){
            override fun mousePressed(e: MouseEvent?) {
                menu.show(ic, 0, ic.height)
            }
        })

        menu.isFocusable = false
        menu.isBorderPainted = true

        addMenuItems(menu)
        return ic
    }

    open fun addMenuItems(menu: JPopupMenu) {
        val closeMenuItem = menu.add(myCloseAction)
        closeMenuItem.font = JBFont.label().deriveFont(Font.BOLD)
    }

    inner class CustomFrameTopBorder(val isTopNeeded: ()-> Boolean = {true}, val isBottomNeeded: ()-> Boolean = {false}) : Border {

        // Bottom border is a line between a window title/main menu area and the frame content.
        private val bottomBorderWidthLogicalPx = JBUI.scale(1)

        // In reality, Windows uses #262626 with alpha-blending with alpha=0.34, but we have no (easy) way of doing the same, so let's just
        // use the value on white background (since it is most noticeable on white).
        //
        // Unfortunately, DWM doesn't offer an API to determine this value, so it has to be hardcoded here.
        private val defaultActiveBorder = Color(0x707070)
        private val inactiveColor = Color(0xaaaaaa)

        private val menuBarBorderColor: Color = JBColor.namedColor("MenuBar.borderColor", JBColor(Gray.xCD, Gray.x51))
        private var colorizationAffectsBorders: Boolean = false
        private var activeColor: Color = defaultActiveBorder

        private fun calculateAffectsBorders(): Boolean {
            val windowsVersion = WINDOWS_VERSION?.toIntOrNull() ?: 0
            if (windowsVersion < 1809) return true // should always be active on older versions on Windows
            return Toolkit.getDefaultToolkit().getDesktopProperty("win.dwm.colorizationColor.affects.borders") as Boolean? ?: true
        }

        private fun calculateActiveBorderColor(): Color {
            if (!colorizationAffectsBorders)
                return defaultActiveBorder

            Toolkit.getDefaultToolkit().apply {
                val colorizationColor = getDesktopProperty("win.dwm.colorizationColor") as Color?
                if (colorizationColor != null) {
                    // The border color is a result of an alpha blend of colorization color and #D9D9D9 with the alpha value set by the
                    // colorization color balance.
                    var colorizationColorBalance = getDesktopProperty("win.dwm.colorizationColorBalance") as Int?
                    if (colorizationColorBalance != null) {
                        // If the desktop setting "Automatically pick an accent color from my background" is active, then the border color
                        // should be the same as the colorization color read from the registry. To detect that setting, we use the fact that
                        // colorization color balance is set to 0xfffffff3 when the setting is active.
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
                       ?: getDesktopProperty("win.frame.activeBorderColor") as Color?
                       ?: menuBarBorderColor
            }
        }

        private fun calculateWindowBorderThicknessInLogicalPx(): Double {
            return windowBorderThicknessInPhysicalPx.toDouble() / JBUIScale.sysScale(window)
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
            for ((propertyName, listener) in listeners)
                toolkit.removePropertyChangeListener(propertyName, listener)
            listeners.clear()
        }

        fun repaintBorder() {
            val borderInsets = getBorderInsets(this@CustomHeader)

            val thickness = calculateWindowBorderThicknessInLogicalPx()
            repaint(0, 0, width, ceil(thickness).toInt())
            repaint(0, height - borderInsets.bottom, width, borderInsets.bottom)
        }

        private val shouldDrawTopBorder: Boolean
            get() {
                val drawTopBorderActive = myActive && (colorizationAffectsBorders || UIUtil.isUnderIntelliJLaF()) // omit in Darcula with colorization disabled
                val drawTopBorderInactive = !myActive && UIUtil.isUnderIntelliJLaF()
                return drawTopBorderActive || drawTopBorderInactive
            }

        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            val thickness = calculateWindowBorderThicknessInLogicalPx()
            if (isTopNeeded() && shouldDrawTopBorder) {
                g.color = if (myActive) activeColor else inactiveColor
                LinePainter2D.paint(g as Graphics2D, x.toDouble(), y.toDouble(), width.toDouble(), y.toDouble(), LinePainter2D.StrokeType.INSIDE, thickness)
            }

            if (isBottomNeeded()) {
                g.color = menuBarBorderColor
                val y1 = y + height - bottomBorderWidthLogicalPx
                LinePainter2D.paint(g as Graphics2D, x.toDouble(), y1.toDouble(), width.toDouble(), y1.toDouble())
            }
        }

        override fun getBorderInsets(c: Component): Insets {
            val thickness = calculateWindowBorderThicknessInLogicalPx()
            val top = if (isTopNeeded() && (colorizationAffectsBorders || UIUtil.isUnderIntelliJLaF())) ceil(thickness).toInt() else 0
            val bottom = if (isBottomNeeded()) bottomBorderWidthLogicalPx else 0
            return Insets(top, 0, bottom, 0)
        }

        override fun isBorderOpaque(): Boolean {
            return true
        }
    }
}

