// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.icons.AllIcons
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Container
import java.awt.Window
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel

class CustomFrameDialogContent(window: Window, content: Container) {
    private val panel = JPanel(MigLayout("novisualpadding, ins 0, gap 0, fill, flowy", "", "[min!][]"))

    init {
        val header: CustomHeader = CustomHeader.create(window)
        val pane = JPanel(MigLayout("fill, ins 0, novisualpadding", "[grow]"))
        pane.isOpaque = false
        pane.add(content, "grow")

        panel.add(header, "growx, wmin 100")
        panel.add(pane, "wmin 0, grow")
    }

    fun getView(): JComponent = panel
}