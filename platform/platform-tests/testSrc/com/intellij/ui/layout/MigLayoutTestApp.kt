// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ui.components.CheckBox
import net.miginfocom.layout.LayoutUtil
import java.awt.Color
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JTextField

object MigLayoutTestApp {
  @JvmStatic
  fun main(args: Array<String>) {
    LayoutUtil.setGlobalDebugMillis(1000)

    val androidModuleNameComponent = JTextField("input")
    val androidCheckBox = CheckBox("Android module name:")
    val panel = panel {
      row("Create Android module") { androidCheckBox() }
      row("Android module name:") { androidModuleNameComponent() }
    }

    val frame = JFrame()
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane = panel
    frame.contentPane.background = Color.WHITE
    frame.background = Color.WHITE
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.minimumSize = Dimension(512, 256)
    frame.isVisible = true
  }
}