// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.highlighting

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.psi.codeStyle.DisplayPrioritySortable
import org.editorconfig.language.filetype.EditorConfigFileType
import org.editorconfig.language.messages.EditorConfigBundle.get
import javax.swing.Icon

internal class EditorConfigColorSettingsPage : ColorSettingsPage, DisplayPrioritySortable {
  override fun getIcon(): Icon = AllIcons.Nodes.Editorconfig
  override fun getHighlighter(): SyntaxHighlighter = EditorConfigSyntaxHighlighter
  override fun getDemoText() =
    """<propertyKey>root</propertyKey> = <propertyValue>true</propertyValue>
      |
      |# this is a comment
      |
      |unexpected_symbol
      |
      |[<specialSymbol>*</specialSymbol><pattern>.java</pattern>] ; this is comment, too
      |<propertyKey>key</propertyKey> = <propertyValue>value1</propertyValue>, <propertyValue>value2</propertyValue>:<propertyValue>value3</propertyValue>
      |
      |# some special symbols
      |[[<specialSymbol>!</specialSymbol>abcdef]<specialSymbol>?</specialSymbol>{<specialSymbol>*</specialSymbol><pattern>.kt</pattern>, <specialSymbol>*</specialSymbol><pattern>.kts</pattern>}]
      |<propertyKey>key</propertyKey> = <propertyValue>other-value</propertyValue>
      |
      |# valid escapes
      |[<validEscape>\#</validEscape><validEscape>\*</validEscape><validEscape>\\</validEscape>]
      |
      |# invalid escapes
      |[<invalidEscape>\J</invalidEscape><pattern>etBrains</pattern>]
      |<propertyKey>great</propertyKey> = <propertyValue>true</propertyValue>
      |
      |# .NET keys
      |<keyDescription>dotnet_naming_rule</keyDescription>.<propertyKey>my_rule</propertyKey>.<keyDescription>severity</keyDescription> = <propertyValue>warning</propertyValue>
      |""".trimMargin()

  override fun getDisplayName() = EditorConfigFileType.fileTypeName
  override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey> = ADDITIONAL_HIGHLIGHTING
  override fun getAttributeDescriptors() = DESCRIPTORS
  override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
  override fun getPriority() = DisplayPriority.LANGUAGE_SETTINGS

  private val DESCRIPTORS = arrayOf(
    AttributesDescriptor(get("color.settings.property.key"), EditorConfigSyntaxHighlighter.PROPERTY_KEY),
    AttributesDescriptor(get("color.settings.property.key.description"), EditorConfigSyntaxHighlighter.KEY_DESCRIPTION),
    AttributesDescriptor(get("color.settings.property.value"), EditorConfigSyntaxHighlighter.PROPERTY_VALUE),
    AttributesDescriptor(get("color.settings.pattern"), EditorConfigSyntaxHighlighter.PATTERN),
    AttributesDescriptor(get("color.settings.special.symbol"), EditorConfigSyntaxHighlighter.SPECIAL_SYMBOL),
    AttributesDescriptor(get("color.settings.valid.char.escape"), EditorConfigSyntaxHighlighter.VALID_CHAR_ESCAPE),
    AttributesDescriptor(get("color.settings.invalid.char.escape"), EditorConfigSyntaxHighlighter.INVALID_CHAR_ESCAPE),

    AttributesDescriptor(get("color.settings.separator"), EditorConfigSyntaxHighlighter.SEPARATOR),
    AttributesDescriptor(get("color.settings.brace"), EditorConfigSyntaxHighlighter.BRACE),
    AttributesDescriptor(get("color.settings.bracket"), EditorConfigSyntaxHighlighter.BRACKET),
    AttributesDescriptor(get("color.settings.comma"), EditorConfigSyntaxHighlighter.COMMA),
    AttributesDescriptor(get("color.settings.identifier"), EditorConfigSyntaxHighlighter.IDENTIFIER),
    AttributesDescriptor(get("color.settings.comment"), EditorConfigSyntaxHighlighter.COMMENT)
  )

  private val ADDITIONAL_HIGHLIGHTING = mutableMapOf(
    "propertyKey" to EditorConfigSyntaxHighlighter.PROPERTY_KEY,
    "keyDescription" to EditorConfigSyntaxHighlighter.KEY_DESCRIPTION,
    "propertyValue" to EditorConfigSyntaxHighlighter.PROPERTY_VALUE,
    "pattern" to EditorConfigSyntaxHighlighter.PATTERN,
    "specialSymbol" to EditorConfigSyntaxHighlighter.SPECIAL_SYMBOL,
    "validEscape" to EditorConfigSyntaxHighlighter.VALID_CHAR_ESCAPE,
    "invalidEscape" to EditorConfigSyntaxHighlighter.INVALID_CHAR_ESCAPE
  )
}
