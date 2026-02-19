package com.intellij.editorconfig.common.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.CLASS_REFERENCE
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.INSTANCE_FIELD
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object EditorConfigTextAttributeKeys {
  val PROPERTY_KEY: TextAttributesKey = TextAttributesKey.createTextAttributesKey("EDITORCONFIG_PROPERTY_KEY", INSTANCE_FIELD)
  val KEY_DESCRIPTION: TextAttributesKey = TextAttributesKey.createTextAttributesKey("EDITORCONFIG_VARIABLE", CLASS_REFERENCE)
}
