// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE") // Required by VirtualFileWrapper

package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBTextField
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.Border
import javax.swing.event.DocumentEvent
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * A "Save File" dialog built on top of [UniversalFileChooser].
 *
 * Capable of choosing a destination in a local file system and in Docker/WSL containers.
 * The implementation operates on [java.nio.file.Path]; the result is wrapped into a
 * [VirtualFileWrapper] only at the very last step.
 */
@ApiStatus.Internal
object UniversalFileSaver {

  @JvmStatic
  fun create(project: Project?, parent: Component?, descriptor: FileSaverDescriptor): Dialog {
    val currProject = project ?: ProjectManager.getInstance().defaultProject
    return Dialog(currProject, parent, descriptor)
  }

  class Dialog(
    private val project: Project,
    parent: Component?,
    private val descriptor: FileSaverDescriptor,
  ) : DialogWrapper(project, parent, true, IdeModalityType.IDE), FileSaverDialog {

    private lateinit var chooserPanel: UniversalFileChooser.Panel
    private val fileNameField: JBTextField = JBTextField(20)
    private val extensionsCombo: JComboBox<String> = ComboBox()
    private val defaultFileNameBorder: Border = fileNameField.border
    private val errorFileNameBorder: Border = JBUI.Borders.customLine(JBColor.RED)

    init {
      val extFilter = descriptor.extensionFilter
      extFilter?.second?.forEach { extensionsCombo.addItem(it) }
      if (extensionsCombo.model.size > 0) {
        extensionsCombo.selectedIndex = 0
      }
      init()
      title = descriptor.title ?: UIBundle.message("file.chooser.save.dialog.default.title")

      fileNameField.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          updateOkEnabled()
        }
      })
    }

    override fun getDimensionServiceKey(): String = "UniversalFileSaverDialog"

    override fun createCenterPanel(): JComponent {
      chooserPanel = UniversalFileChooser.Panel(
        disposable,
        descriptor,
        project,
        Runnable { doOKAction() },
        { onSelectionChanged() },
      )
      val panel = JPanel(BorderLayout())
      panel.border = BorderFactory.createEmptyBorder()
      panel.add(chooserPanel, BorderLayout.CENTER)
      panel.add(createFileNamePanel(), BorderLayout.SOUTH)
      return panel
    }

    private fun createFileNamePanel(): JComponent {
      val panel = JPanel(BorderLayout())
      panel.add(JLabel(UIBundle.message("file.chooser.save.dialog.file.name")), BorderLayout.WEST)
      panel.add(fileNameField, BorderLayout.CENTER)
      if (extensionsCombo.model.size > 0) {
        panel.add(extensionsCombo, BorderLayout.EAST)
      }
      return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = fileNameField

    private fun onSelectionChanged() {
      if (::chooserPanel.isInitialized) {
        val selected = chooserPanel.getSelectedFiles().firstOrNull()
        if (selected != null && !selected.isDirectory()) {
          val name = selected.name
          if (name.isNotEmpty() && fileNameField.text != name) {
            fileNameField.text = name
          }
        }
      }
      updateOkEnabled()
    }

    private fun updateOkEnabled() {
      val nameValid = PathUtil.isValidFileName(fileNameField.text)
      fileNameField.border = if (nameValid || fileNameField.text.isNullOrEmpty()) defaultFileNameBorder else errorFileNameBorder
      val hasSelection = ::chooserPanel.isInitialized && chooserPanel.getSelectedFiles().isNotEmpty()
      super.setOKActionEnabled(nameValid && hasSelection)
    }

    override fun save(baseDir: VirtualFile?, filename: String?): VirtualFileWrapper? {
      val nioBase = baseDir?.let { runCatching { it.toNioPath() }.getOrNull() }
      return save(nioBase, filename)
    }

    override fun save(baseDir: Path?, filename: String?): VirtualFileWrapper? {
      if (baseDir != null) {
        chooserPanel.navigateToFile(baseDir)
      }
      if (filename != null) {
        fileNameField.text = filename
      }

      if (!showAndGet()) return null

      val resolved = resolveResultPath() ?: return null

      if (Files.exists(resolved)) {
        @Suppress("DialogTitleCapitalization") // Leave as is
        val answer = Messages.showYesNoDialog(
          chooserPanel,
          UIBundle.message("file.chooser.save.dialog.confirmation", resolved.fileName?.toString() ?: resolved.toString()),
          UIBundle.message("file.chooser.save.dialog.confirmation.title"),
          Messages.getWarningIcon()
        )
        if (answer != Messages.YES) return null
      }

      return VirtualFileWrapper(File(resolved.toString()))
    }

    private fun resolveResultPath(): Path? {
      val selected = chooserPanel.getSelectedFiles().firstOrNull() ?: return null
      val name = fileNameField.text?.trim().orEmpty()
      if (name.isEmpty()) return null

      val parent = if (selected.isDirectory()) selected else selected.parent ?: return null
      val childPath = runCatching { Path.of(name) }.getOrNull()
      val combined = if (childPath != null && childPath.isAbsolute) childPath else parent.resolve(name)

      return appendExtensionIfNeeded(combined)
    }

    private fun appendExtensionIfNeeded(path: Path): Path {
      val extFilter = descriptor.extensionFilter ?: return path
      val pathString = path.toString()
      val hasMatchingExt = extFilter.second.any { Strings.endsWithIgnoreCase(pathString, ".$it") }
      if (hasMatchingExt) return path
      val selectedExt = extensionsCombo.selectedItem as? String
      if (selectedExt.isNullOrEmpty()) return path
      return Path.of("$pathString.$selectedExt")
    }
  }
}
