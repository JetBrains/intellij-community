// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.ResizableCustomFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.titleLabel.CustomDecorationPath
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.Frame.MAXIMIZED_BOTH
import java.awt.Frame.MAXIMIZED_VERT
import java.awt.event.WindowAdapter
import java.awt.event.WindowStateListener
import java.util.ArrayList
import javax.swing.*
import javax.swing.border.Border
import javax.swing.event.ChangeListener

class FrameHeader(val frame: JFrame) : CustomHeader(frame) {
    private val myIconifyAction: Action = CustomFrameAction("Minimize", AllIcons.Windows.MinimizeSmall) { iconify() }
    private val myRestoreAction: Action = CustomFrameAction("Restore", AllIcons.Windows.RestoreSmall) { restore() }
    private val myMaximizeAction: Action = CustomFrameAction("Maximize", AllIcons.Windows.MaximizeSmall) { maximize() }

    private var windowStateListener: WindowStateListener
    private var changeListener: ChangeListener
    private val mySelectedEditorFilePath: CustomDecorationPath
    private val myIdeMenu: IdeMenuBar
    private var myState = 0

    init {
        windowStateListener = object : WindowAdapter() {
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

        changeListener = ChangeListener {
            setCustomDecorationHitTestSpots()
        }
        mySelectedEditorFilePath = CustomDecorationPath(this)

        val pane = JPanel(MigLayout("fillx, ins 0, novisualpadding", "[pref!][]"))
        pane.isOpaque = false
        pane.add(myIdeMenu, "wmin 0, wmax pref, top, hmin $MIN_HEIGHT")
        pane.add(mySelectedEditorFilePath.getView(), "center, growx, wmin 0, gapbefore $H_GAP, gapafter $H_GAP, gapbottom 1")

        add(pane, "wmin 0, growx")
        add(buttonPanes.getView(), "top, wmin pref")

        setCustomFrameTopBorder({myState != MAXIMIZED_VERT && myState != MAXIMIZED_BOTH}, {true})
    }

    fun setProject(project: Project) {
        mySelectedEditorFilePath.setProject(project)
    }

    override fun createButtonsPane(): CustomFrameTitleButtons = ResizableCustomFrameTitleButtons.create(myCloseAction,
            myRestoreAction, myIconifyAction, myMaximizeAction)


    override fun installListeners() {
        myIdeMenu.selectionModel.addChangeListener(changeListener)
        super.installListeners()
    }

    override fun uninstallListeners() {
        myIdeMenu.selectionModel.removeChangeListener(changeListener)
        super.uninstallListeners()
    }

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
        setCustomDecorationHitTestSpots()
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
        iconRect.width = (iconRect.width * 1.5).toInt()

        if (state != MAXIMIZED_VERT && state != MAXIMIZED_BOTH) {

            if (menuRect != null /*&& !myIdeMenu.isSelected*/) {
                menuRect.y += Math.round((menuRect.height / 3).toFloat())
            }

            if (buttonsRect != null) {
                buttonsRect.y += HIT_TEST_RESIZE_GAP
                buttonsRect.x += HIT_TEST_RESIZE_GAP
                buttonsRect.width -= HIT_TEST_RESIZE_GAP
            }
        }

        hitTestSpots.add(menuRect)

        hitTestSpots.addAll(mySelectedEditorFilePath.getListenerBounds())
        hitTestSpots.add(iconRect)
        hitTestSpots.add(buttonsRect)
        return hitTestSpots
    }
}