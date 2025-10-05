package com.intellij.grazie.remote

import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.panel
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal class OfflineLanguageBundleSelectionDialog private constructor(
  private val project: Project?,
  private val language: Lang
): DialogWrapper(project, true) {
  private var selectedFile: Path? = null

  init {
    title = msg("grazie.offline.language.bundle.dialog.title", language.nativeName)
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        text(msg("grazie.offline.language.bundle.dialog.text", language.nativeName, language.ltRemote!!.url))
      }
    }
  }

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction, ChooseLanguageBundleAction())
  }

  private inner class ChooseLanguageBundleAction: AbstractAction(msg("grazie.offline.language.bundle.dialog.select.action.text")) {
    init {
      putValue(DEFAULT_ACTION, true)
    }

    override fun actionPerformed(event: ActionEvent?) {
      selectedFile = chooseFile()
      if (selectedFile != null) {
        close(OK_EXIT_CODE)
      }
    }

    private fun chooseFile(): Path? {
      val descriptor = LanguageBundleFileChooserDescriptor(language)
      val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
      val file = chooser.choose(project).singleOrNull()?.toNioPath()
      return file?.takeIf { GrazieRemote.isValidBundleForLanguage(language, it) }
    }
  }

  private class LanguageBundleFileChooserDescriptor(private val language: Lang): FileChooserDescriptor(
    true,
    false,
    true,
    true,
    false,
    false
  ) {
    init {
      this.title = msg("grazie.offline.language.bundle.dialog.descriptor.title", language.nativeName)
    }

    override fun isFileSelectable(file: VirtualFile?): Boolean {
      return super.isFileSelectable(file) && file?.extension == "jar"
    }

    override fun validateSelectedFiles(files: Array<out VirtualFile>) {
      super.validateSelectedFiles(files)
      for (file in files) {
        val path = file.toNioPath()
        if (!GrazieRemote.isValidBundleForLanguage(language, path)) {
          throw Exception(msg("grazie.offline.language.bundle.dialog.descriptor.error", file.name, language.nativeName))
        }
      }
    }
  }

  companion object {
    fun show(project: Project?, language: Lang): Path? {
      val dialog = OfflineLanguageBundleSelectionDialog(project, language)
      val result = dialog.showAndGet()
      if (!result) {
        return null
      }
      return dialog.selectedFile
    }
  }
}
