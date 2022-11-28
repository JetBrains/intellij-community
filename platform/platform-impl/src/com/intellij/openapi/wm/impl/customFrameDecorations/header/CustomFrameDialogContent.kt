// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import java.awt.BorderLayout
import java.awt.Container
import java.awt.Window
import javax.swing.*

internal class CustomFrameDialogContent private constructor(private val window: Window,
                                                            private val header: CustomHeader, content: Container) : JPanel() {
  companion object {
    fun getCustomContentHolder(window: Window, content: JComponent): JComponent {
      val header = if (window is JFrame) DefaultFrameHeader(window) else DialogHeader(window)
      return CustomFrameDialogContent(window = window, header = header, content = content)
    }

    fun getCustomContentHolder(window: Window, content: JComponent, header: CustomHeader): JComponent {
      checkContent(window, content) ?: return content
      return CustomFrameDialogContent(window, header, content)
    }

    private fun checkContent(window: Window,
                             content: JComponent): JComponent? {
      if (content is CustomFrameDialogContent) return null

      return when (window) {
          is JWindow -> window.rootPane
          is JDialog -> {
              if (window.isUndecorated) null
              else window.rootPane
          }
          is JFrame -> window.rootPane
        else -> null
      }
    }
  }

  init {
    layout = BorderLayout()
    add(header, BorderLayout.NORTH)
    add(content, BorderLayout.CENTER)
  }

  fun updateLayout() {
    if (window is JDialog && window.isUndecorated) header.isVisible = false
  }

  val headerHeight: Int
    get() = if (header.isVisible) header.preferredSize.height else 0
}
