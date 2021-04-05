// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.ui.layout.*
import org.jetbrains.plugins.groovy.GroovyBundle
import javax.swing.JPanel

class GroovyLocalVariableTypeHintsInlayProvider : InlayHintsProvider<NoSettings> {
  companion object {
    val ourKey: SettingsKey<NoSettings> = SettingsKey("groovy.variable.type.hints")
  }

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector = GroovyLocalVariableTypeHintsCollector(editor)

  override fun createSettings(): NoSettings = NoSettings()

  override val name: String = GroovyBundle.message("local.variable.types")
  override val key: SettingsKey<NoSettings> = ourKey
  override val previewText: String = """
def foo() {
  def x = 1
  var y = "abc"
}
  """.trimIndent()

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
    override val cases: List<ImmediateConfigurable.Case> = emptyList()

    override fun createComponent(listener: ChangeListener): JPanel = panel {}

    override val mainCheckboxText: String
      get() = GroovyBundle.message("settings.inlay.show.variable.type.hints")
  }
}