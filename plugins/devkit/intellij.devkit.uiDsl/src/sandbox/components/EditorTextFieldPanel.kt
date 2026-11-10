// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.columns
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.NonNls
import java.awt.Dimension
import javax.swing.JComponent

private const val JSON_EXTENSION: @NonNls String = "json"
private const val ONE_LINE_JSON: @NonNls String = """{"name": "Sandbox", "version": 2}"""
private const val MULTILINE_JSON: @NonNls String = """{
  "name": "Sandbox",
  "version": 2,
  "description": "This line is longer than the field, so the field shows a horizontal scrollbar.",
  "panels": [
    "ActionButton",
    "ActionToolbar",
    "EditorTextField",
    "Link",
    "ToolbarDecorator"
  ],
  "internal": true,
  "theme": "Islands"
}"""
private const val MULTILINE_HEIGHT = 120

internal class EditorTextFieldPanel : UISandboxPanel {

  override val title: String = "EditorTextField"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.editor.text.field.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.editor.text.field.group.one.line")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.editor.text.field.one.line.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.editor.text.field.label.default")) {
          cell(createField(ONE_LINE_JSON, oneLine = true))
        }
        row(DevkitUiDslBundle.message("sandbox.editor.text.field.label.placeholder")) {
          cell(createField("", oneLine = true).apply {
            setPlaceholder(DevkitUiDslBundle.message("sandbox.editor.text.field.placeholder"))
            setShowPlaceholderWhenFocused(true)
          })
        }
        row(DevkitUiDslBundle.message("sandbox.editor.text.field.label.disabled")) {
          cell(createField(ONE_LINE_JSON, oneLine = true).apply { isEnabled = false })
        }
      }

      group(DevkitUiDslBundle.message("sandbox.editor.text.field.group.multiline")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.editor.text.field.multiline.note"))
        }
        row {
          cell(createMultilineField())
        }
      }
    }
  }

  private fun createField(text: @NonNls String, oneLine: Boolean): EditorTextField {
    val project = ProjectManager.getInstance().defaultProject
    return EditorTextField(text, project, jsonFileType()).apply {
      setOneLineMode(oneLine)
      columns(COLUMNS_MEDIUM)
    }
  }

  private fun createMultilineField(): EditorTextField {
    return createField(MULTILINE_JSON, oneLine = false).apply {
      addSettingsProvider { editor ->
        editor.setVerticalScrollbarVisible(true)
        editor.setHorizontalScrollbarVisible(true)
      }
      preferredSize = Dimension(COLUMNS_LARGE * getFontMetrics(font).charWidth('m'), JBUI.scale(MULTILINE_HEIGHT))
    }
  }

  private fun jsonFileType(): FileType {
    return FileTypeManager.getInstance().getFileTypeByExtension(JSON_EXTENSION)
  }
}
