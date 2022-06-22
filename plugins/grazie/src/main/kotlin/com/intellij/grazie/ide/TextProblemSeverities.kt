package com.intellij.grazie.ide

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeImpl
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.spellchecker.SpellCheckerSeveritiesProvider
import java.util.function.Supplier
import javax.swing.Icon

class TextProblemSeverities: SeveritiesProvider() {
  override fun getSeveritiesHighlightInfoTypes(): MutableList<HighlightInfoType> {
    return mutableListOf(
      TextHighlightInfoType(STYLE_ERROR, STYLE_ERROR_ATTRIBUTES, GrazieIcons.StyleError),
      TextHighlightInfoType(STYLE_WARNING, STYLE_WARNING_ATTRIBUTES, GrazieIcons.StyleWarning),
      TextHighlightInfoType(STYLE_SUGGESTION, STYLE_SUGGESTION_ATTRIBUTES, GrazieIcons.StyleSuggestion),
      TextHighlightInfoType(GRAMMAR_ERROR, GRAMMAR_ERROR_ATTRIBUTES, GrazieIcons.GrammarError)
    )
  }

  private class TextHighlightInfoType(
    severity: HighlightSeverity,
    attributesKey: TextAttributesKey,
    private val icon: Icon
  ): HighlightInfoTypeImpl(severity, attributesKey), HighlightInfoType.Iconable {
    override fun getIcon(): Icon = icon
    override fun isApplicableToInspections() = false
  }

  companion object {
    private fun createStyleSeverity(name: String,
                                    displayName: Supplier<String>,
                                    displayNameCapitalized: Supplier<String>,
                                    value: Int): HighlightSeverity {
      return HighlightSeverity(
        name,
        SpellCheckerSeveritiesProvider.TYPO.myVal - value,
        displayName,
        displayNameCapitalized,
        @Suppress("InvalidBundleOrProperty")
        GrazieBundle.messagePointer("style.problem.severity.count")
      )
    }

    @JvmField
    val STYLE_ERROR = createStyleSeverity(
      "STYLE_ERROR",
      GrazieBundle.messagePointer("style.error.severity.name"),
      GrazieBundle.messagePointer("style.error.severity.name.capitalized"),
      1
    )

    @JvmField
    val STYLE_WARNING = createStyleSeverity(
      "STYLE_WARNING",
      GrazieBundle.messagePointer("style.warning.severity.name"),
      GrazieBundle.messagePointer("style.warning.severity.name.capitalized"),
      2
    )

    @JvmField
    val STYLE_SUGGESTION = createStyleSeverity(
      "STYLE_SUGGESTION",
      GrazieBundle.messagePointer("style.suggestion.severity.name"),
      GrazieBundle.messagePointer("style.suggestion.severity.name.capitalized"),
      3
    )

    @JvmField
    val GRAMMAR_ERROR = HighlightSeverity(
      "GRAMMAR_ERROR",
      SpellCheckerSeveritiesProvider.TYPO.myVal + 1,
      GrazieBundle.messagePointer("grammar.error.severity.name"),
      GrazieBundle.messagePointer("grammar.error.severity.name.capitalized"),
      @Suppress("InvalidBundleOrProperty")
      GrazieBundle.messagePointer("grammar.error.severity.count")
    )

    @JvmField
    val STYLE_ERROR_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("GRAZIE_STYLE_ERROR")

    @JvmField
    val STYLE_WARNING_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("GRAZIE_STYLE_WARNING")

    @JvmField
    val STYLE_SUGGESTION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("GRAZIE_STYLE_SUGGESTION")

    @JvmField
    val GRAMMAR_ERROR_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("GRAZIE_GRAMMAR_ERROR")
  }
}
