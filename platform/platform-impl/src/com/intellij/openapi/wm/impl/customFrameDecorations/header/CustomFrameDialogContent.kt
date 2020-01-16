// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Container
import java.awt.Window
import javax.swing.*

class CustomFrameDialogContent private constructor(val window: Window, content: Container, titleBackgroundColor: Color? = null): JPanel() {
    companion object {
        @JvmStatic
        fun getCustomContentHolder(window: Window, content: JComponent) = getCustomContentHolder(window, content, null)

        @JvmStatic
        fun getCustomContentHolder(window: Window, content: JComponent, titleBackgroundColor: Color? = null): JComponent {
            if (content is CustomFrameDialogContent) return content

            when (window) {
                is JWindow -> window.rootPane
                is JDialog -> {
                    if (window.isUndecorated) null
                    else window.rootPane
                }
                is JFrame -> window.rootPane
                else -> null
            } ?: return content

            return CustomFrameDialogContent(window, content, titleBackgroundColor)
        }
    }

    private val header: CustomHeader = CustomHeader.create(window)

    init {
        layout = MigLayout("novisualpadding, ins 0, gap 0, fill, flowy, hidemode 2", "", "[min!][]")
        titleBackgroundColor?.let {
            header.background = it
        }

        val pane = JPanel(MigLayout("fill, ins 0, novisualpadding", "[grow]"))
        pane.isOpaque = false
        pane.add(content, "grow")

        add(header, "growx")
        add(pane, "wmin 100, grow")
    }

    fun updateLayout() {
        if(window is JDialog && window.isUndecorated) header.isVisible = false
    }

    val headerHeight: Int
        get() = if(header.isVisible) header.preferredSize.height else 0
}
