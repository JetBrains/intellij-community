package com.intellij.grazie.remote

import ai.grazie.nlp.langs.utils.nativeName
import com.intellij.grazie.GrazieBundle.message
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote.checksum
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtilRt.extensionEquals
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.panel
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal class OfflineLanguageBundleSelectionDialog private constructor(
  private val languages: Collection<Lang>,
): DialogWrapper(null, true) {
  private var files: Files? = null

  init {
    title = msg("grazie.offline.language.bundle.dialog.title.plural")
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        val linksHtml = languages.flatMap { it.remoteDescriptors }
          .joinToString(separator = "<br>") {
            "<a href=\"${it.url}\">${it.storageDescriptor}</a>"
          }
        text(msg("grazie.offline.language.bundle.dialog.text", linksHtml))
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
      setErrorText(null)
      val chosenFiles = chooseFile()
      if (chosenFiles == null) {
        setErrorText(msg(
          "grazie.offline.language.missing.languages",
          languages.joinToString { it.toLanguage().nativeName }
        ))
        return
      }
      if (chosenFiles.valid.isNotEmpty() && chosenFiles.invalid.isEmpty()) {
        files = chosenFiles
        close(OK_EXIT_CODE)
        return
      }
      if (chosenFiles.invalid.isNotEmpty()) {
        setErrorText(msg(
          "grazie.offline.language.incorrect.checksum",
          chosenFiles.invalid.joinToString { it.fileName.toString() }
        ))
      }
    }

    private fun chooseFile(): Files? {
      val checksums = languages.flatMap { it.remoteDescriptors }.map { it.checksum }
      val validFiles = mutableListOf<Path>()
      val invalidFiles = mutableListOf<Path>()
      FileChooser.chooseFiles(JarFileChooserDescriptor(), null, null)
        .map { it.toNioPath() }
        .forEach {
          (if (checksum(it) in checksums) validFiles else invalidFiles).add(it)
        }
      if (validFiles.size + invalidFiles.size != checksums.size) return null
      return Files(validFiles, invalidFiles)
    }
  }

  private class JarFileChooserDescriptor: FileChooserDescriptor(FileChooserDescriptorFactory.multiFiles()) {
    init {
      this.title = msg("grazie.offline.language.bundle.dialog.descriptor.title")
    }

    override fun validateSelectedFiles(files: Array<out VirtualFile>) {
      if (files.any { !extensionEquals(it.name, "jar") }) {
        throw ConfigurationException(message("grazie.filetype.jar.incorrect.message"))
      }
    }
  }

  private data class Files(val valid: List<Path>, val invalid: List<Path>)

  companion object {
    fun show(languages: Collection<Lang>): Collection<Path> {
      val dialog = OfflineLanguageBundleSelectionDialog(languages)
      val result = dialog.showAndGet()
      if (!result) {
        return emptyList()
      }
      return dialog.files?.valid ?: emptyList()
    }

    @Deprecated("Use show(Collection<Lang>) instead", ReplaceWith("show(listOf(language))"))
    fun show(project: Project?, language: Lang): Path? = show(listOf(language)).firstOrNull()
  }
}
