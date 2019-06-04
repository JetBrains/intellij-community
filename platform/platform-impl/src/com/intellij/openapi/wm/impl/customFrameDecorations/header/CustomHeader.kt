// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.icons.AllIcons
import com.intellij.jdkEx.JdkEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.WindowsRegistryUtil
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.ui.AppUIUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.Border

abstract class CustomHeader(private val window: Window) : JPanel(), Disposable {
    companion object {
        const val H_GAP = 7
        const val MIN_HEIGHT = 24
        val HIT_TEST_RESIZE_GAP = JBUI.scale(3)

        fun create(window: Window): CustomHeader {
            return if (window is JFrame) {
                if(window.rootPane is IdeRootPane) {
                    createMainFrameHeader(window)
                } else {
                    createFrameHeader(window)
                }
            } else {
                DialogHeader(window)
            }
        }

        private fun createFrameHeader(frame: JFrame): DefaultFrameHeader = DefaultFrameHeader(frame)
        fun createMainFrameHeader(frame: JFrame): MainFrameHeader = MainFrameHeader(frame)
    }

    private var windowListener: WindowAdapter
    private val myComponentListener: ComponentListener
    private val myIconProvider = ScaleContext.Cache { ctx -> AppUIUtil.loadSmallApplicationIcon(ctx) }

    protected var myActive = false
    protected val windowRootPane: JRootPane? = when (window) {
        is JWindow -> window.rootPane
        is JDialog -> window.rootPane
        is JFrame -> window.rootPane
        else -> null
    }

    private var customFrameTopBorder: CustomFrameTopBorder? = null

    private val icon: Icon
        get() {
            val ctx = ScaleContext.create(window)
            ctx.overrideScale(ScaleType.USR_SCALE.of(1.0))
            return myIconProvider.getOrProvide(ctx)!!
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
                updateCustomDecorationHitTestSpots()
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

    }

    override fun addNotify() {
        super.addNotify()
        installListeners()
        updateCustomDecorationHitTestSpots()
    }

    override fun removeNotify() {
        super.removeNotify()
        uninstallListeners()
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
        val toList = getHitTestSpots().map {it.getRectangleOn(window)}.toList()
        JdkEx.setCustomDecorationHitTestSpots(window, toList)
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
        val myMenuBar = object : JMenuBar() {
            override fun getPreferredSize(): Dimension {
                return minimumSize
            }

            override fun getMinimumSize(): Dimension {
                return Dimension(icon.iconWidth, icon.iconHeight)
            }

            override fun paint(g: Graphics?) {
                icon.paintIcon(this, g, 0, 0)
            }
        }

        val menu = object : JMenu() {
            override fun getPreferredSize(): Dimension {
                return myMenuBar.preferredSize
            }
        }
        myMenuBar.add(menu)

        myMenuBar.isOpaque = false
        menu.isFocusable = false
        menu.isBorderPainted = true

        addMenuItems(menu)

        return myMenuBar
    }

    open fun addMenuItems(menu: JMenu) {
        val closeMenuItem = menu.add(myCloseAction)
        closeMenuItem.font = JBFont.label().deriveFont(Font.BOLD)
    }

    inner class CustomFrameTopBorder(val isTopNeeded: ()-> Boolean = {true}, val isBottomNeeded: ()-> Boolean = {false}) : Border {
        val thickness = 1
        private val menuBarBorderColor: Color = JBColor.namedColor("MenuBar.borderColor", JBColor(Gray.xCD, Gray.x51))
        private val affectsBorders: Boolean = Toolkit.getDefaultToolkit().getDesktopProperty("win.dwm.colorizationColor.affects.borders") as Boolean? ?: true
        private val activeColor = Toolkit.getDefaultToolkit().getDesktopProperty("win.dwm.colorizationColor") as Color? ?:
         Toolkit.getDefaultToolkit().getDesktopProperty("win.frame.activeBorderColor") as Color? ?: menuBarBorderColor

        private val windowsVersion = WindowsRegistryUtil.readRegistryValue("HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "ReleaseId")

        fun repaintBorder() {
            val borderInsets = getBorderInsets(this@CustomHeader)

            repaint(0, 0, width, borderInsets.top)
            repaint(0, height - borderInsets.bottom, width, borderInsets.bottom)
        }

        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            if (isTopNeeded() && myActive && isAffectsBorder()) {
                g.color = activeColor
                LinePainter2D.paint(g as Graphics2D, x.toDouble(), y.toDouble(), width.toDouble(), y.toDouble())
            }

            if (isBottomNeeded()) {
                g.color = menuBarBorderColor
                val y1 = y + height - JBUI.scale(thickness)
                LinePainter2D.paint(g as Graphics2D, x.toDouble(), y1.toDouble(), width.toDouble(), y1.toDouble())
            }
        }

      private fun isAffectsBorder(): Boolean {
        if(windowsVersion.isNullOrEmpty()) return true

        val winVersion =  windowsVersion.toIntOrNull() ?: return affectsBorders
        return if(winVersion >= 1809) affectsBorders else true
      }

        override fun getBorderInsets(c: Component): Insets {
            val scale = JBUI.scale(thickness)
            return Insets(if (isTopNeeded() && isAffectsBorder()) thickness else 0, 0, if (isBottomNeeded()) scale else 0, 0)
        }

        override fun isBorderOpaque(): Boolean {
            return true
        }
    }
}

