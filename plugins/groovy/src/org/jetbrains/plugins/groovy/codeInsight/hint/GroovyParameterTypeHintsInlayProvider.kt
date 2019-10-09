// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.*
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.ui.layout.*
import org.jetbrains.plugins.groovy.GroovyLanguage
import javax.swing.JPanel

class GroovyParameterTypeHintsInlayProvider : InlayHintsProvider<GroovyParameterTypeHintsInlayProvider.Settings> {

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector? {
    return GroovyParameterTypeHintsCollector(editor, settings)
  }

  override fun createSettings(): Settings = Settings()

  data class Settings(var showInferredParameterTypes: Boolean = true, var showTypeParameterList: Boolean = true)
  companion object {

    val ourKey: SettingsKey<Settings> = SettingsKey("groovy.parameters.hints")

  }


  fun getBaseLanguage(): Language = GroovyLanguage

  override val name: String
    get() = "Parameter types"

  override val key: SettingsKey<Settings>
    get() = ourKey

  override val previewText: String?
    get() = "def foo(a) {}\n" +
            "foo(1)\n\n\n" +
            "def bar(a, b) {\n" +
            "  a.add(b)\n" +
            "}\n" +
            "bar([1], 1)\n" +
            "bar(['q'], 'q')"

  override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
    override val cases: List<ImmediateConfigurable.Case> = listOf(
      ImmediateConfigurable.Case("Inferred parameter types", "inferred.parameter.types", settings::showInferredParameterTypes),
      ImmediateConfigurable.Case("Type parameter list", "type.parameter.list", settings::showTypeParameterList)
    )

    override fun createComponent(listener: ChangeListener): JPanel = panel {}

    override val mainCheckboxText: String
      get() = "Show type hints for:"
  }
}