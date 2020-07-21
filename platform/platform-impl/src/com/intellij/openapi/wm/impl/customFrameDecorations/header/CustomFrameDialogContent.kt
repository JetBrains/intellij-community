// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.wm.impl.IdeMenuBar
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Window
import javax.swing.*

class CustomFrameDialogContent private constructor(val window: Window, val header: CustomHeader, content: Container): JPanel() {
    companion object {
        @JvmStatic
        fun getCustomContentHolder(window: Window, content: JComponent) = getCustomContentHolder(window, content, null)
        fun getCustomContentHolder(window: Window, content: JComponent, myIdeMenu: IdeMenuBar) = getCustomContentHolder(window, content,
                                                                                                                        null, myIdeMenu)

        @JvmStatic
        fun getCustomContentHolder(window: Window,
                                   content: JComponent,
                                   titleBackgroundColor: Color? = null,
                                   myIdeMenu: IdeMenuBar? = null): JComponent {
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

            val header: CustomHeader = if(window is JFrame && myIdeMenu != null) CustomHeader.create(window, myIdeMenu) else CustomHeader.create(window)
            titleBackgroundColor?.let {
                header.background = it
            }

            return CustomFrameDialogContent(window, header, content)
        }
    }

    init {
        layout = BorderLayout()
        add(header, BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)
    }

    fun updateLayout() {
        if(window is JDialog && window.isUndecorated) header.isVisible = false
    }

    val headerHeight: Int
        get() = if(header.isVisible) header.preferredSize.height else 0
}
