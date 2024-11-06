// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ReflectionUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JWindow

@ApiStatus.Internal
class DumpFocusableComponentHierarchyAction : AnAction(), DumbAware {
  init {
    isEnabledInModalContext = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dump = createDump()

    val panel = JBPanel<Nothing>(BorderLayout())
    panel.add(JBScrollPane(JBTextArea(dump)), BorderLayout.CENTER)

    val parent = WindowManager.getInstance().suggestParentWindow(e.project)
    val popup = JWindow(parent)
    popup.type = Window.Type.POPUP
    popup.isAlwaysOnTop = true
    popup.focusableWindowState = false
    popup.add(panel)

    val closeButton = JButton(object : AbstractAction(IdeBundle.message("dump.focusable.component.hierarchy.close.button")) {
      override fun actionPerformed(e: ActionEvent) {
        CopyPasteManager.copyTextToClipboard(dump)
        popup.dispose()
      }
    })
    panel.add(closeButton, BorderLayout.NORTH)

    popup.setSize(800, 600)
    popup.setLocationRelativeTo(parent)
    popup.isVisible = true
  }

  private fun createDump() : @NlsSafe String {
    val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val activeWindow = keyboardFocusManager.activeWindow
    val focusedWindow = keyboardFocusManager.focusedWindow
    val focusedComponent = keyboardFocusManager.focusOwner

    val dump = mutableListOf<String>()

    try {
      dump.add("Native Focused Window: " +
               ReflectionUtil.getDeclaredMethod(KeyboardFocusManager::class.java, "getNativeFocusedWindow")!!.invoke(keyboardFocusManager))
    } catch (ignored: Throwable) {}
    try {
      dump.add("Native Focus Owner: " +
               ReflectionUtil.getDeclaredMethod(KeyboardFocusManager::class.java, "getNativeFocusOwner")!!.invoke(keyboardFocusManager))
    } catch (ignored: Throwable) {}

    dump.add("Active Window: " + activeWindow?.javaClass?.name)
    dump.add("Focused Window: " + focusedWindow?.javaClass?.name)
    dump.add("Focused Component: " + focusedComponent?.javaClass?.name)

    if (focusedComponent != null) {
      var c = focusedComponent.parent
      while (c != null) {
        dump.add(" - " + c.javaClass.name)
        c = c.parent
      }

      (focusedComponent as? JComponent?)?.let {
        dump.add("Children count in focused component: ${it.componentCount}")
      }
    }

    return dump.joinToString("\n")
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}