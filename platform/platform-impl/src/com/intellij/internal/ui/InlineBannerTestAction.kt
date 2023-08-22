// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author Alexander Lobas
 */
class InlineBannerTestAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = object : DialogWrapper(e.project) {
      init {
        setSize(500, 500)
        title = "Inline Banners"
        init()
      }

      override fun createCenterPanel(): JComponent {
        val panel = JPanel(VerticalLayout(10))

        for (status in EditorNotificationPanel.Status.values()) {
          val banner = InlineBanner("Interactive lesson available", status).addAction("Open lesson") {
            System.out.println("Action!")
          }
          if (status === EditorNotificationPanel.Status.Warning) {
            banner.showCloseButton(false)
          }
          else {
            banner.setCloseAction(Runnable {
              System.out.println("Close!")
            })
          }
          panel.add(banner)
        }

        panel.add(InlineBanner("Interactive lesson available").setIcon(null).addAction("Open lesson") {})
        panel.add(InlineBanner("Interactive lesson available"))

        val banner = InlineBanner("Interactive lesson available")
        for (i in 1..5) {
          banner.addAction("Action $i") {
            System.out.println("Action $i")
          }
        }
        banner.preferredSize = Dimension(JBUI.scale(50), banner.preferredSize.height)
        panel.add(banner)

        return panel
      }
    }
    dialog.show()
  }
}