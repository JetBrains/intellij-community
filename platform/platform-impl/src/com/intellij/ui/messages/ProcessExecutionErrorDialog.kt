// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.messages

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.text.StyleConstants

/**
 * @throws IllegalStateException if [project] is not `null` and it is disposed
 */
fun showProcessExecutionErrorDialog(project: Project?,
                                    @NlsContexts.DialogTitle dialogTitle: String,
                                    command: String,
                                    stdout: String,
                                    stderr: String,
                                    exitCode: Int) {
  check(project == null || !project.isDisposed)

  val errorMessageText = IdeBundle.message("dialog.message.command.could.not.complete", command)
  // HTML format for text in `JBLabel` enables text wrapping
  val errorMessageLabel = JBLabel(UIUtil.toHtml(errorMessageText), Messages.getErrorIcon(), SwingConstants.LEFT)

  val commandOutputTextPane = JTextPane().apply {
    appendProcessOutput(stdout, stderr, exitCode)

    background = JBColor.WHITE
    isEditable = false
  }

  val commandOutputPanel = BorderLayoutPanel().apply {
    border = IdeBorderFactory.createTitledBorder(IdeBundle.message("border.title.command.output"), false)

    addToCenter(
      JBScrollPane(commandOutputTextPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER))
  }

  val formBuilder = FormBuilder()
    .addComponent(errorMessageLabel)
    .addComponentFillVertically(commandOutputPanel, UIUtil.DEFAULT_VGAP)

  object : DialogWrapper(project) {
    init {
      init()
      title = dialogTitle
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = formBuilder.panel.apply {
      preferredSize = Dimension(600, 300)
    }
  }.showAndGet()
}

private fun JTextPane.appendProcessOutput(stdout: String, stderr: String, exitCode: Int) {
  val stdoutStyle = addStyle(null, null)
  StyleConstants.setFontFamily(stdoutStyle, Font.MONOSPACED)
  val stderrStyle = addStyle(null, stdoutStyle)
  StyleConstants.setForeground(stderrStyle, JBColor.RED)
  document.apply {
    arrayOf(stdout to stdoutStyle, stderr to stderrStyle).forEach { (std, style) ->
      if (std.isNotEmpty()) insertString(length, std + "\n", style)
    }
    insertString(length, "Process finished with exit code $exitCode", stdoutStyle)
  }
}