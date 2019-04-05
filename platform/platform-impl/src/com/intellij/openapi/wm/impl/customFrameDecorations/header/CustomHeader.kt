// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.icons.AllIcons
import com.intellij.jdkEx.JdkEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.ui.AppUIUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUIScale
import java.awt.*
import java.awt.event.*
import javax.swing.*

abstract class CustomHeader(private val window: Window) : JPanel(), Disposable {
    companion object {
        const val H_GAP = 7
        const val MIN_HEIGHT = 24
        val HIT_TEST_RESIZE_GAP = JBUI.scale(3);

        fun create(window: Window): CustomHeader {
            return if (window is JFrame && window.rootPane is IdeRootPane) {
                createFrameHeader(window)
            } else {
                DialogHeader(window)
            }
        }

        fun createFrameHeader(frame: JFrame): FrameHeader = FrameHeader(frame)
    }

    private var windowListener: WindowAdapter
    private val myComponentListener: ComponentListener
    private val myIconProvider = JBUIScale.ScaleContext.Cache { ctx ->
        ObjectUtils.notNull(
                AppUIUtil.loadHiDPIApplicationIcon(ctx, 16), AllIcons.Icon_small)
    }

    protected var myActive = false
    protected val windowRootPane: JRootPane? = when (window) {
        is JWindow -> window.rootPane
        is JDialog -> window.rootPane
        is JFrame -> window.rootPane
        else -> null
    }

    private val icon: Icon
        get() {
            val ctx = JBUIScale.ScaleContext.create(window)
            ctx.overrideScale(JBUIScale.ScaleType.USR_SCALE.of(1.0))
            return myIconProvider.getOrProvide(ctx) ?: AllIcons.Icon_small
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

            override fun windowStateChanged(e: java.awt.event.WindowEvent?) {
                windowStateChanged()
            }
        }



        myComponentListener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                setCustomDecorationHitTestSpots()
            }
        }
    }

    abstract fun createButtonsPane(): CustomFrameTitleButtons

    open fun windowStateChanged() {

    }

    override fun addNotify() {
        super.addNotify()
        installListeners()
    }

    override fun removeNotify() {
        super.removeNotify()
        uninstallListeners()
    }

    protected open fun installListeners() {
        window.addWindowListener(windowListener)
        window.addWindowStateListener(windowListener)
        window.addComponentListener(myComponentListener)
    }

    protected open fun uninstallListeners() {
        window.removeWindowListener(windowListener)
        window.removeWindowStateListener(windowListener)
        window.removeComponentListener(myComponentListener)
    }

    protected fun setCustomDecorationHitTestSpots() {
        JdkEx.setCustomDecorationHitTestSpots(window, getHitTestSpots())
    }

    abstract fun getHitTestSpots(): List<Rectangle>

    protected open fun setActive(value: Boolean) {
        myActive = value
        buttonPanes.isSelected = value
        buttonPanes.updateVisibility()
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
        closeMenuItem.font = JBUI.Fonts.label().deriveFont(Font.BOLD)
    }
}

