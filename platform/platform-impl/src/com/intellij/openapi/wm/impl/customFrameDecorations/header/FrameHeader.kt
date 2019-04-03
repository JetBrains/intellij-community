// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.ResizableCustomFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.titleLabel.CustomDecorationPath
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.Frame.MAXIMIZED_BOTH
import java.awt.Frame.MAXIMIZED_VERT
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import java.util.ArrayList
import javax.swing.*
import javax.swing.border.Border

class FrameHeader(val frame: JFrame) : CustomHeader(frame) {
    private val myIconifyAction: Action = CustomFrameAction("Minimize", AllIcons.Windows.MinimizeSmall) { iconify() }
    private val myRestoreAction: Action = CustomFrameAction("Restore", AllIcons.Windows.RestoreSmall) { restore() }
    private val myMaximizeAction: Action = CustomFrameAction("Maximize", AllIcons.Windows.MaximizeSmall) { maximize() }

    private var windowListener: WindowListener
    private val mySelectedEditorFilePath: CustomDecorationPath
    private val myIdeMenu: IdeMenuBar
    private var myState = -100

    init {
        windowListener = object : WindowAdapter() {
            override fun windowStateChanged(e: java.awt.event.WindowEvent?) {
                updateActions()
            }
        }

        layout = MigLayout("novisualpadding, fillx, ins 0, gap 0, top", "$H_GAP[pref!]$H_GAP[][pref!]")
        add(productIcon)

        myIdeMenu = object : IdeMenuBar(ActionManagerEx.getInstanceEx(), DataManager.getInstance()) {
            override fun getBorder(): Border? {
                return JBUI.Borders.empty()
            }
        }
        mySelectedEditorFilePath = CustomDecorationPath(this)

        val pane = JPanel(MigLayout("fillx, ins 0, novisualpadding", "[pref!][]"))
        pane.isOpaque = false
        pane.add(myIdeMenu, "wmin 0, wmax pref, top, hmin $MIN_HEIGHT")
        pane.add(mySelectedEditorFilePath.getView(), "center, growx, wmin 0, gapbefore $H_GAP, gapafter $H_GAP")

        add(pane, "wmin 0, growx")
        add(buttonPanes.getView(), "top, wmin pref")

        myState = frame.extendedState
        updateActions()
    }

    override fun createButtonsPane(): CustomFrameTitleButtons = ResizableCustomFrameTitleButtons.create(myCloseAction,
            myRestoreAction, myIconifyAction, myMaximizeAction)


    override fun installListeners() {
        frame.addWindowListener(windowListener)
    }

    override fun uninstallListeners() {
        frame.removeWindowListener(windowListener)
    }

    private fun iconify() {
        setExtendedState(myState or Frame.ICONIFIED)
    }

    private fun maximize() {
        setExtendedState(myState or Frame.MAXIMIZED_BOTH)
    }

    private fun restore() {
        if (myState and Frame.ICONIFIED != 0) {
            setExtendedState(myState and Frame.ICONIFIED.inv())
        } else {
            setExtendedState(myState and Frame.MAXIMIZED_BOTH.inv())
        }
    }

    private fun setExtendedState(state: Int) {
        if (myState == state) {
            return
        }

        myState = state
        frame.extendedState = state
        updateActions()
    }

    private fun updateActions() {
        val state = myState
        if (frame.isResizable) {
            if (state and Frame.MAXIMIZED_BOTH != 0) {
                myMaximizeAction.isEnabled = false
                myRestoreAction.isEnabled = true
            } else {
                myMaximizeAction.isEnabled = true
                myRestoreAction.isEnabled = false
            }
        } else {
            myMaximizeAction.isEnabled = false
            myRestoreAction.isEnabled = false
        }
        myIconifyAction.isEnabled = true
        myCloseAction.isEnabled = true

        buttonPanes.updateVisibility()
    }

    override fun addMenuItems(menu: JMenu) {
        menu.add(myRestoreAction)
        menu.add(myIconifyAction)
        if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
            menu.add(myMaximizeAction)
        }

        menu.add(JSeparator())

        val closeMenuItem = menu.add(myCloseAction)
        closeMenuItem.font = JBUI.Fonts.label().deriveFont(Font.BOLD)
    }

    override fun getHitTestSpots(): List<Rectangle> {
        val hitTestSpots = ArrayList<Rectangle>()

        val iconRect = RelativeRectangle(productIcon).getRectangleOn(this)
        val menuRect = RelativeRectangle(myIdeMenu).getRectangleOn(this)
        val buttonsRect = RelativeRectangle(buttonPanes.getView()).getRectangleOn(this)

        val state = frame.extendedState
        if (state != MAXIMIZED_VERT && state != MAXIMIZED_BOTH) {

            if (menuRect != null) {
                menuRect.y += Math.round((menuRect.height / 3).toFloat())
            }

            if (iconRect != null) {
                iconRect.y += HIT_TEST_RESIZE_GAP
                iconRect.x += HIT_TEST_RESIZE_GAP
            }

            if (buttonsRect != null) {
                buttonsRect.y += HIT_TEST_RESIZE_GAP
                buttonsRect.x += HIT_TEST_RESIZE_GAP
                buttonsRect.width -= HIT_TEST_RESIZE_GAP
            }
            hitTestSpots.add(menuRect)

            hitTestSpots.addAll(mySelectedEditorFilePath.getListenerBounds())
            hitTestSpots.add(iconRect)

            hitTestSpots.add(buttonsRect)

        }

        return hitTestSpots
    }
}