// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI
import com.jetbrains.CustomWindowDecoration.*
import net.miginfocom.swing.MigLayout
import java.awt.Dialog
import java.awt.Window
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JLabel
import javax.swing.UIManager

internal class DialogHeader(val window: Window) : CustomHeader(window) {
    private val titleLabel = JLabel().apply {
        border = LABEL_BORDER
    }
    private val titleChangeListener = PropertyChangeListener{
        titleLabel.text = getTitle()
    }

    init {
        layout = MigLayout("novisualpadding, ins 0, fillx, gap 0", "[min!][][pref!]")
        titleLabel.text = getTitle()

        productIcon.border = JBUI.Borders.empty(0, H, 0, H)

        add(productIcon)
        add(titleLabel, "wmin 0, left")
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

    @NlsContexts.DialogTitle
    private fun getTitle(): String? {
        when (window) {
            is Dialog -> return window.title
            else -> return ""
        }
    }

    override fun getHitTestSpots(): List<Pair<RelativeRectangle, Int>> {
        return listOf(
          Pair(RelativeRectangle(productIcon), OTHER_HIT_SPOT),
          Pair(RelativeRectangle(buttonPanes.closeButton), CLOSE_BUTTON)
        )
    }
}