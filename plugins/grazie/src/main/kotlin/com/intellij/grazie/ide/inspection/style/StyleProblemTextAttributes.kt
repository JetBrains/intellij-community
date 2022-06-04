package com.intellij.grazie.ide.inspection.style

import com.intellij.openapi.editor.colors.TextAttributesKey

object StyleProblemTextAttributes {
  @JvmField
  val STYLE_ERROR = TextAttributesKey.createTextAttributesKey("GRAZIE_STYLE_ERROR")

  @JvmField
  val STYLE_WARNING = TextAttributesKey.createTextAttributesKey("GRAZIE_STYLE_WARNING")

  @JvmField
  val STYLE_SUGGESTION = TextAttributesKey.createTextAttributesKey("GRAZIE_STYLE_SUGGESTION")
}
