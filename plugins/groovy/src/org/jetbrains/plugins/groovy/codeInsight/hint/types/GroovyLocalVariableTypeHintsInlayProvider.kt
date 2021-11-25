// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.ui.layout.*
import org.jetbrains.plugins.groovy.GroovyBundle
import javax.swing.JPanel

class GroovyLocalVariableTypeHintsInlayProvider : InlayHintsProvider<GroovyLocalVariableTypeHintsInlayProvider.Settings> {
  companion object {
    private val ourKey: SettingsKey<Settings> = SettingsKey("groovy.variable.type.hints")
  }

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector = GroovyLocalVariableTypeHintsCollector(editor, settings)

  override fun createSettings(): Settings = Settings()

  data class Settings(var insertBeforeIdentifier : Boolean = false)

  override val name: String = GroovyBundle.message("local.variable.types")
  override val key: SettingsKey<Settings> = ourKey
  override val group: InlayGroup
    get() = InlayGroup.TYPES_GROUP

  override fun getProperty(key: String): String {
    return GroovyBundle.message(key)
  }

  override val previewText: String = """
def foo() {
  def x = 1
  var y = "abc"
}
  """.trimIndent()

  override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
    override val cases: List<ImmediateConfigurable.Case> = listOf(
      ImmediateConfigurable.Case(GroovyBundle.message("settings.inlay.put.type.hint.before.identifier"), "inferred.parameter.types", settings::insertBeforeIdentifier),
    )

    override fun createComponent(listener: ChangeListener): JPanel = panel {}

    override val mainCheckboxText: String
      get() = GroovyBundle.message("settings.inlay.show.variable.type.hints")
  }
}