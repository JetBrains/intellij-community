// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.editorconfig.Utils
import org.editorconfig.Utils.configValueForKey
import org.editorconfig.plugincomponents.SettingsProviderComponent

// TODO vague naming? Use EditorMaxLineLengthHandler or smth?
class EditorSettingsManager : EditorFactoryListener {
  companion object {
    // Handles the following EditorConfig settings:
    private const val maxLineLengthKey = "max_line_length"

    fun applyEditorSettings(editor: Editor) {
      val document = editor.document
      val file = FileDocumentManager.getInstance().getFile(document) ?: return
      if (file is LightVirtualFile) return
      val project = editor.project ?: return
      if (!Utils.isEnabled(CodeStyle.getSettings(project))) return
      val properties = SettingsProviderComponent.getInstance(project).getProperties(file)
      val maxLineLength = properties.configValueForKey(maxLineLengthKey)
      applyMaxLineLength(project, maxLineLength, editor, file)
    }

    // TODO I'm not sure that I like that the string value parsing is being done here -> use ResourceProperties?
    private fun applyMaxLineLength(project: Project, maxLineLength: String, editor: Editor, file: VirtualFile) {
      if (maxLineLength.isEmpty()) return
      if ("off" == maxLineLength) {
        setRightMarginShown(editor, false)
        return
      }
      try {
        val length = maxLineLength.toInt()
        if (length < 0) {
          Utils.invalidConfigMessage(project, maxLineLength, maxLineLengthKey, file.canonicalPath)
          return
        }
        setRightMarginShown(editor, true)
        editor.settings.setRightMargin(length)
      }
      catch (e: NumberFormatException) {
        Utils.invalidConfigMessage(project, maxLineLength, maxLineLengthKey, file.canonicalPath)
      }
    }

    private fun setRightMarginShown(editor: Editor, isShown: Boolean) {
      val rightMarginColor = if (isShown)
        AbstractColorsScheme.INHERITED_COLOR_MARKER
      else
        null
      editor.colorsScheme.setColor(EditorColors.RIGHT_MARGIN_COLOR, rightMarginColor)
    }
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    applyEditorSettings(event.editor)
  }

}