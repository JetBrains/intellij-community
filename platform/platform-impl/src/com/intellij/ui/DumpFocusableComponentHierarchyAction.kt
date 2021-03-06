// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.*

class DumpFocusableComponentHierarchyAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val visibleFrame = WindowManager.getInstance().findVisibleFrame()
    val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
    val focusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    val dump = CopyOnWriteArrayList<String>()

    dump.add("Active Window: " + if (activeWindow == null) "null" else activeWindow.javaClass.name)
    dump.add("Focused Window: " + if (focusedWindow == null) "null" else focusedWindow.javaClass.name)
    dump.add("Focused Component: " + if (focusedComponent == null) "null" else focusedComponent.javaClass.name)

    val componentTrace = ArrayList<Component>()
    var c = focusedComponent

    while (c != null) {
      componentTrace.add(c)
      c = c.parent
    }

    for (i in componentTrace.indices.reversed()) {
      dump.add(componentTrace[i].javaClass.name)
      if (i != 0) {
        dump.add("^")
      }
    }

    (focusedComponent as? JComponent?)?.let {
      dump.add("Children count in focused component: ${it.componentCount}")
    }

    val fontHeight = 30

    val contentPanel:JPanel = JPanel(BorderLayout())

    val jPanel: JPanel = object : JPanel(BorderLayout()) {
      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.color = JBColor.BLACK
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = JBColor.WHITE
        for (i in dump.indices.reversed()) {
          g.drawString(dump[i], 20, 50 + i * fontHeight)
        }
      }
    }
    contentPanel.preferredSize = Dimension(visibleFrame!!.width, dump.size * fontHeight)
    contentPanel.add(jPanel)

    val scrollPane: JScrollPane = JBScrollPane(contentPanel)

    scrollPane.preferredSize = visibleFrame.size

    val popup = PopupFactory.getSharedInstance().
                  getPopup(visibleFrame, scrollPane, visibleFrame.x, visibleFrame.y)

    val closeButton = JButton(object : AbstractAction(IdeBundle.message("dump.focusable.component.hierarchy.close.button")) {
      override fun actionPerformed(e: ActionEvent) {
        val dumpAsString = StringBuilder()
        for (i in dump.indices.reversed()) {
          dumpAsString.append(dump[i]).append("\n")
        }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
          StringSelection(dumpAsString.toString()), null)
        popup.hide()
      }
    })
    jPanel.add(closeButton, BorderLayout.NORTH)

    popup.show()
  }
}