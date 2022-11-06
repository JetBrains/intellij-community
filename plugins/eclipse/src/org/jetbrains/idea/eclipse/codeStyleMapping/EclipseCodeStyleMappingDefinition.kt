// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions.*
import org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions.addBlankLinesMapping
import org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions.addCommentsMapping
import org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions.addLineWrappingMapping
import org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions.addParenthesesPositionsMapping
import org.jetbrains.idea.eclipse.codeStyleMapping.util.*
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.ParenPositionConvertorFactory
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions

interface AllJavaCodeStyleSettings {
  val general: CodeStyleSettings
  val common: CommonCodeStyleSettings
  val indent: CommonCodeStyleSettings.IndentOptions
  val custom: JavaCodeStyleSettings
  var safeRightMargin: Int
  var isEnsureNewLineAtEOF: Boolean

  companion object {
    private class AllJavaCodeStyleSettingsImpl(override val general: CodeStyleSettings) : AllJavaCodeStyleSettings {
      override val common: CommonCodeStyleSettings = general.getCommonSettings(JavaLanguage.INSTANCE)
      override val indent: CommonCodeStyleSettings.IndentOptions = general.getLanguageIndentOptions(JavaLanguage.INSTANCE)!!
      override val custom: JavaCodeStyleSettings = general.getCustomSettings(JavaCodeStyleSettings::class.java)
      private val editor: EditorSettingsExternalizable? = EditorSettingsExternalizable.getInstance()
      override var isEnsureNewLineAtEOF: Boolean
        get() = editor?.isEnsureNewLineAtEOF ?: true
        set(value) {
          editor?.isEnsureNewLineAtEOF = value
        }
      override var safeRightMargin: Int
        get() = general.getRightMargin(JavaLanguage.INSTANCE)
        set(value) = general.setRightMargin(JavaLanguage.INSTANCE, value)
    }

    @JvmStatic
    fun from(codeStyleSettings: CodeStyleSettings): AllJavaCodeStyleSettings = AllJavaCodeStyleSettingsImpl(codeStyleSettings)
  }
}


internal class EclipseJavaCodeStyleMappingDefinitionBuilder(codeStyleSettings: CodeStyleSettings)
  : MappingDefinitionBuilder(),
    AllJavaCodeStyleSettings by AllJavaCodeStyleSettings.from(codeStyleSettings) {

  private val parenPositionConvertor = ParenPositionConvertorFactory(common.KEEP_LINE_BREAKS)

  fun SettingMapping<Boolean>.convertParenPosition(eclipseValueToExportIfTrue: String) =
    convert(parenPositionConvertor.ifInternalIsTrueExport(eclipseValueToExportIfTrue))

  // IDEA-206840
  var eclipseTabChar: String = EclipseFormatterOptions.TAB_CHAR_SPACE

  override fun preprocessId(id: String): String = EclipseFormatterOptions.completeId(id)
}

fun buildEclipseCodeStyleMappingTo(codeStyleSettings: CodeStyleSettings) =
  EclipseJavaCodeStyleMappingDefinitionBuilder(codeStyleSettings).apply {
    addIndentationMapping()
    addBracePositionsMapping()
    addParenthesesPositionsMapping()
    addWhitespaceMapping()
    addBlankLinesMapping()
    addNewLinesMapping()
    addLineWrappingMapping()
    addCommentsMapping()
    addOnOffTagsMapping()
  }.build()