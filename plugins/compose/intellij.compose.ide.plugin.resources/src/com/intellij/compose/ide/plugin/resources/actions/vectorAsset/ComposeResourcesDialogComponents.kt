// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset

import com.intellij.compose.ide.plugin.resources.ComposeResourcesDir
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.DefaultMutableTreeNode

internal data class ComposeResourcesDrawableDir(
  val resourceDir: ComposeResourcesDir,
  val drawablePath: Path,
)

internal data class ComposeResourcesTreeNodeInfo(
  val name: String,
  val isDirectory: Boolean,
  val alreadyExists: Boolean = false,
)

internal enum class ComposeResourcesSeverity { ERROR, WARNING }

internal class ComposeResourcesOutputTreeCellRenderer : ColoredTreeCellRenderer() {
  override fun customizeCellRenderer(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) {
    val node = value as? DefaultMutableTreeNode ?: return
    when (val obj = node.userObject) {
      is ComposeResourcesTreeNodeInfo -> {
        icon = if (obj.isDirectory) AllIcons.Nodes.Folder else AllIcons.FileTypes.Xml
        append(obj.name, if (obj.alreadyExists) SimpleTextAttributes.ERROR_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
      is String -> {
        icon = AllIcons.Nodes.Folder
        append(obj, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }
  }
}

internal data class ValidationInfo(
  val message: String,
  val severity: ComposeResourcesSeverity,
)

internal class ComposeResourcesValidationPanel : JPanel(BorderLayout()) {

  private var contentPanel: JPanel? = null
  private var fullMessage: String? = null

  fun showMessage(validation: ValidationInfo) {
    clear()

    fullMessage = validation.message

    val icon = when (validation.severity) {
      ComposeResourcesSeverity.ERROR -> AllIcons.General.Error
      ComposeResourcesSeverity.WARNING -> AllIcons.General.Warning
    }

    val isMultiLine = validation.message.lines().size > 1
    val panel = JPanel(BorderLayout()).apply { isOpaque = false }

    val iconLabel = JBLabel(icon)
    panel.add(iconLabel, BorderLayout.WEST)

    if (isMultiLine) {
      val htmlMessage = HtmlChunk.html().children(
        HtmlChunk.text(ComposeIdeBundle.message("compose.vector.asset.preview.multiline.error")),
        HtmlChunk.text(" "),
        HtmlChunk.link("issues", ComposeIdeBundle.message("compose.vector.asset.preview.multiline.error.link.text"))
      ).toString()

      val messagePane = JEditorPane().apply {
        contentType = UIUtil.HTML_MIME
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.empty()
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        text = htmlMessage
        addHyperlinkListener { e ->
          if (e.eventType != HyperlinkEvent.EventType.ACTIVATED) return@addHyperlinkListener
          showErrorDetailsDialog(fullMessage!!)
        }
      }
      panel.add(messagePane, BorderLayout.CENTER)
    }
    else {
      val messageLabel = JBLabel(ComposeIdeBundle.message("compose.vector.asset.preview.error", validation.message))
      panel.add(messageLabel, BorderLayout.CENTER)
    }

    contentPanel = panel
    add(panel, BorderLayout.CENTER)
    revalidate()
    repaint()
  }

  private fun showErrorDetailsDialog(@NlsSafe message: String) {
    val textArea = JTextArea(message).apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
      border = JBUI.Borders.empty(8)
    }

    val scrollPane = JBScrollPane(textArea).apply { preferredSize = JBUI.size(700, 400) }

    val dialog = object : DialogWrapper(true) {
      init {
        title = ComposeIdeBundle.message("compose.vector.asset.preview.error.details.title")
        init()
      }

      override fun createCenterPanel() = scrollPane

      override fun createActions() = arrayOf(okAction)
    }
    dialog.show()
  }

  fun clear() {
    contentPanel?.let { remove(it) }
    contentPanel = null
    fullMessage = null
    revalidate()
    repaint()
  }
}