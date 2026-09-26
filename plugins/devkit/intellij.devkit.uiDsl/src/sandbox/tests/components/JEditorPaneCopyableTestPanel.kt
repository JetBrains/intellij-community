// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.setCopyable
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JComponent
import javax.swing.JEditorPane

internal class JEditorPaneCopyableTestPanel : UISandboxPanel {

  override val title: String = "JEditorPane.setCopyable"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var text: Cell<JEditorPane>
      lateinit var commentText: Cell<JEditorPane>
      row {
        checkBox(DevkitUiDslBundle.message("sandbox.checkbox.copyable"))
          .selected(true)
          .onChanged {
            text.setCopyable(it.isSelected)
            commentText.setCopyable(it.isSelected)
          }
      }

      row {
        text = text(DevkitUiDslBundle.message("sandbox.label.some.copyable.or.not.text", "long ".repeat(50)))
          .comment(DevkitUiDslBundle.message("sandbox.text.some.copyable.or.not.comment", "long ".repeat(20)))
        text.setCopyable(true)
      }

      row {
        commentText = comment(DevkitUiDslBundle.message("sandbox.text.some.copyable.or.not.comment", "long ".repeat(50)))
        commentText.setCopyable(true)
      }

      row {
        textField()
          .comment(DevkitUiDslBundle.message("sandbox.text.check.that.test.selection.hidden"))
      }
    }
  }

  private fun Cell<JEditorPane>.setCopyable(copyable: Boolean) {
    component.setCopyable(copyable)
    comment?.setCopyable(copyable)
  }
}