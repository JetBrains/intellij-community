// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.withStateLabel
import com.intellij.openapi.Disposable
import com.intellij.ui.SearchTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class SearchTextFieldPanel : UISandboxPanel {

  override val title: String = "SearchTextField"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      withStateLabel {
        cell(SearchTextField())
      }
      withStateLabel {
        cell(SearchTextField()).applyToComponent {
          textEditor.isEnabled = false
        }
      }
    }
  }
}