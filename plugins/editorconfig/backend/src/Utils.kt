// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.LineSeparator
import org.ec4j.core.ResourceProperties
import org.editorconfig.configmanagement.*
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.plugincomponents.EditorConfigPropertiesService
import org.editorconfig.settings.EditorConfigSettings
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.IOException
import java.nio.file.Paths

object Utils {
  const val EDITOR_CONFIG_NAME = "EditorConfig"
  const val EDITOR_CONFIG_FILE_NAME = ".editorconfig"
  private const val FULL_SETTINGS_SUPPORT_REG_KEY = "editor.config.full.settings.support"
  const val PLUGIN_ID = "org.editorconfig.editorconfigjetbrains"
  // EC spec does not define "none"
  // WEB-18555 says it is a convention, the description seems the same as "unset"?
  // it seems "unset" was added later to the spec.
  // nevertheless, our current behaviour is that "none", "unset" and "" result in the same behaviour (in Utils.configValueForKey)
  //    - if it is missing from the OutPairs for the given file => the plugin does not override this option for the given file
  // TODO Ec4j has nicer mechanisms to handle "unset", but does not know "none", could it be extended?
  private val UNSET_VALUES = arrayOf("none", "unset")
  private var ourIsFullSettingsSupportEnabledInTest = false

  @TestOnly
  @JvmStatic
  var isEnabledInTests = false

  fun ResourceProperties.configValueForKey(key: String): String {
    val prop = properties[key] ?: return ""
    val value = prop.sourceValue.trim()
    return if (value in UNSET_VALUES) "" else value
  }

  @JvmStatic
  fun isEnabled(currentSettings: CodeStyleSettings?): Boolean =
    currentSettings
      ?.getCustomSettingsIfCreated(EditorConfigSettings::class.java)
      ?.ENABLED
    ?: false

  fun isEnabled(project: Project): Boolean = isEnabled(CodeStyle.getSettings(project))

  fun isFullIntellijSettingsSupport(): Boolean =
    ourIsFullSettingsSupportEnabledInTest || Registry.`is`(FULL_SETTINGS_SUPPORT_REG_KEY)

