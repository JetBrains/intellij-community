// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.jdkEx.JdkEx
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Container
import java.awt.Window
import javax.swing.*

class CustomFrameDialogContent private constructor(window: Window, content: Container, titleBackgroundColor: Color? = null): CustomFrameViewHolder {
    companion object {
        @JvmStatic
        fun getContent(window: Window, content: JComponent) = getContent(window, content, null)

        @JvmStatic
        fun getContent(window: Window, content: JComponent, titleBackgroundColor: Color? = null): JComponent {
            return getCustomContentHolder(window, content, titleBackgroundColor).content
        }

        @JvmStatic
        fun getCustomContentHolder(window: Window, content: JComponent, titleBackgroundColor: Color? = null): CustomFrameViewHolder {
            val rootPane: JRootPane? = when (window) {
                is JWindow -> window.rootPane
                is JDialog -> {
                    if (window.isUndecorated) null
                    else window.rootPane
                }
                is JFrame -> window.rootPane
                else -> null
            }

            rootPane ?: return object : CustomFrameViewHolder {
                override val content: JComponent
                    get() = content
                override val headerHeight: Int
                    get() = 0
            }

            JdkEx.setHasCustomDecoration(window)
            return CustomFrameDialogContent(window, content, titleBackgroundColor)

        }
    }

    private val panel = JPanel(MigLayout("novisualpadding, ins 0, gap 0, fill, flowy", "", "[min!][]"))
    private val header: CustomHeader = CustomHeader.create(window)

    init {
        titleBackgroundColor?.let {
            header.background = it
        }

        val pane = JPanel(MigLayout("fill, ins 0, novisualpadding", "[grow]"))
        pane.isOpaque = false
        pane.add(content, "grow")

        panel.add(header, "growx, wmin 100")
        panel.add(pane, "wmin 0, grow")
    }

    override val content: JComponent
        get() = panel
    override val headerHeight: Int
        get() = header.preferredSize.height
}

interface CustomFrameViewHolder {
    val content: JComponent
    val headerHeight: Int
}
