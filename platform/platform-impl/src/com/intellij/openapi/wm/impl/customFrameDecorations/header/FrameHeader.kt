// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.icons.AllIcons
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.ResizableCustomFrameTitleButtons
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBFont
import java.awt.Font
import java.awt.Frame
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowStateListener
import java.util.*
import javax.swing.Action
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JSeparator

open class FrameHeader(val frame: JFrame) : CustomHeader(frame) {
    private val myIconifyAction: Action = CustomFrameAction("Minimize", AllIcons.Windows.MinimizeSmall) { iconify() }
    private val myRestoreAction: Action = CustomFrameAction("Restore", AllIcons.Windows.RestoreSmall) { restore() }
    private val myMaximizeAction: Action = CustomFrameAction("Maximize", AllIcons.Windows.MaximizeSmall) { maximize() }

    private var windowStateListener: WindowStateListener
    protected var myState = 0

    init {
        windowStateListener = object : WindowAdapter() {
            override fun windowStateChanged(e: java.awt.event.WindowEvent?) {
                updateActions()
            }
        }
     }

    override fun createButtonsPane(): CustomFrameTitleButtons = ResizableCustomFrameTitleButtons.create(myCloseAction,
            myRestoreAction, myIconifyAction, myMaximizeAction)


    override fun windowStateChanged() {
        super.windowStateChanged()
        updateActions()
    }

    private fun iconify() {
        frame.extendedState = myState or Frame.ICONIFIED
    }

    private fun maximize() {
        frame.extendedState = myState or Frame.MAXIMIZED_BOTH
    }

    private fun restore() {
        if (myState and Frame.ICONIFIED != 0) {
            frame.extendedState = myState and Frame.ICONIFIED.inv()
        } else {
            frame.extendedState = myState and Frame.MAXIMIZED_BOTH.inv()
        }
    }

    override fun addNotify() {
        super.addNotify()
        updateActions()
    }

    private fun updateActions() {
        myState = frame.extendedState
        if (frame.isResizable) {
            if (myState and Frame.MAXIMIZED_BOTH != 0) {
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
        updateCustomDecorationHitTestSpots()
    }

    override fun addMenuItems(menu: JMenu) {
        menu.add(myRestoreAction)
        menu.add(myIconifyAction)
        if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
            menu.add(myMaximizeAction)
        }

        menu.add(JSeparator())

        val closeMenuItem = menu.add(myCloseAction)
        closeMenuItem.font = JBFont.label().deriveFont(Font.BOLD)
    }

    override fun getHitTestSpots(): ArrayList<RelativeRectangle> {
        val hitTestSpots = ArrayList<RelativeRectangle>()

        hitTestSpots.add(RelativeRectangle(productIcon))
        hitTestSpots.add(RelativeRectangle(buttonPanes.getView()))
        return hitTestSpots
    }
}