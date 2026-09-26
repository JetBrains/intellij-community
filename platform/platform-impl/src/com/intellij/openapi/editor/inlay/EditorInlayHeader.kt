// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.inlay

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Cursor
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class EditorInlayHeader(
  private val title: @NlsSafe String?,
  private val icon: Icon?,
  private val tooltip: @NlsSafe String?,
  private val showCloseButton: Boolean,
) {
  fun createComponent(onClose: () -> Unit): JComponent {
    val panel = JPanel(BorderLayout())
    panel.isOpaque = false
    panel.cursor = Cursor.getDefaultCursor()
    panel.border = JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBUI.CurrentTheme.Separator.color()),
      JBUI.Borders.empty(1, 8, 1, 1),
    )
    if (title != null) {
      panel.add(JLabel(title, icon, SwingConstants.LEADING).apply {
        tooltip?.let { setToolTipText(HtmlChunk.text(it)) }
      }, BorderLayout.CENTER)
    }
    if (showCloseButton) {
      panel.add(createCloseButton(onClose), BorderLayout.EAST)
    }
    return panel
  }

  private fun createCloseButton(onClose: () -> Unit): JButton {
    val closeText = CommonBundle.message("button.close")
    return JButton(AllIcons.Actions.Close).apply {
      setToolTipText(HtmlChunk.text(closeText))
      accessibleContext.accessibleName = closeText
      rolloverIcon = AllIcons.Actions.CloseHovered
      pressedIcon = AllIcons.Actions.CloseHovered
      isContentAreaFilled = false
      isFocusPainted = false
      border = JBUI.Borders.empty()
      margin = JBUI.emptyInsets()
      addActionListener { onClose() }
    }
  }
}

@ApiStatus.Internal
class EditorInlayHeaderBuilder internal constructor() {
  var title: @NlsSafe String? = null
  var icon: Icon? = null
  var tooltip: @NlsSafe String? = null
  var showCloseButton: Boolean = true

  internal fun build(file: VirtualFile?): EditorInlayHeader {
    val namedFile = file?.takeIf { it.fileSystem !is NonPhysicalFileSystem }
    return EditorInlayHeader(
      title = title ?: namedFile?.presentableName,
      icon = icon ?: namedFile?.fileType?.icon,
      tooltip = tooltip ?: namedFile?.presentableUrl,
      showCloseButton = showCloseButton,
    )
  }
}
