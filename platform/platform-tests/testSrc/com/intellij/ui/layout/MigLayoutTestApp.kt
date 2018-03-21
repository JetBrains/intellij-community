// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.RadioButton
import net.miginfocom.layout.LayoutUtil
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JTextField

object MigLayoutTestApp {
  @JvmStatic
  fun main(args: Array<String>) {
    LayoutUtil.setGlobalDebugMillis(1000)

    val panel = panel {
      row { label("Save passwords:") }

      buttonGroup {
        row {
          RadioButton("In KeePass")()
          row("Database:") {
            JTextField()()
            gearButton(
              object : AnAction("Clear") {
                override fun actionPerformed(event: AnActionEvent) {
                }
              },
              object : AnAction("Import") {
                override fun actionPerformed(event: AnActionEvent) {
                }
              }
            )
          }
          row("Master Password:") {
            JBPasswordField()(growPolicy = GrowPolicy.SHORT_TEXT)
          }
          row {
            hint("Stored using weak encryption.")
          }
        }

        row {
          RadioButton("Do not save, forget passwords after restart")()
        }
        row {
          hint("Existing KeePass file will be removed.")
        }
      }
    }

    val frame = JFrame()
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane.add(panel, BorderLayout.CENTER)
    frame.contentPane.background = Color.WHITE
    frame.background = Color.WHITE
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.minimumSize = Dimension(512, 256)
    frame.isVisible = true

    System.out.println(configurationToJson(panel, panel.layout as MigLayout, false))
  }
}