  @JvmStatic
  @TestOnly
  fun setFullIntellijSettingsSupportEnabledInTest(enabled: Boolean) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      ourIsFullSettingsSupportEnabledInTest = enabled
    }
  }

  fun invalidConfigMessage(project: Project, configValue: String?, configKey: String, filePath: String?) {
    val message = if (configValue != null) {
      EditorConfigBundle.bundle.messageOrDefault(
        key = "invalid.config.value",
        defaultValue = null,
        configValue,
        configKey.ifEmpty { "?" },
        filePath,
      )
    }
    else {
      EditorConfigBundle.message("read.failure")
    }
    EditorConfigNotifier.error(project, configValue ?: "ioError", message)
  }

  fun getFilePath(project: Project, file: VirtualFile): String? {
    return if (!file.isInLocalFileSystem) {
      project.basePath + "/" + file.nameWithoutExtension + "." + file.fileType.defaultExtension
    }
    else file.canonicalPath
  }

  @JvmStatic
  fun exportToString(project: Project): String {
    val settings = CodeStyle.getSettings(project)
    val commonIndentOptions = settings.indentOptions
    val result = StringBuilder()
    addIndentOptions(result,
                     "*",
                     commonIndentOptions,
                     getEncodingLine(project) + getLineEndings(settings) + getTrailingSpacesLine() + getEndOfFileLine())
    FileTypeManager.getInstance().registeredFileTypes.asSequence()
      .filter { FileTypeIndex.containsFileOfType(it, GlobalSearchScope.allScope(project)) }
      .forEach { fileType ->
        val options = settings.getIndentOptions(fileType)
        if (!equalIndents(commonIndentOptions, options)) {
          addIndentOptions(result, buildPattern(fileType), options, "")
        }
      }
    return result.toString()
  }

  fun export(project: Project) {
    val baseDir = project.baseDir ?: run {
      thisLogger().debug("Cannot export .editorconfig from the default project")
      return
    }
    baseDir.findChild(".editorconfig")?.let {
      val message = EditorConfigBundle.message("dialog.message.editorconfig.already.present.in.overwrite", baseDir.path)
      val title = EditorConfigBundle.message("dialog.title.editorconfig.exists")
      if (Messages.showYesNoDialog(project, message, title, null) == Messages.NO) return
    }
    ApplicationManager.getApplication().runWriteAction {
      try {
        val editorConfig = baseDir.findOrCreateChildData(Utils::class.java, ".editorconfig")
        VfsUtil.saveText(editorConfig, exportToString(project))
      }
      catch (e: IOException) {
        thisLogger().error(e)
      }
    }
  }

  private fun getEndOfFileLine(): String =
    "${StandardEditorConfigProperties.INSERT_FINAL_NEWLINE}=${EditorSettingsExternalizable.getInstance().isEnsureNewLineAtEOF}\n"

  private fun getTrailingSpacesLine(): String =
    getTrimTrailingSpaces()?.let { "${StandardEditorConfigProperties.TRIM_TRAILING_WHITESPACE}=$it\n" } ?: ""

  fun getTrimTrailingSpaces(): Boolean? =
    when (EditorSettingsExternalizable.getInstance().stripTrailingSpaces) {
      EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE -> false
      EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE -> true
      else -> null
    }

  private fun getLineEndings(settings: CodeStyleSettings): String {
    val separator = settings.lineSeparator
    return getLineSeparatorString(separator)?.let {
      "end_of_line=$it\n"
    } ?: ""
  }

  fun getLineSeparatorString(separator: String): String? =
    LineSeparator.values()
      .find { separator == it.separatorString }
      ?.let { StringUtil.toLowerCase(it.name) }

  private fun getEncodingLine(project: Project): String =
    getEncoding(project)?.let { "${ConfigEncodingCharsetUtil.charsetKey}=$it\n" } ?: ""

  fun getEncoding(project: Project): String? {
    val encodingManager = EncodingProjectManager.getInstance(project)
    return ConfigEncodingCharsetUtil.toString(encodingManager.defaultCharset, encodingManager.shouldAddBOMForNewUtf8File())
  }

  fun buildPattern(fileType: FileType): String {
    val associations = FileTypeManager.getInstance().getAssociations(fileType)
    val result = associations.asSequence()
      .map { it.presentableString }
      .sorted()
      .joinToString(separator = ",")
    return if (associations.size > 1) "{$result}" else result
  }

  private fun equalIndents(a: CommonCodeStyleSettings.IndentOptions,
                           b: CommonCodeStyleSettings.IndentOptions): Boolean =
    b.USE_TAB_CHARACTER == a.USE_TAB_CHARACTER &&
    b.TAB_SIZE == a.TAB_SIZE &&
    b.INDENT_SIZE == a.INDENT_SIZE

  private fun addIndentOptions(result: StringBuilder,
                               pattern: String,
                               options: CommonCodeStyleSettings.IndentOptions,
                               additionalText: String) {
    if (pattern.isEmpty()) return
    result.apply {
      append("[").append(pattern).append("]").append("\n")
      append(additionalText)
      append(indentStyleKey).append("=")
      if (options.USE_TAB_CHARACTER) {
        append("tab\n")
        append(tabWidthKey).append("=").append(options.TAB_SIZE).append("\n")
      }
      else {
        append("space\n")
        append(indentSizeKey).append("=").append(options.INDENT_SIZE).append("\n")
      }
      append("\n")
    }
  }

  fun editorConfigExists(project: Project): Boolean {
    val projectDir = File(project.basePath ?: return false)
    return EditorConfigPropertiesService.getInstance(project).getRootDirs().asSequence()
      .map { File(it.path) }
      .ifEmpty { sequenceOf(projectDir) }
      .flatMap { rootDir ->
        generateSequence(rootDir) { currRoot ->
          if (EditorConfigRegistry.shouldStopAtProjectRoot() && FileUtil.filesEqual(currRoot, projectDir)) null
          else currRoot.parentFile
        }
      }
      .any { dir -> containsEditorConfig(dir) }
  }

  private fun containsEditorConfig(dir: File): Boolean =
    dir.exists() && dir.isDirectory && FileUtil.exists(dir.path + File.separator + ".editorconfig")

  fun pathsToFiles(paths: List<String>): List<VirtualFile> =
    paths.mapNotNull { VfsUtil.findFile(Paths.get(it), true) }

  fun isApplicableTo(virtualFile: VirtualFile): Boolean =
    virtualFile.run { isInLocalFileSystem && isValid }

  fun isEditorConfigFile(virtualFile: VirtualFile): Boolean =
    EDITOR_CONFIG_FILE_NAME.equals(virtualFile.name, ignoreCase = true)

  fun isEnabledFor(project: Project, virtualFile: VirtualFile): Boolean {
    return isEnabled(CodeStyle.getSettings(project)) &&
           isApplicableTo(virtualFile) && !isEditorConfigFile(virtualFile) &&
           (!ApplicationManager.getApplication().isUnitTestMode() || isEnabledInTests)
  }
}