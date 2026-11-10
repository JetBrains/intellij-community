// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private const val SAMPLE_FILE: @NonNls String = "config/settings.json"
private const val SAMPLE_FOLDER: @NonNls String = "build/output"

internal class TextFieldWithBrowseButtonPanel : UISandboxPanel {

  override val title: String = "TextFieldWithBrowseButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.text.field.with.browse.button.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.text.field.with.browse.button.group.default")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.text.field.with.browse.button.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.text.field.with.browse.button.label.file")) {
          val descriptor = FileChooserDescriptorFactory.singleFile()
            .withTitle(DevkitUiDslBundle.message("sandbox.text.field.with.browse.button.title.file"))
          textFieldWithBrowseButton(descriptor)
            .columns(COLUMNS_MEDIUM)
            .text(SAMPLE_FILE)
        }
        row(DevkitUiDslBundle.message("sandbox.text.field.with.browse.button.label.folder")) {
          val descriptor = FileChooserDescriptorFactory.singleDir()
            .withTitle(DevkitUiDslBundle.message("sandbox.text.field.with.browse.button.title.folder"))
          textFieldWithBrowseButton(descriptor)
            .columns(COLUMNS_MEDIUM)
            .text(SAMPLE_FOLDER)
        }
        row(DevkitUiDslBundle.message("sandbox.text.field.with.browse.button.label.disabled")) {
          textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile())
            .columns(COLUMNS_MEDIUM)
            .text(SAMPLE_FILE)
            .enabled(false)
        }
      }
    }
  }
}
