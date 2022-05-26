// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.Link
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Draws ui for failed state of editor.
 *
 * @param message 'message' is wrapped and supports multi-line,
 * please TRY to make your message short.
 * For example, "Invalid Xml file" instead of " XML parsing error at line 0... (and three more pages of text)"
 *
 * if you STILL want to put a big message , then go to our designers for a consultation.
 * They will probably offer a more elegant solution (it always turned out this way)
 *
 * @param showErrorIcon allows you to enable or disable error icon before text. Other icons not allowed here
 *
 * @param wrapMode enables or disables multiline support
 *
 * @param init builder, where you can add action buttons for the editor
 *
 *
 * @return Jpanel with failed editor ui
 *
 */
fun failedEditorPane(@DialogMessage message: String,
                     showErrorIcon: Boolean,
                     wrapMode: MultilineWrapMode = MultilineWrapMode.Auto,
                     init: FailedEditorBuilder.() -> Unit): JPanel {
  val builder = FailedEditorBuilder(message, if (showErrorIcon) AllIcons.General.Error else null)
  builder.init()
  return builder.draw(wrapMode)
}

enum class MultilineWrapMode {
  /**
   * Disable multiline support (use it for small texts)
   */
  DoNotWrap,

  /**
   * Enable multiline support and text-wrapping witch may be confusing for small texts
   */
  Wrap,

  /**
   * Enables or disable multiline support depends on size of a text
   */
  Auto
}

class FailedEditorBuilder internal constructor(@DialogMessage val message: String, val icon: Icon?) {
  private val myButtons = mutableListOf<Pair<String, () -> Unit>>()

  /**
   * Adds Link at the bottom of the text
   *
   * @param text Text of link
   *
   * @param action Action of link
   */
  fun link(@NlsContexts.LinkLabel text: String, action: () -> Unit) {
    myButtons.add(Pair(text, action))
  }

  /**
   * Adds Link at the bottom of the text that
   * opens tab in target editor
   */
  fun linkThatNavigatesToEditor(@NlsContexts.LinkLabel text: String, editorProviderId: String, project: Project, editor: FileEditor) =
    link(text) {
      editor.tryOpenTab(project, editorProviderId)
    }

  /**
   * Adds Link at the bottom of the text that
   * opens text tab in target editor
   */
  fun linkThatNavigatesToTextEditor(@NlsContexts.LinkLabel text: String, project: Project, editor: FileEditor) =
    linkThatNavigatesToEditor(text,
                              "text-editor",
                              project,
                              editor)

  /**
   * Opens tab in target editor
   */
  private fun FileEditor.tryOpenTab(project: Project, editorProviderId: String): Boolean {
    val impl = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return false

    for (window in impl.windows) {
      for (composite in window.allComposites) {
        for (tab in composite.allEditors) {
          if (tab == this) {
            //move focus to current window
            window.setAsCurrentWindow(true)
            //select editor
            window.setSelectedComposite(composite, true)
            //open tab
            composite.fileEditorManager.setSelectedEditor(composite.file, editorProviderId)
            return true
          }
        }
      }
    }
    return false
  }

  internal fun draw(wrapMode: MultilineWrapMode): JPanel = JPanel(MigLayout("flowy, aligny 47%, alignx 50%, ins 0, gap 0")).apply {
    border = EmptyBorder(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP)

    val wrap = when (wrapMode) {
      MultilineWrapMode.Wrap -> true
      MultilineWrapMode.DoNotWrap -> false
      else -> {
        val maxButtonSize = if (myButtons.any()) myButtons.maxOf { it.first.length } else 0
        (message.length > maxButtonSize && message.length > 65) || message.contains('\n') || message.contains('\r')
      }
    }

    if (wrap)
      drawMessagePane()
    else //draw label otherwise to avoid wrapping on resize
      drawLabel()

    for ((text, action) in myButtons) {
      add(Link(text, null, action), "alignx center, gapbottom ${UIUtil.DEFAULT_VGAP}")
    }
  }

  private fun JPanel.drawLabel() {
    add(JBLabel(icon).apply {
      text = message
      if (icon != null) {
        this.border = EmptyBorder(0, 0, 0, UIUtil.DEFAULT_HGAP + iconTextGap)
      }
    }, "alignx center, gapbottom ${getGapAfterMessage()}")
  }

  private fun JPanel.drawMessagePane() {
    val messageTextPane = JTextPane().apply {
      isFocusable = false
      isEditable = false
      border = null
      font = StartupUiUtil.getLabelFont()
      background = UIUtil.getLabelBackground()
      val centerAttribute = SimpleAttributeSet()
      StyleConstants.setAlignment(centerAttribute, StyleConstants.ALIGN_CENTER)
      styledDocument.insertString(0, message, centerAttribute)
      styledDocument.setParagraphAttributes(0, styledDocument.length, centerAttribute, false)
      text = message
    }

    if (icon != null) {
      // in case of icon - wrap icon and text pane
      // [icon] [gap] [text] [gap+icon size]
      // text has to be aligned as if there is no icon

      val iconAndText = JPanel(BorderLayout()).apply {
        val iconTextGap = JBUI.scale(4)
        add(JBLabel(icon).apply { verticalAlignment = SwingConstants.TOP }, BorderLayout.LINE_START)
        messageTextPane.border = EmptyBorder(0, iconTextGap, 0, UIUtil.DEFAULT_HGAP + iconTextGap)
        add(messageTextPane, BorderLayout.CENTER)
      }
      add(iconAndText, "alignx center, gapbottom ${UIUtil.DEFAULT_VGAP + 1}")
    }
    else {
      // if there is only one action link - then gap is usual
      // but if there is more than one action link - gap is increased
      add(messageTextPane, "alignx center, gapbottom ${getGapAfterMessage()}")
    }
  }

  private fun getGapAfterMessage() =
    if (myButtons.count() > 1)
      UIUtil.DEFAULT_VGAP + 1;
    else
      UIUtil.DEFAULT_VGAP
}
