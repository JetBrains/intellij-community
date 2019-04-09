// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.jdkEx.JdkEx
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Container
import java.awt.Window
import javax.swing.*

class CustomFrameDialogContent private constructor(window: Window, content: Container, val titleBackgroundColor: Color? = null) {
    companion object {
        fun getContent(window: Window, content: JComponent) = getContent(window, content, null)

        fun getContent(window: Window, content: JComponent, titleBackgroundColor: Color? = null): JComponent {
            val rootPane: JRootPane? = when (window) {
                is JWindow -> window.rootPane
                is JDialog -> {
                    if (window.isUndecorated) return content
                    window.rootPane
                }
                is JFrame -> window.rootPane
                else -> null
            }

            rootPane ?: return content

            JdkEx.setHasCustomDecoration(window)
            val custom = CustomFrameDialogContent(window, content, titleBackgroundColor)
            return custom.getView()
        }
    }

    private val panel = JPanel(MigLayout("novisualpadding, ins 0, gap 0, fill, flowy", "", "[min!][]"))

    init {
        val header: CustomHeader = CustomHeader.create(window)
        titleBackgroundColor?.let {
            header.background = it
        }

        val pane = JPanel(MigLayout("fill, ins 0, novisualpadding", "[grow]"))
        pane.isOpaque = false
        pane.add(content, "grow")

        panel.add(header, "growx, wmin 100")
        panel.add(pane, "wmin 0, grow")
    }

    fun getView(): JComponent = panel
}