// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.ui.layout.*
import javax.swing.JComponent

class GroovyParameterTypeHintsInlayProvider : InlayHintsProvider<NoSettings> {

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    return GroovyParameterTypeHintsCollector(editor)
  }

  override fun createSettings(): NoSettings {
    return settings
  }

  companion object {
    val ourKey: SettingsKey<NoSettings> = SettingsKey("groovy.parameters.hints")

    val settings = NoSettings()
  }

  override val name: String
    get() = "Parameter types"
  override val key: SettingsKey<NoSettings>
    get() = ourKey
  override val previewText: String?
    get() = "def foo(a) {}" +
            "foo(1)"

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
    object : ImmediateConfigurable {
      override fun createComponent(listener: ChangeListener): JComponent {
        return panel {}
      }
    }
}