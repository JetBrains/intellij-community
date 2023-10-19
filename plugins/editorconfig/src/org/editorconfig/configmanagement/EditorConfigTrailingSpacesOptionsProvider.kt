// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.openapi.fileEditor.TrailingSpacesOptionsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.ec4j.core.ResourceProperties
import org.editorconfig.Utils
import org.editorconfig.Utils.configValueForKey
import org.editorconfig.plugincomponents.EditorConfigPropertiesService

internal class EditorConfigTrailingSpacesOptionsProvider : TrailingSpacesOptionsProvider, StandardEditorConfigProperties {
  override fun getOptions(project: Project, file: VirtualFile): TrailingSpacesOptionsProvider.Options? {
    if (Utils.isEnabledFor(project, file)) {
      val properties = EditorConfigPropertiesService.getInstance(project).getProperties(file)
      val trimTrailingWhitespace = getBooleanValue(properties, StandardEditorConfigProperties.TRIM_TRAILING_WHITESPACE)
      val insertFinalNewline = getBooleanValue(properties, StandardEditorConfigProperties.INSERT_FINAL_NEWLINE)
      if (trimTrailingWhitespace != null || insertFinalNewline != null) {
        return FileOptions(trimTrailingWhitespace, insertFinalNewline)
      }
    }
    return null
  }

  private class FileOptions(private val myTrimTrailingSpaces: Boolean?,
                            private val myInsertFinalNewLine: Boolean?) : TrailingSpacesOptionsProvider.Options {
    override fun getStripTrailingSpaces(): Boolean? {
      return myTrimTrailingSpaces
    }

    override fun getEnsureNewLineAtEOF(): Boolean? {
      return myInsertFinalNewLine
    }

    override fun getRemoveTrailingBlankLines(): Boolean? {
      return null
    }

    override fun getChangedLinesOnly(): Boolean? {
      return if (myTrimTrailingSpaces != null) !myTrimTrailingSpaces else null
    }

    override fun getKeepTrailingSpacesOnCaretLine(): Boolean? {
      return null
    }
  }

  private fun getBooleanValue(properties: ResourceProperties, key: String): Boolean? {
    val rawValue = properties.configValueForKey(key)
    return when {
      "false".equals(rawValue, ignoreCase = true) -> false
      "true".equals(rawValue, ignoreCase = true) -> true
      else -> null
    }

  }
}