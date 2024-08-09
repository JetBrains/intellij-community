// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch

import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.project.stateStore
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JRadioButton

@ApiStatus.Internal
class CreatePatchConfigurationPanel(val project: Project) {
  private val panel: DialogPanel

  private val fileNameField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()
  private val basePathField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()

  private lateinit var toClipboardRadioButton: JRadioButton
  private lateinit var reverseCheckBox: JCheckBox
  private lateinit var encodingComboBox: ComboBox<Charset>

  private var commonParentDir: File? = null

  init {
    fileNameField.addActionListener {
      val fileName = selectFileName()
      if (fileName != null) {
        fileNameField.text = fileName
      }
    }

    basePathField.addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()))
    basePathField.text = project.stateStore.projectBasePath.toString()

    panel = createPanel()
  }

  private fun selectFileName(): String? {
    @Suppress("DialogTitleCapitalization") val descriptor =
      FileSaverDescriptor(message("patch.creation.save.to.title"), "")
    val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, fileNameField)
    val path = FileUtil.toSystemIndependentName(getFileName())
    val index = path.lastIndexOf("/")
    val baseDir = if (index == -1) project.stateStore.projectBasePath else Paths.get(path.substring(0, index))
    val name = if (index == -1) path else path.substring(index + 1)
    val fileWrapper = dialog.save(baseDir, name) ?: return null
    return fileWrapper.file.path
  }

  private fun createPanel(): DialogPanel {
    return panel {
      buttonsGroup {
        row {
          val toFileButton = radioButton(message("create.patch.file.path"))
          toFileButton.component.isSelected = true
          cell(fileNameField)
            .columns(COLUMNS_LARGE)
            .align(AlignX.FILL)
            .enabledIf(toFileButton.selected)
            .validationOnInput { validateFileName() }
        }.layout(RowLayout.LABEL_ALIGNED)
        row {
          toClipboardRadioButton = radioButton(message("create.patch.to.clipboard"))
            .component
        }
      }

      row(message("patch.creation.base.path.field")) {
        cell(basePathField)
          .columns(COLUMNS_LARGE)
          .align(AlignX.FILL)
          .validationOnInput { validateBaseDirPath() }
      }.topGap(TopGap.SMALL)
      row {
        reverseCheckBox = checkBox(message("create.patch.reverse.checkbox")).component
      }
      row(message("create.patch.encoding")) {
        encodingComboBox = comboBox(DefaultComboBoxModel(CharsetToolkit.getAvailableCharsets())).component
        encodingComboBox.selectedItem = EncodingProjectManager.getInstance(project).defaultCharset
      }
    }
  }

  fun selectBasePath(baseDir: String) {
    basePathField.text = baseDir
  }

  fun getEncoding(): Charset {
    return encodingComboBox.item
  }

  fun setCommonParentPath(commonParentPath: File?) {
    if (commonParentPath != null && !commonParentPath.isDirectory) {
      commonParentDir = commonParentPath.parentFile
    }
    else {
      commonParentDir = commonParentPath
    }
  }

  fun getPanel(): DialogPanel {
    return panel
  }

  fun getFileName(): String {
    return FileUtil.expandUserHome(fileNameField.text.trim())
  }

  fun getBaseDirName(): String {
    return FileUtil.expandUserHome(basePathField.getText().trim())
  }

  fun setFileName(file: Path) {
    fileNameField.text = file.toString()
  }

  fun isReversePatch(): Boolean {
    return reverseCheckBox.isSelected
  }

  fun setReversePatch(reverse: Boolean) {
    reverseCheckBox.isSelected = reverse
  }

  fun setReverseEnabledAndVisible(isAvailable: Boolean) {
    reverseCheckBox.isVisible = isAvailable
    reverseCheckBox.isEnabled = isAvailable
  }

  fun isToClipboard(): Boolean {
    return toClipboardRadioButton.isSelected
  }

  fun setToClipboard(toClipboard: Boolean) {
    toClipboardRadioButton.isSelected = toClipboard
  }

  fun isOkToExecute(): Boolean {
    return panel.validateAll().none { !it.warning }
  }

  private fun validateFileName(): ValidationInfo? {
    val fileName = getFileName()

    val validateNameError = PatchNameChecker.validateName(fileName)
    if (validateNameError != null) {
      return ValidationInfo(validateNameError, fileNameField)
    }

    if (File(fileName).exists()) {
      return ValidationInfo(IdeBundle.message("error.file.with.name.already.exists", fileName)).asWarning().withOKEnabled()
    }

    return null
  }

  private fun validateBaseDirPath(): ValidationInfo? {
    val baseDirName = getBaseDirName()
    if (baseDirName.isBlank()) {
      return ValidationInfo(message("patch.creation.empty.base.path.error"), basePathField)
    }

    val baseFile = File(baseDirName)
    if (!baseFile.exists()) {
      return ValidationInfo(message("patch.creation.base.dir.does.not.exist.error"), basePathField)
    }

    val commonParent = commonParentDir ?: return null
    if (!FileUtil.isAncestor(baseFile, commonParent, false)) {
      return ValidationInfo(message("patch.creation.wrong.base.path.for.changes.error", commonParent.path), basePathField)
    }
    return null
  }
}