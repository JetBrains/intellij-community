// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.ui.layout.*
import org.jetbrains.plugins.groovy.GroovyBundle
import javax.swing.JPanel

class GroovyImplicitNullArgumentHintProvider : InlayHintsProvider<NoSettings> {

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector {
    return GroovyImplicitNullArgumentCollector(editor)
  }

  companion object {
    val ourKey = SettingsKey<NoSettings>("groovy.implicit.null.argument.hint")
  }

  override fun createSettings(): NoSettings = NoSettings()

  override val name: String
    get() = GroovyBundle.message("settings.inlay.implicit.null.argument")
  override val key: SettingsKey<NoSettings> = ourKey
  override val group: InlayGroup
    get() = InlayGroup.VALUES_GROUP
  override val previewText: String
    get() = """
      def foo(a) {}
      
      foo()
    """.trimIndent()

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): JPanel = panel {}

    override val mainCheckboxText: String = GroovyBundle.message("settings.inlay.show.hints.for.implicit.null.argument")
  }

  override fun getProperty(key: String): String {
    return GroovyBundle.message(key)
  }
}
