// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.ide.util.BrowseFilesListener
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileTextField
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.loadElement
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * @author gregsh
 */
class ShowUpdateInfoDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = MyDialog(e.project)
    if (dialog.showAndGet()) {
      try {
        UpdateChecker.testPlatformUpdate(dialog.updateXmlText(), dialog.patchFilePath(), dialog.forceUpdate())
      }
      catch (ex: Exception) {
        Messages.showErrorDialog(e.project, "${ex.javaClass.name}: ${ex.message}", "Something Went Wrong")
      }
    }
  }

  private class MyDialog(private val project: Project?) : DialogWrapper(project, true) {
    private lateinit var textArea: JTextArea
    private lateinit var fileField: FileTextField
    private var forceUpdate = false

    init {
      title = "Updates.xml <channel> Text"
      init()
    }

    override fun createCenterPanel(): JComponent? {
      textArea = JTextArea(40, 100)
      UIUtil.addUndoRedoActions(textArea)
      textArea.wrapStyleWord = true
      textArea.lineWrap = true

      fileField = FileChooserFactory.getInstance().createFileTextField(BrowseFilesListener.SINGLE_FILE_DESCRIPTOR, disposable)
      val fileCombo = TextFieldWithBrowseButton(fileField.field)
      val fileDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
      fileCombo.addBrowseFolderListener("Patch File", "Patch file", project, fileDescriptor)

      val panel = JPanel(BorderLayout(0, JBUI.scale(10)))
      panel.add(ScrollPaneFactory.createScrollPane(textArea), BorderLayout.CENTER)
      panel.add(LabeledComponent.create(fileCombo, "Patch file:"), BorderLayout.SOUTH)
      return panel
    }

    override fun createActions() = arrayOf(
      object : AbstractAction("&Check Updates") {
        override fun actionPerformed(e: ActionEvent?) {
          forceUpdate = false
          doOKAction()
        }
      },
      object : AbstractAction("&Show Dialog") {
        override fun actionPerformed(e: ActionEvent?) {
          forceUpdate = true
          doOKAction()
        }
      },
      cancelAction)

    override fun doValidate(): ValidationInfo? {
      val text = textArea.text?.trim() ?: ""
      if (text.isEmpty()) {
        return ValidationInfo("Please paste something here", textArea)
      }

      try { loadElement(completeUpdateInfoXml(text)) }
      catch (e: Exception) {
        return ValidationInfo(e.message ?: "Error: ${e.javaClass.name}", textArea)
      }

      return super.doValidate()
    }

    override fun getPreferredFocusedComponent() = textArea
    override fun getDimensionServiceKey() = "TEST_UPDATE_INFO_DIALOG"

    internal fun updateXmlText() = completeUpdateInfoXml(textArea.text?.trim() ?: "")
    internal fun forceUpdate() = forceUpdate
    internal fun patchFilePath() = fileField.field.text.nullize(nullizeSpaces = true)

    private fun completeUpdateInfoXml(text: String) =
      when (loadElement(text).name) {
        "products" -> text
        "channel" -> {
          val productName = ApplicationNamesInfo.getInstance().fullProductName
          val productCode = ApplicationInfo.getInstance().build.productCode
          """<products><product name="${productName}"><code>${productCode}</code>${text}</product></products>"""
        }
        else -> throw IllegalArgumentException("Unknown root element")
      }
  }
}