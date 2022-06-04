// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeStyle

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyFileType
import javax.swing.JComponent

class GrCodeStyleGroovydocPanel(settings : CodeStyleSettings) : CodeStyleAbstractPanel(settings) {

  private var myGroovyDocFormattingEnabled : Boolean = true

  override fun getTabTitle(): String = GroovyBundle.message("code.style.groovydoc.tab.name")

  override fun getRightMargin(): Int = 0

  override fun createHighlighter(scheme: EditorColorsScheme?): EditorHighlighter? = null

  override fun getFileType(): FileType = GroovyFileType.GROOVY_FILE_TYPE

  override fun getPreviewText(): String? = null

  override fun apply(settings: CodeStyleSettings) {
    myPanel.apply()
    settings.groovySettings.ENABLE_GROOVYDOC_FORMATTING = myGroovyDocFormattingEnabled
  }

  override fun isModified(settings: CodeStyleSettings): Boolean {
    return myGroovyDocFormattingEnabled != settings.groovySettings.ENABLE_GROOVYDOC_FORMATTING || myPanel.isModified()
  }

  override fun getPanel(): JComponent = myPanel

  private val myPanel = panel {
    row {
      checkBox("Enable GroovyDoc formatting").bindSelected(this@GrCodeStyleGroovydocPanel::myGroovyDocFormattingEnabled)
    }
  }


  private val CodeStyleSettings.groovySettings get() = getCustomSettings(GroovyCodeStyleSettings::class.java)

  override fun resetImpl(settings: CodeStyleSettings) {
    myGroovyDocFormattingEnabled = settings.groovySettings.ENABLE_GROOVYDOC_FORMATTING
    myPanel.reset()
  }
}