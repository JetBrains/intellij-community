// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.ui.awt.RelativeRectangle
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.util.ArrayList
import javax.swing.*

class DialogHeader(val window: Window) : CustomHeader(window) {
    private val myInactiveForeground = UIManager.getColor("inactiveCaptionText")
    private val myDefaultActiveForeground = UIManager.getColor("activeCaptionText")
    private val titleLabel = JLabel()

    init {
        layout = MigLayout("novisualpadding, ins 0, fillx, gap 0, baseline", "", "$H_GAP[]$H_GAP[][pref!]")
        titleLabel.text = getTitle()
        add(productIcon)
        add(titleLabel, "wmin 0, growx")
        add(buttonPanes.getView(), "top, wmin pref")

        minimumSize = Dimension(minimumSize.width, MIN_HEIGHT)
    }

    override fun createButtonsPane(): CustomFrameTitleButtons = CustomFrameTitleButtons.create(myCloseAction)

    override fun setActive(value: Boolean) {
        titleLabel.foreground = if (value) getActiveColor() else myInactiveForeground
        super.setActive(value)
    }

    private fun getActiveColor(): Color? {
        if (window is JFrame) {

            when (window.rootPane.windowDecorationStyle) {
                JRootPane.ERROR_DIALOG -> return UIManager.getColor("OptionPane.errorDialog.titlePane.foreground")
                JRootPane.QUESTION_DIALOG,
                JRootPane.COLOR_CHOOSER_DIALOG,
                JRootPane.FILE_CHOOSER_DIALOG -> return UIManager.getColor("OptionPane.questionDialog.titlePane.foreground")
                JRootPane.WARNING_DIALOG -> return UIManager.getColor("OptionPane.warningDialog.titlePane.foreground")
                JRootPane.PLAIN_DIALOG,
                JRootPane.INFORMATION_DIALOG -> return UIManager.getColor("activeCaptionText")
            }
        }
        return myDefaultActiveForeground
    }

    private fun getTitle(): String? {
        when (window) {
            is Frame -> return window.title
            is Dialog -> return window.title
            else -> return ""
        }
    }

    override fun getHitTestSpots(): List<Rectangle> {
        val hitTestSpots = ArrayList<Rectangle>()

        hitTestSpots.add(RelativeRectangle(productIcon).getRectangleOn(this))
        hitTestSpots.add(RelativeRectangle(buttonPanes.getView()).getRectangleOn(this))
        return hitTestSpots
    }
}