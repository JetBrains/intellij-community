// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.ui.awt.RelativeRectangle
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.beans.PropertyChangeListener
import java.util.ArrayList
import javax.swing.*

class DialogHeader(val window: Window) : CustomHeader(window) {
    private val titleLabel = JLabel()
    private val titleChangeListener = PropertyChangeListener{
        titleLabel.text = getTitle()
    }

    init {
        layout = MigLayout("novisualpadding, ins 0, fillx, gap 0", "$H_GAP[min!]$H_GAP[][pref!]", "")
        titleLabel.text = getTitle()

        add(productIcon)
        add(titleLabel, "wmin 0, left, hmin $MIN_HEIGHT")
        add(buttonPanes.getView(), "top, wmin pref")
    }

    override fun installListeners() {
        super.installListeners()
        window.addPropertyChangeListener("title", titleChangeListener)
    }

    override fun uninstallListeners() {
        super.uninstallListeners()
        window.removePropertyChangeListener(titleChangeListener)
    }

    override fun createButtonsPane(): CustomFrameTitleButtons = CustomFrameTitleButtons.create(myCloseAction)

    override fun updateActive() {
        titleLabel.foreground = if (myActive) UIManager.getColor("Panel.foreground") else UIManager.getColor("Label.disabledForeground")
        super.updateActive()
    }

    override fun windowStateChanged() {
        super.windowStateChanged()
        titleLabel.text = getTitle()
    }

    override fun addNotify() {
        super.addNotify()
        titleLabel.text = getTitle()
    }

    private fun getTitle(): String? {
        when (window) {
            is Dialog -> return window.title
            else -> return ""
        }
    }

    override fun getHitTestSpots(): List<RelativeRectangle> {
        val hitTestSpots = ArrayList<RelativeRectangle>()

        hitTestSpots.add(RelativeRectangle(productIcon))
        hitTestSpots.add(RelativeRectangle(buttonPanes.getView()))

        return hitTestSpots
    }
}