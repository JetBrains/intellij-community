// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset

import com.intellij.compose.ide.plugin.resources.gradle.GradleComposeResourcesDir
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.net.URL
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.DefaultMutableTreeNode

internal object ComposeResourcesVectorAssetDialogComponents {
  sealed class Source {
    data class ClipArt(val url: URL, val name: String?, val button: JButton) : Source()
    data class LocalFile(val path: String) : Source()
  }

  data class LoadedAsset(
    val xml: String,
    val name: String,
    val dimensions: Dimension?,
    val warnings: String = "",
  ) {
    val aspectRatio: Double = dimensions?.let {
      if (it.height > 0) it.width.toDouble() / it.height else 1.0
    } ?: 1.0
  }

  sealed class LoadResult {
    data class Success(val asset: LoadedAsset) : LoadResult()
    data class Error(val message: String) : LoadResult()
    data object Cancelled : LoadResult()
  }

  data class DrawableDir(
    val resourceDir: GradleComposeResourcesDir,
    val drawablePath: Path,
  )

  enum class Page { CONFIG, OUTPUT }

  enum class ValidationSeverity { ERROR, WARNING }

  data class Validation(
    @param:NlsSafe val message: String,
    val severity: ValidationSeverity,
  )

  data class TreeNode(
    @param:NlsSafe val name: String,
    val isDirectory: Boolean = true,
    val alreadyExists: Boolean = false,
  )

  class TreeCellRenderer : ColoredTreeCellRenderer() {
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
        is TreeNode -> {
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

  class ValidationPanel : JPanel(BorderLayout()) {
    private var contentPanel: JPanel? = null

    fun showMessage(validation: Validation) {
      clear()

      val icon = if (validation.severity == ValidationSeverity.ERROR) AllIcons.General.Error else AllIcons.General.Warning

      val panel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(JBLabel(icon).apply { border = JBUI.Borders.emptyRight(8) }, BorderLayout.WEST)
        add(createMessageComponent(validation.message), BorderLayout.CENTER)
      }
      contentPanel = panel
      add(panel, BorderLayout.CENTER)
      revalidate()
      repaint()
    }

    private fun createMessageComponent(@NlsContexts.Label message: String): JComponent {
      if (message.lines().size <= 1) {
        val errorPrefix = ComposeIdeBundle.message("compose.vector.asset.preview.incomplete.error")
        val htmlMessage = HtmlChunk.html().child(HtmlChunk.text("$errorPrefix $message")).toString()
        return JBLabel(htmlMessage)
      }

      val htmlMessage = HtmlChunk.html().children(
        HtmlChunk.text(ComposeIdeBundle.message("compose.vector.asset.preview.incomplete.multiline.error")),
        HtmlChunk.text(" "),
        HtmlChunk.link("issues", ComposeIdeBundle.message("compose.vector.asset.preview.multiline.error.link.text"))
      ).toString()

      return JEditorPane(UIUtil.HTML_MIME, htmlMessage).apply {
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.empty()
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        addHyperlinkListener { e ->
          if (e.eventType != HyperlinkEvent.EventType.ACTIVATED) return@addHyperlinkListener
          if (e.description == "issues") showErrorDetailsDialog(message)
        }
      }
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
      revalidate()
      repaint()
    }
  }
}