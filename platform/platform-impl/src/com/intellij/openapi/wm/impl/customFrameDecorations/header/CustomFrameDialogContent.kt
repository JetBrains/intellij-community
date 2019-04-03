// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.icons.AllIcons
import net.miginfocom.swing.MigLayout
import java.awt.Container
import java.awt.Window
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel

class CustomFrameDialogContent(window: Window, content: Container) {
    private val panel = JPanel(MigLayout("ins 0, gap 0, fill, flowy", "", "[min!][]"))

    init {
        val header: CustomHeader = CustomHeader.create(window)
        panel.add(header, "growx, wmin 200")
        panel.add(content, "grow")
    }

    fun getView(): JComponent = panel
}