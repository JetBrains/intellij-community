// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.CommonBundle
import com.intellij.ide.BrowserUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.dialog
import javax.swing.JPasswordField
import javax.swing.JTextField

object MigLayoutTestApp {
  @JvmStatic
  fun main(args: Array<String>) {
//    LayoutUtil.setGlobalDebugMillis(1000)

    runInEdtAndWait {
      val passwordField = JPasswordField()
//      val panel = panel {
//        noteRow("Profiler requires access to the kernel-level API.\nEnter the sudo password to allow this. ")
//        row("Sudo password:") { passwordField() }
//        row { CheckBox(CommonBundle.message("checkbox.remember.password"), true)() }
//      }

      val panel = panel {
        noteRow("Login to JetBrains Account to get notified\nwhen the submitted exceptions are fixed.")
        row("Username:") { JTextField()(growPolicy = GrowPolicy.SHORT_TEXT) }
        row("Password:") { passwordField() }
        row {
          CheckBox(CommonBundle.message("checkbox.remember.password"))()
          right {
            link("Forgot password?") {
              BrowserUtil.browse("https://account.jetbrains.com/forgot-password?username=")
            }
          }
        }
        noteRow("""Do not have an account? <a href="https://account.jetbrains.com/login?signup">Sign Up</a>""")
      }
      dialog(
        title = "Access Required",
        panel = panel,
        focusedComponent = passwordField,
        okActionEnabled = false
      ) {
        return@dialog null
      }
        .showAndGet()
    }

//    val frame = JFrame()
//    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
//    frame.contentPane.add(panel, BorderLayout.CENTER)
//    frame.contentPane.background = Color.WHITE
//    frame.background = Color.WHITE
//    frame.pack()
//    frame.setLocationRelativeTo(null)
//    frame.minimumSize = Dimension(512, 256)
//    frame.isVisible = true
//
//    System.out.println(configurationToJson(panel, panel.layout as MigLayout, false))
  }
}