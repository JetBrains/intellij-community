// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.ide.util.BrowseFilesListener
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * @author gregsh
 */
class ShowUpdateInfoDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    val textArea = JTextArea(40, 100)
    UIUtil.addUndoRedoActions(textArea)
    textArea.wrapStyleWord = true
    textArea.lineWrap = true

    val disposable = Disposer.newDisposable()
    val fileField = FileChooserFactory.getInstance().createFileTextField(BrowseFilesListener.SINGLE_FILE_DESCRIPTOR, disposable)
    val fileCombo = TextFieldWithBrowseButton(fileField.field)
    val fileDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
    fileCombo.addBrowseFolderListener("Patch File", "Patch file", project, fileDescriptor)

    val panel = JPanel(BorderLayout(0, JBUI.scale(10)))
    panel.add(ScrollPaneFactory.createScrollPane(textArea), BorderLayout.CENTER)
    panel.add(LabeledComponent.create(fileCombo, "Patch file:"), BorderLayout.SOUTH)

    val builder = DialogBuilder(project)
    builder.addDisposable(disposable)
    builder.setCenterPanel(panel)
    builder.setPreferredFocusComponent(textArea)
    builder.setTitle("Updates.xml <channel> Text")
    builder.addOkAction()
    builder.addCancelAction()
    builder.setDimensionServiceKey("TEST_UPDATE_INFO_DIALOG")

    if (builder.showAndGet()) {
      val updateInfoText = StringUtil.trim(textArea.text)
      if (!StringUtil.isEmpty(updateInfoText)) {
        val patchFilePath = fileCombo.text.nullize(nullizeSpaces = true)
        UpdateChecker.testPlatformUpdate(updateInfoText, patchFilePath)
      }
    }
  }
}