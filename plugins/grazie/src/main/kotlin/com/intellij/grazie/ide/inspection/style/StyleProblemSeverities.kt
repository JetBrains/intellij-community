package com.intellij.grazie.ide.inspection.style

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeImpl
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.spellchecker.SpellCheckerSeveritiesProvider
import javax.swing.Icon

class StyleProblemSeverities: SeveritiesProvider() {
  override fun getSeveritiesHighlightInfoTypes(): MutableList<HighlightInfoType> {
    return mutableListOf(
      StyleHighlightInfoType(STYLE_ERROR, StyleProblemTextAttributes.STYLE_ERROR, GrazieIcons.StyleError),
      StyleHighlightInfoType(STYLE_WARNING, StyleProblemTextAttributes.STYLE_WARNING, GrazieIcons.StyleWarning),
      StyleHighlightInfoType(STYLE_SUGGESTION, StyleProblemTextAttributes.STYLE_SUGGESTION, GrazieIcons.StyleSuggestion)
    )
  }

  private class StyleHighlightInfoType(
    severity: HighlightSeverity,
    attributesKey: TextAttributesKey,
    private val icon: Icon
  ): HighlightInfoTypeImpl(severity, attributesKey), HighlightInfoType.Iconable {
    override fun getIcon(): Icon = icon
    override fun isApplicableToInspections() = false
  }

  companion object {
    private fun createStyleSeverity(name: String, value: Int): HighlightSeverity {
      return HighlightSeverity(
        name,
        SpellCheckerSeveritiesProvider.TYPO.myVal - value,
        GrazieBundle.messagePointer("style.problem.severity.name"),
        GrazieBundle.messagePointer("style.problem.severity.name.capitalized"),
        @Suppress("InvalidBundleOrProperty")
        GrazieBundle.messagePointer("style.problem.severity.count")
      )
    }

    @JvmField
    val STYLE_ERROR = createStyleSeverity("STYLE_ERROR", 1)

    @JvmField
    val STYLE_WARNING = createStyleSeverity("STYLE_WARNING", 2)

    @JvmField
    val STYLE_SUGGESTION = createStyleSeverity("STYLE_SUGGESTION", 3)
  }
}
