// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.FileIndentOptionsProvider
import com.intellij.psi.codeStyle.IndentStatusBarUIContributor
import org.ec4j.core.ResourceProperties
import org.editorconfig.Utils
import org.editorconfig.Utils.configValueForKey
import org.editorconfig.plugincomponents.EditorConfigPropertiesService

// Handles the following EditorConfig settings:
internal const val indentSizeKey = "indent_size"
private const val continuationSizeKey = "continuation_indent_size"
internal const val tabWidthKey = "tab_width"
internal  const val indentStyleKey = "indent_style"

internal class EditorConfigIndentOptionsProvider : FileIndentOptionsProvider() {
  override fun getIndentOptions(project: Project, settings: CodeStyleSettings, file: VirtualFile): CommonCodeStyleSettings.IndentOptions? {
    if (Utils.isFullIntellijSettingsSupport()) return null
    if (project.isDisposed || !Utils.isEnabled(settings)) return null
    // Get editorconfig settings
    val properties = EditorConfigPropertiesService.getInstance(project).getProperties(file)
    // Apply editorconfig settings for the current editor
    return applyCodeStyleSettings(project, properties, file, settings)
  }

  override fun getIndentStatusBarUiContributor(indentOptions: CommonCodeStyleSettings.IndentOptions): IndentStatusBarUIContributor {
    return EditorConfigIndentStatusBarUIContributor(indentOptions)
  }


  private fun applyCodeStyleSettings(project: Project,
                                     properties: ResourceProperties,
                                     file: VirtualFile,
                                     settings: CodeStyleSettings): CommonCodeStyleSettings.IndentOptions? {
    // Apply indent options
    val indentSize = properties.configValueForKey(indentSizeKey)
    val continuationIndentSize = properties.configValueForKey(continuationSizeKey)
    val tabWidth = properties.configValueForKey(tabWidthKey)
    val indentStyle = properties.configValueForKey(indentStyleKey)
    val indentOptions = settings.getIndentOptions(file.fileType).clone() as CommonCodeStyleSettings.IndentOptions
    if (applyIndentOptions(project, indentOptions, indentSize, continuationIndentSize, tabWidth, indentStyle, file.canonicalPath)) {
      indentOptions.isOverrideLanguageOptions = true
      return indentOptions
    }
    return null
  }

  private fun applyIndentOptions(project: Project, indentOptions: CommonCodeStyleSettings.IndentOptions,
                                 indentSize: String, continuationIndentSize: String, tabWidth: String,
                                 indentStyle: String, filePath: String?): Boolean {
    var changed = false
    val calculatedIndentSize = calculateIndentSize(tabWidth, indentSize, indentOptions)
    val calculatedContinuationSize = calculateContinuationIndentSize(calculatedIndentSize, continuationIndentSize)
    val calculatedTabWidth = calculateTabWidth(tabWidth, indentSize)
    if (!calculatedIndentSize.isEmpty()) {
      if (applyIndentSize(indentOptions, calculatedIndentSize)) {
        changed = true
      }
      else {
        Utils.invalidConfigMessage(project, calculatedIndentSize, indentSizeKey, filePath)
      }
    }
    if (!calculatedContinuationSize.isEmpty()) {
      if (applyContinuationIndentSize(indentOptions, calculatedContinuationSize)) {
        changed = true
      }
      else {
        Utils.invalidConfigMessage(project, calculatedIndentSize, indentSizeKey, filePath)
      }
    }
    if (!calculatedTabWidth.isEmpty()) {
      if (applyTabWidth(indentOptions, calculatedTabWidth)) {
        changed = true
      }
      else {
        Utils.invalidConfigMessage(project, calculatedTabWidth, tabWidthKey, filePath)
      }
    }
    if (!indentStyle.isEmpty()) {
      if (applyIndentStyle(indentOptions, indentStyle)) {
        changed = true
      }
      else {
        Utils.invalidConfigMessage(project, indentStyle, indentStyleKey, filePath)
      }
    }
    return changed
  }

  private fun calculateIndentSize(tabWidth: String, indentSize: String, options: CommonCodeStyleSettings.IndentOptions): String =
    if (indentSize == "tab") tabWidth.ifEmpty { options.TAB_SIZE.toString() }
    else indentSize

  private fun calculateContinuationIndentSize(indentSize: String, continuationIndentSize: String): String =
    continuationIndentSize.ifEmpty { indentSize }

  private fun calculateTabWidth(tabWidth: String, indentSize: String): String =
    if (tabWidth.isEmpty() && indentSize == "tab") {
      ""
    }
    else if (tabWidth.isEmpty()) {
      indentSize
    }
    else {
      tabWidth
    }

  private fun applyIndentSize(indentOptions: CommonCodeStyleSettings.IndentOptions, indentSize: String): Boolean =
    indentSize.toIntOrNull()?.let {
      indentOptions.INDENT_SIZE = it
      true
    } ?: false

  private fun applyContinuationIndentSize(indentOptions: CommonCodeStyleSettings.IndentOptions, continuationIndentSize: String): Boolean =
    continuationIndentSize.toIntOrNull()?.let {
      indentOptions.CONTINUATION_INDENT_SIZE = it
      true
    } ?: false

  private fun applyTabWidth(indentOptions: CommonCodeStyleSettings.IndentOptions, tabWidth: String): Boolean =
    tabWidth.toIntOrNull()?.let {
      indentOptions.TAB_SIZE = it
      true
    } ?: false

  private fun applyIndentStyle(indentOptions: CommonCodeStyleSettings.IndentOptions, indentStyle: String): Boolean {
    if (indentStyle == "tab" || indentStyle == "space") {
      indentOptions.USE_TAB_CHARACTER = indentStyle == "tab"
      return true
    }
    return false
  }
}
