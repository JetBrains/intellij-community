package com.intellij.grazie.ide

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeImpl
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.spellchecker.SpellCheckerSeveritiesProvider
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

class TextProblemSeverities : SeveritiesProvider() {
  override fun getSeveritiesHighlightInfoTypes(): List<HighlightInfoType> {
    return listOf(
      TextHighlightInfoType(STYLE_SUGGESTION, STYLE_SUGGESTION_ATTRIBUTES, GrazieIcons.StyleSuggestion),
      TextHighlightInfoType(GRAMMAR_ERROR, GRAMMAR_ERROR_ATTRIBUTES, AllIcons.General.InspectionsGrammar, applicableToInspections = true)
    )
  }

  private class TextHighlightInfoType(
    severity: HighlightSeverity,
    attributesKey: TextAttributesKey,
    private val icon: Icon,
    private val applicableToInspections: Boolean = false,
  ) : HighlightInfoTypeImpl(severity, attributesKey), HighlightInfoType.Iconable {
    override fun getIcon(): Icon = icon
    override fun isApplicableToInspections() = applicableToInspections
  }

  @Suppress("CompanionObjectInExtension")
  companion object {

    @JvmField
    val STYLE_SUGGESTION: HighlightSeverity = HighlightSeverity(
      "STYLE_SUGGESTION",
      SpellCheckerSeveritiesProvider.TYPO.myVal - 1,
      GrazieBundle.messagePointer("style.suggestion.severity.name"),
      GrazieBundle.messagePointer("style.suggestion.severity.name.capitalized"),
      @Suppress("InvalidBundleOrProperty")
      GrazieBundle.messagePointer("style.problem.severity.count")
    )

    @JvmField
    @Deprecated("Use STYLE_SUGGESTION instead", ReplaceWith("STYLE_SUGGESTION"))
    @ApiStatus.ScheduledForRemoval
    val STYLE_ERROR: HighlightSeverity = STYLE_SUGGESTION

    @JvmField
    @Deprecated("Use STYLE_SUGGESTION instead", ReplaceWith("STYLE_SUGGESTION"))
    @ApiStatus.ScheduledForRemoval
    val STYLE_WARNING: HighlightSeverity = STYLE_SUGGESTION

    @JvmField
    val GRAMMAR_ERROR: HighlightSeverity = HighlightSeverity(
      "GRAMMAR_ERROR",
      SpellCheckerSeveritiesProvider.TYPO.myVal + 1,
      GrazieBundle.messagePointer("grammar.error.severity.name"),
      GrazieBundle.messagePointer("grammar.error.severity.name.capitalized"),
      @Suppress("InvalidBundleOrProperty")
      GrazieBundle.messagePointer("grammar.error.severity.count")
    )

    @JvmField
    val STYLE_SUGGESTION_ATTRIBUTES: TextAttributesKey = TextAttributesKey.createTextAttributesKey("TEXT_STYLE_SUGGESTION")

    @JvmField
    @Deprecated("Use STYLE_SUGGESTION_ATTRIBUTES instead", ReplaceWith("STYLE_SUGGESTION_ATTRIBUTES"))
    @ApiStatus.ScheduledForRemoval
    val STYLE_ERROR_ATTRIBUTES: TextAttributesKey = STYLE_SUGGESTION_ATTRIBUTES

    @JvmField
    @Deprecated("Use STYLE_SUGGESTION_ATTRIBUTES instead", ReplaceWith("STYLE_SUGGESTION_ATTRIBUTES"))
    @ApiStatus.ScheduledForRemoval
    val STYLE_WARNING_ATTRIBUTES: TextAttributesKey = STYLE_SUGGESTION_ATTRIBUTES

    @JvmField
    val GRAMMAR_ERROR_ATTRIBUTES: TextAttributesKey = TextAttributesKey.createTextAttributesKey("GRAMMAR_ERROR")
  }
}
