// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.StatusText
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal class ComposePreviewBusyPanel(private val project: Project) : JBPanelWithEmptyText(BorderLayout()), DumbAware {
  @Volatile
  private var busy: Boolean = false
  private var busyIcon: AsyncProcessIcon? = null

  init {
    emptyText.isCenterAlignText = false
    emptyText.clear()
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.empty.text.top"))
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.empty.text.compile"))
    emptyText.appendLine("")
    appendBuildHintText(project)
  }

  @Suppress("HardCodedStringLiteral")
  private fun appendBuildHintText(project: Project) {
    val text = emptyText

    val buildLine = DevkitComposeBundle.message("compose.preview.build")
    text.appendLine(buildLine.substringBefore("<"))
    text.appendText(buildLine.substringAfter('<').substringBefore('>'), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
      val compileAction = ActionManager.getInstance().getAction("CompileDirty")!!
      val dataContext = SimpleDataContext.builder().add(PROJECT, project).build()
      ActionUtil.performAction(compileAction, AnActionEvent.createEvent(dataContext, Presentation(), "", ActionUiKind.NONE, null))
    }
    text.appendText(buildLine.substringAfter(">"))
    val shortcut = ActionManager.getInstance().getKeyboardShortcut("CompileDirty")
    val shortcutText = shortcut?.let { " (${KeymapUtil.getShortcutText(shortcut)})" } ?: ""
    text.appendText(shortcutText)

    addRefreshHintText(text, project)
  }

  @Suppress("HardCodedStringLiteral")
  private fun addRefreshHintText(text: StatusText, project: Project) {
    val refreshLine = DevkitComposeBundle.message("compose.preview.refresh")
    text.appendLine(refreshLine.substringBefore("<"))
    text.appendText(refreshLine.substringAfter('<').substringBefore('>'), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
      project.service<ComposePreviewChangesTracker>().refresh()
    }
    text.appendText(refreshLine.substringAfter(">"))
  }

  fun setPaintBusy(paintBusy: Boolean) {
    if (busy == paintBusy) return

    busy = paintBusy
    updateBusy()

    revalidate()
    repaint()
  }

  private fun updateBusy() {
    if (busy) {
      if (busyIcon == null) {
        busyIcon = AsyncProcessIcon.Big(toString())
        busyIcon!!.setOpaque(false)
        busyIcon!!.setPaintPassiveIcon(false)
        add(busyIcon!!, BorderLayout.CENTER)
      }
    }

    val current = busyIcon
    if (current != null) {
      if (busy) {
        removeAll()
        add(current, BorderLayout.CENTER)
        current.resume()
      }
      else {
        current.suspend()
        SwingUtilities.invokeLater(Runnable {
          if (busyIcon != null) {
            repaint()
          }
        })
      }
      current.updateLocation(this)
    }
  }

  fun displayUnsupportedFile() {
    removeAll()

    emptyText.clear()
    emptyText.appendLine(AllIcons.Ide.FatalErrorRead, DevkitComposeBundle.message("compose.preview.unsupported.file"),
                         StatusText.DEFAULT_ATTRIBUTES, null)
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.enable.composable"))

    addRefreshHintText(emptyText, project)

    revalidate()
    repaint()
  }

  fun displayMissingLocals(e: ComposeLocalContextException) {
    removeAll()

    emptyText.clear()
    emptyText.appendLine(AllIcons.Ide.FatalErrorRead, e.cause!!.message ?: DevkitComposeBundle.message("compose.preview.insufficient.local.context"),
                         StatusText.DEFAULT_ATTRIBUTES, null)
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.insufficient.local.hint"))

    addRefreshHintText(emptyText, project)

    revalidate()
    repaint()
  }

  fun setContent(content: JComponent) {
    removeAll()

    add(content, BorderLayout.CENTER)
    revalidate()
    repaint()
  }
}